package com.aozainkmc.sigillum.advancement;

import com.aozainkmc.sigillum.SigillumMod;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SigillumAdvancementTriggers {
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
        DeferredRegister.create(Registries.TRIGGER_TYPE, SigillumMod.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> TALISMAN_CREATED =
        register("talisman_created");
    public static final DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> TAIL_MODIFIER_CHAOS =
        register("tail_modifier_chaos");
    public static final DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> SPECIFIED_BOUND =
        register("specified_bound");
    public static final DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> QUICK_CAST =
        register("quick_cast");
    public static final DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> TALISMAN_CAST =
        register("talisman_cast");
    public static final DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> INSCRIPTION_CHANGED =
        register("inscription_changed");
    public static final DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> SOUL_RECALLED =
        register("soul_recalled");
    public static final DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> SHIELD_EVENT =
        register("shield_event");
    public static final DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> SPECIAL_EFFECT =
        register("special_effect");

    private SigillumAdvancementTriggers() {}

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }

    public static void talismanCreated(ServerPlayer player, SigillumCriterionTrigger.Event event) {
        trigger(TALISMAN_CREATED, player, event);
    }

    public static void tailModifierChaos(ServerPlayer player, SigillumCriterionTrigger.Event event) {
        trigger(TAIL_MODIFIER_CHAOS, player, event);
    }

    public static void specifiedBound(ServerPlayer player, String skill) {
        trigger(SPECIFIED_BOUND, player, SigillumCriterionTrigger.Event.empty().withSkill(skill));
    }

    public static void quickCast(ServerPlayer player, String skill) {
        trigger(QUICK_CAST, player, SigillumCriterionTrigger.Event.empty().withSkill(skill));
    }

    public static void talismanCast(ServerPlayer player, SigillumCriterionTrigger.Event event) {
        trigger(TALISMAN_CAST, player, event);
    }

    public static void inscriptionChanged(ServerPlayer player, SigillumCriterionTrigger.Event event) {
        trigger(INSCRIPTION_CHANGED, player, event);
    }

    public static void soulRecalled(ServerPlayer player, int count) {
        trigger(SOUL_RECALLED, player, SigillumCriterionTrigger.Event.empty().withCount(count));
    }

    public static void shieldEvent(ServerPlayer player, SigillumCriterionTrigger.Event event) {
        trigger(SHIELD_EVENT, player, event);
    }

    public static void specialEffect(ServerPlayer player, SigillumCriterionTrigger.Event event) {
        trigger(SPECIAL_EFFECT, player, event);
    }

    private static DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> register(String name) {
        return TRIGGERS.register(name, SigillumCriterionTrigger::new);
    }

    private static void trigger(DeferredHolder<CriterionTrigger<?>, SigillumCriterionTrigger> holder,
            ServerPlayer player, SigillumCriterionTrigger.Event event) {
        if (player != null) {
            holder.get().trigger(player, event);
        }
    }
}
