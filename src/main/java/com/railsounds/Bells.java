package com.railsounds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The bell on the locomotive itself - the slow clang a GP40 rings pulling through a yard or
 * leaving a platform. Nothing to do with the crossing bell, which belongs to the crossing.
 *
 * The bell key (default B) rings it on the train you're driving and rings it off again. It
 * keeps ringing whether the train is moving or not, and whether the engine is running or not,
 * because a real one is on its own air or battery. Saved per train in the world folder.
 */
public final class Bells {
    private Bells() {}

    /** keybind action id, on the same payload as the shunting keys */
    public static final int TOGGLE = 51;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** train ids whose bell is ringing */
    private static Set<String> ringing = new HashSet<>();
    private static Path file;

    /** train id -> ticks until the clip needs replaying */
    private static final Map<UUID, Integer> TIMER = new HashMap<>();

    public static boolean isRinging(Train train) {
        return train != null && ringing.contains(train.id.toString());
    }

    public static void set(Train train, boolean on) {
        if (train == null) return;
        if (on) ringing.add(train.id.toString());
        else ringing.remove(train.id.toString());
        TIMER.remove(train.id);
        save();
    }

    /** a cut split off a ringing train keeps ringing - the bell is on the loco, so usually it won't */
    public static void onSplit(Train original, Train newTrain) {
        if (original == null || newTrain == null || newTrain == original) return;
        if (isRinging(original)) {
            ringing.add(newTrain.id.toString());
            save();
        }
    }

    public static void onKey(ServerPlayer player) {
        CarriageContraptionEntity cce = player.getVehicle() instanceof CarriageContraptionEntity e ? e : null;
        Train train = cce == null || cce.getCarriage() == null ? null : cce.getCarriage().train;
        if (train == null) {
            player.sendOverlayMessage(Component.literal("Sit in a train to ring its bell").withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean on = !isRinging(train);
        set(train, on);
        player.sendOverlayMessage(Component.literal("Bell " + (on ? "on" : "off") + " - " + RailSounds.nameOf(train))
            .withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    public static void tick(MinecraftServer server) {
        RailConfig.Values c = RailConfig.V;
        if (!c.locoBell || ringing.isEmpty()) {
            if (!TIMER.isEmpty()) TIMER.clear();
            return;
        }
        if (Create.RAILWAYS == null || Create.RAILWAYS.trains == null) return;

        for (Train train : new java.util.ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (train == null || train.invalid || train.derailed) continue;
            if (!isRinging(train)) {
                TIMER.remove(train.id);
                continue;
            }
            int left = TIMER.getOrDefault(train.id, 0);
            if (left > 0) {
                TIMER.put(train.id, left - 1);
                continue;
            }
            TrainSoundTracker.Located at = null;
            for (var car : train.carriages) {
                at = TrainSoundTracker.locate(car);
                if (at != null) break;
            }
            if (at == null) {
                TIMER.remove(train.id);
                continue;
            }
            SoundPlayer.play(at.level(), at.pos(), "loco_bell", c.locoBellVolume, 1.0f, c.locoBellHearingRange);
            TIMER.put(train.id, Math.max(1, c.locoBellClipTicks));
        }

        if (server.getTickCount() % 200 == 0) {
            TIMER.keySet().removeIf(id -> !Create.RAILWAYS.trains.containsKey(id));
            // a train that no longer exists shouldn't keep its bell on file forever
            if (ringing.removeIf(s -> {
                try {
                    return !Create.RAILWAYS.trains.containsKey(UUID.fromString(s));
                } catch (IllegalArgumentException e) {
                    return true;
                }
            })) save();
        }
    }

    public static void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("railsounds_bells.json");
        ringing = new HashSet<>();
        TIMER.clear();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Set<String> loaded = GSON.fromJson(r, new TypeToken<Set<String>>() {}.getType());
                    if (loaded != null) ringing = new HashSet<>(loaded);
                }
            }
        } catch (Exception e) {
            RailSounds.LOG.error("Could not read railsounds_bells.json", e);
        }
    }

    public static void unload() {
        ringing = new HashSet<>();
        TIMER.clear();
        file = null;
    }

    private static void save() {
        if (file == null) return;
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(ringing, w);
        } catch (Exception e) {
            RailSounds.LOG.error("Could not save railsounds_bells.json", e);
        }
    }
}
