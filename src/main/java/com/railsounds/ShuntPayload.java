package com.railsounds;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: a shunting key was pressed (see Shunting.COUPLE etc). */
public record ShuntPayload(int action, int number) implements CustomPacketPayload {

    public static final Type<ShuntPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "shunt"));

    public static final StreamCodec<FriendlyByteBuf, ShuntPayload> CODEC =
        StreamCodec.ofMember(ShuntPayload::write, ShuntPayload::read);

    private static ShuntPayload read(FriendlyByteBuf buf) {
        return new ShuntPayload(buf.readVarInt(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(number);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
