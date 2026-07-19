package com.aozainkmc.sigillum.event;

import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.network.ShowTutorialToastPayload;
import com.aozainkmc.sigillum.network.SigillumNetworking;
import com.aozainkmc.sigillum.tutorial.SigillumTutorialData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class SigillumTutorialEvents {
    private static final ResourceLocation BLANK_YELLOW_TALISMAN =
        ResourceLocation.fromNamespaceAndPath("aozaink_input", "yellow_talisman");

    private SigillumTutorialEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SigillumNetworking.syncTutorial(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SigillumNetworking.syncTutorial(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SigillumNetworking.syncTutorial(player);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafting = event.getCrafting();
        if (crafting.isEmpty()) return;
        if (!BLANK_YELLOW_TALISMAN.equals(BuiltInRegistries.ITEM.getKey(crafting.getItem()))) return;
        if (SigillumTutorialData.placeHintShown(player)) return;

        SigillumTutorialData.setCraftedBlankTalisman(player, true);
        SigillumTutorialData.setPlaceHintShown(player, true);
        PacketDistributor.sendToPlayer(player, new ShowTutorialToastPayload(ShowTutorialToastPayload.PLACE_TALISMAN));
    }
}
