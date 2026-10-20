package com.railsounds.client;

import com.railsounds.display.DisplaySources;
import com.zurrtum.create.client.AllDisplaySourceRenders;
import com.zurrtum.create.client.api.behaviour.display.DisplaySourceRender;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The settings rows you see in the Display Link screen for our two sources. */
public final class DisplaySourceScreens {
    private DisplaySourceScreens() {}

    private static final int BLUE = 0xFF5391E1;

    public static void register() {
        AllDisplaySourceRenders.register(DisplaySources.DEPARTURE_BOARD, () -> (DisplaySourceRender) (source, context, builder, isFirstLine) -> {
            if (isFirstLine) {
                filterBox(builder);
            } else {
                builder.addScrollInput(0, 32, (si, l) -> {
                    si.titled(Component.literal("Train name column")).withRange(0, 73).withShiftStep(12);
                    si.setState(40);
                    l.withSuffix("%");
                }, "NameColumn");
                builder.addScrollInput(36, 22, (si, l) -> {
                    si.titled(Component.literal("Platform column width")).withRange(0, 16).withShiftStep(4);
                    si.setState(3);
                }, "PlatformColumn");
                builder.addSelectionScrollInput(62, 60, (si, l) -> {
                    si.forOptions(List.of(Component.literal("No via"), Component.literal("Show via")));
                    si.titled(Component.literal("Stops of the next train"));
                    si.setState(1);
                }, "Via");
            }
        });
        AllDisplaySourceRenders.register(DisplaySources.PLATFORM_DISPLAY, () -> (DisplaySourceRender) (source, context, builder, isFirstLine) -> {
            if (isFirstLine) filterBox(builder);
        });
        // the sensor readouts take their settings from the sensor block itself, so no rows here
        AllDisplaySourceRenders.register(DisplaySources.CARGO_READOUT,
            () -> (DisplaySourceRender) (source, context, builder, isFirstLine) -> {});
        AllDisplaySourceRenders.register(DisplaySources.CAR_COUNT,
            () -> (DisplaySourceRender) (source, context, builder, isFirstLine) -> {});
    }

    private static void filterBox(com.zurrtum.create.client.foundation.gui.ModularGuiLineBuilder builder) {
        builder.addTextInput(0, 137, (e, t) -> {
            e.setValue("");
            t.withTooltip(List.of(
                Component.literal("Station name to show (use * for the platform number,").withColor(BLUE),
                Component.literal("e.g. Spruce Town * for Spruce Town 1, 2, 3...)").withColor(BLUE)));
        }, "Filter");
    }
}
