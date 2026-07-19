package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TutorialSyncPayload(
    boolean menuHintAck,
    boolean craftedBlankTalisman,
    boolean placeHintShown
) implements CustomPacketPayload {
    public static final Type<TutorialSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "tutorial_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TutorialSyncPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.menuHintAck);
                buffer.writeBoolean(payload.craftedBlankTalisman);
                buffer.writeBoolean(payload.placeHintShown);
            },
            buffer -> new TutorialSyncPayload(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
