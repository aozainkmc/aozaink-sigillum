package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShowTutorialToastPayload(int hint) implements CustomPacketPayload {
    public static final int OPEN_MENU = 0;
    public static final int PLACE_TALISMAN = 1;

    public static final Type<ShowTutorialToastPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "show_tutorial_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowTutorialToastPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> buffer.writeByte(payload.hint),
            buffer -> new ShowTutorialToastPayload(buffer.readByte())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
