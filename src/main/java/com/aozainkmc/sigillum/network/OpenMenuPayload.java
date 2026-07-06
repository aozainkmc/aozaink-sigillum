package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMenuPayload(List<Entry> entries, List<InscriptionEntry> inscriptions) implements CustomPacketPayload {
    public static final Type<OpenMenuPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "open_menu"));

    public OpenMenuPayload(List<Entry> entries) {
        this(entries, List.of());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entries.size());
                for (Entry entry : payload.entries) {
                    buffer.writeVarInt(entry.slot());
                    buffer.writeUtf(entry.glyph());
                }
                buffer.writeVarInt(payload.inscriptions.size());
                for (InscriptionEntry entry : payload.inscriptions) {
                    buffer.writeUtf(entry.dimension());
                    buffer.writeLong(entry.pos());
                    buffer.writeUtf(entry.name());
                    buffer.writeFloat(entry.progress());
                    buffer.writeFloat(entry.radius());
                    buffer.writeBoolean(entry.strong());
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new Entry(buffer.readVarInt(), buffer.readUtf()));
                }
                int inscriptionSize = buffer.readVarInt();
                List<InscriptionEntry> inscriptions = new ArrayList<>(inscriptionSize);
                for (int i = 0; i < inscriptionSize; i++) {
                    inscriptions.add(new InscriptionEntry(
                        buffer.readUtf(),
                        buffer.readLong(),
                        buffer.readUtf(),
                        buffer.readFloat(),
                        buffer.readFloat(),
                        buffer.readBoolean()
                    ));
                }
                return new OpenMenuPayload(entries, inscriptions);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(int slot, String glyph) {}

    public record InscriptionEntry(String dimension, long pos, String name, float progress, float radius, boolean strong) {}
}
