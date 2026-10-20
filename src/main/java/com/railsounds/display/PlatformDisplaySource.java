package com.railsounds.display;

import com.zurrtum.create.api.behaviour.display.DisplaySource;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkContext;
import com.zurrtum.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.zurrtum.create.content.trains.display.GlobalTrainDisplayData.TrainDeparturePrediction;
import com.zurrtum.create.content.trains.station.GlobalStation;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform display for one platform: the next train in detail, then the one after.
 *   14:35  Coal Express        (in 3 min)
 *   to Mine Junction
 *   via Spruce Yard, Ridge, Mine Junction
 *   then 14:52  Local to Spruce Town
 * Works on display boards, signs, lecterns and nixie tubes (as many lines as fit).
 */
public class PlatformDisplaySource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        try {
            return text(context, stats);
        } catch (RuntimeException e) {
            return List.of(Component.literal("--"));
        }
    }

    private List<MutableComponent> text(DisplayLinkContext context, DisplayTargetStats stats) {
        CompoundTag conf = context.sourceConfig();
        String filter = conf.getStringOr("Filter", "");
        int width = Math.max(8, stats.maxColumns());
        List<TrainDeparturePrediction> next = Departures.upcoming(filter, 2);
        List<MutableComponent> lines = new ArrayList<>();
        if (next.isEmpty()) {
            lines.add(Component.literal(Departures.trim("No trains scheduled", width)));
            return lines;
        }
        TrainDeparturePrediction p = next.get(0);
        String time = Departures.clock(context.level(), p.ticks);
        String in = Departures.countdown(p.ticks);
        String head = time + "  " + com.railsounds.RailSounds.nameOf(p.train);
        String tail = time.equals("now") || in.equals("~") ? "" : "  (" + in + ")";
        lines.add(Component.literal(Departures.trim(head + (head.length() + tail.length() <= width ? tail : ""), width)));
        lines.add(Component.literal(Departures.trim("to " + p.scheduleTitle.getString(), width)));
        List<String> via = Departures.via(p, 5);
        if (!via.isEmpty()) lines.add(Component.literal(Departures.trim("via " + String.join(", ", via), width)));
        if (next.size() > 1) {
            TrainDeparturePrediction q = next.get(1);
            lines.add(Component.literal(Departures.trim("then " + Departures.clock(context.level(), q.ticks) + "  "
                + com.railsounds.RailSounds.nameOf(q.train) + " to " + q.scheduleTitle.getString(), width)));
        }
        int rows = Math.max(1, stats.maxRows());
        return lines.size() > rows ? new ArrayList<>(lines.subList(0, rows)) : lines;
    }

    @Override
    public void populateData(DisplayLinkContext context) {
        CompoundTag conf = context.sourceConfig();
        if (!conf.contains("Filter") && context.getSourceBlockEntity() instanceof StationBlockEntity sbe) {
            GlobalStation st = sbe.getStation();
            if (st != null) conf.putString("Filter", st.name);
        }
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 40;
    }

    @Override
    protected String getTranslationKey() {
        return "platform_display";
    }
}
