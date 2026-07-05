package com.aozainkmc.sigillum.event;

import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.cast.SigillumComboState;
import com.aozainkmc.sigillum.cast.SigillumShieldManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class SigillumShieldEvents {
    private SigillumShieldEvents() {}

    @SubscribeEvent
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity attacker = event.getSource().getEntity();
        float incoming = SigillumComboState.beforeShieldDamage(player, attacker, event.getAmount());
        String continuousKey = continuousDamageKey(event.getSource());
        float remaining = continuousKey == null
            ? SigillumShieldManager.absorb(player, incoming)
            : SigillumShieldManager.absorbContinuous(player, incoming, continuousKey, 20);
        SigillumComboState.afterShieldBlocked(player, attacker, Math.max(0.0f, incoming - remaining));
        event.setAmount(remaining);
    }

    private static String continuousDamageKey(DamageSource source) {
        if (source.is(DamageTypes.IN_FIRE)) return "in_fire";
        if (source.is(DamageTypes.ON_FIRE)) return "on_fire";
        if (source.is(DamageTypes.LAVA)) return "lava";
        if (source.is(DamageTypes.HOT_FLOOR)) return "hot_floor";
        if (source.is(DamageTypes.DRAGON_BREATH)) return "dragon_breath";
        return null;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SigillumShieldManager.clear(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SigillumShieldManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SigillumShieldManager.discard(player);
        }
    }
}
