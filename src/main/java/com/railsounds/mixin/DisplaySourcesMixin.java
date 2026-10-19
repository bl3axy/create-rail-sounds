package com.railsounds.mixin;

import com.railsounds.display.DisplaySources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Register our display sources right after Create registers its own, so Create is ready for them. */
@Mixin(targets = "com.zurrtum.create.AllDisplaySources", remap = false)
public abstract class DisplaySourcesMixin {
    @Inject(method = "register()V", at = @At("RETURN"))
    private static void railsounds$addSources(CallbackInfo ci) {
        DisplaySources.register();
    }
}
