package com.imjustseiyin.block;

import com.imjustseiyin.JustOutdoorStuffsMod;
import com.imjustseiyin.block.custom.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.function.Function;

public class ModBlocks {

    private static Block registerBlock(String name, Function<Properties, Block> factory, Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(JustOutdoorStuffsMod.MOD_ID, name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
            new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    public static final Block GARDEN_BAG_FERTILIZERS = registerBlock("garden_bag_fertilizers",
            p -> new ModHorizontalFacingBlock(p, Block.box(2.5,0,2,13.5,15,15)),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.ROOTED_DIRT));

    public static final Block GARDEN_BIRDBATH = registerBlock("garden_birdbath",
            p -> new ModHorizontalFacingBlock(p, Shapes.or(
                            Block.box(0,17,0,16,20,16),
                            Block.box(3,0,3,13,17,13)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_BUCKET = registerBlock("garden_bucket",
            p -> new ModHorizontalFacingBlock(p, Block.box(5,0,5,11,7,11)),
            Properties.of().noOcclusion().strength(0.3F).instabreak());

    public static final Block GARDEN_FLAMINGO = registerBlock("garden_flamingo",
            p -> new ChairBlock(p, 0.6,
                    Shapes.or(
                            Block.box(5.5,0,3,10.5,15,12.5),
                            Block.box(5.5,15,3,10.5,23,8)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_GNOME_1 = registerBlock("garden_gnome_1",
            p -> new ModHorizontalFacingBlock(p, Block.box(3,0,3,13,20,12)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_GNOME_2 = registerBlock("garden_gnome_2",
            p -> new ModHorizontalFacingBlock(p, Block.box(3,0,3,13,23,12)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_GNOME_3 = registerBlock("garden_gnome_3",
            p -> new ModHorizontalFacingBlock(p, Block.box(3,0,3,13,23,12)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_HOE = registerBlock("garden_hoe",
            p -> new ModHorizontalFacingBlock(p, Block.box(0,-6,13,16,16,16)),
            Properties.of().noOcclusion().strength(0.3F).instabreak());

    public static final Block GARDEN_HOSE = registerBlock("garden_hose",
            p -> new ModHorizontalFacingBlock(p, Block.box(3,0,3,13,12,13)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_LAWN_MOWER = registerBlock("garden_lawn_mower",
            p -> new ModHorizontalFacingBlock(p, Block.box(1,0,1,15,8,15)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_PITCHFORK = registerBlock("garden_pitchfork",
            p -> new ModHorizontalFacingBlock(p, Block.box(0,-13,13,16,16,16)),
            Properties.of().mapColor(MapColor.STONE).noOcclusion().strength(0.3F).instabreak());

    public static final Block GARDEN_PLANTER = registerBlock("garden_planter",
            p -> new PlanterHorizontalFacingBlock(p, Shapes.or(
                            Block.box(0,14,0,16,16.5,16),
                            Block.box(1,0,1,15,14,15)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_RAKE = registerBlock("garden_rake",
            p -> new ModHorizontalFacingBlock(p, Block.box(0,-11,13,16,13,16)),
            Properties.of().mapColor(MapColor.STONE).noOcclusion().strength(0.3F).instabreak());

    public static final Block GARDEN_SHOVEL = registerBlock("garden_shovel",
            p -> new ModHorizontalFacingBlock(p, Block.box(0,-13,13,16,16,16)),
            Properties.of().mapColor(MapColor.STONE).noOcclusion().strength(0.3F).instabreak());

    public static final Block GARDEN_SPADE = registerBlock("garden_spade",
            p -> new ModHorizontalFacingBlock(p, Block.box(0,-14,13,16,16,16)),
            Properties.of().mapColor(MapColor.STONE).noOcclusion().strength(0.3F).instabreak());

    public static final Block GARDEN_STEP_STOOL = registerBlock("garden_step_stool",
            p -> new ModHorizontalFacingBlock(p, Shapes.or(
                            Block.box(0,0,-2,16,4.5,16),
                            Block.box(0,4,0,16,10,14),
                            Block.box(0,10,2,16,15,10)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_TABLE_LARGE = registerBlock("garden_table_large",
            p -> new QuadTableBlock(p, Shapes.or(
                            Block.box(0,14,0,16,16,16),
                            Block.box(0,0,10, 6 ,14 ,16)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_TABLE_SMALL = registerBlock("garden_table_small",
            p -> new ModHorizontalFacingBlock(p, Shapes.or(
                            Block.box(0,14,0,16,16,16),
                            Block.box(6,0,6,10,14,10)

                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_TABLE_WIDE = registerBlock("garden_table_wide",
            p -> new DoubleTableBlock(p, Shapes.or(
                            Block.box(0,14,0,16,16,16),
                            Block.box(4,0,4,12,14,16)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_TOOLS = registerBlock("garden_tools",
            p -> new ModHorizontalFacingBlock(p, Block.box(0,6,13,16,14,16)),
            Properties.of().mapColor(MapColor.STONE).noOcclusion().strength(0.3F).instabreak());

    public static final Block GARDEN_WATERING_CAN = registerBlock("garden_watering_can",
            p -> new ModHorizontalFacingBlock(p, Block.box(5,0,5,11,7,11)),
            Properties.of().mapColor(MapColor.STONE).noOcclusion().strength(0.3F).instabreak());

    public static final Block GARDEN_WHEELBARROW = registerBlock("garden_wheelbarrow",
            p -> new ModHorizontalFacingBlock(p, Block.box(2,0,-3,14,12,16)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_BBQ_GRILL = registerBlock("patio_bbq_grill",
            p -> new ModHorizontalFacingBlock(p, Block.box(-2,0,2,18,16,14)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_CHECKERS_SET = registerBlock("patio_checkers_set",
            p -> new ModHorizontalFacingBlock(p, Block.box(3,0,3,13,1,13)),
            Properties.of().mapColor(MapColor.STONE).noOcclusion().strength(0.3F).instabreak());

    public static final Block PATIO_COFFEE_TABLE = registerBlock("patio_coffee_table",
            p -> new DoubleTableBlock(p, Block.box(0,0,0,16,8,16)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_COFFEE_TABLE_GLASS = registerBlock("patio_coffee_table_glass",
            p -> new DoubleTableBlock(p, Block.box(0,0,0,16,8,16)),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.GLASS));

    public static final Block PATIO_IRON_PLANTER = registerBlock("patio_iron_planter",
            p -> new PlanterHorizontalFacingBlock(p, Block.box(0,0,0,16,17,16)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_IRON_TABLE_LARGE = registerBlock("patio_iron_table_large",
            p -> new QuadTableBlock(p, Shapes.or(
                            Block.box(0,14,0,16,16,16),
                            Block.box(0,0,1,15 ,14 ,16)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_IRON_TABLE_SMALL = registerBlock("patio_iron_table_small",
            p -> new ModHorizontalFacingBlock(p, Shapes.or(
                            Block.box(0,14,0,16,16,16),
                            Block.box(1,0,1,15 ,14 ,15)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_IRON_TABLE_WIDE = registerBlock("patio_iron_table_wide",
            p -> new DoubleTableBlock(p, Shapes.or(
                            Block.box(0,14,0,16,16,16),
                            Block.box(1,0,1,15 ,14 ,16)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_MODERN_PLANTER_BLACK = registerBlock("patio_modern_planter_black",
            p -> new PlanterHorizontalFacingBlock(p, Block.box(0,0,0,16,16.5,16)),
            Properties.of().strength(0.3F));

    public static final Block PATIO_MODERN_PLANTER_WHITE = registerBlock("patio_modern_planter_white",
            p -> new PlanterHorizontalFacingBlock(p, Block.box(0,0,0,16,16.5,16)),
            Properties.of().strength(0.3F));

    public static final Block PATIO_SIDE_TABLE = registerBlock("patio_side_table",
            p -> new ModHorizontalFacingBlock(p, Shapes.or(
                            Block.box(1,14,1,15,16,15),
                            Block.box(2,0,2, 14,14,14)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_TEA_SET = registerBlock("patio_tea_set",
            p -> new ModHorizontalFacingBlock(p, Block.box(0,0,0,16,8,16)),
            Properties.of().mapColor(MapColor.STONE).noOcclusion().strength(0.3F).instabreak().sound(SoundType.GLASS));

    public static final Block GARDEN_BENCH = registerBlock("garden_bench",
            p -> new DoubleChairBlock(p, 0.4,
                    Shapes.or(
                            Block.box(0,0,0,16,15,2),
                            Block.box(0,8,2, 16,10,16),
                            Block.box(14,15,0, 16,24,16)

                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_CHAIR = registerBlock("garden_chair",
            p -> new ChairBlock(p, 0.4,
                    Shapes.or(
                            Block.box(2,0,2,14,10,14),
                            Block.box(2,10,12, 14,22,14)
                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block GARDEN_STOOL = registerBlock("garden_stool",
            p -> new ChairBlock(p, 0.4,
                    Block.box(3,0,3,13,10,13)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_ARMCHAIR = registerBlock("patio_armchair",
            p -> new ChairBlock(p, 0.4,
                    Shapes.or(
                            Block.box(0,0,0,16,9,17),
                            Block.box(0,9,12,16,19,17),

                            Block.box(13,0,2,18,13,17),
                            Block.box(-2,0,2,3,13,17)
                    )),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.WOOL));

    public static final Block PATIO_BENCH = registerBlock("patio_bench",
            p -> new DoubleChairBlock(p, 0.3,
                    Shapes.or(
                            Block.box(2,9,0,14,13,3),
                            Block.box(2,0,0,14,9,16)
                    )),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.WOOL));

    public static final Block PATIO_IRON_BENCH = registerBlock("patio_iron_bench",
            p -> new DoubleChairBlock(p, 0.4,
                    Shapes.or(
                            Block.box(0,0,0,16,14,2),
                            Block.box(0,8,2, 16,10,16),
                            Block.box(14,14,0, 16,23,16)

                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_IRON_CHAIR = registerBlock("patio_iron_chair",
            p -> new ChairBlock(p, 0.4,
                    Shapes.or(
                            Block.box(2,0,2,14,10,14),
                            Block.box(2,10,12, 14,22,14)

                    )),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_IRON_ROCKING_CHAIR = registerBlock("patio_iron_rocking_chair",
            p -> new ChairBlock(p, 0.4,
                    Block.box(0,0,3,16,11,18)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_IRON_STOOL = registerBlock("patio_iron_stool",
            p -> new ChairBlock(p, 0.4,
                    Block.box(3,0,3,13,10,13)),
            Properties.of().noOcclusion().strength(0.3F));

    public static final Block PATIO_LOUNGE_CHAIR = registerBlock("patio_lounge_chair",
            p -> new ChairBlock(p, 0.35,
                    Block.box(1,0,0,15,9,18.5)),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.WOOL));

    public static final Block PATIO_SOFA_CHAISE = registerBlock("patio_sofa_chaise",
            p -> new ChairBlock(p, 0.4,
                    Block.box(0,0,0,16,9,16)),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.WOOL));

    public static final Block PATIO_SOFA_CORNER = registerBlock("patio_sofa_corner",
            p -> new ChairBlock(p, 0.4,
                    Shapes.or(
                            Block.box(0,0,0,16,9,17),
                            Block.box(0,9,12,16,19,17),

                            Block.box(-1,0,0,4,19,17)
                    )),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.WOOL));

    public static final Block PATIO_SOFA_LEFT = registerBlock("patio_sofa_left",
            p -> new ChairBlock(p, 0.4,
                    Shapes.or(
                            Block.box(0,0,0,16,9,17),
                            Block.box(0,9,12,14,19,17),

                            Block.box(12,0,2,17,13,17)
                    )),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.WOOL));

    public static final Block PATIO_SOFA_MID = registerBlock("patio_sofa_mid",
            p -> new ChairBlock(p, 0.4,
                    Shapes.or(
                            Block.box(0,0,0,16,9,17),
                            Block.box(0,9,12,16,19,17)
                    )),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.WOOL));

    public static final Block PATIO_SOFA_RIGHT = registerBlock("patio_sofa_right",
            p -> new ChairBlock(p, 0.4,
                    Shapes.or(
                            Block.box(0,0,0,16,9,17),
                            Block.box(2,9,12,16,19,17),

                            Block.box(-1,0,2,4,13,17)
                    )),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.WOOL));

    public static final Block PATIO_SOFA_STOOL = registerBlock("patio_sofa_stool",
            p -> new ChairBlock(p, 0.35,
                    Block.box(4,0,4,12,9,12)),
            Properties.of().noOcclusion().strength(0.3F).sound(SoundType.WOOL));

    public static void registerModBlocks() {
    }
}
