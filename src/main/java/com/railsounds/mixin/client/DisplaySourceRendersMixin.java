package com.railsounds.mixin.client;

import com.railsounds.client.DisplaySourceScreens;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Settings rows for our display sources, added right after Create sets up its own. */
@Mixin(targets = "com.zurrtum.create.client.AllDisplaySourceRenders", remap = false)
public abstract class DisplaySourceRendersMixin {
    @Inject(method = "register()V", at = @At("RETURN"), require = 1)
    private static void railsounds$addScreens(CallbackInfo ci) {
        DisplaySourceScreens.register();
    }
}
