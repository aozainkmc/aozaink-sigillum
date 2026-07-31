package com.aozainkmc.sigillum.client;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.client.MoluMenuOpenHook;
import com.aozainkmc.core.api.client.TalismanPlacedHook;
import com.aozainkmc.input.api.GlyphPreviewRegistry;
import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.client.tutorial.SigillumTutorialClient;
import com.aozainkmc.sigillum.glyph.GlyphEffectDescriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SigillumClientSetup {
    private SigillumClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            GlyphPreviewRegistry.register(SigillumMod.MOD_ID, new GlyphEffectDescriber());
            AozaiInkCoreApi.registerService(MoluMenuOpenHook.class, () -> SigillumTutorialClient.onMenuOpened());
            AozaiInkCoreApi.registerService(TalismanPlacedHook.class, () -> SigillumTutorialClient.onTalismanPlaced());
        });
    }
}
