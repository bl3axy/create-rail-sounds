package com.railsounds;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: start or release a held horn/whistle on a carriage entity.
 * Only sent to players who have this mod installed; everyone else gets the old
 * plain sound packets. Having the client run the horn means letting go can fade
 * the sound out from exactly where it was, instead of cutting it and playing a tail.
 */
public record HornPayload(int entityId, String sound, float volume, boolean start, int fadeTicks)
    implements CustomPacketPayload {

    public static final Type<HornPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "horn"));

    public static final StreamCodec<FriendlyByteBuf, HornPayload> CODEC =
        StreamCodec.ofMember(HornPayload::write, HornPayload::read);

    private static HornPayload read(FriendlyByteBuf buf) {
        return new HornPayload(buf.readVarInt(), buf.readUtf(64), buf.readFloat(), buf.readBoolean(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(sound, 64);
        buf.writeFloat(volume);
        buf.writeBoolean(start);
        buf.writeVarInt(fadeTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
