package com.railsounds.display;

import com.zurrtum.create.content.trains.display.GlobalTrainDisplayData;
import com.zurrtum.create.content.trains.display.GlobalTrainDisplayData.TrainDeparturePrediction;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Shared bits for the departure board and platform display: clock times and "via" stops. */
final class Departures {
    private Departures() {}

    /** next trains for a station filter (Create's own glob: "Spruce*" etc), soonest first */
    static List<TrainDeparturePrediction> upcoming(String filter, int max) {
        if (filter == null || filter.isBlank()) return List.of();
        try {
            return GlobalTrainDisplayData.prepare(filter, max);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** in-game clock time the train is due, like "14:35", or "now" / "--:--" */
    static String clock(Level level, int ticks) {
        if (ticks == -1 || ticks >= 11700) return "--:--";
        if (ticks < 200) return "now";
        if (!(level instanceof ServerLevel sl)) return "--:--";
        long t = sl.getOverworldClockTime() + ticks;
        int day = (int) (t % 24000);
        int hours = (day / 1000 + 6) % 24;
        int minutes = day % 1000 * 60 / 1000;
        return (hours < 10 ? "0" : "") + hours + ":" + (minutes < 10 ? "0" : "") + minutes;
    }

    /** "in 3 min" style countdown */
    static String countdown(int ticks) {
        if (ticks == -1 || ticks >= 11700) return "~";
        if (ticks < 200) return "now";
        int min = Math.max(1, Math.round(ticks / 1200f));
        return min + " min";
    }

    /** the stops this train makes after the given one (from its schedule), up to max */
    static List<String> via(TrainDeparturePrediction p, int max) {
        List<String> out = new ArrayList<>();
        try {
            Train t = p.train;
            if (t == null || t.runtime == null || t.runtime.getSchedule() == null) return out;
            List<TrainDeparturePrediction> all = new ArrayList<>(t.runtime.submitPredictions());
            // the board's numbers are from Create's last refresh, so match the visit to this
            // station whose time is closest, not an exact tick count
            int start = -1;
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < all.size(); i++) {
                TrainDeparturePrediction q = all.get(i);
                if (!q.destination.equals(p.destination)) continue;
                int d = Math.abs(q.ticks - p.ticks);
                if (d < best) {
                    best = d;
                    start = i;
                }
            }
            if (start < 0) return out;
            for (int i = start + 1; i < all.size(); i++) {
                TrainDeparturePrediction q = all.get(i);
                if (q.destination.equals(p.destination)) break; // came back round
                if (!out.contains(q.destination)) out.add(q.destination);
                if (out.size() >= max) break;
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    /** the platform part of a station name, the same way Create does it: the text matched by * */
    static String platform(String filter, String destination) {
        if (!filter.contains("*")) return "";
        String platform = destination;
        for (String part : filter.split("\\*"))
            if (!part.isEmpty()) platform = platform.replace(part, "");
        return platform.replace("*", "?").trim();
    }

    static String trim(String s, int len) {
        if (len <= 0) return "";
        return s.length() <= len ? s : s.substring(0, Math.max(0, len - 1)) + ".";
    }
}
