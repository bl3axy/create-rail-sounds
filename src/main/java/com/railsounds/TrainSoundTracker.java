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
                ripple(server, cars, c.couplerRippleTicksStart, c.couplerVolume, 0.95f, true);
            }

            // ---------- coming to a stop: slack bunches, then the air dumps ----------
            if (s.lastSpeed >= STOPPED && speed < STOPPED) {
                ripple(server, cars, c.couplerRippleTicksStop, c.couplerVolume * 0.7f, 1.05f, false);
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
                    SoundPlayer.play(l.level, l.pos, SoundPlayer.CLACK, vol, pitch + rnd(0.05f), c.clackHearingRange);
                    // second bogey hits the same joint a moment later
                    if (car.isOnTwoBogeys()) {
                        int gap = (int) Math.round(car.bogeySpacing / speed);
                        if (gap >= 1 && gap <= 40)
                            SoundPlayer.playLater(server, gap, l.level, l.pos, SoundPlayer.CLACK,
                                vol * 0.9f, pitch + rnd(0.05f), c.clackHearingRange);
                    }
                }
            }

            s.stoppedTicks = speed < STOPPED ? s.stoppedTicks + 1 : 0;
            s.lastSpeed = speed;
        }
        STATES.keySet().retainAll(seen);
    }

    private static void ripple(MinecraftServer server, List<Carriage> cars, int gap, float vol, float pitch, boolean frontToBack) {
        RailConfig.Values c = RailConfig.V;
        for (int i = 0; i < cars.size(); i++) {
            Carriage car = cars.get(frontToBack ? i : cars.size() - 1 - i);
            Located l = locate(car);
            if (l == null) continue;
            // first and last car only clunk if there's a neighbour to clunk against
            if (cars.size() == 1) {
                SoundPlayer.playLater(server, 0, l.level, l.pos, SoundPlayer.COUPLER, vol * 0.6f, pitch, c.couplerHearingRange);
                return;
            }
            SoundPlayer.playLater(server, i * gap, l.level, l.pos, SoundPlayer.COUPLER,
                vol, pitch + rnd(0.1f), c.couplerHearingRange);
        }
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
