package com.railsounds.display;

import com.railsounds.RailSounds;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllDisplaySources;
import com.zurrtum.create.api.registry.CreateRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/** Our display link sources, offered when a Display Link is pointed at a Train Station. */
public final class DisplaySources {
    private DisplaySources() {}

    public static DepartureBoardSource DEPARTURE_BOARD;
    public static PlatformDisplaySource PLATFORM_DISPLAY;

    public static void register() {
        DEPARTURE_BOARD = Registry.register(CreateRegistries.DISPLAY_SOURCE,
            Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "departure_board"), new DepartureBoardSource());
        PLATFORM_DISPLAY = Registry.register(CreateRegistries.DISPLAY_SOURCE,
            Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "platform_display"), new PlatformDisplaySource());
        AllDisplaySources.register(DEPARTURE_BOARD, AllBlocks.TRACK_STATION);
        AllDisplaySources.register(PLATFORM_DISPLAY, AllBlocks.TRACK_STATION);
    }
}
