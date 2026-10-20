package com.railsounds.schedule;

import com.railsounds.shunt.Shunting;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.graph.DiscoveredPath;
import com.zurrtum.create.content.trains.schedule.ScheduleRuntime;
import com.zurrtum.create.content.trains.schedule.destination.DestinationInstruction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * "Back into station": same as Travel to Station, but the train drives there in reverse,
 * even a single-cab loco. Create can't turn round mid-trip, so it reverses from where it is,
 * which means the stop before this one should be the lead / wye just past the switch.
 */
public class BackInInstruction extends DestinationInstruction {
    public BackInInstruction(Identifier id) {
        super(id);
    }

    @Override
    public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        Train train = runtime.train;
        Shunting.BACK_DOCK.add(train.id);
        Shunting.MUST_BACK.add(train.id);
        DiscoveredPath path;
        try {
            path = super.start(runtime, level);
        } finally {
            Shunting.MUST_BACK.remove(train.id);
        }
        if (path == null) Shunting.BACK_DOCK.remove(train.id);
        return path;
    }
}
