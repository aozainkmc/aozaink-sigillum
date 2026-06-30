package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.client.SigillumShieldHud;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SigillumNetworking {
    private SigillumNetworking() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ShieldSyncPayload.TYPE, ShieldSyncPayload.STREAM_CODEC, SigillumNetworking::handleShieldSync);
    }

    private static void handleShieldSync(ShieldSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SigillumShieldHud.update(payload.amount(), payload.max());
        }
    }
}
