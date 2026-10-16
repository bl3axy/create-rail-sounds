package com.roadbed;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

import java.util.ArrayList;
import java.util.List;

/**
 * Railroad Roadbed: slabs and stairs made from loose ground blocks (gravel, dirt, sand, mud, clay)
 * for building track ballast and embankments. They don't fall like gravel does.
 */
public class Roadbed implements ModInitializer {
    public static final String MOD_ID = "roadbed";

    private static final List<Item> ITEMS = new ArrayList<>();

    @Override
    public void onInitialize() {
        material("gravel", Blocks.GRAVEL);
        material("coarse_dirt", Blocks.COARSE_DIRT);
        material("dirt", Blocks.DIRT);
        material("sand", Blocks.SAND);
        material("red_sand", Blocks.RED_SAND);
        material("packed_mud", Blocks.PACKED_MUD);
        material("clay", Blocks.CLAY);

        // custom-textured railroad ballast: full block + slab + stairs each
        ballast("ballast");
        ballast("weathered_ballast");
        ballast("cinder_ballast");

        // show them in the Building Blocks creative tab
        ResourceKey<CreativeModeTab> building = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("minecraft", "building_blocks"));
        CreativeModeTabEvents.modifyOutputEvent(building).register(output -> {
            for (Item item : ITEMS) output.accept(item);
        });
    }

    /** registers <name>_slab and <name>_stairs, copying the look/sound/hardness of the base block */
    private static void material(String name, Block base) {
        BlockItemId slabId = id(name + "_slab");
        Block slab = Blocks.register(slabId, SlabBlock::new, Properties.ofFullCopy(base));
        ITEMS.add(Items.registerBlock(slabId, slab));

        BlockItemId stairsId = id(name + "_stairs");
        Block stairs = Blocks.register(stairsId, props -> new StairBlock(base.defaultBlockState(), props), Properties.ofFullCopy(base));
        ITEMS.add(Items.registerBlock(stairsId, stairs));
    }

    /** a brand-new full block (sounds/hardness like gravel, but it doesn't fall) plus its slab and stairs */
    private static void ballast(String name) {
        BlockItemId blockId = id(name);
        Block block = Blocks.register(blockId, Block::new, Properties.ofFullCopy(Blocks.GRAVEL));
        ITEMS.add(Items.registerBlock(blockId, block));
        material(name, block);
    }

    private static BlockItemId id(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, path);
        return BlockItemId.create(id, id);
    }
}
