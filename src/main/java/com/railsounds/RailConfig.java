package com.railsounds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** All the tunable numbers. Lives in config/railsounds.json; edit it and run /railsounds reload. */
public class RailConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("railsounds.json");

    public static Values V = new Values();

    public static class Values {
        // ---- rail joint clack ----
        /** blocks between rail joints. real track ~ every 12m; 6 sounds better at MC train speeds */
        public double clackSpacing = 6.0;
        /** below this speed (blocks/tick) no clack. 0.05 b/t = 1 block/sec */
        public double clackMinSpeed = 0.05;
        public float clackVolume = 0.8f;
        public int clackHearingRange = 32;

        // ---- brakes ----
        /** squeal when slowing faster than this fraction of the train's own braking rate.
         *  Uses Create's acceleration (also respects Steam 'n' Rails realism + powered trains) */
        public double brakeDecelFraction = 0.5;
        /** never count as braking below this deceleration (blocks/tick^2), stops jitter squeals */
        public double brakeDecelMin = 0.0005;
        /** don't squeal when crawling slower than this */
        public double brakeMinSpeed = 0.04;
        /** length of your brake_squeal.ogg in ticks (20 ticks = 1 sec). replays every this many ticks while braking */
        public int brakeClipTicks = 100;
        public float brakeVolume = 0.45f;
        public int brakeHearingRange = 64;

        // ---- starting / stopping ----
        /** ticks between each car's coupler clunk when the slack runs out pulling away */
        public int couplerRippleTicksStart = 6;
        /** ticks between each car's clunk when slack bunches up on stopping */
        public int couplerRippleTicksStop = 3;
        public float couplerVolume = 1.0f;
        public float airReleaseVolume = 1.0f;
        /** train must have been stopped this long before a start counts (stops spam at signals creeping) */
        public int minStoppedTicksForStart = 20;
        public int couplerHearingRange = 48;

        // ---- crossings ----
        /** warning distance. 64 blocks ~ 2.3s warning at Create top speed, more for slower trains */
        public int defaultCrossingRadius = 64;
        /** length of your crossing_bell.ogg in ticks; replays every this many ticks while active */
        public int bellClipTicks = 48;
        public float bellVolume = 1.0f;
        public int bellHearingRange = 48;
        /** keep ringing this many ticks after the last car clears */
        public int bellTailTicks = 40;
        /** a car this close to the crossing counts as ON it (rings even if stopped).
         *  Otherwise the bell only rings for trains heading TOWARD the crossing inside its radius */
        public int onCrossingDistance = 6;

        // ---- Steam 'n' Rails coupler ----
        /** how close (blocks) a bogey has to be to a coupler marker to count as lined up.
         *  S&R's own value is 0.8. Too big on short cars and it may grab the wrong joint. */
        public double couplerAlignmentTolerance = 3.0;

        // ---- diesel horn / steam whistle (/diesel, /steam) ----
        /** length of your diesel_horn.ogg in ticks; replays every this many ticks while held */
        public int hornClipTicks = 60;
        /** length of your steam_whistle.ogg in ticks */
        public int whistleClipTicks = 60;
        public float hornVolume = 1.0f;
        public int hornHearingRange = 128;
        /** cut the horn if no "still holding" signal arrives for this many ticks (stops it sticking) */
        public int hornReleaseTimeoutTicks = 12;

        // ---- misc ----
        /** skip Steam 'n' Rails handcars (no brake squeal / couplers on a handcar) */
        public boolean ignoreHandcars = true;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Values loaded = GSON.fromJson(r, Values.class);
                    if (loaded != null) V = loaded;
                }
            }
            save(); // writes defaults / fills in any new fields
        } catch (Exception e) {
            RailSounds.LOG.error("Could not read railsounds.json, using defaults", e);
            V = new Values();
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(V, w);
        } catch (Exception e) {
            RailSounds.LOG.error("Could not write railsounds.json", e);
        }
    }
}
