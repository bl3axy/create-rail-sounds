package com.railsounds.schedule;

import com.railsounds.RailSounds;
import com.zurrtum.create.AllSchedules;
import com.zurrtum.create.catnip.data.Pair;
import net.minecraft.resources.Identifier;

/** Our schedule entries, added to Create's lists so they show up in the schedule editor. */
public final class ScheduleTypes {
    private ScheduleTypes() {}

    public static final Identifier COUPLE = Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "couple");
    public static final Identifier UNCOUPLE = Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "uncouple");
    public static final Identifier BACK_IN = Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "back_in");

    public static void register() {
        AllSchedules.CONDITION_TYPES.add(Pair.of(COUPLE, CoupleCondition::new));
        AllSchedules.CONDITION_TYPES.add(Pair.of(UNCOUPLE, UncoupleCondition::new));
        // right after Travel to Station in the instruction list
        int at = 1;
        for (int i = 0; i < AllSchedules.INSTRUCTION_TYPES.size(); i++)
            if (AllSchedules.INSTRUCTION_TYPES.get(i).getFirst().equals(AllSchedules.DESTINATION)) at = i + 1;
        AllSchedules.INSTRUCTION_TYPES.add(Math.min(at, AllSchedules.INSTRUCTION_TYPES.size()), Pair.of(BACK_IN, BackInInstruction::new));
    }
}
