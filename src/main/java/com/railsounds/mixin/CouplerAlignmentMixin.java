package com.railsounds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Steam 'n' Rails only counts a bogey as "on" a coupler marker when it is within
 * 0.8 blocks of it, which is near impossible to hit when driving by hand.
 * Widen it to couplerAlignmentTolerance from config/railsounds.json (default 3).
 */
@Mixin(targets = "com.railwayteam.railways.content.coupling.coupler.TrackCouplerBlockEntity", remap = false)
public abstract class CouplerAlignmentMixin {

    @ModifyConstant(method = "isCarriageWheelOnPoint", constant = @Constant(doubleValue = 0.6400000000000001), require = 0)
    private double railsounds$looserAlignment(double original) {
        double t = com.railsounds.RailConfig.V.couplerAlignmentTolerance;
        return t > 0 ? t * t : original;
    }
}
