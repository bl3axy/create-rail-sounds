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
    }

    public static List<Crossing> LIST = new ArrayList<>();
    /** server stopping: next start re-checks signals, and ringing state starts fresh */
    public static void onServerStop() {
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
        if (LIST.isEmpty() || Create.RAILWAYS == null) return;
        RailConfig.Values cfg = RailConfig.V;

        // gather every loaded car position once
        List<TrainSoundTracker.Located> cars = new ArrayList<>();
        List<Boolean> moving = new ArrayList<>();
        for (Train t : new ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (t == null || t.invalid || t.carriages == null) continue;
            boolean isMoving = Math.abs(t.speed) > 0.003;
            for (Carriage car : t.carriages) {
                TrainSoundTracker.Located l = TrainSoundTracker.locate(car);
                if (l != null) {
                    cars.add(l);
                    moving.add(isMoving);
                }
            }
        }
        double stop2 = (double) cfg.stoppedTrainBellDistance * cfg.stoppedTrainBellDistance;

        for (Crossing x : LIST) {
            ServerLevel level = levelFor(server, x.dimension);
            if (level == null) continue;
            Vec3 center = new Vec3(x.x, x.y, x.z);
            double r2 = (double) x.radius * x.radius;

            boolean trainNear = false;
            for (int i = 0; i < cars.size(); i++) {
                TrainSoundTracker.Located l = cars.get(i);
                if (l.level() != level) continue;
                double dx = l.pos().x - x.x, dz = l.pos().z - x.z, dy = l.pos().y - x.y;
                double d2 = dx * dx + dz * dz;
                // parked trains only count if they're actually sitting on the crossing
                double limit = moving.get(i) ? r2 : Math.min(r2, stop2);
                if (d2 <= limit && Math.abs(dy) < 16) {
                    trainNear = true;
                    break;
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
    }

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
