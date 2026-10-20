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
import java.util.UUID;

/**
 * Which lever on which interlocking panel throws which switch. Saved in the world folder as
 * railsounds_towers.json so links survive restarts.
 */
public final class TowerLinks {
    private TowerLinks() {}

    public static class Link {
        public String switchId;
        public int x, y, z; // switch block, for messages
        public String filter; // route sensors only: which trains count as "matching"

        UUID id() {
            try {
                return switchId == null ? null : UUID.fromString(switchId);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** "dimension|x,y,z" of the panel -> lever number (0-7) -> link */
    private static Map<String, Map<Integer, Link>> panels = new HashMap<>();
    private static Path file;

    static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static Link get(Level level, BlockPos panel, int lever) {
        Map<Integer, Link> m = panels.get(key(level, panel));
        return m == null ? null : m.get(lever);
    }

    public static Map<Integer, Link> all(Level level, BlockPos panel) {
        Map<Integer, Link> m = panels.get(key(level, panel));
        return m == null ? Map.of() : m;
    }

    public static void put(Level level, BlockPos panel, int lever, UUID switchId, BlockPos switchPos) {
        Link old = get(level, panel, lever);
        Link l = new Link();
        l.filter = old == null ? null : old.filter; // relinking a route sensor keeps its filter
        l.switchId = switchId.toString();
        l.x = switchPos.getX();
        l.y = switchPos.getY();
        l.z = switchPos.getZ();
        panels.computeIfAbsent(key(level, panel), k -> new HashMap<>()).put(lever, l);
        save();
    }

    public static void setFilter(Level level, BlockPos panel, int lever, String filter) {
        Link l = get(level, panel, lever);
        if (l == null) return;
        l.filter = filter;
        save();
    }

    public static void clear(Level level, BlockPos panel, int lever) {
        Map<Integer, Link> m = panels.get(key(level, panel));
        if (m == null) return;
        m.remove(lever);
        if (m.isEmpty()) panels.remove(key(level, panel));
        save();
    }

    public static void removePanel(Level level, BlockPos panel) {
        if (panels.remove(key(level, panel)) != null) save();
    }

    public static void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("railsounds_towers.json");
        panels = new HashMap<>();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Map<String, Map<Integer, Link>> loaded =
                        GSON.fromJson(r, new TypeToken<Map<String, Map<Integer, Link>>>() {}.getType());
                    if (loaded != null) panels = loaded;
                }
            }
        } catch (Exception e) {
            RailSounds.LOG.error("Could not read railsounds_towers.json", e);
        }
    }

    public static void unload() {
        panels = new HashMap<>();
        file = null;
    }

    private static void save() {
        if (file == null) return;
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(panels, w);
        } catch (Exception e) {
            RailSounds.LOG.error("Could not save railsounds_towers.json", e);
        }
    }
}
