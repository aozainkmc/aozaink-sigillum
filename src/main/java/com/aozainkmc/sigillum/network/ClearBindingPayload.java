package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClearBindingPayload(int slot) implements CustomPacketPayload {
    public static final Type<ClearBindingPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "clear_binding"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearBindingPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.slot),
            buffer -> new ClearBindingPayload(buffer.readVarInt())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
