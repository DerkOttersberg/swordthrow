package com.derko.swordthrow.network;

import com.derko.swordthrow.SwordThrowMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ThrowSwordPayload(int chargeTicks) implements CustomPacketPayload {
    public static final Type<ThrowSwordPayload> TYPE = new Type<>(SwordThrowMod.id("throw_sword"));
    public static final StreamCodec<FriendlyByteBuf, ThrowSwordPayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> buf.writeVarInt(value.chargeTicks),
        buf -> new ThrowSwordPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
