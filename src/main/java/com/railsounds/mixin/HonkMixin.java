package com.railsounds.mixin;

import com.railsounds.Horns;
import com.railsounds.TrainTypes;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.infrastructure.packet.c2s.HonkPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trains marked diesel/steam get our horn instead of Create's whistle. We skip
 * Create's broadcast for them, so clients never start Create's looping whistle.
 * The packet can arrive off the server thread, so the horn work is handed to it.
 */
@Mixin(targets = "com.zurrtum.create.AllHandle", remap = false)
public abstract class HonkMixin {

    @Inject(method = "onTrainHonk", at = @At("HEAD"), cancellable = true)
    private static void railsounds$customHorn(ServerGamePacketListenerImpl listener, HonkPacket packet, CallbackInfo ci) {
        ServerPlayer player = listener.player;
        Train train = com.zurrtum.create.Create.RAILWAYS.sided(player.level()).trains.get(packet.trainId());
        if (train == null || TrainTypes.get(train) == null) return;
        ci.cancel();
        MinecraftServer server = player.level().getServer();
        boolean pressed = packet.isHonk();
        server.execute(() -> Horns.onHonk(server, train, pressed));
    }
}
