package com.railsounds.schedule;

import com.railsounds.shunt.YardRules;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.resources.Identifier;

/** Schedule condition: drop cars off the end away from the loco (Count 0 = all of them), then carry on. */
public class UncoupleCondition extends ShuntCondition {
    public UncoupleCondition(Identifier id) {
        super(id);
    }

    public int getCount() {
        return Math.max(0, intData("Count"));
    }

    @Override
    protected String doMove(Train train) {
        int n = getCount();
        return YardRules.uncoupleAtStop(train, n) ? (n == 0 ? "Dropped the cars" : "Dropped " + n + (n == 1 ? " car" : " cars"))
            : "Nothing to drop";
    }

    @Override
    protected String verb() {
        return "Uncoupling";
    }
}
