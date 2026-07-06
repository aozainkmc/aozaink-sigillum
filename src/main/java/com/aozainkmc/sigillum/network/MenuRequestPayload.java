package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MenuRequestPayload() implements CustomPacketPayload {
    public static final Type<MenuRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "menu_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuRequestPayload> STREAM_CODEC =
        StreamCodec.of((buffer, payload) -> {}, buffer -> new MenuRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
