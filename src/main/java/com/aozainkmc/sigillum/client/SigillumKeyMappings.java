package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class SigillumKeyMappings {

    public static final KeyMapping OPEN_MENU = new KeyMapping(
        "key.aozaink_sigillum.open_menu",
        GLFW.GLFW_KEY_M,
        "key.categories.aozaink_sigillum"
    );

    private SigillumKeyMappings() {}

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }
}
