package com.railsounds.mixin.client;

import com.railsounds.client.ScheduleRenders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Add our schedule entries' editor looks right after Create registers its own. */
@Mixin(targets = "com.zurrtum.create.client.AllScheduleRenders", remap = false)
public abstract class ScheduleRendersMixin {
    @Inject(method = "register()V", at = @At("RETURN"), require = 1)
    private static void railsounds$addRenders(CallbackInfo ci) {
        ScheduleRenders.register();
    }
}
