package com.derko.swordthrow.network;

import com.derko.swordthrow.SwordThrowMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record ThrowSwordPayload(int chargeTicks) implements CustomPayload {
    public static final Id<ThrowSwordPayload> ID = new Id<>(SwordThrowMod.id("throw_sword"));
    public static final PacketCodec<PacketByteBuf, ThrowSwordPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeVarInt(value.chargeTicks),
        buf -> new ThrowSwordPayload(buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
