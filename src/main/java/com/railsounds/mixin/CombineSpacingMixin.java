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
        try {
            if (frontTrain.carriages.isEmpty() || backTrain.carriages.isEmpty())
                return spacing;
            Carriage last = frontTrain.carriages.get(frontTrain.carriages.size() - 1);
            Carriage first = backTrain.carriages.get(0);
            Vec3 a = last.getTrailingPoint().getPosition(frontTrain.graph);
            Vec3 b = first.getLeadingPoint().getPosition(backTrain.graph);
            int gap = (int) Math.round(a.distanceTo(b));
            return gap >= 1 && gap <= 64 ? gap : spacing;
        } catch (RuntimeException e) {
            return spacing;
        }
    }
}
