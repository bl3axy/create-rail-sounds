package com.railsounds.mixin;

import com.railsounds.shunt.Shunting;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Buffers: right before Create works out how far the train moves this tick, stop it if its
 * leading end is touching another train (slowly). Also lets uncoupled cars coast.
 */
@Mixin(value = Train.class, remap = false)
public abstract class TrainShuntMixin {

    @Inject(method = "tick(Lnet/minecraft/world/level/Level;)V",
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/entity/Train;tickPassiveSlowdown()V"))
    private void railsounds$buffers(Level level, CallbackInfo ci) {
        try {
            Shunting.preMove((Train) (Object) this, level);
        } catch (RuntimeException ignored) {
            // never break Create's train tick over this
        }
    }

    @Inject(method = "tickPassiveSlowdown", at = @At("HEAD"), cancellable = true)
    private void railsounds$coast(CallbackInfo ci) {
        try {
            if (Shunting.coast((Train) (Object) this)) ci.cancel();
        } catch (RuntimeException ignored) {
        }
    }
}
