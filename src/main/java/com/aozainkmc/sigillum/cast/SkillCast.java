package com.aozainkmc.sigillum.cast;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SkillCast {

    public static final double RANGE = 16.0;
    private static final float INFERIOR_SHIELD = 8.0f;
    private static final float FINE_SHIELD = 16.0f;
    private static final float EXQUISITE_SHIELD = 30.0f;

    private SkillCast() {}

    public enum Outcome { HIT, MISS }

    public static boolean isImplementedSkill(String glyph) {
        return "火".equals(glyph) || "雷".equals(glyph) || "护".equals(glyph) || "净".equals(glyph);
    }

    public static Outcome cast(ServerPlayer player, String glyph, float m) {
        return switch (glyph) {
            case "火" -> castFire(player, m);
            case "雷" -> castThunder(player, m);
            case "护" -> castShield(player, m);
            case "净" -> castPurify(player, m);
            default -> Outcome.HIT;
        };
    }

    private static Outcome castFire(ServerPlayer player, float m) {
        ServerLevel level = player.serverLevel();
        LivingEntity target = targetLiving(player);
        if (target == null) return Outcome.MISS;
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        float dps = (undead ? 3.0f : 2.0f) * m;
        SigillumEffectTicker.scheduleBurn(level, target, dps, 4);
        level.sendParticles(ParticleTypes.FLAME,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            24, 0.25, 0.4, 0.25, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.0f);
        return Outcome.HIT;
    }

    private static Outcome castThunder(ServerPlayer player, float m) {
        ServerLevel level = player.serverLevel();
        LivingEntity target = targetLiving(player);
        if (target == null) return Outcome.MISS;
        float damage = 12.0f * m * (level.isRainingAt(target.blockPosition()) ? 1.3f : 1.0f);
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().lightningBolt(), damage);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        if (level.random.nextFloat() < 0.5f) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 1));
        }
        return Outcome.HIT;
    }

    private static Outcome castShield(ServerPlayer player, float m) {
        ServerLevel level = player.serverLevel();
        float amount = shieldAmount(m);
        SigillumShieldManager.grant(player, amount);
        level.sendParticles(ParticleTypes.ENCHANT,
            player.getX(), player.getY() + 1.0, player.getZ(),
            40, 0.4, 0.6, 0.4, 0.6);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.3f);
        return Outcome.HIT;
    }

    private static float shieldAmount(float m) {
        if (m >= 0.95f) return EXQUISITE_SHIELD;
        if (m >= 0.75f) return FINE_SHIELD;
        return INFERIOR_SHIELD;
    }

    private static Outcome castPurify(ServerPlayer player, float m) {
        ServerLevel level = player.serverLevel();
        int cleanseCount = m >= 1.0f ? 3 : (m >= 0.8f ? 2 : 1);
        for (MobEffectInstance effect : player.getActiveEffects().stream()
                .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                .limit(cleanseCount)
                .toList()) {
            player.removeEffect(effect.getEffect());
        }

        LivingEntity target = targetLiving(player);
        if (target != null && target.getType().is(EntityTypeTags.UNDEAD)) {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().magic(), 4.0f * m);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                16, 0.25, 0.4, 0.25, 0.01);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            player.getX(), player.getY() + 1.0, player.getZ(),
            16, 0.35, 0.5, 0.35, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        return Outcome.HIT;
    }

    private static LivingEntity targetLiving(ServerPlayer player) {
        HitResult hr = ProjectileUtil.getHitResultOnViewVector(player,
            e -> e instanceof LivingEntity && e != player && e.isAlive(), RANGE);
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    public static Vec3 landPoint(ServerPlayer player) {
        return ProjectileUtil.getHitResultOnViewVector(player, e -> false, RANGE).getLocation();
    }
}
