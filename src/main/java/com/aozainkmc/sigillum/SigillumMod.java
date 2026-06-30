package com.aozainkmc.sigillum;

import com.aozainkmc.sigillum.command.SigillumCommand;
import com.aozainkmc.sigillum.glyph.GlyphSemantics;
import com.aozainkmc.sigillum.network.SigillumNetworking;
import com.aozainkmc.core.AozaiInkCoreApi;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(SigillumMod.MOD_ID)
public final class SigillumMod {
    public static final String MOD_ID = "aozaink_sigillum";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SigillumMod(IEventBus modBus) {
        AozaiInkCoreApi.registerGlyphs(GlyphSemantics.words());
        modBus.addListener(SigillumNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        SigillumCommand.register(event.getDispatcher());
    }
}
