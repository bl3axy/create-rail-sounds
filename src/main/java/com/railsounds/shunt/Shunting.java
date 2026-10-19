package com.railsounds.shunt;

import com.railsounds.Compat;
import com.railsounds.RailConfig;
import com.railsounds.RailSounds;
import com.railsounds.SoundPlayer;
import com.railsounds.TrainTypes;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraption;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.schedule.ScheduleRuntime;
import com.zurrtum.create.content.trains.station.GlobalStation;
import com.zurrtum.create.infrastructure.packet.s2c.TrainEditReturnPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Coupling and uncoupling without coupler blocks.
 *
 *  - Buffers: a train that touches another one slowly stops against it instead of crashing.
 *    Faster than bufferMaxSpeed, Create's normal crash still happens.
 *  - Auto-couple: touching a cut of cars (no controls) slowly couples, like knuckle couplers.
 *  - Keys (client): couple, uncouple + number (counted from your car), uncouple the joint you look at.
 *  - Yard rules: stations that couple / drop cars when a train arrives (see YardRules).
 *
 * The actual joining/splitting is Steam 'n' Rails' TrainUtils, the same code its coupler block
 * uses, so clients stay in sync. Called by reflection so nothing breaks without S&R.
 * Names, diesel/steam marks and schedules stay with the train that "owns" the move.
 */
public final class Shunting {
    private Shunting() {}

    // ---------------- Steam 'n' Rails hooks ----------------
    private static boolean looked;
    private static Method combine, split, allLoaded;

    private static boolean snr() {
        if (!looked) {
            looked = true;
            try {
                Class<?> u = Class.forName("com.railwayteam.railways.content.coupling.TrainUtils");
                combine = u.getMethod("combineTrains", Train.class, Train.class, Vec3.class, Level.class, int.class);
                split = u.getMethod("splitTrain", Train.class, int.class);
                allLoaded = u.getMethod("allCarriagesLoaded", Train.class);
            } catch (Throwable t) {
                RailSounds.LOG.warn("[railsounds] Steam 'n' Rails not found; keybind coupling is off");
                combine = split = allLoaded = null;
            }
        }
        return combine != null;
    }

    /** set while we call S&R combine ourselves, so CombineSpacingMixin keeps the spacing we chose */
    public static boolean explicitSpacing;

    // ---------------- state ----------------
    private static final Map<String, Long> CONTACT = new HashMap<>();        // pair -> last tick touching
    private static final Map<String, Long> AUTO_TRIED = new HashMap<>();     // pair -> last auto-couple try
    private static final Map<String, Long> PENDING_SEPARATION = new HashMap<>(); // just split: no auto-couple until apart
    private static final Set<UUID> AUTO_OFF = new HashSet<>();               // players who turned auto-couple off
    private static final Map<String, Long> MSG_COOLDOWN = new HashMap<>();
    private static final List<Runnable> QUEUE = new ArrayList<>();
    private static long now;

    public static void reset() {
        CONTACT.clear();
        AUTO_TRIED.clear();
        PENDING_SEPARATION.clear();
        MSG_COOLDOWN.clear();
        QUEUE.clear();
        TrainEnds.clearCache();
        YardRules.resetArrivals();
    }

    private static String pair(Train a, Train b) {
        return a.id.compareTo(b.id) < 0 ? a.id + "|" + b.id : b.id + "|" + a.id;
    }

    private static Iterable<Train> trains() {
        return new ArrayList<>(Create.RAILWAYS.trains.values());
    }

    // =====================================================================================
    // Buffers + auto-couple. Runs inside Create's Train.tick, right before the train moves.
    // =====================================================================================
    public static void preMove(Train train, Level level) {
        RailConfig.Values c = RailConfig.V;
        if (!c.shuntingEnabled || train.derailed || train.graph == null || train.speed == 0) return;
        if (train.carriages.isEmpty()) return;
        boolean server = !level.isClientSide();
        TrainEnds.End ours = TrainEnds.end(train, train.speed > 0);
        if (ours == null) return;
        Iterable<Train> list = server ? trains() : new ArrayList<>(Create.RAILWAYS.sided(level).trains.values());
        TrainEnds.Contact contact = TrainEnds.findContact(ours, c.contactGap, list);
        if (contact == null) return;
        Train other = contact.theirs().train();
        // how fast they're closing: us moving into them, plus them moving into us
        double toward = contact.theirs().front() ? other.speed : -other.speed;
        double closing = Math.abs(train.speed) + toward;
        if (closing <= 1e-4) return;             // they're pulling away faster than we follow
        if (closing > c.bufferMaxSpeed) return;  // way too fast: let Create crash them

        train.speed = 0; // buffers meet: stop right here
        if (!server) return;

        String key = pair(train, other);
        Long last = CONTACT.put(key, now);
        boolean firstTouch = last == null || now - last > 2;
        if (firstTouch && level instanceof ServerLevel sl) {
            float vol = (float) Math.max(0.35, Math.min(1.5, closing / c.coupleMaxSpeed)) * c.couplerVolume;
            Vec3 at = ours.face().add(contact.theirs().face()).scale(0.5);
            SoundPlayer.play(sl, at, SoundPlayer.COUPLER, vol, 0.85f + (float) Math.random() * 0.15f, c.couplerHearingRange);
        }
        considerAutoCouple(contact, closing);
    }

    private static void considerAutoCouple(TrainEnds.Contact contact, double closing) {
        RailConfig.Values c = RailConfig.V;
        Train a = contact.ours().train(), b = contact.theirs().train();
        String key = pair(a, b);
        if (!c.autoCouple || PENDING_SEPARATION.containsKey(key)) return;
        boolean aCut = !hasControls(a), bCut = !hasControls(b);
        if (!aCut && !bCut) return; // two locomotives / two powered trains never grab each other on their own
        for (ServerPlayer p : drivers(a)) if (AUTO_OFF.contains(p.getUUID())) return;
        for (ServerPlayer p : drivers(b)) if (AUTO_OFF.contains(p.getUUID())) return;
        Long tried = AUTO_TRIED.get(key);
        if (tried != null && now - tried < 40) return;
        AUTO_TRIED.put(key, now);

        if (contact.ours().front() == contact.theirs().front()) {
            tellDrivers(a, b, "orientation", "Can't couple: those cars face the other way round (Create can't flip a car)");
            return;
        }
        if (closing > c.coupleMaxSpeed) {
            tellDrivers(a, b, "fast", String.format("Too fast to couple (%.1f b/s, max %.1f) - ease in slower",
                closing * 20, c.coupleMaxSpeed * 20));
            return;
        }
        TrainEnds.End ea = contact.ours(), eb = contact.theirs();
        QUEUE.add(() -> couple(ea.train(), ea.front(), eb.train(), eb.front(), null, c.contactGap + 0.2));
    }

    /** cars without a loco coast further instead of stopping like they have brakes on */
    public static boolean coast(Train train) {
        RailConfig.Values c = RailConfig.V;
        if (!c.shuntingEnabled || c.cutCoastFactor >= 1 || c.cutCoastFactor < 0) return false;
        if (train.manualTick || train.navigation.destination != null || train.speed == 0) return false;
        if (hasControls(train)) return false;
        double dec = train.acceleration() * c.cutCoastFactor;
        train.speed = train.speed > 0 ? Math.max(train.speed - dec, 0) : Math.min(train.speed + dec, 0);
        return true;
    }

    // =====================================================================================
    // Server tick: run queued couple/split work (never inside Create's train loop)
    // =====================================================================================
    public static void tick(MinecraftServer server) {
        now = server.getTickCount();
        if (!QUEUE.isEmpty()) {
            List<Runnable> jobs = new ArrayList<>(QUEUE);
            QUEUE.clear();
            for (Runnable r : jobs) {
                try {
                    r.run();
                } catch (Throwable t) {
                    RailSounds.LOG.error("[railsounds] shunting move failed", t);
                } finally {
                    explicitSpacing = false;
                }
            }
        }
        if (now % 20 == 0) prune();
        YardRules.tick(server, now);
    }

    private static void prune() {
        CONTACT.values().removeIf(t -> now - t > 100);
        AUTO_TRIED.values().removeIf(t -> now - t > 200);
        MSG_COOLDOWN.values().removeIf(t -> now - t > 200);
        Iterator<Map.Entry<String, Long>> it = PENDING_SEPARATION.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now > e.getValue()) {
                it.remove();
                continue;
            }
            String[] ids = e.getKey().split("\\|");
            Train a = Create.RAILWAYS.trains.get(UUID.fromString(ids[0]));
            Train b = Create.RAILWAYS.trains.get(UUID.fromString(ids[1]));
            if (a == null || b == null || nearestGap(a, b) > RailConfig.V.contactGap + 0.75) it.remove();
        }
    }

    private static double nearestGap(Train a, Train b) {
        double best = Double.MAX_VALUE;
        for (boolean f : new boolean[]{true, false}) {
            TrainEnds.End e = TrainEnds.end(a, f);
            if (e == null) continue;
            TrainEnds.Contact ct = TrainEnds.findContact(e, 8, List.of(b));
            if (ct != null) best = Math.min(best, ct.gap());
        }
        return best;
    }

    // =====================================================================================
    // Player keys
    // =====================================================================================
    public static final int COUPLE = 0, UNCOUPLE = 1, UNCOUPLE_LOOK = 2, TOGGLE_AUTO = 3, HINT = 4;

    public static void onKey(ServerPlayer player, int action, int n) {
        if (!RailConfig.V.shuntingEnabled) return;
        if (!snr()) {
            bar(player, "Keybind coupling needs Steam 'n' Rails on the server");
            return;
        }
        CarriageContraptionEntity cce = player.getVehicle() instanceof CarriageContraptionEntity e ? e : null;
        Train mine = cce == null || cce.getCarriage() == null ? null : cce.getCarriage().train;
        switch (action) {
            case COUPLE -> QUEUE.add(() -> coupleKey(player, mine));
            case UNCOUPLE -> {
                if (mine == null) {
                    QUEUE.add(() -> uncoupleLook(player));
                    return;
                }
                Carriage car = cce.getCarriage();
                QUEUE.add(() -> uncoupleNumber(player, mine, car, n));
            }
            case UNCOUPLE_LOOK -> QUEUE.add(() -> uncoupleLook(player));
            case TOGGLE_AUTO -> {
                boolean off = !AUTO_OFF.remove(player.getUUID());
                if (off) AUTO_OFF.add(player.getUUID());
                bar(player, off ? "Auto-couple OFF for trains you drive (use the couple key)" : "Auto-couple ON");
            }
            case HINT -> {
                if (mine == null || mine.carriages.size() < 2) return;
                int idx = mine.carriages.indexOf(cce.getCarriage());
                int size = mine.carriages.size();
                boolean fromFront = idx < size / 2.0;
                int max = fromFront ? size - 1 - idx : idx;
                if (max <= 0) {
                    bar(player, "No couplings on that side of your car");
                    return;
                }
                bar(player, "Uncouple: hold key + 1-" + Math.min(9, max) + " (couplings counted from your car), tap alone for the one you look at");
            }
            default -> {
            }
        }
    }

    private static void coupleKey(ServerPlayer player, Train mine) {
        RailConfig.Values c = RailConfig.V;
        double reach = c.contactGap + c.couplerReach;
        TrainEnds.Contact best = null;
        if (mine != null) {
            for (boolean f : new boolean[]{true, false}) {
                TrainEnds.End e = TrainEnds.end(mine, f);
                if (e == null) continue;
                TrainEnds.Contact ct = TrainEnds.findContact(e, reach, trains());
                if (ct != null && (best == null || ct.gap() < best.gap())) best = ct;
            }
        } else {
            best = lookedAtContact(player, reach);
        }
        if (best == null) {
            bar(player, "Nothing close enough to couple to (get within " + String.format("%.1f", c.couplerReach) + " blocks)");
            return;
        }
        Train a = best.ours().train(), b = best.theirs().train();
        if (Math.abs(a.speed) > c.coupleMaxSpeed || Math.abs(b.speed) > c.coupleMaxSpeed) {
            bar(player, "Too fast to couple - slow down first");
            return;
        }
        couple(a, best.ours().front(), b, best.theirs().front(), player, reach);
    }

    private static TrainEnds.Contact lookedAtContact(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        double limit = RailConfig.V.onFootReach + 2;
        TrainEnds.Contact best = null;
        double bestScore = 2.0;
        for (Train t : trains()) {
            if (t.graph == null || t.derailed) continue;
            for (boolean f : new boolean[]{true, false}) {
                TrainEnds.End e = TrainEnds.end(t, f);
                if (e == null || e.face().distanceTo(eye) > limit) continue;
                TrainEnds.Contact ct = TrainEnds.findContact(e, reach, trains());
                if (ct == null) continue;
                Vec3 mid = e.face().add(ct.theirs().face()).scale(0.5);
                double score = rayDistance(eye, look, mid);
                if (score < bestScore) {
                    bestScore = score;
                    best = ct;
                }
            }
        }
        return best;
    }

    private static double rayDistance(Vec3 eye, Vec3 look, Vec3 p) {
        double t = Math.max(0, p.subtract(eye).dot(look));
        return p.distanceTo(eye.add(look.scale(t)));
    }

    private static void uncoupleNumber(ServerPlayer player, Train train, Carriage car, int n) {
        int size = train.carriages.size();
        int idx = train.carriages.indexOf(car);
        if (idx < 0 || size < 2) {
            bar(player, "Nothing to uncouple");
            return;
        }
        boolean fromFront = idx < size / 2.0;
        int offEnd = fromFront ? size - (idx + n) : size - (idx - n + 1);
        if (n < 1 || offEnd < 1 || offEnd > size - 1) {
            int max = fromFront ? size - 1 - idx : idx;
            bar(player, "There " + (max == 1 ? "is only 1 coupling" : "are only " + max + " couplings") + " on that side of your car");
            return;
        }
        uncouple(train, offEnd, player);
    }

    private static void uncoupleLook(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        double limit = RailConfig.V.onFootReach + 2;
        Train bestTrain = null;
        int bestJoint = -1;
        double bestScore = 2.0;
        for (Train t : trains()) {
            if (t.graph == null || t.derailed || t.carriages.size() < 2) continue;
            for (int i = 0; i < t.carriages.size() - 1; i++) {
                Vec3 p = joint(t, i);
                if (p == null || p.distanceTo(eye) > limit) continue;
                double score = rayDistance(eye, look, p);
                if (score < bestScore) {
                    bestScore = score;
                    bestTrain = t;
                    bestJoint = i;
                }
            }
        }
        if (bestTrain == null) {
            bar(player, "Look at a coupling (within " + (int) RailConfig.V.onFootReach + " blocks) to uncouple it");
            return;
        }
        uncouple(bestTrain, bestTrain.carriages.size() - (bestJoint + 1), player);
    }

    /** midpoint between car i and car i+1 */
    static Vec3 joint(Train t, int i) {
        try {
            Vec3 a = t.carriages.get(i).trailingBogey().getAnchorPosition(false);
            Vec3 b = t.carriages.get(i + 1).leadingBogey().getAnchorPosition(false);
            return a == null || b == null ? null : a.add(b).scale(0.5).add(0, 0.6, 0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // =====================================================================================
    // The two moves
    // =====================================================================================

    /**
     * Couple the given end of train a to the given end of train b.
     * @param maxGap refuse if the car bodies are further apart than this
     * @return true if it coupled
     */
    public static boolean couple(Train a, boolean aFront, Train b, boolean bFront, ServerPlayer actor, double maxGap) {
        if (!snr() || a == b || !alive(a) || !alive(b)) return false;
        if (aFront == bFront) {
            say(actor, a, b, "Can't couple: those cars face the other way round (Create can't flip a car)");
            return false;
        }
        if (Compat.isHandcar(a) || Compat.isHandcar(b)) {
            say(actor, a, b, "Handcars don't couple");
            return false;
        }
        if (!loaded(a) || !loaded(b)) {
            say(actor, a, b, "Can't couple: part of the train isn't loaded");
            return false;
        }
        // S&R joins frontTrain's back to backTrain's front
        Train front = aFront ? b : a;
        Train back = aFront ? a : b;
        TrainEnds.End ef = TrainEnds.end(front, false);
        TrainEnds.End eb = TrainEnds.end(back, true);
        if (ef == null || eb == null) return false;
        double bogeyGap = TrainEnds.bogeyGap(ef, eb);
        double bodyGap = bogeyGap - ef.overhang() - eb.overhang();
        if (bodyGap > maxGap) {
            say(actor, a, b, "Too far apart to couple");
            return false;
        }
        // spacing that puts the car bodies end to end; if they were a bit apart Create draws them in
        int spacing = (int) Math.max(1, Math.min(64, Math.round(ef.overhang() + eb.overhang())));
        if (bodyGap < -1.5) spacing = (int) Math.max(1, Math.round(bogeyGap)); // already overlapping: don't shove

        Train keeper = keeper(front, back, actor);
        Train otherTrain = keeper == front ? back : front;
        Component name = keeper.name.copy();
        var icon = keeper.icon;
        int color = keeper.mapColorIndex;
        String type = TrainTypes.get(keeper) != null ? TrainTypes.get(keeper) : TrainTypes.get(otherTrain);
        // whichever train is running a schedule keeps it (keeper first)
        Train scheduled = hasSchedule(keeper) ? keeper : hasSchedule(otherTrain) ? otherTrain : null;
        UUID station = scheduled != null && scheduled.currentStation != null ? scheduled.currentStation
            : keeper.currentStation != null ? keeper.currentStation : otherTrain.currentStation;
        int fuel = Math.max(front.fuelTicks, back.fuelTicks);
        int carsBefore = front.carriages.size() + back.carriages.size();
        Vec3 at = ef.face().add(eb.face()).scale(0.5);
        ServerLevel level = levelOf(front);
        if (level == null) return false;

        Train result;
        try {
            explicitSpacing = true;
            result = (Train) combine.invoke(null, front, back, at, level, spacing);
        } catch (Throwable t) {
            RailSounds.LOG.error("[railsounds] coupling failed", t);
            say(actor, a, b, "Coupling failed (see server log)");
            return false;
        } finally {
            explicitSpacing = false;
        }
        if (result == null || result.carriages.size() != carsBefore) {
            say(actor, a, b, "Couldn't couple those two");
            return false;
        }

        if (scheduled != null && scheduled != result) swapSchedule(scheduled, result);
        if (station != null) {
            result.currentStation = station;
            GlobalStation gs = result.getCurrentStation();
            if (gs != null) gs.reserveFor(result);
            YardRules.markAt(result);
        }
        result.fuelTicks = fuel;
        result.speed = 0;
        setName(level.getServer(), result, name, icon, color);
        if (type != null) TrainTypes.set(result, type);
        PENDING_SEPARATION.remove(pair(front, back));

        RailConfig.Values c = RailConfig.V;
        SoundPlayer.play(level, at, SoundPlayer.COUPLER, c.couplerVolume, 1.0f, c.couplerHearingRange);
        SoundPlayer.playLater(level.getServer(), 12, level, at, SoundPlayer.AIR, c.airReleaseVolume * 0.5f, 1.15f, c.couplerHearingRange);
        String msg = "Coupled - " + name.getString() + " is now " + result.carriages.size() + " cars";
        if (actor != null) bar(actor, msg);
        for (ServerPlayer p : drivers(result)) if (p != actor) bar(p, msg);
        return true;
    }

    /** take numberOffEnd cars off the back of the train */
    public static boolean uncouple(Train train, int numberOffEnd, ServerPlayer actor) {
        RailConfig.Values c = RailConfig.V;
        if (!snr() || !alive(train)) return false;
        int size = train.carriages.size();
        if (numberOffEnd < 1 || numberOffEnd >= size) return false;
        if (Compat.isHandcar(train)) return false;
        if (c.uncoupleMaxSpeed >= 0 && Math.abs(train.speed) > c.uncoupleMaxSpeed) {
            say(actor, train, null, String.format("Too fast to pull the pin (max %.1f b/s)", c.uncoupleMaxSpeed * 20));
            return false;
        }
        if (!loaded(train)) {
            say(actor, train, null, "Can't uncouple: part of the train isn't loaded");
            return false;
        }
        ServerLevel level = levelOf(train);
        if (level == null) return false;
        Vec3 at = joint(train, size - numberOffEnd - 1);
        double speed = train.speed;
        boolean scheduled = hasSchedule(train);
        UUID station = train.currentStation;

        Train cut;
        try {
            cut = (Train) split.invoke(null, train, numberOffEnd);
        } catch (Throwable t) {
            RailSounds.LOG.error("[railsounds] uncoupling failed", t);
            say(actor, train, null, "Uncoupling failed (see server log)");
            return false;
        }
        if (cut == null || cut == train) {
            say(actor, train, null, "Couldn't uncouple there");
            return false;
        }
        // both halves keep rolling; the half without a loco coasts to a stop on its own
        cut.speed = speed;
        cut.targetSpeed = 0;
        PENDING_SEPARATION.put(pair(train, cut), now + 1200);

        // the half with the driver (or else the loco) keeps the schedule and the platform
        boolean locoIsCut = !drivers(cut).isEmpty()
            || (drivers(train).isEmpty() && hasControls(cut) && !hasControls(train));
        if (locoIsCut) {
            if (scheduled) swapSchedule(train, cut);
            cut.currentStation = station;
            train.currentStation = null;
            GlobalStation gs = cut.getCurrentStation();
            if (gs != null) {
                gs.cancelReservation(train);
                gs.reserveFor(cut);
            }
            YardRules.markAt(cut);
        }
        if (at != null) {
            SoundPlayer.play(level, at, SoundPlayer.COUPLER, c.couplerVolume * 0.8f, 1.1f, c.couplerHearingRange);
            SoundPlayer.playLater(level.getServer(), 4, level, at, SoundPlayer.AIR, c.airReleaseVolume, 1.0f, c.couplerHearingRange);
        }
        String msg = "Uncoupled " + numberOffEnd + (numberOffEnd == 1 ? " car" : " cars");
        if (actor != null) bar(actor, msg);
        return true;
    }

    // ---------------- S&R's own coupler block: keep names/schedules there too ----------------
    private record Saved(Train front, Train back, Train scheduled, Component name,
                         com.zurrtum.create.content.trains.entity.TrainIconType icon, int color, String type, UUID station) {}
    private static Saved saved;

    /** called at the start of S&R's combineTrains when it wasn't us calling it */
    public static void beforeExternalCombine(Train front, Train back) {
        saved = null;
        if (explicitSpacing || front == null || back == null) return;
        Train keeper = keeper(front, back, null);
        Train other = keeper == front ? back : front;
        Train scheduled = hasSchedule(keeper) ? keeper : hasSchedule(other) ? other : null;
        String type = TrainTypes.get(keeper) != null ? TrainTypes.get(keeper) : TrainTypes.get(other);
        UUID station = scheduled != null && scheduled.currentStation != null ? scheduled.currentStation : keeper.currentStation;
        saved = new Saved(front, back, scheduled, keeper.name.copy(), keeper.icon, keeper.mapColorIndex, type, station);
    }

    public static void afterExternalCombine(Train result) {
        Saved s = saved;
        saved = null;
        if (s == null || result == null || result != s.front || !s.back.carriages.isEmpty()) return; // didn't couple
        if (s.scheduled != null && s.scheduled != result) swapSchedule(s.scheduled, result);
        if (s.station != null && result.currentStation == null) result.currentStation = s.station;
        ServerLevel level = levelOf(result);
        if (level != null) setName(level.getServer(), result, s.name, s.icon, s.color);
        if (s.type != null) TrainTypes.set(result, s.type);
    }

    // ---------------- helpers ----------------

    private static boolean alive(Train t) {
        return t != null && !t.invalid && !t.derailed && t.graph != null && !t.carriages.isEmpty()
            && Create.RAILWAYS.trains.get(t.id) == t;
    }

    private static boolean loaded(Train t) {
        try {
            return (boolean) allLoaded.invoke(null, t);
        } catch (Throwable e) {
            return false;
        }
    }

    private static ServerLevel levelOf(Train t) {
        for (Carriage car : t.carriages) {
            CarriageContraptionEntity e = car.anyAvailableEntity();
            if (e != null && e.level() instanceof ServerLevel sl) return sl;
        }
        return null;
    }

    public static boolean hasControls(Train t) {
        for (Carriage car : t.carriages) {
            CarriageContraptionEntity e = car.anyAvailableEntity();
            if (e != null && e.getContraption() instanceof CarriageContraption cc
                && (cc.hasForwardControls() || cc.hasBackwardControls())) return true;
        }
        return false;
    }

    private static boolean hasSchedule(Train t) {
        return t.runtime != null && t.runtime.getSchedule() != null;
    }

    /** move the whole schedule (progress included) from one train to another */
    private static void swapSchedule(Train from, Train to) {
        ScheduleRuntime a = from.runtime, b = to.runtime;
        from.runtime = b;
        to.runtime = a;
        if (a != null) a.train = to;
        if (b != null) b.train = from;
    }

    /** who keeps its name: the train being driven by the player doing it, then any driven one,
     *  then the one running a schedule, then the loco, then the front one */
    private static Train keeper(Train front, Train back, ServerPlayer actor) {
        if (actor != null) {
            Entity v = actor.getVehicle();
            if (v instanceof CarriageContraptionEntity e && e.getCarriage() != null) {
                if (e.getCarriage().train == back) return back;
                if (e.getCarriage().train == front) return front;
            }
        }
        boolean fd = !drivers(front).isEmpty(), bd = !drivers(back).isEmpty();
        if (fd != bd) return fd ? front : back;
        boolean fs = hasSchedule(front), bs = hasSchedule(back);
        if (fs != bs) return fs ? front : back;
        boolean fc = hasControls(front), bc = hasControls(back);
        if (fc != bc) return fc ? front : back;
        return front;
    }

    static List<ServerPlayer> drivers(Train t) {
        List<ServerPlayer> out = new ArrayList<>();
        for (Carriage car : t.carriages) {
            CarriageContraptionEntity e = car.anyAvailableEntity();
            if (e == null || !(e.level() instanceof ServerLevel sl)) continue;
            e.getControllingPlayer().ifPresent(id -> {
                ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
                if (p != null && !out.contains(p)) out.add(p);
            });
        }
        return out;
    }

    private static void setName(MinecraftServer server, Train t, Component name, com.zurrtum.create.content.trains.entity.TrainIconType icon, int color) {
        t.name = name;
        if (icon != null) t.icon = icon;
        t.mapColorIndex = color;
        server.getPlayerList().broadcastAll(new TrainEditReturnPacket(t.id, name.getString(), t.icon.id(), color));
    }

    static void bar(ServerPlayer p, String msg) {
        if (p != null) p.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(msg)));
    }

    private static void say(ServerPlayer actor, Train a, Train b, String msg) {
        if (actor != null) {
            bar(actor, msg);
            return;
        }
        tellDrivers(a, b, msg, msg);
    }

    private static void tellDrivers(Train a, Train b, String kind, String msg) {
        for (Train t : new Train[]{a, b}) {
            if (t == null) continue;
            for (ServerPlayer p : drivers(t)) {
                String k = p.getUUID() + kind;
                Long last = MSG_COOLDOWN.get(k);
                if (last != null && now - last < 60) continue;
                MSG_COOLDOWN.put(k, now);
                bar(p, msg);
            }
        }
    }

    // used by yard rules
    static void queue(Runnable r) {
        QUEUE.add(r);
    }

    static Iterable<Train> allTrains() {
        return trains();
    }

    static long now() {
        return now;
    }
}
