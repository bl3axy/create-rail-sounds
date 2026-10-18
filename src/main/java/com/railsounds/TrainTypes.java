package com.railsounds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.Train;
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
 * Remembers which trains are diesel and which are steam.
 * /diesel or /steam next to a station in build (assembly) mode marks that station;
 * the train assembled there gets the type. Disassembling hands it back to the station,
 * so rebuilding keeps it. Saved in the world folder: railsounds_traintypes.json
 */
public class TrainTypes {
    public static final String DIESEL = "diesel";
    public static final String STEAM = "steam";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Data {
        Map<String, String> trains = new HashMap<>();   // train uuid -> type
        Map<String, String> stations = new HashMap<>(); // "dimension|x|y|z" -> type waiting for assembly
    }

    private static Data data = new Data();
    private static Path file;

    public static String stationKey(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    public static String get(Train train) {
        return train == null ? null : data.trains.get(train.id.toString());
    }

    public static String pending(String stationKey) {
        return data.stations.get(stationKey);
    }

    public static void setPending(String stationKey, String type) {
        data.stations.put(stationKey, type);
        save();
    }

    public static void onAssembled(String stationKey, Train train) {
        String type = data.stations.remove(stationKey);
        if (type != null) {
            data.trains.put(train.id.toString(), type);
            save();
        }
    }

    public static void onDisassembled(String stationKey, String type) {
        if (type == null) return;
        data.stations.put(stationKey, type);
        save();
    }

    public static void onSplit(Train original, Train newTrain) {
        String type = get(original);
        if (type != null && newTrain != null && newTrain != original) {
            data.trains.put(newTrain.id.toString(), type);
            save();
        }
    }

    /** drop trains that no longer exist (coupled into another, removed, ...) */
    public static void cleanup() {
        if (Create.RAILWAYS == null || Create.RAILWAYS.trains.isEmpty()) return;
        boolean changed = data.trains.keySet().removeIf(id -> {
            try {
                return !Create.RAILWAYS.trains.containsKey(UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                return true;
            }
        });
        if (changed) save();
    }

    public static void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("railsounds_traintypes.json");
        data = new Data();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Data loaded = GSON.fromJson(r, Data.class);
                    if (loaded != null) {
                        if (loaded.trains != null) data.trains = loaded.trains;
                        if (loaded.stations != null) data.stations = loaded.stations;
                    }
                }
            }
        } catch (Exception e) {
            RailSounds.LOG.error("Could not read railsounds_traintypes.json", e);
        }
    }

    public static void unload() {
        data = new Data();
        file = null;
    }

    private static void save() {
        if (file == null) return;
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(data, w);
        } catch (Exception e) {
            RailSounds.LOG.error("Could not save railsounds_traintypes.json", e);
        }
    }
}
