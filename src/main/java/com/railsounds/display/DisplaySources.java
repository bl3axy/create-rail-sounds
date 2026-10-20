package com.railsounds.display;

import com.railsounds.RailSounds;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllDisplaySources;
import com.zurrtum.create.api.registry.CreateRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Our display link sources.
 *
 * Registration happens in two steps on purpose. We depend on Create, so Create's init - and
 * the mixin that calls {@link #register()} - runs before ours. At that moment our own blocks
 * don't exist yet, so anything pointing at them has to wait for {@link #bindOwnBlocks(Block,
 * Block)}, which RailBlocks calls once it has registered them.
 */
public final class DisplaySources {
    private DisplaySources() {}

    public static DepartureBoardSource DEPARTURE_BOARD;
    public static PlatformDisplaySource PLATFORM_DISPLAY;
    public static SensorDisplaySource CARGO_READOUT;
    public static SensorDisplaySource CAR_COUNT;

    /** step 1, during Create's init: make the sources and bind the ones that sit on a Station */
    public static void register() {
        DEPARTURE_BOARD = Registry.register(CreateRegistries.DISPLAY_SOURCE,
            Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "departure_board"), new DepartureBoardSource());
        PLATFORM_DISPLAY = Registry.register(CreateRegistries.DISPLAY_SOURCE,
            Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "platform_display"), new PlatformDisplaySource());
        CARGO_READOUT = Registry.register(CreateRegistries.DISPLAY_SOURCE,
            Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "cargo_readout"), new SensorDisplaySource(false));
        CAR_COUNT = Registry.register(CreateRegistries.DISPLAY_SOURCE,
            Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "car_count"), new SensorDisplaySource(true));

        AllDisplaySources.register(DEPARTURE_BOARD, AllBlocks.TRACK_STATION);
        AllDisplaySources.register(PLATFORM_DISPLAY, AllBlocks.TRACK_STATION);
    }

    /** step 2, once our blocks exist: point a Display Link at a sensor and read it */
    public static void bindOwnBlocks(Block cargoSensor, Block carCounter) {
        if (CARGO_READOUT == null || CAR_COUNT == null) {
            // register() never ran - Create's init changed shape. The sources simply won't be offered.
            RailSounds.LOG.warn("[railsounds] display sources were not set up; sensor readouts unavailable");
            return;
        }
        AllDisplaySources.register(CARGO_READOUT, cargoSensor);
        AllDisplaySources.register(CAR_COUNT, carCounter);
    }
}
