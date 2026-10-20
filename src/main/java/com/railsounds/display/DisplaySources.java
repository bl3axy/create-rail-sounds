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
 * Binding the two sensor sources needs both our sources AND our blocks to exist, and the
 * order those happen in is NOT stable - a test server bound them fine creating a fresh world
 * and came up the other way round loading that same world again. So both sides call
 * {@link #tryBindSensors()} and whichever runs second does the work, once.
 */
public final class DisplaySources {
    private DisplaySources() {}

    public static DepartureBoardSource DEPARTURE_BOARD;
    public static PlatformDisplaySource PLATFORM_DISPLAY;
    public static SensorDisplaySource CARGO_READOUT;
    public static SensorDisplaySource CAR_COUNT;

    private static Block cargoSensorBlock, carCounterBlock;
    private static boolean sensorsBound;

    /** Create's side: make the sources and bind the ones that sit on a Station */
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
        tryBindSensors();
    }

    /** our side: hand over the blocks as soon as they exist */
    public static void ownBlocksReady(Block cargoSensor, Block carCounter) {
        cargoSensorBlock = cargoSensor;
        carCounterBlock = carCounter;
        tryBindSensors();
    }

    /** runs twice, binds once - on whichever call finds both halves present */
    private static synchronized void tryBindSensors() {
        if (sensorsBound) return;
        if (CARGO_READOUT == null || CAR_COUNT == null) return;
        if (cargoSensorBlock == null || carCounterBlock == null) return;
        AllDisplaySources.register(CARGO_READOUT, cargoSensorBlock);
        AllDisplaySources.register(CAR_COUNT, carCounterBlock);
        sensorsBound = true;
        RailSounds.LOG.info("[railsounds] sensor display sources bound");
    }
}
