package com.imjustseiyin.item;

import com.imjustseiyin.JustOutdoorStuffsMod;
import com.imjustseiyin.block.ModBlocks;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModItemGroups {
    public static CreativeModeTab JUSTOUTDOORSTUFF_GROUP;

    public static void registerItemGroups() {
        JUSTOUTDOORSTUFF_GROUP = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(JustOutdoorStuffsMod.MOD_ID, "justoutdoorstuff"),
            FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.justoutdoorstuffs.just_outdoor_stuffs"))
                .icon(() -> new ItemStack(ModBlocks.GARDEN_WATERING_CAN))
                .displayItems((params, entries) -> {
                    entries.accept(ModBlocks.GARDEN_CHAIR);
                    entries.accept(ModBlocks.GARDEN_STOOL);
                    entries.accept(ModBlocks.GARDEN_BENCH);
                    entries.accept(ModBlocks.GARDEN_TABLE_SMALL);
                    entries.accept(ModBlocks.GARDEN_TABLE_WIDE);
                    entries.accept(ModBlocks.GARDEN_TABLE_LARGE);
                    entries.accept(ModBlocks.GARDEN_PLANTER);
                    entries.accept(ModBlocks.GARDEN_LAWN_MOWER);
                    entries.accept(ModBlocks.GARDEN_WHEELBARROW);
                    entries.accept(ModBlocks.GARDEN_HOSE);
                    entries.accept(ModBlocks.GARDEN_WATERING_CAN);
                    entries.accept(ModBlocks.GARDEN_STEP_STOOL);
                    entries.accept(ModBlocks.GARDEN_BAG_FERTILIZERS);
                    entries.accept(ModBlocks.GARDEN_TOOLS);
                    entries.accept(ModBlocks.GARDEN_HOE);
                    entries.accept(ModBlocks.GARDEN_RAKE);
                    entries.accept(ModBlocks.GARDEN_SHOVEL);
                    entries.accept(ModBlocks.GARDEN_SPADE);
                    entries.accept(ModBlocks.GARDEN_PITCHFORK);
                    entries.accept(ModBlocks.GARDEN_BUCKET);
                    entries.accept(ModBlocks.GARDEN_BIRDBATH);
                    entries.accept(ModBlocks.GARDEN_FLAMINGO);
                    entries.accept(ModBlocks.GARDEN_GNOME_1);
                    entries.accept(ModBlocks.GARDEN_GNOME_2);
                    entries.accept(ModBlocks.GARDEN_GNOME_3);
                    entries.accept(ModBlocks.PATIO_IRON_CHAIR);
                    entries.accept(ModBlocks.PATIO_IRON_STOOL);
                    entries.accept(ModBlocks.PATIO_IRON_BENCH);
                    entries.accept(ModBlocks.PATIO_IRON_TABLE_SMALL);
                    entries.accept(ModBlocks.PATIO_IRON_TABLE_WIDE);
                    entries.accept(ModBlocks.PATIO_IRON_TABLE_LARGE);
                    entries.accept(ModBlocks.PATIO_IRON_PLANTER);
                    entries.accept(ModBlocks.PATIO_MODERN_PLANTER_BLACK);
                    entries.accept(ModBlocks.PATIO_MODERN_PLANTER_WHITE);
                    entries.accept(ModBlocks.PATIO_IRON_ROCKING_CHAIR);
                    entries.accept(ModBlocks.PATIO_ARMCHAIR);
                    entries.accept(ModBlocks.PATIO_SOFA_LEFT);
                    entries.accept(ModBlocks.PATIO_SOFA_MID);
                    entries.accept(ModBlocks.PATIO_SOFA_RIGHT);
                    entries.accept(ModBlocks.PATIO_SOFA_CORNER);
                    entries.accept(ModBlocks.PATIO_SOFA_CHAISE);
                    entries.accept(ModBlocks.PATIO_SOFA_STOOL);
                    entries.accept(ModBlocks.PATIO_BENCH);
                    entries.accept(ModBlocks.PATIO_LOUNGE_CHAIR);
                    entries.accept(ModBlocks.PATIO_SIDE_TABLE);
                    entries.accept(ModBlocks.PATIO_COFFEE_TABLE);
                    entries.accept(ModBlocks.PATIO_COFFEE_TABLE_GLASS);
                    entries.accept(ModBlocks.PATIO_TEA_SET);
                    entries.accept(ModBlocks.PATIO_CHECKERS_SET);
                    entries.accept(ModBlocks.PATIO_BBQ_GRILL);
                    entries.accept(ModItems.BLUEPRINT_GARDEN_STUFFS);
                    entries.accept(ModItems.BLUEPRINT_PATIO_STUFFS);
                })
                .build());
    }
}
