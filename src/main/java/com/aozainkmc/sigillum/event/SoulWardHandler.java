package com.aozainkmc.sigillum.event;

import com.aozainkmc.sigillum.SigillumMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class SoulWardHandler {

    private static final int WINDOW_TICKS_LOW = 40;
    private static final int WINDOW_TICKS_MID = 600;
    private static final int WINDOW_TICKS_HIGH = 2400;
    private static final int LOCK_TICKS_HIGH = 100;
    private static final Map<UUID, WardEntry> WARDS = new HashMap<>();

    private SoulWardHandler() {}

    public static void ward(ServerPlayer player, float powerMultiplier, float durationMultiplier) {
        int tier = tierOf(powerMultiplier);
        int windowTicks = Math.max(1, Math.round(windowTicksOf(tier) * durationMultiplier));
        long gameTime = player.level().getGameTime();
        WARDS.put(player.getUUID(), new WardEntry(gameTime + windowTicks, tier));
        player.displayClientMessage(Component.literal("魄 · 定魄 " + formatWindow(windowTicks)), true);
        player.playNotifySound(SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.7f, 0.7f);
    }

    private static String formatWindow(int windowTicks) {
        int totalSeconds = Math.max(1, Math.round(windowTicks / 20.0f));
        if (totalSeconds >= 60) {
            return (totalSeconds / 60) + " 分" + (totalSeconds % 60 == 0 ? "" : " " + (totalSeconds % 60) + " 秒");
        }
        return totalSeconds + " 秒";
    }

    static int tierOf(float powerMultiplier) {
        return powerMultiplier >= 0.95f ? 2 : (powerMultiplier >= 0.7f ? 1 : 0);
    }

    static int windowTicksOf(int tier) {
        return tier >= 2 ? WINDOW_TICKS_HIGH : (tier >= 1 ? WINDOW_TICKS_MID : WINDOW_TICKS_LOW);
    }

    static int lockTicksOf(int tier) {
        return tier >= 2 ? LOCK_TICKS_HIGH : 0;
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        WardEntry ward = WARDS.get(player.getUUID());
        if (ward == null) return;
        long gameTime = player.level().getGameTime();
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        if (ward.triggeredUntil > gameTime) {
            if (event.getNewDamage() >= player.getHealth()) {
                event.setNewDamage(Math.max(0.0f, player.getHealth() - 1.0f));
            }
            return;
        }
        if (ward.expiresAt <= gameTime) {
            WARDS.remove(player.getUUID());
            return;
        }
        if (event.getNewDamage() < player.getHealth()) return;

        event.setNewDamage(Math.max(0.0f, player.getHealth() - 1.0f));
        int lockTicks = lockTicksOf(ward.tier);
        if (lockTicks > 0) {
            ward.triggeredUntil = gameTime + lockTicks;
        } else {
            WARDS.remove(player.getUUID());
        }
        applyTriggerBuffs(player, ward.tier);
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
            player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.6, 0.4, 0.6);
        player.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.1f);
        player.displayClientMessage(Component.literal(lockTicks > 0
            ? "魄 · 定魄锁血 " + formatWindow(lockTicks)
            : "魄 · 定魄挡下致命一击"), true);
    }

    private static void applyTriggerBuffs(ServerPlayer player, int tier) {
        if (tier >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60, 1));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 10, 2));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0));
        } else if (tier == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 30, 0));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        WARDS.entrySet().removeIf(e -> e.getValue().expiresAt <= now && e.getValue().triggeredUntil <= now);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WARDS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WARDS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WARDS.clear();
    }

    private static final class WardEntry {
        private final long expiresAt;
        private final int tier;
        private long triggeredUntil;

        private WardEntry(long expiresAt, int tier) {
            this.expiresAt = expiresAt;
            this.tier = tier;
        }
    }
}
