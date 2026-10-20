package com.railsounds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

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
 * Engine state: a loco is either running or shut down. A running one idles when it's stopped,
 * the way a real diesel sits ticking over at a platform; a shut-down one makes no engine
 * noise at all. The shutdown key (default K) toggles the train you're driving.
 *
 * Saved in the world folder as railsounds_engines.json, so a loco you shut down overnight
 * is still shut down tomorrow. Trains not listed are running, which is what you want for
 * everything that already exists.
 */
public final class Engines {
    private Engines() {}

    /** keybind action id, sent on the same payload as the shunting keys */
    public static final int TOGGLE = 50;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** only the exceptions are stored: train ids whose engine is OFF */
    private static Set<String> shutDown = new HashSet<>();
    private static Path file;

    /** train id -> ticks until its idle clip needs replaying */
    private static final Map<UUID, Integer> IDLE_TIMER = new HashMap<>();

    public static boolean isRunning(Train train) {
        return train != null && !shutDown.contains(train.id.toString());
    }

    public static void set(Train train, boolean running) {
        if (train == null) return;
        if (running) shutDown.remove(train.id.toString());
        else shutDown.add(train.id.toString());
        IDLE_TIMER.remove(train.id);
        save();
    }

    /** a cut taken off a running train keeps its state */
    public static void onSplit(Train original, Train newTrain) {
        if (original == null || newTrain == null || newTrain == original) return;
        if (!isRunning(original)) {
            shutDown.add(newTrain.id.toString());
            save();
        }
    }

    /** the shutdown key: toggle the engine of the train the player is riding */
    public static void onKey(ServerPlayer player) {
        CarriageContraptionEntity cce = player.getVehicle() instanceof CarriageContraptionEntity e ? e : null;
        Train train = cce == null || cce.getCarriage() == null ? null : cce.getCarriage().train;
        if (train == null) {
            bar(player, "Sit in a train to start or shut down its engine", ChatFormatting.GRAY);
            return;
        }
        boolean nowRunning = !isRunning(train);
        set(train, nowRunning);
        String type = TrainTypes.get(train);
        String what = TrainTypes.STEAM.equals(type) ? "Fire" : "Engine";
        bar(player, what + (nowRunning ? " started" : " shut down") + " - " + RailSounds.nameOf(train),
            nowRunning ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        if (nowRunning) playStart(train);
    }

    private static void bar(ServerPlayer player, String text, ChatFormatting colour) {
        player.sendOverlayMessage(Component.literal(text).withStyle(colour));
    }

    /** one-shot startup sound when the engine is lit */
    private static void playStart(Train train) {
        RailConfig.Values c = RailConfig.V;
        if (!c.engineSounds) return;
        Spot spot = spotOf(train);
        if (spot == null) return;
        String type = TrainTypes.get(train);
        String sound = TrainTypes.STEAM.equals(type) ? "steam_start" : "diesel_start";
        SoundPlayer.play(spot.level, spot.pos, sound, c.engineVolume, 1.0f, c.engineHearingRange);
    }

    private record Spot(ServerLevel level, Vec3 pos) {}

    /** where to play a train's engine from: its first carriage that's actually loaded */
    private static Spot spotOf(Train train) {
        if (train.carriages == null) return null;
        for (Carriage car : train.carriages) {
            TrainSoundTracker.Located l = TrainSoundTracker.locate(car);
            if (l != null) return new Spot(l.level(), l.pos());
        }
        return null;
    }

    /**
     * Keeps the idle loop going for every running train that is sitting still. Moving trains
     * are handled by the engine sounds proper (once those clips exist); this is only the
     * tick-over at a standstill.
     */
    public static void tick(MinecraftServer server) {
        RailConfig.Values c = RailConfig.V;
        if (!c.engineSounds || !c.idleWhenStopped) {
            if (!IDLE_TIMER.isEmpty()) IDLE_TIMER.clear();
            return;
        }
        if (Create.RAILWAYS == null || Create.RAILWAYS.trains == null) return;

        // copy first, same as the other per-tick loops: Create can drop a train mid-tick
        for (Train train : new java.util.ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (train == null || train.invalid || train.derailed) continue;
            if (!isRunning(train)) {
                IDLE_TIMER.remove(train.id);
                continue;
            }
            if (Math.abs(train.speed) > 0.005) {   // moving: not an idle
                IDLE_TIMER.remove(train.id);
                continue;
            }
            if (Compat.isHandcar(train)) continue;

            int left = IDLE_TIMER.getOrDefault(train.id, 0);
            if (left > 0) {
                IDLE_TIMER.put(train.id, left - 1);
                continue;
            }
            Spot spot = spotOf(train);
            if (spot == null) {
                IDLE_TIMER.remove(train.id);
                continue;
            }
            String type = TrainTypes.get(train);
            String sound = TrainTypes.STEAM.equals(type) ? "steam_idle" : "diesel_idle";
            SoundPlayer.play(spot.level, spot.pos, sound, c.idleVolume, 1.0f, c.idleHearingRange);
            IDLE_TIMER.put(train.id, Math.max(1, c.idleClipTicks));
        }

        // drop timers for trains that no longer exist
        if (server.getTickCount() % 200 == 0) {
            IDLE_TIMER.keySet().removeIf(id -> !Create.RAILWAYS.trains.containsKey(id));
        }
    }

    // ---- persistence -------------------------------------------------------------------

    public static void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("railsounds_engines.json");
        shutDown = new HashSet<>();
        IDLE_TIMER.clear();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Set<String> loaded = GSON.fromJson(r, new TypeToken<Set<String>>() {}.getType());
                    if (loaded != null) shutDown = new HashSet<>(loaded);
                }
            }
        } catch (Exception e) {
            RailSounds.LOG.error("Could not read railsounds_engines.json", e);
        }
    }

    public static void unload() {
        shutDown = new HashSet<>();
        IDLE_TIMER.clear();
        file = null;
    }

    private static void save() {
        if (file == null) return;
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(shutDown, w);
        } catch (Exception e) {
            RailSounds.LOG.error("Could not save railsounds_engines.json", e);
        }
    }
}
