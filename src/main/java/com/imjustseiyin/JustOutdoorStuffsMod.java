package com.imjustseiyin;

import com.imjustseiyin.block.ModBlocks;
import com.imjustseiyin.item.ModItemGroups;
import com.imjustseiyin.item.ModItems;
import com.imjustseiyin.sit.SitEntity;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JustOutdoorStuffsMod implements ModInitializer {
    public static final String MOD_ID = "justoutdoorstuffs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ResourceKey<EntityType<?>> SIT_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "entity_sit"));

    public static final EntityType<SitEntity> SIT_ENTITY_TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, SIT_KEY,
        EntityType.Builder.<SitEntity>of(SitEntity::new, MobCategory.MISC)
            .sized(0.001F, 0.001F).noSummon().fireImmune().clientTrackingRange(10)
            .build(SIT_KEY));

    @Override
    public void onInitialize() {
        ModBlocks.registerModBlocks();
        ModItems.registerModItems();
        ModItemGroups.registerItemGroups();
    }
}
