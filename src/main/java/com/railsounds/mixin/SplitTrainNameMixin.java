package com.railsounds.mixin;

import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Steam 'n' Rails names the back half of a decoupled train "Split off from: <name>".
 * Keep the original name instead. Runs before S&R sends the new train to clients,
 * so everyone sees the right name straight away.
 */
@Mixin(targets = "com.railwayteam.railways.content.coupling.TrainUtils", remap = false)
public abstract class SplitTrainNameMixin {

    @Redirect(method = "splitTrain",
        at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.PUTFIELD,
            target = "Lcom/zurrtum/create/content/trains/entity/Train;name:Lnet/minecraft/network/chat/Component;"),
        require = 0)
    private static void railsounds$keepName(Train newTrain, Component splitName, Train original, int numberOffEnd) {
        newTrain.name = original.name.copy();
    }

    /** the split-off half keeps the diesel/steam mark too */
    @org.spongepowered.asm.mixin.injection.Inject(method = "splitTrain", at = @At("RETURN"), require = 0)
    private static void railsounds$keepType(Train original, int numberOffEnd,
                                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Train> cir) {
        com.railsounds.TrainTypes.onSplit(original, cir.getReturnValue());
        com.railsounds.Engines.onSplit(original, cir.getReturnValue());
        com.railsounds.Bells.onSplit(original, cir.getReturnValue());
    }
}
