package com.railsounds.schedule;

import com.railsounds.shunt.YardRules;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.resources.Identifier;

/** Schedule condition: couple to the cars this train is touching (either end), then carry on. */
public class CoupleCondition extends ShuntCondition {
    public CoupleCondition(Identifier id) {
        super(id);
    }

    @Override
    protected String doMove(Train train) {
        return YardRules.coupleAtStop(train) ? "Coupled" : "Nothing to couple to";
    }

    @Override
    protected String verb() {
        return "Coupling";
    }
}
