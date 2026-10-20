package com.railsounds.client;

import com.railsounds.schedule.BackInInstruction;
import com.railsounds.schedule.CoupleCondition;
import com.railsounds.schedule.ScheduleTypes;
import com.railsounds.schedule.UncoupleCondition;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.catnip.data.Pair;
import com.zurrtum.create.client.AllScheduleRenders;
import com.zurrtum.create.client.content.trains.schedule.IScheduleInput;
import com.zurrtum.create.client.content.trains.schedule.destination.DestinationInstructionRender;
import com.zurrtum.create.client.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** How our schedule entries look and are edited in Create's schedule screen. */
public final class ScheduleRenders {
    private ScheduleRenders() {}

    public static void register() {
        AllScheduleRenders.ALL.put(ScheduleTypes.COUPLE, new IScheduleInput<CoupleCondition>() {
            @Override
            public Pair<ItemStack, Component> getSummary(CoupleCondition input) {
                return Pair.of(new ItemStack(Items.LEAD), Component.literal("Couple up"));
            }

            @Override
            public List<Component> getTitleAs(CoupleCondition input, String type) {
                return List.of(Component.literal("Couple to the cars this train is touching").withStyle(ChatFormatting.GOLD),
                    Component.literal("either end, within 1.5 blocks").withStyle(ChatFormatting.GRAY));
            }
        });

        AllScheduleRenders.ALL.put(ScheduleTypes.UNCOUPLE, new IScheduleInput<UncoupleCondition>() {
            @Override
            public Pair<ItemStack, Component> getSummary(UncoupleCondition input) {
                int n = input.getCount();
                return Pair.of(new ItemStack(Items.SHEARS), Component.literal(n == 0 ? "Drop all cars" : "Drop " + n + (n == 1 ? " car" : " cars")));
            }

            @Override
            public List<Component> getTitleAs(UncoupleCondition input, String type) {
                int n = input.getCount();
                return List.of(Component.literal(n == 0 ? "Uncouple all the cars" : "Uncouple " + n + (n == 1 ? " car" : " cars")).withStyle(ChatFormatting.GOLD),
                    Component.literal("off the end away from the loco").withStyle(ChatFormatting.GRAY));
            }

            @Override
            public void initConfigurationWidgets(UncoupleCondition input, ModularGuiLineBuilder builder) {
                builder.addScrollInput(0, 60, (i, l) -> i.titled(Component.literal("Cars to drop"))
                    .withRange(0, 33).withShiftStep(5)
                    .format(v -> Component.literal(v == 0 ? "All" : v + (v == 1 ? " car" : " cars"))), "Count");
            }
        });

        AllScheduleRenders.ALL.put(ScheduleTypes.BACK_IN, new DestinationInstructionRender() {
            @Override
            public List<Component> getTitleAs(com.zurrtum.create.content.trains.schedule.destination.DestinationInstruction input, String type) {
                return List.of(Component.literal("Back into station").withStyle(ChatFormatting.GOLD),
                    Component.literal("\"" + input.getLabelText() + "\"").withStyle(ChatFormatting.GRAY));
            }

            @Override
            public Pair<ItemStack, Component> getSummary(com.zurrtum.create.content.trains.schedule.destination.DestinationInstruction input) {
                return Pair.of(AllItems.TRACK_STATION.getDefaultInstance(), Component.literal("\u21A9 " + input.getLabelText()));
            }
        });
    }

    @SuppressWarnings("unused")
    private static final Class<?> KEEP = BackInInstruction.class;
}
