package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.binding.GlyphBinding;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SigillumClientHooks {

    private SigillumClientHooks() {}

    public static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null) return;
        if (server == null) {
            mc.player.displayClientMessage(Component.literal("[符咒] 当前版本菜单仅单人可用"), false);
            return;
        }
        UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            Map<Integer, String> snapshot = readBindings(sp);
            mc.execute(() -> mc.setScreen(new SigillumMenuScreen(snapshot)));
        });
    }

    public static void clearBinding(int digit) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) {
                GlyphBinding.bind(sp, GlyphBinding.toChineseDigit(digit), "");
            }
        });
    }

    private static Map<Integer, String> readBindings(ServerPlayer sp) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (sp == null) return out;
        for (int i = 1; i <= 9; i++) {
            final int slot = i;
            GlyphBinding.getBoundGlyph(sp, GlyphBinding.toChineseDigit(slot))
                .ifPresent(glyph -> out.put(slot, glyph));
        }
        return out;
    }
}
