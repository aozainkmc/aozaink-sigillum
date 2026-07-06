package com.aozainkmc.sigillum.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;

public final class SigillumTexts {
    public static final int GOLD = 0xE8B448;
    public static final int CREAM = 0xFFF2BD;
    public static final int CINNABAR = 0xB43C2B;
    public static final int HOT = 0xE2CFAB;

    private SigillumTexts() {}

    public static void actionbar(Player player, String text, int color) {
        player.displayClientMessage(colored(text, color), true);
    }

    public static void actionbar(Player player, Component component) {
        player.displayClientMessage(component, true);
    }

    public static Component colored(String text, int color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }

    public static Component chat(String text, int color) {
        return colored(text, color);
    }
}
