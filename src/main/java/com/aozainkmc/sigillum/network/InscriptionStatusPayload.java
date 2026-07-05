package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InscriptionStatusPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<InscriptionStatusPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "inscription_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InscriptionStatusPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entries.size());
                for (Entry entry : payload.entries) {
                    buffer.writeLong(entry.posLong());
                    buffer.writeFloat(entry.progress());
                    buffer.writeFloat(entry.radius());
                    buffer.writeVarInt(entry.style());
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new Entry(buffer.readLong(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt()));
                }
                return new InscriptionStatusPayload(entries);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(long posLong, float progress, float radius, int style) {
        public static final int STYLE_WARD = 0;
        public static final int STYLE_HOSTILE = 1;
        public static final int STYLE_SELF = 2;

        public boolean ward() {
            return style == STYLE_WARD;
        }
    }
}
