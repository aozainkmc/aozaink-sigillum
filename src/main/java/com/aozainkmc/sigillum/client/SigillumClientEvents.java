package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID, value = Dist.CLIENT)
public final class SigillumClientEvents {

    private SigillumClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (SigillumKeyMappings.OPEN_MENU.consumeClick()) {
            if (mc.screen == null) {
                SigillumClientHooks.openMenu();
            }
        }
    }
}
