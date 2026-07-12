package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InscriptionRevealPayload(BlockPos pos, UUID ownerId, float startRadius, float radius, boolean ward) implements CustomPacketPayload {
    public static final Type<InscriptionRevealPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "inscription_reveal")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, InscriptionRevealPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeBlockPos(payload.pos);
            buffer.writeUUID(payload.ownerId);
            buffer.writeFloat(payload.startRadius);
            buffer.writeFloat(payload.radius);
            buffer.writeBoolean(payload.ward);
        },
        buffer -> new InscriptionRevealPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readFloat(),
            buffer.readFloat(), buffer.readBoolean())
    );

    public static int durationTicks(float radius) {
        return Math.max(14, Math.min(26, Math.round(10.0F + radius * 1.3F)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
