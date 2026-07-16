package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
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

}
