package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.binding.GlyphBinding;
import com.aozainkmc.sigillum.cast.SigillumInscriptionManager;
import com.aozainkmc.sigillum.client.SigillumBindingOverlay;
import com.aozainkmc.sigillum.client.SigillumMenuScreen;
import com.aozainkmc.sigillum.client.SigillumShieldHud;
import com.aozainkmc.sigillum.client.SigillumInscriptionOverlay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SigillumNetworking {
    private SigillumNetworking() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ShieldSyncPayload.TYPE, ShieldSyncPayload.STREAM_CODEC, SigillumNetworking::handleShieldSync);
        registrar.playToClient(InscriptionStatusPayload.TYPE, InscriptionStatusPayload.STREAM_CODEC, SigillumNetworking::handleInscriptionStatus);
        registrar.playToClient(OpenMenuPayload.TYPE, OpenMenuPayload.STREAM_CODEC, SigillumNetworking::handleOpenMenu);
        registrar.playToClient(BindingRitualPayload.TYPE, BindingRitualPayload.STREAM_CODEC, SigillumNetworking::handleBindingRitual);
        registrar.playToServer(MenuRequestPayload.TYPE, MenuRequestPayload.STREAM_CODEC, SigillumNetworking::handleMenuRequest);
        registrar.playToServer(ClearBindingPayload.TYPE, ClearBindingPayload.STREAM_CODEC, SigillumNetworking::handleClearBinding);
    }

    private static void handleShieldSync(ShieldSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SigillumShieldHud.update(payload.amount(), payload.max());
        }
    }

    private static void handleInscriptionStatus(InscriptionStatusPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SigillumInscriptionOverlay.update(payload.entries());
        }
    }

    private static void handleOpenMenu(OpenMenuPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        Map<Integer, String> snapshot = new LinkedHashMap<>();
        for (OpenMenuPayload.Entry entry : payload.entries()) {
            snapshot.put(entry.slot(), entry.glyph());
        }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new SigillumMenuScreen(snapshot, payload.inscriptions())));
    }

    private static void handleBindingRitual(BindingRitualPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        Vec3 center = new Vec3(payload.x(), payload.y(), payload.z());
        Minecraft.getInstance().execute(() -> SigillumBindingOverlay.add(center));
    }

    private static void handleMenuRequest(MenuRequestPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        sendMenu(serverPlayer);
    }

    private static void handleClearBinding(ClearBindingPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        String chinese = GlyphBinding.toChineseDigit(payload.slot());
        if (!chinese.isEmpty()) {
            GlyphBinding.bind(serverPlayer, chinese, "");
        }
    }

    public static void sendMenu(ServerPlayer player) {
        List<OpenMenuPayload.Entry> entries = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            final int slot = i;
            GlyphBinding.getBoundGlyph(player, GlyphBinding.toChineseDigit(slot))
                .ifPresent(glyph -> entries.add(new OpenMenuPayload.Entry(slot, glyph)));
        }
        List<OpenMenuPayload.InscriptionEntry> inscriptions = new ArrayList<>();
        for (SigillumInscriptionManager.MenuInscription inscription
                : SigillumInscriptionManager.ownedInscriptions(player.getServer(), player.getUUID(), 128)) {
            inscriptions.add(new OpenMenuPayload.InscriptionEntry(
                inscription.dimension(),
                inscription.pos().asLong(),
                inscription.name(),
                inscription.progress(),
                (float) inscription.radius(),
                inscription.strong()
            ));
        }
        PacketDistributor.sendToPlayer(player, new OpenMenuPayload(entries, inscriptions));
    }
}
