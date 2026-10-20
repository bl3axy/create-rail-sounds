package com.railsounds.display;

import com.railsounds.RailSounds;
import com.railsounds.rail.CargoSensorBlock;
import com.railsounds.rail.StationSensors;
import com.zurrtum.create.api.behaviour.display.DisplaySource;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkContext;
import com.zurrtum.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.station.GlobalStation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts a Cargo Sensor or Car Counter reading on a display board.
 *
 *   Cargo Sensor     Car Counter
 *   Spruce Freight   Spruce Freight
 *   78% full         8 cars
 *   [######---]
 *
 * The sensor block itself still has to be touching a Train Station; this just shows what it
 * reads. The Cargo Sensor source follows that block's own items/fluids setting, so sneak-
 * right-clicking the sensor changes the board too.
 */
public class SensorDisplaySource extends DisplaySource {

    private final boolean counter;

    public SensorDisplaySource(boolean counter) {
        this.counter = counter;
    }

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        try {
            return text(context, stats);
        } catch (RuntimeException e) {
            return List.of(Component.literal("--"));
        }
    }

    private List<MutableComponent> text(DisplayLinkContext context, DisplayTargetStats stats) {
        int width = Math.max(4, stats.maxColumns());
        int rows = Math.max(1, stats.maxRows());
        Level level = context.level();
        BlockPos pos = context.getSourcePos();
        List<MutableComponent> lines = new ArrayList<>();

        GlobalStation station = StationSensors.adjacentStation(level, pos);
        if (station == null) {
            lines.add(Component.literal(Departures.trim("No station", width)));
            return lines;
        }
        Train train = StationSensors.dockedTrain(station);
        if (train == null) {
            lines.add(Component.literal(Departures.trim(station.name, width)));
            if (rows > 1) lines.add(Component.literal(Departures.trim(counter ? "no train" : "empty", width)));
            return lines;
        }

        lines.add(Component.literal(Departures.trim(RailSounds.nameOf(train), width)));
        if (counter) {
            int cars = train.carriages.size();
            lines.add(Component.literal(Departures.trim(cars + (cars == 1 ? " car" : " cars"), width)));
        } else {
            BlockState state = level.getBlockState(pos);
            boolean fluids = state.hasProperty(CargoSensorBlock.FLUIDS) && state.getValue(CargoSensorBlock.FLUIDS);
            double fill = StationSensors.fill(train, fluids);
            if (fill < 0) {
                lines.add(Component.literal(Departures.trim(fluids ? "no tanks" : "no storage", width)));
            } else {
                int pct = (int) Math.round(fill * 100);
                lines.add(Component.literal(Departures.trim(pct + "% full", width)));
                if (lines.size() < rows && width >= 6) lines.add(Component.literal(bar(fill, width)));
            }
        }
        return lines.size() > rows ? new ArrayList<>(lines.subList(0, rows)) : lines;
    }

    /** [####----] sized to the display, so a nixie tube doesn't get a 40-wide bar */
    private static String bar(double fill, int width) {
        int inner = Math.max(2, Math.min(20, width - 2));
        int on = (int) Math.round(Math.max(0, Math.min(1, fill)) * inner);
        return "[" + "#".repeat(on) + "-".repeat(inner - on) + "]";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    @Override
    protected String getTranslationKey() {
        return counter ? "car_counter" : "cargo_sensor";
    }
}
