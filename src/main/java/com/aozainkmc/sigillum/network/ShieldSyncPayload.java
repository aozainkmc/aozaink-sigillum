package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShieldSyncPayload(float amount, float max) implements CustomPacketPayload {
    public static final Type<ShieldSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "shield_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldSyncPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeFloat(payload.amount);
                buffer.writeFloat(payload.max);
            },
            buffer -> new ShieldSyncPayload(buffer.readFloat(), buffer.readFloat())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
