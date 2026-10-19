package com.railsounds.display;

import com.zurrtum.create.api.behaviour.display.DisplaySource;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkContext;
import com.zurrtum.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.zurrtum.create.content.trains.display.FlapDisplayBlockEntity;
import com.zurrtum.create.content.trains.display.FlapDisplayLayout;
import com.zurrtum.create.content.trains.display.FlapDisplaySection;
import com.zurrtum.create.content.trains.display.GlobalTrainDisplayData.TrainDeparturePrediction;
import com.zurrtum.create.content.trains.station.GlobalStation;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Station departure board, like a real one: departure CLOCK time, train, destination, platform.
 * One train per row. Filter works like Create's (put * where the platform number goes,
 * e.g. "Spruce Town *" for stations named "Spruce Town 1", "Spruce Town 2"...).
 * Optional "via" line under the first train when Via = 1.
 */
public class DepartureBoardSource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        try {
            return text(context, stats);
        } catch (RuntimeException e) {
            return List.of(Component.literal("--"));
        }
    }

    private List<MutableComponent> text(DisplayLinkContext context, DisplayTargetStats stats) {
        // plain-text targets (signs, lecterns, nixies): one line per train
        String filter = context.sourceConfig().getStringOr("Filter", "");
        List<MutableComponent> out = new ArrayList<>();
        for (TrainDeparturePrediction p : Departures.upcoming(filter, Math.max(1, stats.maxRows()))) {
            String plat = Departures.platform(filter, p.destination);
            out.add(Component.literal(Departures.clock(context.level(), p.ticks) + " " + p.train.name.getString()
                + " - " + p.scheduleTitle.getString() + (plat.isEmpty() ? "" : " [" + plat + "]")));
        }
        return out.isEmpty() ? List.of(Component.literal("No departures")) : out;
    }

    @Override
    public List<List<MutableComponent>> provideFlapDisplayText(DisplayLinkContext context, DisplayTargetStats stats) {
        try {
            return flap(context, stats);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<List<MutableComponent>> flap(DisplayLinkContext context, DisplayTargetStats stats) {
        CompoundTag conf = context.sourceConfig();
        String filter = conf.getStringOr("Filter", "");
        boolean hasPlatform = filter.contains("*");
        boolean via = conf.getIntOr("Via", 1) == 1;
        int rows = stats.maxRows();
        List<List<MutableComponent>> list = new ArrayList<>();
        List<TrainDeparturePrediction> next = Departures.upcoming(filter, rows);
        for (int i = 0; i < next.size() && list.size() < rows; i++) {
            TrainDeparturePrediction p = next.get(i);
            List<MutableComponent> row = new ArrayList<>();
            row.add(Component.literal(Departures.clock(context.level(), p.ticks)));
            row.add(p.train.name.copy());
            row.add(p.scheduleTitle.copy());
            if (hasPlatform) row.add(Component.literal(Departures.platform(filter, p.destination)));
            list.add(row);
            // under the very next train: where it stops on the way
            if (i == 0 && via && rows >= 3 && list.size() < rows) {
                List<String> stops = Departures.via(p, 4);
                if (!stops.isEmpty()) {
                    List<MutableComponent> v = new ArrayList<>();
                    v.add(WHITESPACE);
                    v.add(WHITESPACE);
                    v.add(Component.literal("via " + String.join(", ", stops)));
                    if (hasPlatform) v.add(WHITESPACE);
                    list.add(v);
                }
            }
        }
        while (list.size() < rows) {
            List<MutableComponent> empty = new ArrayList<>();
            empty.add(WHITESPACE);
            empty.add(WHITESPACE);
            empty.add(WHITESPACE);
            if (hasPlatform) empty.add(WHITESPACE);
            list.add(empty);
        }
        return list;
    }

    @Override
    public void loadFlapDisplayLayout(DisplayLinkContext context, FlapDisplayBlockEntity flapDisplay, FlapDisplayLayout layout) {
        CompoundTag conf = context.sourceConfig();
        boolean hasPlatform = conf.getStringOr("Filter", "").contains("*");
        int nameCol = conf.getIntOr("NameColumn", 40);
        int platChars = conf.getIntOr("PlatformColumn", 3);
        String key = "RSDepartures" + nameCol + hasPlatform + platChars + flapDisplay.xSize;
        if (layout.isLayout(key)) return;

        float gap = 8f;
        float timeW = 5 * 7f;                       // "14:35"
        float total = flapDisplay.xSize * 32f - 4f - timeW - gap * 2;
        float platW = hasPlatform ? Math.min(platChars * 7f, total / 3) : 0;
        if (hasPlatform) total -= gap + platW;
        total = Math.max(0, total);
        int nameChars = Math.max(0, (int) (nameCol / 100f * total / 7f));
        int destChars = Math.max(0, (int) ((total - nameChars * 7f) / 7f));

        List<FlapDisplaySection> sections = new ArrayList<>();
        sections.add(new FlapDisplaySection(timeW, "instant", false, true));
        sections.add(new FlapDisplaySection(nameChars * 7f, "alphabet", false, nameChars > 0));
        sections.add(new FlapDisplaySection(destChars * 7f, "alphabet", false, hasPlatform));
        if (hasPlatform) sections.add(new FlapDisplaySection(platW, "numeric", false, false).rightAligned());
        layout.configure(key, sections);
    }

    @Override
    public void populateData(DisplayLinkContext context) {
        CompoundTag conf = context.sourceConfig();
        if (!conf.contains("NameColumn")) conf.putInt("NameColumn", 40);
        if (!conf.contains("PlatformColumn")) conf.putInt("PlatformColumn", 3);
        if (!conf.contains("Via")) conf.putInt("Via", 1);
        if (!conf.contains("Filter") && context.getSourceBlockEntity() instanceof StationBlockEntity sbe) {
            GlobalStation st = sbe.getStation();
            if (st != null) conf.putString("Filter", st.name);
        }
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 40; // clock times and "now" stay fresh
    }

    @Override
    protected String getTranslationKey() {
        return "departure_board";
    }
}
