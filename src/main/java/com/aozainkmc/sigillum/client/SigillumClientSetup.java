package com.aozainkmc.sigillum.client;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.GlyphDescriber;
import com.aozainkmc.sigillum.SigillumMod;
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
        event.enqueueWork(() ->
            AozaiInkCoreApi.registerService(GlyphDescriber.class, new GlyphEffectDescriber()));
    }
}
