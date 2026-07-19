package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MenuHintAckPayload() implements CustomPacketPayload {
    public static final Type<MenuHintAckPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "menu_hint_ack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuHintAckPayload> STREAM_CODEC =
        StreamCodec.unit(new MenuHintAckPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
