package com.aozainkmc.sigillum.cast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public final class SigillumComboState {
    private static final List<SealShieldState> SEAL_SHIELDS = new ArrayList<>();
    private static final List<KineticShieldState> KINETIC_SHIELDS = new ArrayList<>();
    private static final List<LightShieldState> LIGHT_SHIELDS = new ArrayList<>();

    private SigillumComboState() {}

    public static void registerSealShield(ServerPlayer player, LivingEntity target, float multiplier, int ticks) {
        SealShieldState state = new SealShieldState(player.getUUID(), target.getUUID(), multiplier);
        SEAL_SHIELDS.add(state);
        scheduleRemove(SEAL_SHIELDS, state, ticks);
    }

    public static void registerKineticShield(ServerPlayer player, float multiplier, int ticks) {
        KineticShieldState state = new KineticShieldState(player.getUUID(), multiplier);
        KINETIC_SHIELDS.add(state);
        scheduleRemove(KINETIC_SHIELDS, state, ticks);
    }

    public static void registerLightShield(ServerPlayer player, int ticks) {
        LightShieldState state = new LightShieldState(player.getUUID(), ticks);
        LIGHT_SHIELDS.add(state);
        scheduleRemove(LIGHT_SHIELDS, state, ticks);
    }

    public static float beforeShieldDamage(ServerPlayer player, Entity attacker, float damage) {
        if (damage <= 0.0f || attacker == null) return damage;
        UUID playerId = player.getUUID();
        UUID attackerId = attacker.getUUID();
        for (Iterator<SealShieldState> iterator = SEAL_SHIELDS.iterator(); iterator.hasNext();) {
            SealShieldState state = iterator.next();
            if (!state.playerId.equals(playerId)) continue;
            if (!state.targetId.equals(attackerId)) continue;
            SigillumShieldManager.grantUncapped(player, damage * Math.max(0.5f, state.multiplier));
            player.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                18, 0.35, 0.45, 0.35, 0.02);
            player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.55f, 0.85f);
            return 0.0f;
        }
        return damage;
    }

    public static void afterShieldBlocked(ServerPlayer player, Entity attacker, float blockedDamage) {
        if (blockedDamage <= 0.0f || attacker == null) return;
        UUID playerId = player.getUUID();
        if (attacker instanceof LivingEntity livingAttacker) {
            for (KineticShieldState state : KINETIC_SHIELDS) {
                if (state.playerId.equals(playerId)) {
                    knockAway(player, livingAttacker, blockedDamage * 0.08f * Math.max(1.0f, state.multiplier));
                }
            }
            for (LightShieldState state : LIGHT_SHIELDS) {
                if (state.playerId.equals(playerId)) {
                    livingAttacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, state.remainingHintTicks, 0));
                }
            }
        }
    }

    private static void knockAway(ServerPlayer player, LivingEntity target, float strength) {
        Vec3 delta = target.position().subtract(player.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.001) return;
        double scale = Math.max(0.45, Math.min(2.0, strength));
        target.setDeltaMovement(target.getDeltaMovement().add(delta.x / horizontal * scale, 0.25, delta.z / horizontal * scale));
        target.hurtMarked = true;
        player.serverLevel().sendParticles(ParticleTypes.CLOUD,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            14, 0.2, 0.2, 0.2, 0.06);
    }

    private static <T> void scheduleRemove(List<T> list, T state, int ticks) {
        int[] remaining = {Math.max(1, ticks)};
        SigillumEffectTicker.add(() -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                list.remove(state);
                return true;
            }
            return false;
        });
    }

    private record SealShieldState(UUID playerId, UUID targetId, float multiplier) {}
    private record KineticShieldState(UUID playerId, float multiplier) {}
    private record LightShieldState(UUID playerId, int remainingHintTicks) {}
}
