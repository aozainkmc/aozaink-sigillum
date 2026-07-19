package com.aozainkmc.sigillum.client.tutorial;

import com.aozainkmc.input.client.AozaiInkClientConfig;
import com.aozainkmc.input.client.MoluMenuClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.network.chat.Component;

public final class SigillumTutorialHints {
    private static final int TICKS_PER_SECOND = 20;
    private static final int HINT_DURATION_SECONDS = 10;

    private static TutorialToast openMenuToast;
    private static TutorialToast placeTalismanToast;

    private SigillumTutorialHints() {}

    public static void showOpenMenuHint() {
        if (!canShow() || openMenuToast != null) return;
        Minecraft minecraft = Minecraft.getInstance();
        openMenuToast = new TutorialToast(
            TutorialToast.Icons.RECIPE_BOOK,
            Component.translatable("tutorial.aozaink_sigillum.open_menu.title"),
            Component.translatable(
                "tutorial.aozaink_sigillum.open_menu.description",
                MoluMenuClient.OPEN_MENU.getTranslatedKeyMessage()
            ),
            false
        );
        minecraft.getToasts().addToast(openMenuToast);
    }

    public static void showPlaceTalismanHint() {
        if (!canShow() || placeTalismanToast != null) return;
        Minecraft minecraft = Minecraft.getInstance();
        placeTalismanToast = new TutorialToast(
            TutorialToast.Icons.RIGHT_CLICK,
            Component.translatable("tutorial.aozaink_sigillum.place_talisman.title"),
            Component.translatable(
                "tutorial.aozaink_sigillum.place_talisman.description",
                minecraft.options.keyShift.getTranslatedKeyMessage(),
                minecraft.options.keyUse.getTranslatedKeyMessage()
            ),
            false
        );
        minecraft.getToasts().addToast(placeTalismanToast);
    }

    public static void completeOpenMenuHint() {
        if (openMenuToast == null) return;
        openMenuToast.hide();
        openMenuToast = null;
    }

    public static void completePlaceTalismanHint() {
        if (placeTalismanToast == null) return;
        placeTalismanToast.hide();
        placeTalismanToast = null;
    }

    public static void clearHints() {
        if (openMenuToast != null) {
            openMenuToast.hide();
            openMenuToast = null;
        }
        if (placeTalismanToast != null) {
            placeTalismanToast.hide();
            placeTalismanToast = null;
        }
    }

    private static boolean canShow() {
        Minecraft minecraft = Minecraft.getInstance();
        return AozaiInkClientConfig.hintsEnabled() && minecraft.player != null;
    }
}
