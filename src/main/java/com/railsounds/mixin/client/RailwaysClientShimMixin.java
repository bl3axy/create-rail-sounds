package com.railsounds.mixin.client;

import com.zurrtum.create.content.trains.GlobalRailwayManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Steam 'n' Rails (Fly port) looks trains up on the client through its
 * CreateClient.RAILWAYS() shim, but that shim returns the common/server-side
 * manager (com.zurrtum.create.Create.RAILWAYS). Create Fly keeps the client's
 * trains in a separate manager (com.zurrtum.create.client.Create.RAILWAYS).
 * On a dedicated server the client copy never gets found, so decoupling never
 * removes the cars from the old train on screen (the coupling stretches out)
 * and coupling never attaches them. Point the shim at the real client manager.
 */
@Mixin(targets = "com.railwayteam.railways.shim.create.client.CreateClient", remap = false)
public abstract class RailwaysClientShimMixin {

    @Inject(method = "RAILWAYS", at = @At("HEAD"), cancellable = true, require = 0)
    private static void railsounds$clientManager(CallbackInfoReturnable<GlobalRailwayManager> cir) {
        GlobalRailwayManager client = com.zurrtum.create.client.Create.RAILWAYS;
        if (client != null)
            cir.setReturnValue(client);
    }
}
