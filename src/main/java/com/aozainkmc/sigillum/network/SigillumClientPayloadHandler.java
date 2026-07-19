package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.client.SigillumInscriptionCamera;
import com.aozainkmc.sigillum.client.SigillumInscriptionOverlay;
import com.aozainkmc.sigillum.client.SigillumShieldHud;
import com.aozainkmc.sigillum.client.tutorial.SigillumTutorialClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class SigillumClientPayloadHandler {
    private SigillumClientPayloadHandler() {}

    static void handleShieldSync(ShieldSyncPayload payload) {
        SigillumShieldHud.update(payload.amount(), payload.max());
    }

    static void handleInscriptionStatus(InscriptionStatusPayload payload) {
        SigillumInscriptionOverlay.update(payload.entries());
    }

    static void handleInscriptionReveal(InscriptionRevealPayload payload) {
        SigillumInscriptionOverlay.beginReveal(payload);
        SigillumInscriptionCamera.begin(payload);
    }

    static void handleTutorialSync(TutorialSyncPayload payload) {
        SigillumTutorialClient.onSync(payload);
    }

    static void handleShowTutorialToast(ShowTutorialToastPayload payload) {
        SigillumTutorialClient.onShowToast(payload);
    }
}
