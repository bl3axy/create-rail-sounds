package com.railsounds.rail;

import com.railsounds.RailSounds;
import com.zurrtum.create.api.behaviour.movement.MovementBehaviour;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.railsounds.rail.RailShapes.box;
import static com.railsounds.rail.RailShapes.horizontal;

/**
 * Every block this mod adds. Blocks mean this mod now has to be installed on the server AND
 * every client (Minecraft refuses to join otherwise).
 */
public final class RailBlocks {
    private RailBlocks() {}

    private static final List<Item> WORKING = new ArrayList<>();
    private static final List<Item> DECOR = new ArrayList<>();

    public static Block CARGO_SENSOR, CAR_COUNTER, LOADING_HATCH, SEARCHLIGHT_SIGNAL, DEFECT_DETECTOR,
        SPEED_SIGN, SWITCH_STAND, INTERLOCKING_PANEL, CROSSING_SIGNAL, CROSSING_GATE, CAR_RETARDER,
        SWITCH_MACHINE, ROUTE_SENSOR, SIGNAL_MAST;

    public static void register() {
        // ---- working blocks ----
        CARGO_SENSOR = add(WORKING, "cargo_sensor", CargoSensorBlock::new, metal(MapColor.COLOR_GRAY));
        CAR_COUNTER = add(WORKING, "car_counter", CarCounterBlock::new, metal(MapColor.COLOR_GRAY));
        LOADING_HATCH = add(WORKING, "loading_hatch", Block::new,
            Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL));
        SEARCHLIGHT_SIGNAL = add(WORKING, "searchlight_signal", SearchlightSignalBlock::new,
            metal(MapColor.COLOR_BLACK).lightLevel(SearchlightSignalBlock::light));
        DEFECT_DETECTOR = add(WORKING, "defect_detector", DefectDetectorBlock::new, metal(MapColor.COLOR_LIGHT_GRAY));
        SPEED_SIGN = add(WORKING, "speed_sign", SpeedSignBlock::new, metal(MapColor.SNOW));
        SWITCH_STAND = add(WORKING, "switch_stand", SwitchStandBlock::new, metal(MapColor.COLOR_BLACK));
        INTERLOCKING_PANEL = add(WORKING, "interlocking_panel", InterlockingPanelBlock::new, metal(MapColor.COLOR_RED));
        CROSSING_SIGNAL = add(WORKING, "crossing_signal", CrossingSignalBlock::new,
            metal(MapColor.SNOW).lightLevel(CrossingSignalBlock::light));
        CROSSING_GATE = add(WORKING, "crossing_gate", CrossingGateBlock::new,
            metal(MapColor.SNOW).lightLevel(CrossingGateBlock::light));
        CAR_RETARDER = add(WORKING, "car_retarder", CarRetarderBlock::new, metal(MapColor.COLOR_YELLOW));
        SWITCH_MACHINE = add(WORKING, "switch_machine", SwitchMachineBlock::new, metal(MapColor.COLOR_GRAY));
        ROUTE_SENSOR = add(WORKING, "route_sensor", RouteSensorBlock::new, metal(MapColor.COLOR_YELLOW));
        RailItems.register();
        WORKING.add(RailItems.SWITCH_LINKER);

        // ---- trackside decor ----
        SIGNAL_MAST = decor("signal_mast", metal(MapColor.COLOR_BLACK), box(7, 0, 7, 9, 16, 9));
        decor("mile_post", stone(MapColor.SNOW), box(6, 0, 6, 10, 15, 10));
        decor("whistle_post", metal(MapColor.SNOW), box(7, 0, 7, 9, 9, 9), box(4, 7, 7, 12, 15, 8));
        decor("yard_limit_sign", metal(MapColor.SNOW), box(2, 0, 7.5, 3, 8, 8.5), box(13, 0, 7.5, 14, 8, 8.5),
            box(0, 8, 7, 16, 15, 8));
        decor("crossbuck", metal(MapColor.SNOW), box(7, 0, 7, 9, 16, 9), box(1, 6, 6, 15, 16, 7));
        decor("relay_case", metal(MapColor.COLOR_LIGHT_GRAY), box(2, 0, 3, 14, 15, 13));
        decor("battery_box", metal(MapColor.COLOR_LIGHT_GRAY), box(1, 0, 4, 15, 7, 12));
        decor("phone_box", metal(MapColor.COLOR_YELLOW), box(7, 0, 7, 9, 8, 9), box(4, 8, 6, 12, 16, 10));
        decor("buffer_stop", metal(MapColor.COLOR_RED), box(1, 0, 1, 15, 12, 14));
        decor("wheel_stop", metal(MapColor.COLOR_YELLOW), box(2, 0, 5, 14, 4, 11));
        decor("derail", metal(MapColor.COLOR_YELLOW), box(3, 0, 6, 13, 3, 10));
        decor("tie_pile", wood(MapColor.COLOR_BROWN), box(0, 0, 1, 16, 9, 15));
        decor("rail_rack", metal(MapColor.METAL), box(0, 0, 2, 16, 5, 14));
        decor("spike_keg", wood(MapColor.WOOD), box(4, 0, 4, 12, 11, 12));
        decor("wheelset", metal(MapColor.METAL), box(1, 0, 3, 3, 10, 13), box(13, 0, 3, 15, 10, 13),
            box(3, 4, 7, 13, 6, 9));
        add(DECOR, "yard_light", p -> new FacingRailBlock(p, horizontal(box(7, 0, 7, 9, 16, 9), box(6, 12, 2, 10, 16, 9))),
            metal(MapColor.COLOR_GRAY).lightLevel(s -> 15));
        decor("telegraph_pole", wood(MapColor.WOOD), box(6.5, 0, 6.5, 9.5, 16, 9.5), box(0, 12, 7, 16, 14, 9));

        MovementBehaviour.REGISTRY.register(LOADING_HATCH, new LoadingHatchMovement());
        UseBlockCallback.EVENT.register(RailItems::onUseBlock);

        tab("redstone_blocks", WORKING);
        tab("functional_blocks", DECOR);
        RailSounds.LOG.info("[railsounds] {} railroad blocks registered", WORKING.size() + DECOR.size());
    }

    private static Properties metal(MapColor color) {
        return Properties.of().mapColor(color).strength(1.5f, 6.0f).sound(SoundType.METAL).noOcclusion();
    }

    private static Properties wood(MapColor color) {
        return Properties.of().mapColor(color).strength(1.5f, 3.0f).sound(SoundType.WOOD).noOcclusion();
    }

    private static Properties stone(MapColor color) {
        return Properties.of().mapColor(color).strength(1.5f, 6.0f).sound(SoundType.STONE).noOcclusion();
    }

    private static Block decor(String name, Properties props, RailShapes.Box... boxes) {
        Map<Direction, VoxelShape> shapes = horizontal(boxes);
        return add(DECOR, name, p -> new FacingRailBlock(p, shapes), props);
    }

    private static Block add(List<Item> list, String name, Function<Properties, Block> factory, Properties props) {
        Identifier id = Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, name);
        BlockItemId key = BlockItemId.create(id, id);
        Block block = Blocks.register(key, factory, props);
        list.add(Items.registerBlock(key, block));
        return block;
    }

    private static void tab(String vanillaTab, List<Item> items) {
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("minecraft", vanillaTab));
        CreativeModeTabEvents.modifyOutputEvent(key).register(output -> {
            for (Item item : items) output.accept(item);
        });
    }
}
