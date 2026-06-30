package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = SigillumMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SigillumShieldHud {
    private static final ResourceLocation LAYER =
        ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "shield_hud");
    private static final int BAR_WIDTH = 82;
    private static final int BAR_HEIGHT = 5;

    private static float amount;
    private static float max;

    private SigillumShieldHud() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, LAYER, SigillumShieldHud::render);
    }

    public static void update(float shieldAmount, float maxShield) {
        amount = Math.max(0.0f, shieldAmount);
        max = Math.max(0.0f, maxShield);
        if (amount <= 0.0f) {
            max = 0.0f;
        }
    }

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.gameMode == null) return;
        if (amount <= 0.0f || max <= 0.0f) return;

        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 49;
        if (minecraft.player.getArmorValue() > 0) {
            y -= 10;
        }

        float progress = Math.max(0.0f, Math.min(1.0f, amount / max));
        int fill = Math.max(1, Math.round(BAR_WIDTH * progress));

        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0x99000000);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xAA2A3340);
        graphics.fill(x, y, x + fill, y + BAR_HEIGHT, 0xE831A8FF);
        graphics.fill(x, y, x + fill, y + 1, 0xF0BDEBFF);
    }
}
