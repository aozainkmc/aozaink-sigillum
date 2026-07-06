package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.network.ClearBindingPayload;
import com.aozainkmc.sigillum.network.MenuRequestPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SigillumClientHooks {

    private SigillumClientHooks() {}

    public static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PacketDistributor.sendToServer(new MenuRequestPayload());
    }

    public static void clearBinding(int digit) {
        PacketDistributor.sendToServer(new ClearBindingPayload(digit));
    }
}
