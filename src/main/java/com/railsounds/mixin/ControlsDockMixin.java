package com.railsounds.mixin;

import com.railsounds.RailConfig;
import com.railsounds.shunt.Shunting;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Navigation;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.station.GlobalStation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Create only looks for (and lets you Space-dock at) stations behind you if the train has
 * controls at both ends. With a single-cab loco reversing, look behind too, so you can back
 * into a platform, couple up and drive off.
 */
@Mixin(value = CarriageContraptionEntity.class, remap = false)
public abstract class ControlsDockMixin {

    @Redirect(method = "control",
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/entity/Navigation;findNearestApproachable(Z)Lcom/zurrtum/create/content/trains/station/GlobalStation;"))
    private GlobalStation railsounds$lookBehind(Navigation nav, boolean forward) {
        Train train = nav.train;
        if (RailConfig.V.shuntingEnabled && RailConfig.V.reverseIntoStations && train != null && !train.doubleEnded) {
            if (train.speed < -1e-3) {
                Shunting.BACK_DOCK.add(train.id);
                return nav.findNearestApproachable(false);
            }
        }
        return nav.findNearestApproachable(forward);
    }
}
