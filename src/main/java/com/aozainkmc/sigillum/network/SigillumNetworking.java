package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.tutorial.SigillumTutorialData;
import net.minecraft.server.level.ServerPlayer;
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
        registrar.playToClient(InscriptionRevealPayload.TYPE, InscriptionRevealPayload.STREAM_CODEC,
            SigillumNetworking::handleInscriptionReveal);
        registrar.playToClient(TutorialSyncPayload.TYPE, TutorialSyncPayload.STREAM_CODEC, SigillumNetworking::handleTutorialSync);
        registrar.playToClient(ShowTutorialToastPayload.TYPE, ShowTutorialToastPayload.STREAM_CODEC, SigillumNetworking::handleShowTutorialToast);
        registrar.playToServer(MenuHintAckPayload.TYPE, MenuHintAckPayload.STREAM_CODEC, SigillumNetworking::handleMenuHintAck);
        registrar.playToServer(PlaceHintAckPayload.TYPE, PlaceHintAckPayload.STREAM_CODEC, SigillumNetworking::handlePlaceHintAck);
    }

    public static void syncTutorial(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new TutorialSyncPayload(
            SigillumTutorialData.menuHintAck(player),
            SigillumTutorialData.craftedBlankTalisman(player),
            SigillumTutorialData.placeHintShown(player)
        ));
    }

    private static void handleShieldSync(ShieldSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SigillumClientPayloadHandler.handleShieldSync(payload));
    }

    private static void handleInscriptionStatus(InscriptionStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SigillumClientPayloadHandler.handleInscriptionStatus(payload));
    }

    private static void handleInscriptionReveal(InscriptionRevealPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SigillumClientPayloadHandler.handleInscriptionReveal(payload));
    }

    private static void handleTutorialSync(TutorialSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SigillumClientPayloadHandler.handleTutorialSync(payload));
    }

    private static void handleShowTutorialToast(ShowTutorialToastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SigillumClientPayloadHandler.handleShowTutorialToast(payload));
    }

    private static void handlePlaceHintAck(PlaceHintAckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SigillumTutorialData.setPlaceHintShown(player, true);
            }
        });
    }

    private static void handleMenuHintAck(MenuHintAckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SigillumTutorialData.setMenuHintAck(player, true);
            }
        });
    }

}
