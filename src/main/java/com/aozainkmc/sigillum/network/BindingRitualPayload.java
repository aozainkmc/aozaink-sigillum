package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BindingRitualPayload(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<BindingRitualPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "binding_ritual"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BindingRitualPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeDouble(payload.x);
                buffer.writeDouble(payload.y);
                buffer.writeDouble(payload.z);
            },
            buffer -> new BindingRitualPayload(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
