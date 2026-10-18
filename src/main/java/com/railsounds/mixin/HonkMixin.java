package com.railsounds.mixin;

import com.railsounds.Horns;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.infrastructure.packet.c2s.HonkPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trains marked diesel/steam get our horn instead of Create's whistle. We skip
 * Create's broadcast for them, so clients never start Create's looping whistle.
 * Runs after Create has moved the packet onto the server thread.
 */
@Mixin(targets = "com.zurrtum.create.AllHandle", remap = false)
public abstract class HonkMixin {

    @Inject(method = "onTrainHonk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread", shift = At.Shift.AFTER),
        cancellable = true, require = 0)
    private static void railsounds$customHorn(ServerGamePacketListenerImpl listener, HonkPacket packet, CallbackInfo ci) {
        ServerPlayer player = listener.player;
        Train train = com.zurrtum.create.Create.RAILWAYS.sided(player.level()).trains.get(packet.trainId());
        if (train == null) return;
        if (Horns.onHonk(player.level().getServer(), train, packet.isHonk()))
            ci.cancel();
    }
}
