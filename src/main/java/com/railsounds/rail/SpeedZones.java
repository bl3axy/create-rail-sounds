package com.railsounds.rail;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageBogey;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Speed limits set by Speed Limit signs. Like a real railroad: a sign sets the speed from that
 * point on, until the next sign changes it (or a "resume" sign lifts it). Applies to scheduled
 * and hand-driven trains alike. Kept in memory: after a server restart trains run at full speed
 * until they pass a sign again.
 */
public final class SpeedZones {
    private SpeedZones() {}

    /** mph per LIMIT index; the last index means "resume normal speed" */
    public static final int[] MPH = {10, 15, 20, 25, 30, 40, 50, 60};
    public static final int RESUME = MPH.length;
    private static final double MPH_TO_BPT = 0.44704 / 20.0; // 1 block = 1 m, 20 ticks/s

    /** train id -> speed cap in blocks/tick */
    private static final Map<UUID, Double> CAPS = new HashMap<>();

    public static void set(Train train, int limitIndex) {
        if (limitIndex < 0 || limitIndex >= RESUME) CAPS.remove(train.id);
        else CAPS.put(train.id, MPH[limitIndex] * MPH_TO_BPT);
    }

    public static Integer currentMph(Train train) {
        Double cap = CAPS.get(train.id);
        return cap == null ? null : (int) Math.round(cap / MPH_TO_BPT);
    }

    public static void reset() {
        CAPS.clear();
    }

    public static void tick(MinecraftServer server) {
        if (CAPS.isEmpty() || Create.RAILWAYS == null || Create.RAILWAYS.trains == null) return;
        Map<UUID, Train> trains = Create.RAILWAYS.trains;
        if (server.getTickCount() % 200 == 0) CAPS.keySet().removeIf(id -> !trains.containsKey(id));
        for (Map.Entry<UUID, Double> e : CAPS.entrySet()) {
            Train t = trains.get(e.getKey());
            if (t == null || t.derailed || t.invalid) continue;
            double cap = e.getValue();
            if (Math.abs(t.speed) > cap) t.speed = Math.signum(t.speed) * cap;
        }
    }

    /** direction a carriage is actually moving in (horizontal, unit), or null if stopped/unknown */
    static Vec3 heading(Carriage car) {
        Train train = car.train;
        if (train == null || train.graph == null || Math.abs(train.speed) < 1e-4) return null;
        try {
            Vec3 dir;
            if (car.isOnTwoBogeys()) {
                Vec3 lead = car.leadingBogey().getAnchorPosition(false);
                Vec3 trail = car.trailingBogey().getAnchorPosition(false);
                if (lead == null || trail == null) return null;
                dir = lead.subtract(trail);
            } else {
                CarriageBogey b = car.leadingBogey();
                dir = b.leading().getPosition(train.graph).subtract(b.trailing().getPosition(train.graph));
            }
            dir = new Vec3(dir.x, 0, dir.z);
            if (dir.lengthSqr() < 1e-6) return null;
            return dir.normalize().scale(Math.signum(train.speed));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
