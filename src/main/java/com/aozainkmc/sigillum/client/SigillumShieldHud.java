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
    private static final ResourceLocation UNFILLED = texture("unfilled");
    private static final ResourceLocation FILLED = texture("filled");
    private static final ResourceLocation BETWEEN = texture("between");
    private static final ResourceLocation FRAME = texture("frame");
    private static final ResourceLocation SHIELD_LOGO = texture("shield_logo");
    private static final int BAR_WIDTH = 73;
    private static final int BAR_HEIGHT = 5;
    private static final int FRAME_WIDTH = 82;
    private static final int FRAME_HEIGHT = 13;
    private static final int LOGO_WIDTH = 10;
    private static final int LOGO_HEIGHT = 12;
    private static final int BETWEEN_WIDTH = 4;

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

        int bottom = graphics.guiHeight() - 42;
        if (minecraft.player.getArmorValue() > 0) {
            bottom -= 10;
        }
        int frameX = graphics.guiWidth() / 2 - 91;
        int frameY = bottom - FRAME_HEIGHT;
        int barX = frameX + 5;
        int barY = frameY + 4;

        float progress = Math.max(0.0f, Math.min(1.0f, amount / max));
        int fillWidth = Math.max(0, Math.min(BAR_WIDTH, Math.round(BAR_WIDTH * progress)));

        blit(graphics, FRAME, frameX, frameY, FRAME_WIDTH, FRAME_HEIGHT);
        // Both layers retain their complete 108x16 texture. Only the visible rectangle of the
        // gold layer is clipped, so partial shield never stretches either half of the artwork.
        blit(graphics, UNFILLED, barX, barY, BAR_WIDTH, BAR_HEIGHT);
        if (fillWidth > 0) {
            graphics.enableScissor(barX, barY, barX + fillWidth, barY + BAR_HEIGHT);
            blit(graphics, FILLED, barX, barY, BAR_WIDTH, BAR_HEIGHT);
            graphics.disableScissor();
        }
        if (fillWidth > 0 && fillWidth < BAR_WIDTH) {
            blit(graphics, BETWEEN, barX + fillWidth - BETWEEN_WIDTH / 2, barY,
                BETWEEN_WIDTH, BAR_HEIGHT);
        }

        blit(graphics, SHIELD_LOGO, frameX - LOGO_WIDTH - 2,
            frameY + (FRAME_HEIGHT - LOGO_HEIGHT) / 2, LOGO_WIDTH, LOGO_HEIGHT);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
            SigillumMod.MOD_ID, "textures/gui/shield/" + name + ".png");
    }

    private static void blit(
        GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height
    ) {
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F,
            width, height, width, height);
    }
}
