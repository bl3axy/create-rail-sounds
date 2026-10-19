package com.imjustseiyin.item;

import com.imjustseiyin.util.BlueprintType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class BlueprintItem extends Item {
    private final BlueprintType type;

    public BlueprintItem(Properties properties, BlueprintType type) {
        super(properties);
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(type == BlueprintType.GARDEN ? "tooltip.justoutdoorstuffs.garden" : "tooltip.justoutdoorstuffs.patio"));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
