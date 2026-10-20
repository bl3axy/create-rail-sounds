package com.railsounds.schedule;

import com.railsounds.shunt.Shunting;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.schedule.condition.ScheduleWaitCondition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * Base for "do a shunting move, then carry on" schedule conditions.
 * The move can't happen inside Create's train loop (coupling removes a train from the list
 * Create is walking), so it's queued for the end of the tick; the condition is met once it ran.
 */
public abstract class ShuntCondition extends ScheduleWaitCondition {
    protected ShuntCondition(Identifier id) {
        super(id);
    }

    /** do the move now (end of tick); return a short result for the status line */
    protected abstract String doMove(Train train);

    protected abstract String verb();

    @Override
    public boolean tickCompletion(Level level, Train train, CompoundTag context) {
        if (context.getBooleanOr("RSDone", false)) return true;
        long now = level.getGameTime();
        long queuedAt = context.getLongOr("RSQueuedAt", -1L);
        // queue it once; if a restart lost the queued job, queue it again after a moment
        if (queuedAt < 0 || now - queuedAt > 60) {
            context.putLong("RSQueuedAt", now);
            Shunting.queueAction(() -> {
                String result;
                try {
                    // the schedule may have moved to another train object (coupling), so use the
                    // train that owns this runtime now
                    result = doMove(train.runtime != null && train.runtime.train != null ? train.runtime.train : train);
                } catch (RuntimeException e) {
                    result = "failed";
                }
                context.putString("RSResult", result == null ? "" : result);
                context.putBoolean("RSDone", true);
                requestStatusToUpdate(context);
            });
        }
        return false;
    }

    @Override
    public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag context) {
        String r = context.getStringOr("RSResult", "");
        return Component.literal(r.isEmpty() ? verb() + "..." : r);
    }
}
