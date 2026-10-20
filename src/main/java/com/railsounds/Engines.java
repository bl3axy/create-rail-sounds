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
    /** train id -> ticks until the running clip needs replaying */
    private static final Map<UUID, Integer> RUN_TIMER = new HashMap<>();
    /** train id -> true once it has been moving, so notching up only fires on the way up */
    private static final Map<UUID, Boolean> WAS_MOVING = new HashMap<>();
    /** train id -> ticks before it may play the notch-up layer again */
    private static final Map<UUID, Integer> NOTCH_COOLDOWN = new HashMap<>();
    /** train id -> the notch its running loop is currently sounding at */
    private static final Map<UUID, Integer> STEP = new HashMap<>();

    private static final double MOVING = 0.005;

    public static boolean isRunning(Train train) {
        return train != null && !shutDown.contains(train.id.toString());
    }

    public static void set(Train train, boolean running) {
        if (train == null) return;
        if (running) shutDown.remove(train.id.toString());
        else shutDown.add(train.id.toString());
        drop(train.id);
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

    /**
     * One-shot startup when the engine is lit, and set the idle to come in just before the
     * start clip ends so the two overlap instead of leaving a hole. The first idle loop plays
     * quieter (startFadeVolume) so it rises under the tail of the start rather than slamming in.
     */
    private static void playStart(Train train) {
        RailConfig.Values c = RailConfig.V;
        if (!c.engineSounds) return;
        Spot spot = spotOf(train);
        if (spot == null) return;
        String sound = TrainTypes.STEAM.equals(TrainTypes.get(train)) ? "steam_start" : "diesel_start";
        SoundPlayer.play(spot.level, spot.pos, sound, c.engineVolume, 1.0f, c.engineHearingRange);
        int hold = Math.max(0, c.startClipTicks - Math.max(0, c.startFadeTicks));
        IDLE_TIMER.put(train.id, hold);
        RUN_TIMER.put(train.id, hold);
        FADING_IN.add(train.id);
    }

    /** trains whose next idle/run loop is the first one after a start, so it fades in */
    private static final java.util.Set<UUID> FADING_IN = new HashSet<>();

    /** volume for a loop, reduced once while it comes in under the start clip */
    private static float enter(UUID id, float full) {
        if (!FADING_IN.remove(id)) return full;
        float f = RailConfig.V.startFadeVolume;
        return full * (f <= 0 || f > 1 ? 0.6f : f);
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
        if (!c.engineSounds) {
            // every map, not just the idle one: turning engine sounds off while a train was
            // moving used to leave the run/notch/step state behind
            if (!(IDLE_TIMER.isEmpty() && RUN_TIMER.isEmpty() && STEP.isEmpty()
                && WAS_MOVING.isEmpty() && NOTCH_COOLDOWN.isEmpty() && FADING_IN.isEmpty())) forget();
            return;
        }
        if (Create.RAILWAYS == null || Create.RAILWAYS.trains == null) return;

        // copy first, same as the other per-tick loops: Create can drop a train mid-tick
        for (Train train : new java.util.ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (train == null || train.invalid || train.derailed) continue;
            if (!isRunning(train) || Compat.isHandcar(train)) {
                drop(train.id);
                continue;
            }
            boolean moving = Math.abs(train.speed) > MOVING;
            boolean wasMoving = WAS_MOVING.getOrDefault(train.id, false);
            WAS_MOVING.put(train.id, moving);

            if (moving) {
                IDLE_TIMER.remove(train.id);
                int step = stepOf(train, c);
                Integer before = STEP.put(train.id, step);
                float pitch = pitchFor(step, c, train);
                // The new notch is picked up when the loop next comes round, NOT straight away.
                // Restarting it here would leave the old four-second clip still playing on the
                // client at the old pitch - vanilla can only stop a sound by name, which would
                // also cut every other loco within earshot. Waiting for the boundary sounds
                // right anyway: a real engine takes a few seconds to settle into a notch, and
                // the notch-up layer below is the immediate cue that it changed.
                if (!wasMoving || (before != null && step > before)) notchUp(train, c, pitch);
                loop(train, RUN_TIMER, steam(train) ? "steam_run" : "diesel_run",
                    c.engineVolume, pitch, c.runClipTicks, c.engineHearingRange);
            } else {
                RUN_TIMER.remove(train.id);
                STEP.remove(train.id);
                if (!c.idleWhenStopped) {
                    IDLE_TIMER.remove(train.id);
                    continue;
                }
                loop(train, IDLE_TIMER, steam(train) ? "steam_idle" : "diesel_idle",
                    c.idleVolume, 1.0f, c.idleClipTicks, c.idleHearingRange);
            }
        }

        // drop state for trains that no longer exist
        if (server.getTickCount() % 200 == 0) {
            IDLE_TIMER.keySet().removeIf(id -> !Create.RAILWAYS.trains.containsKey(id));
            RUN_TIMER.keySet().removeIf(id -> !Create.RAILWAYS.trains.containsKey(id));
            WAS_MOVING.keySet().removeIf(id -> !Create.RAILWAYS.trains.containsKey(id));
            NOTCH_COOLDOWN.keySet().removeIf(id -> !Create.RAILWAYS.trains.containsKey(id));
            STEP.keySet().removeIf(id -> !Create.RAILWAYS.trains.containsKey(id));
        }
        NOTCH_COOLDOWN.replaceAll((id, t) -> t - 1);
        NOTCH_COOLDOWN.values().removeIf(t -> t <= 0);
    }

    private static boolean steam(Train train) {
        return TrainTypes.STEAM.equals(TrainTypes.get(train));
    }

    /** a steam engine's chuff rate follows the wheels, so give it fine steps rather than notches */
    private static final int STEAM_STEPS = 16;

    private static int steps(Train train, RailConfig.Values c) {
        return steam(train) ? STEAM_STEPS : Math.max(1, Math.min(32, c.dieselNotches));
    }

    /**
     * Which notch the train is working in, 0 up to steps-1, from how much of the top speed it
     * is doing. maxSpeed() is Create's configured ceiling in blocks/tick - the higher powered
     * figure for a fuelled train - and it isn't scaled by the throttle, so easing off drops the
     * notch the way it should instead of pinning it at full.
     */
    private static int stepOf(Train train, RailConfig.Values c) {
        int n = steps(train, c);
        if (n <= 1) return 0;
        float top = train.maxSpeed();
        if (!(top > 0.001f)) top = 1.0f;
        double frac = Math.min(1.0, Math.abs(train.speed) / top);
        return Math.max(0, Math.min(n - 1, (int) Math.round(frac * (n - 1))));
    }

    /** playback pitch for a notch. Minecraft clamps to 0.5-2.0, so this does too */
    private static float pitchFor(int step, RailConfig.Values c, Train train) {
        int n = steps(train, c);
        float lo = c.enginePitchMin, hi = c.enginePitchMax;
        float p = n <= 1 ? lo : lo + (hi - lo) * ((float) step / (n - 1));
        return Math.max(0.5f, Math.min(2.0f, p));
    }

    /** replay a looping clip when its timer runs out */
    private static void loop(Train train, Map<UUID, Integer> timer,
                             String sound, float volume, float pitch, int clipTicks, int range) {
        int left = timer.getOrDefault(train.id, 0);
        if (left > 0) {
            timer.put(train.id, left - 1);
            return;
        }
        Spot spot = spotOf(train);
        if (spot == null) {
            timer.remove(train.id);
            return;
        }
        SoundPlayer.play(spot.level, spot.pos, sound, enter(train.id, volume), pitch, range);
        timer.put(train.id, Math.max(1, clipTicks));
    }

    /** the engine digging in as it pulls away or takes another notch, over the running loop */
    private static void notchUp(Train train, RailConfig.Values c, float pitch) {
        if (NOTCH_COOLDOWN.containsKey(train.id)) return;
        Spot spot = spotOf(train);
        if (spot == null) return;
        SoundPlayer.play(spot.level, spot.pos, steam(train) ? "steam_notch" : "diesel_notch",
            c.engineVolume, pitch, c.engineHearingRange);
        NOTCH_COOLDOWN.put(train.id, Math.max(20, c.notchCooldownTicks));
    }

    private static void drop(UUID id) {
        IDLE_TIMER.remove(id);
        RUN_TIMER.remove(id);
        WAS_MOVING.remove(id);
        FADING_IN.remove(id);
        STEP.remove(id);
    }

    private static void forget() {
        IDLE_TIMER.clear();
        RUN_TIMER.clear();
        WAS_MOVING.clear();
        NOTCH_COOLDOWN.clear();
        FADING_IN.clear();
        STEP.clear();
    }

    // ---- persistence -------------------------------------------------------------------

    public static void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("railsounds_engines.json");
        shutDown = new HashSet<>();
        forget();
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
        forget();
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
