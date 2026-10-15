package com.railsounds;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The live part: reads each Create train's real speed every tick and turns changes into sounds. */
public class TrainSoundTracker {
    private static final double STOPPED = 0.003; // blocks/tick, basically standing still

    private static class State {
        double lastSpeed;      // |speed| last tick
        double smoothDecel;    // smoothed deceleration (positive = slowing)
        int stoppedTicks = 1000;
        int brakeTimer;        // ticks until the squeal can replay
        boolean braking;
        boolean backwards;
        double[] clackDist = new double[0]; // distance travelled since last joint, per carriage
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void reset() {
        STATES.clear();
    }

    public static void tick(MinecraftServer server) {
        if (Create.RAILWAYS == null || Create.RAILWAYS.trains == null) return;
        RailConfig.Values c = RailConfig.V;
        Set<UUID> seen = new HashSet<>();

        for (Train train : new ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (train == null || train.invalid || train.carriages == null || train.carriages.isEmpty()) continue;
            if (c.ignoreHandcars && Compat.isHandcar(train)) continue;
            seen.add(train.id);
            State s = STATES.get(train.id);
            if (s == null) {
                // first time we see this train (server start / new train): no fake "start" clunk
                s = new State();
                s.lastSpeed = Math.abs(train.speed);
                STATES.put(train.id, s);
            }
            if (s.clackDist.length != train.carriages.size()) s.clackDist = new double[train.carriages.size()];

            double speed = train.derailed ? 0 : Math.abs(train.speed);
            double decel = s.lastSpeed - speed;
            s.smoothDecel = s.smoothDecel * 0.7 + decel * 0.3;

            // carriages in the order the train is moving: leading car first.
            // remember the direction so the stop ripple is right even on the tick speed hits 0
            if (train.speed != 0) s.backwards = train.speed < 0;
            List<Carriage> cars = new ArrayList<>(train.carriages);
            if (s.backwards) java.util.Collections.reverse(cars);

            // ---------- pulling away: slack runs out car by car ----------
            if (s.lastSpeed < STOPPED && speed >= STOPPED && s.stoppedTicks >= c.minStoppedTicksForStart) {
                ripple(server, cars, s.backwards, c.couplerRippleTicksStart, c.couplerVolume, 0.95f);
            }

            // ---------- coming to a stop: slack bunches, then the air dumps ----------
            if (s.lastSpeed >= STOPPED && speed < STOPPED) {
                ripple(server, cars, s.backwards, c.couplerRippleTicksStop, c.couplerVolume * 0.7f, 1.05f);
                int delay = cars.size() * c.couplerRippleTicksStop + 10;
                for (Carriage car : cars) {
                    Located l = locate(car);
                    if (l != null) SoundPlayer.playLater(server, delay, l.level, l.pos, SoundPlayer.AIR,
                        c.airReleaseVolume, 0.9f + rnd(0.15f), c.couplerHearingRange);
                    delay += 4;
                }
                s.braking = false;
            }

            // ---------- brakes: louder the harder it's slowing ----------
            // train.acceleration() is Create's braking rate for THIS train (S&R realism / fuel adjust it)
            double accel = Math.max(1e-5, train.acceleration());
            double threshold = Math.max(c.brakeDecelMin, accel * c.brakeDecelFraction);
            boolean brakingNow = speed > c.brakeMinSpeed && s.smoothDecel > threshold;
            if (brakingNow) {
                // replay when the clip is done; a fresh brake application can restart it once it's half over
                if (s.brakeTimer <= 0 || (!s.braking && s.brakeTimer < c.brakeClipTicks / 2)) {
                    float hardness = (float) Math.min(1.5, s.smoothDecel / accel);
                    float vol = c.brakeVolume * (0.4f + 0.6f * hardness);
                    // squeal gets a bit higher pitched as the train gets slower, like real brake shoes
                    float pitch = (float) Math.max(0.8, Math.min(1.4, 1.3 - speed * 0.6));
                    for (int i = 0; i < cars.size(); i++) {
                        Located l = locate(cars.get(i));
                        if (l == null) continue;
                        SoundPlayer.playLater(server, i * 2, l.level, l.pos, SoundPlayer.BRAKE,
                            vol, pitch + rnd(0.08f), c.brakeHearingRange);
                    }
                    s.brakeTimer = c.brakeClipTicks;
                }
                s.braking = true;
            } else {
                s.braking = false;
            }
            if (s.brakeTimer > 0) s.brakeTimer--;

            // ---------- rail joints: clack-clack per bogey, speed sets the rhythm ----------
            if (speed >= c.clackMinSpeed) {
                for (int i = 0; i < train.carriages.size(); i++) {
                    s.clackDist[i] += speed;
                    if (s.clackDist[i] < c.clackSpacing) continue;
                    s.clackDist[i] -= c.clackSpacing;
                    Carriage car = train.carriages.get(i);
                    Located l = locate(car);
                    if (l == null) continue;
                    float vol = (float) Math.min(1.0, 0.35 + speed * 0.9) * c.clackVolume;
                    float pitch = (float) Math.min(1.25, 0.85 + speed * 0.35);
                    // the joint is under the front wheels right now; the rear wheels hit the same spot later
                    Vec3 joint = wheels(car, l, s.backwards, true);
                    SoundPlayer.play(l.level, joint, SoundPlayer.CLACK, vol, pitch + rnd(0.05f), c.clackHearingRange);
                    // second bogey hits the same joint a moment later
                    if (car.isOnTwoBogeys()) {
                        int gap = (int) Math.round(car.bogeySpacing / speed);
                        if (gap >= 1 && gap <= 40)
                            SoundPlayer.playLater(server, gap, l.level, joint, SoundPlayer.CLACK,
                                vol * 0.9f, pitch + rnd(0.05f), c.clackHearingRange);
                    }
                }
            }

            s.stoppedTicks = speed < STOPPED ? s.stoppedTicks + 1 : 0;
            s.lastSpeed = speed;
        }
        STATES.keySet().retainAll(seen);
    }

    /**
     * Couplers clunk one after another, from the front of the train to the back (the slack runs out
     * when pulling away, and runs in when stopping - both travel front to back on a real train).
     * Each clunk plays at the coupling between two cars, so a 3-car train makes 2 clunks.
     */
    private static void ripple(MinecraftServer server, List<Carriage> cars, boolean backwards, int gap, float vol, float pitch) {
        RailConfig.Values c = RailConfig.V;
        if (cars.size() == 1) {
            // lone loco: one soft clunk of its own draft gear
            Located l = locate(cars.get(0));
            if (l != null) SoundPlayer.playLater(server, 0, l.level, l.pos, SoundPlayer.COUPLER, vol * 0.6f, pitch, c.couplerHearingRange);
            return;
        }
        for (int i = 0; i < cars.size() - 1; i++) {
            Located a = locate(cars.get(i));
            Located b = locate(cars.get(i + 1));
            if (a == null || b == null || a.level != b.level) continue;
            // rear wheels of the front car + front wheels of the next car = where the coupling is
            Vec3 pa = wheels(cars.get(i), a, backwards, false);
            Vec3 pb = wheels(cars.get(i + 1), b, backwards, true);
            Vec3 joint = pa.add(pb).scale(0.5);
            SoundPlayer.playLater(server, i * gap, a.level, joint, SoundPlayer.COUPLER,
                vol, pitch + rnd(0.1f), c.couplerHearingRange);
        }
    }

    /** position of a car's front (or rear) wheels in the direction of travel; falls back to the car centre */
    static Vec3 wheels(Carriage car, Located l, boolean backwards, boolean front) {
        try {
            Carriage.DimensionalCarriageEntity d = car.getDimensionalIfPresent(l.level.dimension());
            if (d != null) {
                boolean leading = front != backwards; // moving backwards swaps which bogey is "in front"
                Vec3 v = leading ? d.leadingAnchor() : d.trailingAnchor();
                if (v != null) return v;
            }
        } catch (Throwable ignored) {
        }
        return l.pos;
    }

    record Located(ServerLevel level, Vec3 pos) {}

    /** where this carriage physically is right now, or null if it isn't loaded (nobody near it anyway) */
    static Located locate(Carriage car) {
        CarriageContraptionEntity e = car.anyAvailableEntity();
        if (e == null || e.isRemoved()) return null;
        if (!(e.level() instanceof ServerLevel sl)) return null;
        return new Located(sl, e.position());
    }

    /** debug info for /railsounds debug */
    static String describe(Train t) {
        State s = STATES.get(t.id);
        double bps = Math.abs(t.speed) * 20;
        return String.format("%s: %.1f b/s, decel %.4f%s", t.name == null ? "train" : t.name.getString(), bps,
            s == null ? 0 : s.smoothDecel, s != null && s.braking ? " BRAKING" : "");
    }

    private static float rnd(float spread) {
        return (float) ((Math.random() - 0.5) * 2 * spread);
    }
}
