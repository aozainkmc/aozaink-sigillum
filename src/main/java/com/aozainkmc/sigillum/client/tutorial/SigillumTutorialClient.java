package com.aozainkmc.sigillum.client.tutorial;

import com.aozainkmc.input.client.AozaiInkClientConfig;
import com.aozainkmc.sigillum.network.MenuHintAckPayload;
import com.aozainkmc.sigillum.network.PlaceHintAckPayload;
import com.aozainkmc.sigillum.network.ShowTutorialToastPayload;
import com.aozainkmc.sigillum.network.TutorialSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SigillumTutorialClient {
    private static final int LOGIN_DELAY_TICKS = 60;

    private static boolean syncReceived;
    private static boolean menuHintAck;
    private static boolean craftedBlankTalisman;
    private static boolean placeHintShown;
    private static int loginTicks;
    private static boolean openMenuHintShownThisSession;

    private static boolean lastHintsEnabled = true;

    private SigillumTutorialClient() {}

    public static void onSync(TutorialSyncPayload payload) {
        if (!menuHintAck) {
            menuHintAck = payload.menuHintAck();
        }
        craftedBlankTalisman = payload.craftedBlankTalisman();
        placeHintShown = payload.placeHintShown();
        syncReceived = true;
        lastHintsEnabled = AozaiInkClientConfig.hintsEnabled();

        if (menuHintAck) {
            SigillumTutorialHints.completeOpenMenuHint();
        }
    }

    public static void onShowToast(ShowTutorialToastPayload payload) {
        if (!AozaiInkClientConfig.hintsEnabled()) return;
        if (payload.hint() == ShowTutorialToastPayload.PLACE_TALISMAN) {
            SigillumTutorialHints.showPlaceTalismanHint();
        }
    }

    public static void onMenuOpened() {
        if (!menuHintAck) {
            menuHintAck = true;
            SigillumTutorialHints.completeOpenMenuHint();
            PacketDistributor.sendToServer(new MenuHintAckPayload());
        }
    }

    public static void onTalismanPlaced() {
        if (!placeHintShown) {
            placeHintShown = true;
            SigillumTutorialHints.completePlaceTalismanHint();
            PacketDistributor.sendToServer(new PlaceHintAckPayload());
        }
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null) {
            loginTicks = 0;
            return;
        }
        if (!syncReceived) return;

        boolean hintsEnabled = AozaiInkClientConfig.hintsEnabled();
        if (!hintsEnabled && lastHintsEnabled) {
            SigillumTutorialHints.clearHints();
        }
        lastHintsEnabled = hintsEnabled;

        loginTicks++;
        if (loginTicks == LOGIN_DELAY_TICKS
                && !menuHintAck
                && !openMenuHintShownThisSession
                && hintsEnabled) {
            openMenuHintShownThisSession = true;
            SigillumTutorialHints.showOpenMenuHint();
        }
    }

    public static void onLogout() {
        syncReceived = false;
        menuHintAck = false;
        craftedBlankTalisman = false;
        placeHintShown = false;
        loginTicks = 0;
        openMenuHintShownThisSession = false;
        lastHintsEnabled = true;
        SigillumTutorialHints.clearHints();
    }
}
