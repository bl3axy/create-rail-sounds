package com.railsounds;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: is auto-couple on for this player (shown in the top-left train box). */
public record AutoCouplePayload(boolean on) implements CustomPacketPayload {

    public static final Type<AutoCouplePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "autocouple"));

    public static final StreamCodec<FriendlyByteBuf, AutoCouplePayload> CODEC =
        StreamCodec.ofMember((p, buf) -> buf.writeBoolean(p.on), buf -> new AutoCouplePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
