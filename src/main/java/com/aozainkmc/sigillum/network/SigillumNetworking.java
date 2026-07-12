package com.aozainkmc.sigillum.network;

import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.client.SigillumShieldHud;
import com.aozainkmc.sigillum.client.SigillumInscriptionOverlay;
import com.aozainkmc.sigillum.client.SigillumInscriptionCamera;
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
        registrar.playToClient(InscriptionRevealPayload.TYPE, InscriptionRevealPayload.STREAM_CODEC,
            SigillumNetworking::handleInscriptionReveal);
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

    private static void handleInscriptionReveal(InscriptionRevealPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        Minecraft.getInstance().execute(() -> {
            SigillumInscriptionOverlay.beginReveal(payload);
            SigillumInscriptionCamera.begin(payload);
        });
    }

}
