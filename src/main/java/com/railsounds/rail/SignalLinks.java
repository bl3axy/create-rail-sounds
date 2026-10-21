package com.railsounds.rail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.railsounds.RailSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Which Create Train Signal each Searchlight Signal is reading.
 *
 * Without a link a searchlight only reads a signal it is touching, which means the head has to
 * sit right on the track. That is no good for a signal on the far side of a yard or one hanging
 * from a bridge over four tracks, so the Switch Linker can point a head at any signal in the
 * same dimension: click the Create signal, then click the head.
 *
 * Stored in the world folder, like the tower links, so the wiring survives a restart.
 */
public final class SignalLinks {
    private SignalLinks() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** "dim|headPos" -> the Create signal's position */
    private static Map<String, int[]> links = new HashMap<>();
    private static Path file;

    static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static BlockPos get(Level level, BlockPos head) {
        int[] v = links.get(key(level, head));
        return v == null || v.length != 3 ? null : new BlockPos(v[0], v[1], v[2]);
    }

    public static void put(Level level, BlockPos head, BlockPos signal) {
        links.put(key(level, head), new int[]{signal.getX(), signal.getY(), signal.getZ()});
        save();
    }

    /** a head that's been broken shouldn't keep its link on file forever */
    public static void remove(Level level, BlockPos head) {
        if (links.remove(key(level, head)) != null) save();
    }

    public static int count() {
        return links.size();
    }

    public static void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("railsounds_signals.json");
        links = new HashMap<>();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Map<String, int[]> loaded = GSON.fromJson(r, new TypeToken<Map<String, int[]>>() {}.getType());
                    if (loaded != null) links = new HashMap<>(loaded);
                }
            }
        } catch (Exception e) {
            RailSounds.LOG.error("Could not read railsounds_signals.json", e);
        }
    }

    public static void unload() {
        links = new HashMap<>();
        file = null;
    }

    private static void save() {
        if (file == null) return;
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(links, w);
        } catch (Exception e) {
            RailSounds.LOG.error("Could not save railsounds_signals.json", e);
        }
    }
}
