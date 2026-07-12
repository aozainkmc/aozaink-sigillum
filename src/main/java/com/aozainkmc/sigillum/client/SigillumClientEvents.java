package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID, value = Dist.CLIENT)
public final class SigillumClientEvents {

    private SigillumClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        SigillumInscriptionCamera.tick(mc);
        if (mc.player == null) return;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SigillumInscriptionCamera.reset();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        SigillumInscriptionOverlay.render(event);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (SigillumInscriptionCamera.isActive()) event.setCanceled(true);
    }
}
