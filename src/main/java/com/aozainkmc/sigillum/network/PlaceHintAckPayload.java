package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlaceHintAckPayload() implements CustomPacketPayload {
    public static final Type<PlaceHintAckPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "place_hint_ack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceHintAckPayload> STREAM_CODEC =
        StreamCodec.unit(new PlaceHintAckPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
