package com.imjustseiyin.item;

import com.imjustseiyin.JustOutdoorStuffsMod;
import com.imjustseiyin.util.BlueprintType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ModItems {
    private static Item blueprint(String name, BlueprintType type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(JustOutdoorStuffsMod.MOD_ID, name));
        return Registry.register(BuiltInRegistries.ITEM, key, new BlueprintItem(new Item.Properties().setId(key), type));
    }

    public static final Item BLUEPRINT_GARDEN_STUFFS = blueprint("blueprint_garden_stuffs", BlueprintType.GARDEN);
    public static final Item BLUEPRINT_PATIO_STUFFS = blueprint("blueprint_patio_stuffs", BlueprintType.PATIO);

    public static void registerModItems() {
    }
}
