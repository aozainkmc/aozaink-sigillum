package com.aozainkmc.sigillum;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.sigillum.advancement.SigillumAdvancementTriggers;
import com.aozainkmc.sigillum.command.SigillumCommand;
import com.aozainkmc.sigillum.glyph.GlyphSemantics;
import com.aozainkmc.sigillum.glyph.GlyphCodex;
import com.aozainkmc.sigillum.cast.SigillumInscriptionManager;
import com.aozainkmc.input.api.MoluMenuRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import com.aozainkmc.sigillum.network.SigillumNetworking;
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
        registerMoluMenuContent();
        SigillumAdvancementTriggers.register(modBus);
        modBus.addListener(SigillumNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private static void registerMoluMenuContent() {
        for (String glyph : GlyphCodex.basicGlyphs()) {
            MoluMenuRegistry.registerGlyph(MOD_ID, glyph, GlyphCodex.brief(glyph), GlyphCodex.detail(glyph));
        }
        MoluMenuRegistry.registerTab(MOD_ID, "inscriptions", "刻印", "刻印列表",
            List.of("位置", "刻印", "余势", "范围 / 效果"), player -> {
                List<List<String>> rows = new ArrayList<>();
                for (SigillumInscriptionManager.MenuInscription inscription
                        : SigillumInscriptionManager.ownedInscriptions(player.getServer(), player.getUUID(), 128)) {
                    BlockPos pos = inscription.pos();
                    String dimension = inscription.dimension().replace("minecraft:", "");
                    rows.add(List.of(
                        dimension + " (" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")",
                        inscription.name(),
                        Math.round(inscription.progress() * 100.0f) + "%",
                        "半径" + String.format(java.util.Locale.ROOT, "%.1f", inscription.radius())
                            + (inscription.strong() ? " · 强" : "")
                    ));
                }
                return rows;
            });
    }

    private void registerCommands(RegisterCommandsEvent event) {
        SigillumCommand.register(event.getDispatcher());
    }
}
