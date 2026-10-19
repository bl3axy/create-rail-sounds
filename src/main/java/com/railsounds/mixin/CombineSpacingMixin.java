package com.railsounds.mixin;

import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * When S&R couples two trains it stores the coupler's edge spacing as the gap between
 * them. Create then pulls/pushes the cars every tick until the real gap matches that
 * number, which yanks one train forward or back. Use the gap the trains actually have.
 */
@Mixin(targets = "com.railwayteam.railways.content.coupling.TrainUtils", remap = false)
public abstract class CombineSpacingMixin {

    @ModifyVariable(
        method = "combineTrains(Lcom/zurrtum/create/content/trains/entity/Train;Lcom/zurrtum/create/content/trains/entity/Train;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;I)Lcom/zurrtum/create/content/trains/entity/Train;",
        at = @At("HEAD"), argsOnly = true, require = 0)
    private static int railsounds$actualGap(int spacing, Train frontTrain, Train backTrain, Vec3 dropPos, Level level) {
        if (com.railsounds.shunt.Shunting.explicitSpacing)
            return spacing; // our own keybind/yard coupling already picked the spacing
        try {
            if (frontTrain.carriages.isEmpty() || backTrain.carriages.isEmpty())
                return spacing;
            Carriage last = frontTrain.carriages.get(frontTrain.carriages.size() - 1);
            Carriage first = backTrain.carriages.get(0);
            // same measurement Create uses when it enforces spacing: bogey centre to bogey centre
            Vec3 a = last.trailingBogey().getAnchorPosition(false);
            Vec3 b = first.leadingBogey().getAnchorPosition(false);
            double d2 = a.distanceToSqr(b);
            if (first.leadingBogey().isUpsideDown() != last.trailingBogey().isUpsideDown())
                d2 -= 4;
            int gap = (int) Math.round(Math.sqrt(Math.max(0, d2)));
            return gap >= 1 && gap <= 64 ? gap : spacing;
        } catch (RuntimeException e) {
            return spacing;
        }
    }

    /** S&R's coupler block keeps the front train's name; keep the loco's / driven train's instead */
    @org.spongepowered.asm.mixin.injection.Inject(
        method = "combineTrains(Lcom/zurrtum/create/content/trains/entity/Train;Lcom/zurrtum/create/content/trains/entity/Train;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;I)Lcom/zurrtum/create/content/trains/entity/Train;",
        at = @At("HEAD"), require = 0)
    private static void railsounds$beforeCombine(Train frontTrain, Train backTrain, Vec3 dropPos, Level level, int spacing,
                                                 org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Train> cir) {
        try {
            com.railsounds.shunt.Shunting.beforeExternalCombine(frontTrain, backTrain);
        } catch (RuntimeException ignored) {
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(
        method = "combineTrains(Lcom/zurrtum/create/content/trains/entity/Train;Lcom/zurrtum/create/content/trains/entity/Train;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;I)Lcom/zurrtum/create/content/trains/entity/Train;",
        at = @At("RETURN"), require = 0)
    private static void railsounds$afterCombine(Train frontTrain, Train backTrain, Vec3 dropPos, Level level, int spacing,
                                                org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Train> cir) {
        try {
            com.railsounds.shunt.Shunting.afterExternalCombine(cir.getReturnValue());
        } catch (RuntimeException ignored) {
        }
    }
}
