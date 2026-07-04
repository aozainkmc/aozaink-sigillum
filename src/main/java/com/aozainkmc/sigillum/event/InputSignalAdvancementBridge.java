package com.aozainkmc.sigillum.event;

import com.aozainkmc.core.api.InkModuleSignalEvent;
import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.advancement.SigillumAdvancementTriggers;
import com.aozainkmc.sigillum.advancement.SigillumCriterionTrigger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class InputSignalAdvancementBridge {
    private static final String INPUT_NAMESPACE = "aozaink_input";
    private static final ResourceLocation TALISMAN_CREATED =
        ResourceLocation.fromNamespaceAndPath(INPUT_NAMESPACE, "talisman_created");
    private static final ResourceLocation TAIL_MODIFIER_CHAOS =
        ResourceLocation.fromNamespaceAndPath(INPUT_NAMESPACE, "tail_modifier_chaos");

    private InputSignalAdvancementBridge() {}

    @SubscribeEvent
    public static void onInputSignal(InkModuleSignalEvent event) {
        ResourceLocation signalId = event.signalId();
        CompoundTag payload = event.payload();
        if (TALISMAN_CREATED.equals(signalId)) {
            SigillumAdvancementTriggers.talismanCreated(event.player(), SigillumCriterionTrigger.Event.empty()
                .withType(payload.getString("type"))
                .withGrade(payload.getString("grade"))
                .withTail(payload.getBoolean("tail")));
        } else if (TAIL_MODIFIER_CHAOS.equals(signalId)) {
            SigillumAdvancementTriggers.tailModifierChaos(event.player(), SigillumCriterionTrigger.Event.empty()
                .withEscaped(payload.getBoolean("escaped"))
                .withHitPlayer(payload.getBoolean("hit_player"))
                .withKilledEntity(payload.getBoolean("killed_entity")));
        }
    }
}
