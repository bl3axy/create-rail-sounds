package com.railsounds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.Train;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Railroad crossings. Each one is a spot + radius; while any train car is inside the radius
 * the bell rings there. Optionally a "signal" block spot gets a redstone block while active,
 * so you can wire lamps / gates / Create mechanical bearings to it.
 */
public class Crossings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("railsounds-crossings.json");

    public static class Crossing {
        public String name;
        public String dimension;
        public double x, y, z;
        public int radius;
        public Integer signalX, signalY, signalZ; // optional

        transient boolean active;
        transient int bellTimer;
        transient int tail;
        transient boolean signalChecked;

        BlockPos signalPos() {
            return signalX == null ? null : new BlockPos(signalX, signalY, signalZ);
        }

        /** true while the bell is ringing (a train is approaching or on the crossing) */
        public boolean isActive() {
            return active;
        }
    }

    /** the crossing a crossing-signal / gate block at pos belongs to: nearest one within maxDist in that dimension */
    public static Crossing nearest(ServerLevel level, BlockPos pos, double maxDist) {
        String dim = level.dimension().identifier().toString();
        Crossing best = null;
        double bestD = maxDist * maxDist;
        for (Crossing c : LIST) {
            if (!dim.equals(c.dimension)) continue;
            double dx = c.x - (pos.getX() + 0.5), dy = c.y - (pos.getY() + 0.5), dz = c.z - (pos.getZ() + 0.5);
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** name used for a crossing that a Crossing Signal block made for itself */
    public static String blockName(BlockPos pos) {
        return "signal_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    /** make a crossing centred on a crossing-signal block */
    public static Crossing addForBlock(ServerLevel level, BlockPos pos, int radius) {
        Crossing c = get(blockName(pos));
        if (c != null) return c;
        c = new Crossing();
        c.name = blockName(pos);
        c.dimension = level.dimension().identifier().toString();
        c.x = pos.getX() + 0.5;
        c.y = pos.getY();
        c.z = pos.getZ() + 0.5;
        c.radius = radius;
        LIST.add(c);
        saveSoon();
        return c;
    }

    /** remove the crossing a signal block made for itself (crossings made by command are left alone) */
    public static void removeForBlock(BlockPos pos) {
        Crossing c = get(blockName(pos));
        if (c == null) return;
        LIST.remove(c);
        lastDist.keySet().removeIf(k -> k.startsWith(c.name + "#"));
        saveSoon();
    }

    public static List<Crossing> LIST = new ArrayList<>();
    /** server stopping: next start re-checks signals, and ringing state starts fresh */
    public static void onServerStop() {
        if (dirty) {
            dirty = false;
            save();
        }
        lastDist = new HashMap<>();
        for (Crossing c : LIST) {
            c.signalChecked = false;
            c.active = false;
            c.tail = 0;
            c.bellTimer = 0;
        }
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    List<Crossing> l = GSON.fromJson(r, new TypeToken<List<Crossing>>() {}.getType());
                    if (l != null) LIST = new ArrayList<>(l);
                }
            }
        } catch (Exception e) {
            RailSounds.LOG.error("Could not read railsounds-crossings.json", e);
        }
    }

    /** block placement can add many crossings in one tick; coalesce those into one write */
    private static boolean dirty;

    public static void saveSoon() {
        dirty = true;
    }

    /** called from the server tick; writes at most once a second */
    static void flush(MinecraftServer server) {
        if (!dirty || server.getTickCount() % 20 != 0) return;
        dirty = false;
        save();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(LIST, w);
        } catch (Exception e) {
            RailSounds.LOG.error("Could not write railsounds-crossings.json", e);
        }
    }

    public static Crossing get(String name) {
        for (Crossing c : LIST) if (c.name.equalsIgnoreCase(name)) return c;
        return null;
    }

    public static void tick(MinecraftServer server) {
        flush(server);
        if (LIST.isEmpty() || Create.RAILWAYS == null) return;
        RailConfig.Values cfg = RailConfig.V;

        // every loaded car: its centre plus both wheel sets, so long cars are caught properly
        List<CarPoints> cars = new ArrayList<>();
        for (Train t : new ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (t == null || t.invalid || t.carriages == null) continue;
            boolean isMoving = Math.abs(t.speed) > 0.003;
            for (Carriage car : t.carriages) {
                TrainSoundTracker.Located l = TrainSoundTracker.locate(car);
                if (l == null) continue;
                List<Vec3> pts = new ArrayList<>();
                pts.add(l.pos());
                pts.add(TrainSoundTracker.wheels(car, l, false, true));
                pts.add(TrainSoundTracker.wheels(car, l, false, false));
                cars.add(new CarPoints(car.id, l.level(), pts, isMoving));
            }
        }
        double on2 = (double) cfg.onCrossingDistance * cfg.onCrossingDistance;
        Map<String, Double> nowDist = new HashMap<>();

        for (Crossing x : LIST) {
            ServerLevel level = levelFor(server, x.dimension);
            if (level == null) continue;
            Vec3 center = new Vec3(x.x, x.y, x.z);
            double r2 = (double) x.radius * x.radius;

            // rings while a train is heading toward the crossing inside the radius, or any car is on it.
            // once the last car clears and the train is moving away, it stops (after a short tail).
            boolean trainNear = false;
            for (CarPoints cp : cars) {
                if (cp.level != level) continue;
                double best = Double.MAX_VALUE;
                for (Vec3 p : cp.points) {
                    if (Math.abs(p.y - x.y) >= 16) continue;
                    double dx = p.x - x.x, dz = p.z - x.z;
                    best = Math.min(best, dx * dx + dz * dz);
                }
                if (best == Double.MAX_VALUE) continue;
                String key = x.name + "#" + cp.id;
                Double before = lastDist.get(key);
                if (best <= r2 * 1.5) nowDist.put(key, best);

                boolean onIt = best <= on2;
                boolean approaching = cp.moving && best <= r2 && before != null && best < before - 1e-4;
                if (onIt || approaching) {
                    trainNear = true;
                }
            }
            // after a restart a signal could be stuck "on" from before: switch it off once its chunk is loaded
            if (!x.signalChecked) {
                BlockPos sp = x.signalPos();
                if (sp == null) {
                    x.signalChecked = true;
                } else if (level.isLoaded(sp)) {
                    if (!x.active) setSignal(level, x, false);
                    x.signalChecked = true;
                }
            }

            if (trainNear) x.tail = cfg.bellTailTicks;
            boolean shouldRing = trainNear || x.tail > 0;
            if (x.tail > 0 && !trainNear) x.tail--;

            if (shouldRing && !x.active) {
                x.active = true;
                x.bellTimer = 0;
                setSignal(level, x, true);
            } else if (!shouldRing && x.active) {
                x.active = false;
                setSignal(level, x, false);
            }

            if (x.active) {
                if (x.bellTimer <= 0) {
                    SoundPlayer.play(level, center, SoundPlayer.BELL, cfg.bellVolume, 1.0f, cfg.bellHearingRange);
                    x.bellTimer = Math.max(1, cfg.bellClipTicks);
                }
                x.bellTimer--;
            }
        }
        lastDist = nowDist;
    }

    private record CarPoints(int id, ServerLevel level, List<Vec3> points, boolean moving) {}

    /** each car's distance to each crossing last tick, to tell approaching from leaving */
    private static Map<String, Double> lastDist = new HashMap<>();

    private static void setSignal(ServerLevel level, Crossing x, boolean on) {
        BlockPos p = x.signalPos();
        if (p == null || !level.isLoaded(p)) return;
        level.setBlockAndUpdate(p, on ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    static ServerLevel levelFor(MinecraftServer server, String dim) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().identifier().toString().equals(dim)) return l;
        }
        return null;
    }
}
