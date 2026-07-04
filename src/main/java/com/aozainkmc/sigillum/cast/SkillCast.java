package com.aozainkmc.sigillum.cast;

import com.aozainkmc.sigillum.advancement.SigillumAdvancementTriggers;
import com.aozainkmc.sigillum.advancement.SigillumCriterionTrigger;
import com.aozainkmc.sigillum.event.SoulRecallHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SkillCast {

    public static final double RANGE = 16.0;
    public static final double AOE_RADIUS = 4.0;
    private static final double TARGET_AIM_TOLERANCE = 0.35;
    private static final float INFERIOR_SHIELD = 8.0f;
    private static final float FINE_SHIELD = 16.0f;
    private static final float EXQUISITE_SHIELD = 30.0f;
    private static final int SUPPRESS_TICKS = 60;
    private static final int SEAL_TICKS = 60;
    private static final int SEAL_UNDEAD_STUN_TICKS = 30;
    private static final int REPEL_STUN_TICKS = 20;
    private static final double REPEL_STRENGTH = 1.2;
    private static final double LURE_STRENGTH = 1.3;
    private static final int LIGHT_RADIUS = 24;
    private static final int LIGHT_TICKS = 20 * 60 * 5;
    private static final int LIGHT_WIDE_TICKS = 20 * 75;

    private SkillCast() {}

    public enum Outcome { HIT, MISS }

    public enum TargetRequirement { NONE, OPTIONAL, REQUIRED }

    public record CastEnv(float multiplier, float durationMultiplier, boolean wideSupport, boolean suppressSupport) {
        public CastEnv(float multiplier, float durationMultiplier) {
            this(multiplier, durationMultiplier, false, false);
        }

        public static CastEnv of(float multiplier) {
            return new CastEnv(multiplier, 1.0f);
        }

        public CastEnv withMultiplier(float factor) {
            return new CastEnv(multiplier * factor, durationMultiplier, wideSupport, suppressSupport);
        }

        public CastEnv withWideSupport() {
            return new CastEnv(multiplier, durationMultiplier, true, false);
        }

        public CastEnv withoutSelfSupport() {
            return new CastEnv(multiplier, durationMultiplier, wideSupport, true);
        }
    }

    public enum LinkedComboKind {
        DRAIN, SOUL,
        SUPPRESS_SEAL, SUPPRESS_REPEL, SUPPRESS_LURE, SUPPRESS_FIRE,
        SUPPRESS_THUNDER, SUPPRESS_SHIELD, SUPPRESS_PURIFY, SUPPRESS_SLASH, SUPPRESS_LIGHT,
        SEAL_PAIR, REPEL_PAIR, LURE_PAIR, FIRE_PAIR, THUNDER_PAIR,
        SHIELD_PAIR, PURIFY_PAIR, SLASH_PAIR, LIGHT_PAIR, DIRECT_PAIR
    }

    public record LinkedComboSpec(String first, String second, LinkedComboKind kind, boolean needsTarget, String label) {}

    public static LinkedComboSpec linkedComboSpec(String a, String b) {
        LinkedComboRegistry.ComboSpec spec = LinkedComboRegistry.lookup(a, b);
        if (spec == null) return null;
        return new LinkedComboSpec(spec.key().first(), spec.key().second(), spec.kind(), spec.needsTarget(), spec.label());
    }

    public static String briefLabel(String glyph) {
        return switch (glyph) {
            case "镇" -> "镇压";
            case "封" -> "封印";
            case "退" -> "击退";
            case "引" -> "牵引";
            case "火" -> "点燃";
            case "雷" -> "雷击";
            case "护" -> "护盾";
            case "净" -> "净化";
            case "斩" -> "斩杀";
            case "明" -> "照妖";
            case "吸" -> "吸血";
            case "魄" -> "招魄";
            default -> glyph;
        };
    }

    public static boolean isImplementedSkill(String glyph) {
        return switch (glyph) {
            case "火", "雷", "护", "净", "镇", "封", "退", "引", "斩", "明", "吸", "魄" -> true;
            default -> false;
        };
    }

    public static boolean isSelfSkill(String glyph) {
        return hasSelfEffect(glyph);
    }

    public static boolean hasSelfEffect(String glyph) {
        return switch (glyph) {
            case "护", "净", "明", "魄" -> true;
            default -> false;
        };
    }

    public static boolean hasTargetEffect(String glyph) {
        return switch (glyph) {
            case "火", "雷", "镇", "封", "退", "引", "斩", "明", "吸" -> true;
            default -> false;
        };
    }

    public static TargetRequirement targetRequirement(String glyph) {
        return switch (glyph) {
            case "明" -> TargetRequirement.OPTIONAL;
            case "火", "雷", "镇", "封", "退", "引", "斩", "吸" -> TargetRequirement.REQUIRED;
            default -> TargetRequirement.NONE;
        };
    }

    public static boolean requiresTarget(String glyph) {
        return targetRequirement(glyph) == TargetRequirement.REQUIRED;
    }

    public static boolean isModifier(String glyph) {
        return switch (glyph) {
            case "强", "续", "广", "穿" -> true;
            default -> false;
        };
    }

    public static boolean hasWideSupportEffect(String glyph) {
        return switch (glyph) {
            case "护", "净" -> true;
            default -> false;
        };
    }

    private static String comboKey(String a, String b) {
        return skillRank(a) < skillRank(b) ? a + b : b + a;
    }

    private static int skillRank(String glyph) {
        return switch (glyph) {
            case "镇" -> 0;
            case "封" -> 1;
            case "退" -> 2;
            case "引" -> 3;
            case "火" -> 4;
            case "雷" -> 5;
            case "护" -> 6;
            case "净" -> 7;
            case "斩" -> 8;
            case "明" -> 9;
            case "吸" -> 10;
            case "魄" -> 11;
            default -> 99;
        };
    }

    public static Outcome cast(ServerPlayer player, String glyph, float m) {
        return cast(player, glyph, CastEnv.of(m));
    }

    public static Outcome cast(ServerPlayer player, String glyph, CastEnv env) {
        TargetRequirement targetRequirement = targetRequirement(glyph);
        LivingEntity target = targetRequirement == TargetRequirement.NONE ? null : targetLiving(player);
        if (targetRequirement == TargetRequirement.REQUIRED && target == null) return Outcome.MISS;

        boolean applied = false;
        if (hasSelfEffect(glyph)) {
            applySelf(player, glyph, env);
            applied = true;
        }
        if (target != null && hasTargetEffect(glyph)) {
            applyToTarget(player, target, glyph, env);
            applied = true;
        }
        return applied ? Outcome.HIT : Outcome.MISS;
    }

    public static void applyToTarget(ServerPlayer player, LivingEntity target, String glyph, CastEnv env) {
        switch (glyph) {
            case "火" -> applyFire(player, target, env);
            case "雷" -> applyThunder(player, target, env);
            case "镇" -> applySuppress(player, target, env);
            case "封" -> applySeal(player, target, env);
            case "退" -> applyRepel(player, target, env);
            case "引" -> applyLure(player, target, env);
            case "斩" -> applySlash(player, target, env);
            case "明" -> applyLightTarget(player, target, env);
            case "吸" -> applyDrain(player, target, env);
        }
    }

    public static void applySelf(ServerPlayer player, String glyph, CastEnv env) {
        switch (glyph) {
            case "护" -> applyShield(player, env);
            case "净" -> applyPurify(player, env);
            case "明" -> applyLightSelf(player, env);
            case "魄" -> applySoulRecall(player, env);
        }
    }

    public static int applyWideSupport(ServerPlayer player, String glyph, CastEnv env) {
        return switch (glyph) {
            case "护" -> distributeShieldSupport(player, shieldAmount(env.multiplier()));
            case "净" -> distributePurifySupport(player, cleanseCount(env.multiplier()));
            default -> 0;
        };
    }

    public static void applyLinkedCombo(ServerPlayer player, LivingEntity target, LinkedComboSpec spec, CastEnv env) {
        LinkedComboRegistry.ComboSpec registrySpec = LinkedComboRegistry.lookup(spec.first(), spec.second());
        if (registrySpec != null) {
            registrySpec.executor().execute(player, target, env);
        }
    }

    public static boolean supportsPiercingCombo(LinkedComboSpec spec) {
        if (spec == null || !spec.needsTarget()) return false;
        String a = spec.first();
        String b = spec.second();
        if ("魄".equals(a) || "魄".equals(b)) return false;
        if ("护".equals(a) || "护".equals(b)) return false;
        if ("明".equals(a) || "明".equals(b)) {
            return comboKey(a, b).equals("退明");
        }
        if ("净".equals(a) || "净".equals(b)) {
            return comboKey(a, b).equals("火净") || comboKey(a, b).equals("雷净");
        }
        return true;
    }

    public static void applyDrainCombo(ServerPlayer player, LivingEntity target, String otherSkill, CastEnv env) {
        switch (otherSkill) {
            case "镇" -> {
                applySuppress(player, target, env);
                int ticks = suppressTicks(target, env);
                schedulePeriodicDrain(player, target, env, ticks, 0.25f);
            }
            case "封" -> {
                applySeal(player, target, env);
                int ticks = sealTicks(env);
                schedulePeriodicDrain(player, target, env, ticks, 0.25f);
            }
            case "退" -> {
                Vec3 before = target.position();
                drain(player, target, env);
                applyRepel(player, target, env);
                scheduleMovementHeal(player, target, before, env, 10);
            }
            case "引" -> {
                Vec3 before = target.position();
                applyLure(player, target, env);
                scheduleMovementDrain(player, target, before, env, 10);
            }
            case "火" -> applyFireDrain(player, target, env);
            case "雷" -> applyThunderDrain(player, target, env);
            case "护" -> {
                applyShield(player, env);
                scheduleDrainToOverflowShield(player, target, env, Math.round(100.0f * env.durationMultiplier()));
            }
            case "净" -> {
                applyPurifySelf(player, env);
                if (target.getType().is(EntityTypeTags.UNDEAD)) {
                    float dealt = hurtAndMeasure(player.serverLevel(), target,
                        player.serverLevel().damageSources().magic(), 4.0f * env.multiplier());
                    healPlayer(player, dealt);
                }
                drain(player, target, env);
            }
            case "斩" -> applySlashDrain(player, target, env);
            case "明" -> applyLightDrain(player, target, env);
            case "魄" -> {
                drain(player, target, env);
                applySoulRecall(player, env);
            }
            default -> drain(player, target, env);
        }
    }

    public static void applySoulCombo(ServerPlayer player, LivingEntity target, String otherSkill, CastEnv env) {
        switch (otherSkill) {
            case "退" -> {
                SoulRecallHandler.RecoveryResult result = applySoulRecallResult(player, env);
                if (result.recovered()) {
                    repelPulse(player.serverLevel(), player.position(), env.multiplier());
                    if (result.deathDimension() != null && result.deathPos() != null) {
                        ServerLevel deathLevel = player.getServer().getLevel(result.deathDimension());
                        if (deathLevel != null && deathLevel.hasChunkAt(result.deathPos())) {
                            repelPulse(deathLevel, Vec3.atCenterOf(result.deathPos()), env.multiplier());
                        }
                    }
                }
            }
            case "引" -> SoulRecallHandler.recoverToFeet(player, soulRecallRatio(env));
            case "护" -> {
                applyShield(player, env);
                applySoulRecall(player, env);
            }
            case "净" -> {
                applyPurify(player, env);
                applySoulRecall(player, env);
            }
            case "明" -> {
                applyLight(player, env);
                applySoulRecall(player, env);
            }
            default -> {
                if (target != null) {
                    applyToTarget(player, target, otherSkill, env);
                }
                applySoulRecall(player, env);
            }
        }
    }

    static void applySuppressSeal(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        int ticks = Math.min(suppressTicks(target, env), sealTicks(env));
        int strongAmp = env.multiplier() > 1.0f ? 1 : 0;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, (undead ? 4 : 3) + strongAmp));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2 + strongAmp));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 2 + strongAmp));
        level.sendParticles(ParticleTypes.SCULK_SOUL,
            target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
            24, 0.3, 0.45, 0.3, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    static void applySuppressRepel(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyRepel(player, target, env);
        scheduleDelayedSuppress(player, target, new CastEnv(env.multiplier(), env.durationMultiplier() * 0.5f), 12, 24);
    }

    static void applySuppressLure(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyLure(player, target, env);
        scheduleDelayedSuppress(player, target, new CastEnv(env.multiplier(), env.durationMultiplier() * 0.5f), 8, 20);
    }

    static void applySuppressFire(ServerPlayer player, LivingEntity target, CastEnv env) {
        int suppressTicks = applySuppressState(player, target, env);
        ServerLevel level = player.serverLevel();
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        float dps = (undead ? 3.0f : 2.0f) * env.multiplier();
        int seconds = Math.max(fireSeconds(env), Math.max(1, (int)Math.ceil(suppressTicks / 20.0)));
        SigillumEffectTicker.scheduleBurn(level, target, dps, seconds);
        level.sendParticles(ParticleTypes.FLAME,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            32, 0.3, 0.45, 0.3, 0.02);
    }

    static void applySuppressThunder(ServerPlayer player, LivingEntity target, CastEnv env) {
        applySuppressState(player, target, env);
        strikeThunder(player, target, env, true);
        LivingEntity chain = nearestEnemy(player.serverLevel(), target.position(), 5.0, target);
        if (chain != null) {
            strikeThunder(player, chain, env.withMultiplier(0.35f), false);
        }
    }

    static void applySuppressShield(ServerPlayer player, LivingEntity target, CastEnv env) {
        int ticks = applySuppressState(player, target, env);
        Vec3[] last = {target.position()};
        double[] accumulated = {0.0};
        scheduleWhile(ticks, () -> player.isAlive() && target.isAlive() && target.level() == player.serverLevel(), tick -> {
            Vec3 now = target.position();
            accumulated[0] += now.distanceTo(last[0]);
            last[0] = now;
            while (accumulated[0] >= 1.0) {
                SigillumShieldManager.grantUncapped(player, Math.max(0.25f, env.multiplier()));
                accumulated[0] -= 1.0;
            }
        });
    }

    static void applySuppressPurify(ServerPlayer player, LivingEntity target, CastEnv env) {
        int ticks = applySuppressState(player, target, env);
        Map<Holder<MobEffect>, Integer> knownHarmful = harmfulEffectDurations(player);
        int[] remaining = {ticks};
        int[] extraBudget = {Math.round(40.0f * env.durationMultiplier())};
        SigillumEffectTicker.add(() -> {
            if (!player.isAlive() || !target.isAlive() || target.level() != player.serverLevel()) return true;
            remaining[0]--;
            for (MobEffectInstance effect : player.getActiveEffects().stream()
                    .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                    .toList()) {
                Integer previousDuration = knownHarmful.get(effect.getEffect());
                boolean newlyApplied = previousDuration == null || effect.getDuration() > previousDuration + 5;
                if (newlyApplied) {
                    player.removeEffect(effect.getEffect());
                    applyHolyPulse(player.serverLevel(), target, 2.0f * env.multiplier());
                    int extend = Math.min(extraBudget[0], 20);
                    if (extend > 0) {
                        extraBudget[0] -= extend;
                        remaining[0] += extend;
                        applySuppressState(player, target, new CastEnv(env.multiplier(), Math.max(0.25f, extend / (float) SUPPRESS_TICKS)));
                    }
                }
            }
            knownHarmful.clear();
            knownHarmful.putAll(harmfulEffectDurations(player));
            return remaining[0] <= 0;
        });
    }

    static void applySuppressSlash(ServerPlayer player, LivingEntity target, CastEnv env) {
        int ticks = applySuppressState(player, target, env);
        Vec3[] last = {target.position()};
        double[] accumulated = {0.0};
        scheduleWhile(ticks, () -> player.isAlive() && target.isAlive() && target.level() == player.serverLevel(), tick -> {
            Vec3 now = target.position();
            accumulated[0] += now.distanceTo(last[0]);
            last[0] = now;
            while (accumulated[0] >= 1.0 && target.isAlive()) {
                applySlash(player, target, env);
                accumulated[0] -= 1.0;
            }
        });
    }

    static void applySuppressLight(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyLight(player, env);
        int ticks = applySuppressState(player, target, env);
        LivingEntity[] current = {target};
        boolean[] transferred = {false};
        int[] remaining = {ticks};
        SigillumEffectTicker.add(() -> {
            if (!player.isAlive()) return true;
            remaining[0]--;
            if (!current[0].isAlive() && !transferred[0]) {
                LivingEntity next = nearestGlowingEnemy(player, current[0].position());
                if (next != null) {
                    current[0] = next;
                    transferred[0] = true;
                    remaining[0] = ticks;
                    applyLight(player, env);
                    applySuppressState(player, next, env);
                }
            }
            return remaining[0] <= 0 || current[0].level() != player.serverLevel();
        });
    }

    static void applyPairCombo(ServerPlayer player, LivingEntity target, String first, String second, CastEnv env) {
        switch (comboKey(first, second)) {
            case "封退" -> applySealRepel(player, target, env);
            case "封引" -> applySealLure(player, target, env);
            case "封火", "封明" -> applyGenericPair(player, target, first, second, env);
            case "封雷" -> applySealThunder(player, target, env);
            case "封护" -> applySealShield(player, target, env);
            case "封净" -> applySealPurify(player, target, env);
            case "封斩" -> applySealSlash(player, target, env);
            case "退引" -> applyRepelLure(player, target, env);
            case "退火" -> applyRepelFire(player, target, env);
            case "退雷" -> applyRepelThunder(player, target, env);
            case "退护" -> applyRepelShield(player, target, env);
            case "退净" -> applyRepelPurify(player, target, env);
            case "退斩" -> applyRepelSlash(player, target, env);
            case "退明" -> applyRepelLight(player, target, env);
            case "引火" -> applyLureFire(player, target, env);
            case "引雷" -> applyLureThunder(player, target, env);
            case "引护" -> applyLureShield(player, target, env);
            case "引净" -> applyLurePurify(player, target, env);
            case "引斩" -> applyLureSlash(player, target, env);
            case "引明" -> applyLureLight(player, target, env);
            case "火雷" -> applyFireThunder(player, target, env);
            case "火护" -> applyFireShield(player, target, env);
            case "火净" -> applyFirePurify(player, target, env);
            case "火斩" -> applyFireSlash(player, target, env);
            case "火明" -> applyFireLight(player, target, env);
            case "雷护" -> applyThunderShield(player, target, env);
            case "雷净" -> applyThunderPurify(player, target, env);
            case "雷斩" -> applyThunderSlash(player, target, env);
            case "雷明" -> applyThunderLight(player, target, env);
            case "护净" -> applyShieldPurify(player, env);
            case "护斩" -> applyShieldSlash(player, target, env);
            case "护明" -> applyShieldLight(player, env);
            case "净斩" -> applyPurifySlash(player, target, env);
            case "净明" -> applyPurifyLight(player, env);
            case "斩明" -> applySlashLight(player, target, env);
            default -> applyGenericPair(player, target, first, second, env);
        }
    }

    private static void applyGenericPair(ServerPlayer player, LivingEntity target, String first, String second, CastEnv env) {
        for (String skill : new String[] {first, second}) {
            if (hasSelfEffect(skill)) {
                applySelf(player, skill, env);
            }
            if (target != null && hasTargetEffect(skill)) {
                applyToTarget(player, target, skill, env);
            }
        }
    }

    private static void applySealRepel(ServerPlayer player, LivingEntity target, CastEnv env) {
        Vec3 before = target.position();
        applyRepel(player, target, env.withMultiplier(1.15f));
        target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.9 + 0.25 * env.multiplier(), 0.0));
        target.hurtMarked = true;
        scheduleLanding(player, target, 70, () -> applySeal(player, target, env));
        player.serverLevel().sendParticles(ParticleTypes.CLOUD,
            before.x, before.y + target.getBbHeight() * 0.5, before.z,
            28, 0.25, 0.35, 0.25, 0.08);
    }

    private static void applySealLure(ServerPlayer player, LivingEntity target, CastEnv env) {
        pullToSafeFront(player, target, env, 3.0);
        scheduleDelayed(player, target, 8, () -> applySeal(player, target, env));
    }

    private static void applySealThunder(ServerPlayer player, LivingEntity target, CastEnv env) {
        int ticks = sealTicks(env);
        applySeal(player, target, env);
        scheduleWhile(ticks, () -> player.isAlive() && target.isAlive() && target.level() == player.serverLevel(), tick -> {
            if (tick % 40 == 1) {
                strikeThunder(player, target, env.withMultiplier(0.65f), true);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Math.min(40, ticks), 9));
            }
        });
    }

    private static void applySealShield(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyShield(player, env);
        int ticks = sealTicks(env);
        applySeal(player, target, env);
        SigillumComboState.registerSealShield(player, target, env.multiplier(), ticks);
    }

    private static void applySealPurify(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyPurifySelf(player, env);
        if (target.getType().is(EntityTypeTags.UNDEAD)) {
            applyHolyPulse(player.serverLevel(), target, 5.0f * env.multiplier());
        }
        target.removeEffect(MobEffects.REGENERATION);
        applySeal(player, target, env);
    }

    private static void applySealSlash(ServerPlayer player, LivingEntity target, CastEnv env) {
        applySeal(player, target, env);
        applySlashWithThreshold(player, target, Math.min(0.75f, slashThreshold(env) + 0.12f * Math.max(0.55f, env.multiplier())));
    }

    private static void applyRepelLure(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyRepel(player, target, env);
        hurtAndMeasure(player.serverLevel(), target, player.serverLevel().damageSources().playerAttack(player), 20.0f * Math.max(1.0f, env.multiplier()));
        scheduleDelayed(player, target, 12, () -> pullEnemiesToPoint(player.serverLevel(), target.position(), 4.0, env.multiplier(), target));
    }

    private static void applyRepelFire(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyRepel(player, target, env);
        applyFire(player, target, env);
        scheduleDelayed(player, target, 12, () -> burnAround(player, target.position(), 3.0, env.withMultiplier(0.6f), target));
    }

    private static void applyRepelThunder(ServerPlayer player, LivingEntity target, CastEnv env) {
        strikeThunder(player, target, env, true);
        repelPulse(player.serverLevel(), target.position(), env.multiplier());
    }

    private static void applyRepelShield(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyShield(player, env);
        SigillumComboState.registerKineticShield(player, env.multiplier(), Math.round(120.0f * env.durationMultiplier()));
        scheduleWhile(Math.round(80.0f * env.durationMultiplier()), player::isAlive, tick -> {
            AABB box = player.getBoundingBox().inflate(0.75);
            for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive())) {
                applyRepelFromCenter(player.serverLevel(), player.position(), entity, 0.75 * Math.max(1.0f, env.multiplier()));
            }
        });
        applyRepel(player, target, env);
    }

    private static void applyRepelPurify(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyPurifySelf(player, env);
        if (target.getType().is(EntityTypeTags.UNDEAD)) {
            applyHolyPulse(player.serverLevel(), target, 4.0f * env.multiplier());
            applyRepel(player, target, env.withMultiplier(1.35f));
        } else {
            applyRepel(player, target, env);
        }
    }

    private static void applyRepelSlash(ServerPlayer player, LivingEntity target, CastEnv env) {
        applySlash(player, target, env);
        if (!target.isAlive()) return;
        applyRepel(player, target, env);
        scheduleWhile(Math.round(60.0f * env.durationMultiplier()), () -> player.isAlive() && target.isAlive() && target.level() == player.serverLevel(), tick -> {
            if (target.getHealth() / target.getMaxHealth() <= slashThreshold(env)) {
                applySlash(player, target, env);
            }
        });
    }

    private static void applyRepelLight(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyRepel(player, target, env);
        scheduleDelayed(player, target, 12, () -> glowAround(player, target.position(), 5.0, Math.round(100.0f * env.durationMultiplier())));
    }

    private static void applyLureFire(ServerPlayer player, LivingEntity target, CastEnv env) {
        Vec3 start = target.position();
        applyLure(player, target, env);
        applyFire(player, target, env);
        burnAlongLine(player, start, player.position(), env.withMultiplier(0.45f), target);
    }

    private static void applyLureThunder(ServerPlayer player, LivingEntity target, CastEnv env) {
        Vec3 start = target.position();
        applyLure(player, target, env);
        strikeThunderAlongLine(player, start, player.position(), env.withMultiplier(0.45f), target);
        scheduleDelayed(player, target, 8, () -> strikeThunder(player, target, env, true));
    }

    private static void applyLureShield(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyShield(player, env);
        pullToSafeFront(player, target, env, 3.0);
    }

    private static void applyLurePurify(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyPurifySelf(player, env);
        applyLure(player, target, env);
        scheduleDelayed(player, target, 8, () -> {
            if (target.getType().is(EntityTypeTags.UNDEAD)) {
                holyBurst(player, target.position(), 3.0, 4.0f * env.multiplier());
            }
        });
    }

    private static void applyLureSlash(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyLure(player, target, env);
        scheduleDelayed(player, target, 8, () -> applySlash(player, target, env));
    }

    private static void applyLureLight(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyLight(player, env);
        applyLure(player, target, env);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.round((LIGHT_TICKS + 20) * env.durationMultiplier()), 0));
    }

    private static void applyFireThunder(ServerPlayer player, LivingEntity target, CastEnv env) {
        int seconds = fireSeconds(env);
        applyFire(player, target, env);
        strikeThunder(player, target, env, true);
        scheduleDelayed(player, target, 2, () -> explodeFireBudget(player, target.position(), seconds, env, target));
    }

    private static void applyFireShield(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyShield(player, env);
        scheduleFireDamage(player, target, env, dealt -> SigillumShieldManager.grantUncapped(player, dealt));
    }

    private static void applyFirePurify(ServerPlayer player, LivingEntity target, CastEnv env) {
        int seconds = fireSeconds(env);
        target.removeEffect(MobEffects.REGENERATION);
        scheduleWhile(seconds * 20, () -> player.isAlive() && target.isAlive() && target.level() == player.serverLevel(), tick -> {
            if (tick % 20 == 0) {
                DamageSource source = target.getType().is(EntityTypeTags.UNDEAD)
                    ? player.serverLevel().damageSources().magic()
                    : player.serverLevel().damageSources().onFire();
                hurtAndMeasure(player.serverLevel(), target, source, (target.getType().is(EntityTypeTags.UNDEAD) ? 3.0f : 2.0f) * env.multiplier());
            }
        });
        player.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            24, 0.25, 0.4, 0.25, 0.01);
    }

    private static void applyFireSlash(ServerPlayer player, LivingEntity target, CastEnv env) {
        scheduleFireDamage(player, target, env, dealt -> applySlash(player, target, env));
    }

    private static void applyFireLight(ServerPlayer player, LivingEntity target, CastEnv env) {
        int seconds = fireSeconds(env);
        scheduleFireDamage(player, target, env, dealt -> glowAround(player, target.position(), 5.0, 40));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, seconds * 20, 0));
    }

    private static void applyThunderShield(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyShield(player, env);
        strikeThunderMeasured(player, target, env, dealt -> SigillumShieldManager.grantUncapped(player, dealt * 0.5f));
    }

    private static void applyThunderPurify(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyPurifySelf(player, env);
        strikeThunder(player, target, env, true);
        if (target.getType().is(EntityTypeTags.UNDEAD)) {
            applyHolyPulse(player.serverLevel(), target, 4.0f * env.multiplier());
            removeOneBeneficial(target);
        }
    }

    private static void applyThunderSlash(ServerPlayer player, LivingEntity target, CastEnv env) {
        strikeThunderMeasured(player, target, env, dealt -> applySlash(player, target, env));
    }

    private static void applyThunderLight(ServerPlayer player, LivingEntity target, CastEnv env) {
        strikeThunder(player, target, env, true);
        glowAround(player, target.position(), 10.0, Math.round(100.0f * env.durationMultiplier()));
    }

    private static void applyShieldPurify(ServerPlayer player, CastEnv env) {
        applyShield(player, env);
        applyPurifySelf(player, env);
        Map<Holder<MobEffect>, Integer> knownHarmful = harmfulEffectDurations(player);
        scheduleWhile(Math.round(120.0f * env.durationMultiplier()), player::isAlive, tick -> {
            for (MobEffectInstance effect : player.getActiveEffects().stream()
                    .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                    .toList()) {
                Integer previousDuration = knownHarmful.get(effect.getEffect());
                if (previousDuration == null || effect.getDuration() > previousDuration + 5) {
                    player.removeEffect(effect.getEffect());
                    SigillumShieldManager.absorb(player, 4.0f);
                }
            }
            knownHarmful.clear();
            knownHarmful.putAll(harmfulEffectDurations(player));
        });
    }

    private static void applyShieldSlash(ServerPlayer player, LivingEntity target, CastEnv env) {
        if (target.getHealth() / target.getMaxHealth() <= slashThreshold(env)) {
            float shield = target.getHealth() * Math.max(0.5f, env.multiplier());
            applySlash(player, target, env);
            SigillumShieldManager.grantUncapped(player, shield);
        } else {
            applyShield(player, env);
        }
    }

    private static void applyShieldLight(ServerPlayer player, CastEnv env) {
        applyShield(player, env);
        applyLight(player, env);
        SigillumComboState.registerLightShield(player, Math.round(LIGHT_TICKS * env.durationMultiplier()));
    }

    private static void applyPurifySlash(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyPurifySelf(player, env);
        if (target.getType().is(EntityTypeTags.UNDEAD)) {
            applyHolyPulse(player.serverLevel(), target, 4.0f * env.multiplier());
            removeOneBeneficial(target);
        }
        applySlash(player, target, env);
    }

    private static void applyPurifyLight(ServerPlayer player, CastEnv env) {
        applyPurifySelf(player, env);
        ServerLevel level = player.serverLevel();
        AABB box = new AABB(player.blockPosition()).inflate(LIGHT_RADIUS);
        int ticks = Math.round(LIGHT_TICKS * env.durationMultiplier());
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy)) {
            if (entity.getType().is(EntityTypeTags.UNDEAD) || entity.hasEffect(MobEffects.INVISIBILITY)
                    || entity.getActiveEffects().stream().anyMatch(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)) {
                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0));
    }

    private static void applySlashLight(ServerPlayer player, LivingEntity target, CastEnv env) {
        applyLight(player, env);
        List<LivingEntity> candidates = new ArrayList<>();
        candidates.add(target);
        AABB box = new AABB(player.blockPosition()).inflate(LIGHT_RADIUS);
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e != target && e.isAlive() && e instanceof Enemy && e.hasEffect(MobEffects.GLOWING))) {
            candidates.add(entity);
        }
        for (LivingEntity candidate : candidates) {
            if (candidate.isAlive() && candidate.getHealth() / candidate.getMaxHealth() <= slashThreshold(env)) {
                applySlash(player, candidate, env);
                return;
            }
        }
        if (candidates.size() >= 5) {
            SigillumAdvancementTriggers.specialEffect(player, SigillumCriterionTrigger.Event.empty()
                .withSpecial("slash_mark_miss_all")
                .withCount(candidates.size()));
        }
    }

    private static void applyFire(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        float dps = (undead ? 3.0f : 2.0f) * env.multiplier();
        int seconds = fireSeconds(env);
        SigillumEffectTicker.scheduleBurn(level, target, dps, seconds);
        level.sendParticles(ParticleTypes.FLAME,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            24, 0.25, 0.4, 0.25, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    private static void applyThunder(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        float damage = 12.0f * env.multiplier() * (level.isRainingAt(target.blockPosition()) ? 1.3f : 1.0f);
        triggerLightningTransmutation(player, target);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setCause(player);
            level.addFreshEntity(bolt);
        }
        int[] delay = {1};
        SigillumEffectTicker.add(() -> {
            if (delay[0]-- > 0) return false;
            if (!target.isAlive() || target.level() != level) return true;
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().lightningBolt(), damage);
            return true;
        });
        if (level.random.nextFloat() < 0.5f) {
            int stun = Math.round(10.0f * env.durationMultiplier());
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 1));
        }
    }

    private static void applyShield(ServerPlayer player, CastEnv env) {
        if (env.suppressSupport()) return;
        ServerLevel level = player.serverLevel();
        float amount = shieldAmount(env.multiplier());
        if (env.wideSupport()) {
            distributeShieldSupport(player, amount);
        } else {
            SigillumShieldManager.grant(player, amount);
            level.sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.4, 0.6, 0.4, 0.6);
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.3f);
        }
    }

    public static float shieldAmount(float m) {
        float base = m >= 0.95f ? EXQUISITE_SHIELD : (m >= 0.75f ? FINE_SHIELD : INFERIOR_SHIELD);
        return base * Math.max(1.0f, m);
    }

    private static int distributeShieldSupport(ServerPlayer player, float amount) {
        float remaining = amount;
        int affected = 0;
        float before = remaining;
        remaining = SigillumShieldManager.grantWithOverflow(player, remaining);
        if (remaining < before) {
            affected++;
            supportShieldParticles(player);
        }
        for (ServerPlayer ally : nearbySupportPlayers(player)) {
            if (remaining <= 0.0f) break;
            before = remaining;
            remaining = SigillumShieldManager.grantWithOverflow(ally, remaining);
            if (remaining < before) {
                affected++;
                supportShieldParticles(ally);
            }
        }
        if (affected > 0) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.3f);
        }
        if (affected > 1) {
            SigillumAdvancementTriggers.shieldEvent(player, SigillumCriterionTrigger.Event.empty()
                .withType("shared")
                .withShared(true)
                .withCount(affected));
        }
        return affected;
    }

    private static int distributePurifySupport(ServerPlayer player, int cleanseCount) {
        int remaining = cleanseCount;
        int affected = 0;
        int removed = cleanseHarmful(player, remaining);
        if (removed > 0) {
            remaining -= removed;
            affected++;
            supportPurifyParticles(player);
        }
        for (ServerPlayer ally : nearbySupportPlayers(player)) {
            if (remaining <= 0) break;
            removed = cleanseHarmful(ally, remaining);
            if (removed > 0) {
                remaining -= removed;
                affected++;
                supportPurifyParticles(ally);
            }
        }
        if (affected > 0) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        }
        if (affected > 1) {
            SigillumAdvancementTriggers.shieldEvent(player, SigillumCriterionTrigger.Event.empty()
                .withType("shared")
                .withShared(true)
                .withCount(affected));
        }
        return affected;
    }

    private static List<ServerPlayer> nearbySupportPlayers(ServerPlayer player) {
        double radiusSqr = AOE_RADIUS * AOE_RADIUS;
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer candidate : player.serverLevel().players()) {
            if (candidate == player || !candidate.isAlive() || candidate.isSpectator()) continue;
            if (player.distanceToSqr(candidate) > radiusSqr) continue;
            players.add(candidate);
        }
        players.sort(Comparator.comparingDouble(player::distanceToSqr));
        return players;
    }

    private static void supportShieldParticles(ServerPlayer player) {
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT,
            player.getX(), player.getY() + 1.0, player.getZ(),
            28, 0.35, 0.5, 0.35, 0.45);
    }

    private static void supportPurifyParticles(ServerPlayer player) {
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
            player.getX(), player.getY() + 1.0, player.getZ(),
            12, 0.3, 0.45, 0.3, 0.0);
    }

    private static void applyPurify(ServerPlayer player, CastEnv env) {
        ServerLevel level = player.serverLevel();
        if (!env.suppressSupport()) {
            if (env.wideSupport()) {
                distributePurifySupport(player, cleanseCount(env.multiplier()));
            } else {
                cleanseHarmful(player, cleanseCount(env.multiplier()));
            }
        }

        LivingEntity target = targetLiving(player);
        if (target != null && target.getType().is(EntityTypeTags.UNDEAD)) {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().magic(), 4.0f * env.multiplier());
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                16, 0.25, 0.4, 0.25, 0.01);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            player.getX(), player.getY() + 1.0, player.getZ(),
            16, 0.35, 0.5, 0.35, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
    }

    private static void applySuppress(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        int ticks = suppressTicks(target, env);
        int strongAmp = env.multiplier() > 1.0f ? 1 : 0;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, (undead ? 2 : 1) + strongAmp));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, (undead ? 1 : 0) + strongAmp));
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
            target.getX(), target.getY() + target.getBbHeight() * 0.8, target.getZ(),
            12, 0.25, 0.3, 0.25, 0.0);
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.4f);
    }

    private static void applySeal(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        int ticks = sealTicks(env);
        int strongAmp = env.multiplier() > 1.0f ? 1 : 0;
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9 + strongAmp));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 4 + strongAmp));
        if (undead) {
            int stun = ticks + Math.round(SEAL_UNDEAD_STUN_TICKS * env.durationMultiplier());
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 6 + strongAmp));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, stun, 128 + strongAmp * 32));
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL,
            target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
            14, 0.25, 0.4, 0.25, 0.01);
        level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7f, 0.8f);
    }

    private static void applyRepel(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        double strength = REPEL_STRENGTH * env.multiplier() * (undead ? 2.0 : 1.0);
        target.knockback(strength, player.getX() - target.getX(), player.getZ() - target.getZ());
        target.hurtMarked = true;
        int ticks = Math.round(REPEL_STUN_TICKS * env.durationMultiplier());
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 3));
        level.sendParticles(ParticleTypes.CLOUD,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            20, 0.2, 0.2, 0.2, 0.08);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    private static void applyLure(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        if (target instanceof Player) return;
        Vec3 toPlayer = new Vec3(
            player.getX() - target.getX(),
            (player.getY() + 0.5) - target.getY(),
            player.getZ() - target.getZ());
        double dist = toPlayer.length();
        if (dist < 0.001) return;
        double falloff = Math.max(0.25, 1.0 - dist / RANGE);
        double strength = LURE_STRENGTH * env.multiplier() * falloff;
        Vec3 pull = toPlayer.normalize().scale(strength).add(0.0, 0.3, 0.0);
        target.setDeltaMovement(target.getDeltaMovement().scale(0.2).add(pull));
        target.hurtMarked = true;
        level.sendParticles(ParticleTypes.PORTAL,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            18, 0.3, 0.4, 0.3, 0.4);
        level.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    private static void applySlash(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        float base = env.multiplier() >= 1.0f ? 0.50f : (env.multiplier() >= 0.8f ? 0.25f : 0.10f);
        float threshold = Math.min(0.75f, base * env.multiplier());
        if (target.getHealth() / target.getMaxHealth() <= threshold) {
            target.invulnerableTime = 0;
            float lethalDamage = Math.max(target.getHealth(), target.getMaxHealth()) + 1024.0f;
            target.hurt(level.damageSources().playerAttack(player), lethalDamage);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                8, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.9f);
        } else {
            level.sendParticles(ParticleTypes.SMOKE,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                10, 0.2, 0.3, 0.2, 0.02);
        }
    }

    private static void applyLight(ServerPlayer player, CastEnv env) {
        applyNightVision(player, lightTicks(env));
        applyLightArea(player, env, lightTicks(env));
    }

    private static void applyLightSelf(ServerPlayer player, CastEnv env) {
        applyNightVision(player, lightTicks(env));
    }

    private static void applyLightTarget(ServerPlayer player, LivingEntity target, CastEnv env) {
        int ticks = lightTicks(env);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
            target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
            8, 0.2, 0.35, 0.2, 0.03);
    }

    public static int applyWideLight(ServerPlayer player, CastEnv env) {
        int ticks = wideLightTicks(env);
        applyNightVision(player, ticks);
        return applyLightArea(player, env, ticks);
    }

    private static void applyNightVision(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0));
    }

    private static int applyLightArea(ServerPlayer player, CastEnv env, int ticks) {
        ServerLevel level = player.serverLevel();
        AABB box = new AABB(player.blockPosition()).inflate(LIGHT_RADIUS);
        int lit = 0;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy)) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
            lit++;
        }
        level.sendParticles(ParticleTypes.END_ROD,
            player.getX(), player.getY() + 1.0, player.getZ(),
            24 + lit * 2, 0.5, 0.6, 0.5, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.6f);
        return lit;
    }

    private static int lightTicks(CastEnv env) {
        return Math.max(20, Math.round(LIGHT_TICKS * env.durationMultiplier()));
    }

    private static int wideLightTicks(CastEnv env) {
        return Math.max(20, Math.round(LIGHT_WIDE_TICKS * env.durationMultiplier()));
    }

    private static void applyDrain(ServerPlayer player, LivingEntity target, CastEnv env) {
        drain(player, target, env);
    }

    private static float drain(ServerPlayer player, LivingEntity target, CastEnv env) {
        float amount = drainAmount(env);
        float dealt = hurtAndMeasure(player.serverLevel(), target, player.serverLevel().damageSources().magic(), amount);
        healPlayer(player, dealt);
        spawnDrainFeedback(player, target);
        return dealt;
    }

    private static float drainAmount(CastEnv env) {
        float hearts = env.multiplier() >= 1.0f ? 1.5f : (env.multiplier() >= 0.8f ? 1.0f : 0.5f);
        hearts *= Math.max(1.0f, env.multiplier());
        return hearts * 2.0f;
    }

    private static void spawnDrainFeedback(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            8, 0.25, 0.4, 0.25, 0.01);
        level.sendParticles(ParticleTypes.HEART,
            player.getX(), player.getY() + 1.0, player.getZ(),
            3, 0.3, 0.4, 0.3, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    private static void applySoulRecall(ServerPlayer player, CastEnv env) {
        SoulRecallHandler.recover(player, soulRecallRatio(env));
    }

    private static SoulRecallHandler.RecoveryResult applySoulRecallResult(ServerPlayer player, CastEnv env) {
        return SoulRecallHandler.recover(player, soulRecallRatio(env));
    }

    private static float soulRecallRatio(CastEnv env) {
        return env.multiplier() >= 1.0f ? 1.0f : (env.multiplier() >= 0.8f ? 0.75f : 0.5f);
    }

    private static void repelPulse(ServerLevel level, Vec3 center, float multiplier) {
        double radius = 4.0;
        double strength = 1.0 * Math.max(0.75f, multiplier);
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e instanceof Enemy)) {
            Vec3 delta = entity.position().subtract(center);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            if (horizontal < 0.001) continue;
            double dx = delta.x / horizontal;
            double dz = delta.z / horizontal;
            entity.setDeltaMovement(entity.getDeltaMovement().add(dx * strength, 0.2, dz * strength));
            entity.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.3, center.z, 28, radius * 0.25, 0.25, radius * 0.25, 0.08);
        level.playSound(null, BlockPos.containing(center), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.7f, 0.8f);
    }

    public static LivingEntity targetLiving(ServerPlayer player) {
        HitResult hr = ProjectileUtil.getHitResultOnViewVector(player,
            e -> e instanceof LivingEntity && e != player && e.isAlive(), RANGE);
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            return le;
        }
        return forgivingTargetLiving(player);
    }

    private static LivingEntity forgivingTargetLiving(ServerPlayer player) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 maxEnd = start.add(player.getViewVector(1.0f).scale(RANGE));
        HitResult blockLimit = ProjectileUtil.getHitResultOnViewVector(player, e -> false, RANGE);
        Vec3 end = blockLimit.getType() == HitResult.Type.MISS ? maxEnd : blockLimit.getLocation();
        double bestDistanceSqr = start.distanceToSqr(end);
        LivingEntity best = null;

        AABB sweep = new AABB(start, end).inflate(TARGET_AIM_TOLERANCE);
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class, sweep,
                e -> e != player && e.isAlive())) {
            AABB targetBox = candidate.getBoundingBox().inflate(TARGET_AIM_TOLERANCE);
            double distanceSqr;
            if (targetBox.contains(start)) {
                distanceSqr = 0.0;
            } else {
                Vec3 hit = targetBox.clip(start, end).orElse(null);
                if (hit == null) continue;
                distanceSqr = start.distanceToSqr(hit);
            }
            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                best = candidate;
            }
        }
        return best;
    }

    public static Vec3 landPoint(ServerPlayer player) {
        return ProjectileUtil.getHitResultOnViewVector(player, e -> false, RANGE).getLocation();
    }

    private static void applyFireDrain(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        float dps = (undead ? 3.0f : 2.0f) * env.multiplier();
        int seconds = fireSeconds(env);
        int[] remaining = {seconds};
        int[] ticks = {0};
        SigillumEffectTicker.add(() -> {
            if (!player.isAlive() || !target.isAlive() || target.level() != level) return true;
            ticks[0]++;
            if (ticks[0] % 20 == 0) {
                float dealt = hurtAndMeasure(level, target, level.damageSources().onFire(), dps);
                healPlayer(player, dealt);
                level.sendParticles(ParticleTypes.FLAME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    8, 0.2, 0.3, 0.2, 0.01);
                if (--remaining[0] <= 0) return true;
            }
            return false;
        });
        level.sendParticles(ParticleTypes.FLAME,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            24, 0.25, 0.4, 0.25, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    private static void applyThunderDrain(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        float damage = 12.0f * env.multiplier() * (level.isRainingAt(target.blockPosition()) ? 1.3f : 1.0f);
        triggerLightningTransmutation(player, target);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setCause(player);
            level.addFreshEntity(bolt);
        }
        int[] delay = {1};
        SigillumEffectTicker.add(() -> {
            if (delay[0]-- > 0) return false;
            if (!player.isAlive() || !target.isAlive() || target.level() != level) return true;
            float dealt = hurtAndMeasure(level, target, level.damageSources().lightningBolt(), damage);
            healPlayer(player, dealt);
            return true;
        });
        if (level.random.nextFloat() < 0.5f) {
            int stun = Math.round(10.0f * env.durationMultiplier());
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 1));
        }
    }

    private static void applySlashDrain(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        float threshold = slashThreshold(env);
        if (target.getHealth() / target.getMaxHealth() <= threshold) {
            float heal = target.getHealth();
            target.invulnerableTime = 0;
            float lethalDamage = Math.max(target.getHealth(), target.getMaxHealth()) + 1024.0f;
            if (target.hurt(level.damageSources().playerAttack(player), lethalDamage)) {
                healPlayer(player, heal);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8, 0.3, 0.3, 0.3, 0.0);
                level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.9f);
            }
            return;
        }
        float damage = target.getMaxHealth() * threshold;
        float dealt = hurtAndMeasure(level, target, level.damageSources().playerAttack(player), damage);
        healPlayer(player, dealt);
        level.sendParticles(ParticleTypes.SMOKE,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            10, 0.2, 0.3, 0.2, 0.02);
    }

    private static void applyLightDrain(ServerPlayer player, LivingEntity target, CastEnv env) {
        ServerLevel level = player.serverLevel();
        int ticks = Math.round(LIGHT_TICKS * env.durationMultiplier());
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0));
        AABB box = new AABB(player.blockPosition()).inflate(LIGHT_RADIUS);
        float lowDrain = drainAmount(env) * 0.25f;
        float remainingGroupHeal = drainAmount(env) * 3.0f;
        int lit = 0;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy)) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
            lit++;
            if (entity != target && remainingGroupHeal > 0.0f) {
                float dealt = hurtAndMeasure(level, entity, level.damageSources().magic(), Math.min(lowDrain, remainingGroupHeal));
                healPlayer(player, dealt);
                remainingGroupHeal -= dealt;
            }
        }
        drain(player, target, env);
        level.sendParticles(ParticleTypes.END_ROD,
            player.getX(), player.getY() + 1.0, player.getZ(),
            24 + lit * 2, 0.5, 0.6, 0.5, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    private static void schedulePeriodicDrain(ServerPlayer player, LivingEntity target, CastEnv env, int durationTicks, float factor) {
        int[] ticks = {0};
        int[] remaining = {Math.max(1, durationTicks)};
        SigillumEffectTicker.add(() -> {
            if (!player.isAlive() || !target.isAlive() || target.level() != player.serverLevel()) return true;
            ticks[0]++;
            remaining[0]--;
            if (ticks[0] % 20 == 0) {
                float amount = drainAmount(env) * factor;
                float dealt = hurtAndMeasure(player.serverLevel(), target, player.serverLevel().damageSources().magic(), amount);
                healPlayer(player, dealt);
                spawnDrainFeedback(player, target);
            }
            return remaining[0] <= 0;
        });
    }

    private static void scheduleMovementHeal(ServerPlayer player, LivingEntity target, Vec3 start, CastEnv env, int delayTicks) {
        int[] delay = {delayTicks};
        SigillumEffectTicker.add(() -> {
            if (delay[0]-- > 0) return false;
            if (!player.isAlive() || target.level() != player.serverLevel()) return true;
            double moved = target.position().distanceTo(start);
            float heal = Math.min(drainAmount(env), (float) moved * 0.5f * Math.max(1.0f, env.multiplier()));
            healPlayer(player, heal);
            return true;
        });
    }

    private static void scheduleMovementDrain(ServerPlayer player, LivingEntity target, Vec3 start, CastEnv env, int delayTicks) {
        int[] delay = {delayTicks};
        SigillumEffectTicker.add(() -> {
            if (delay[0]-- > 0) return false;
            if (!player.isAlive() || !target.isAlive() || target.level() != player.serverLevel()) return true;
            double moved = target.position().distanceTo(start);
            float factor = Math.min(1.0f, Math.max(0.25f, (float) moved / 4.0f));
            float dealt = hurtAndMeasure(player.serverLevel(), target, player.serverLevel().damageSources().magic(), drainAmount(env) * factor);
            healPlayer(player, dealt);
            spawnDrainFeedback(player, target);
            return true;
        });
    }

    private static void scheduleDrainToOverflowShield(ServerPlayer player, LivingEntity target, CastEnv env, int durationTicks) {
        int[] ticks = {0};
        int[] remaining = {Math.max(1, durationTicks)};
        SigillumEffectTicker.add(() -> {
            if (!player.isAlive() || !target.isAlive() || target.level() != player.serverLevel()) return true;
            ticks[0]++;
            remaining[0]--;
            if (ticks[0] % 20 == 0) {
                float dealt = hurtAndMeasure(player.serverLevel(), target, player.serverLevel().damageSources().magic(), drainAmount(env));
                healOrShieldOverflow(player, dealt);
                spawnDrainFeedback(player, target);
            }
            return remaining[0] <= 0;
        });
    }

    private static void applyPurifySelf(ServerPlayer player, CastEnv env) {
        if (env.suppressSupport()) return;
        if (env.wideSupport()) {
            distributePurifySupport(player, cleanseCount(env.multiplier()));
        } else {
            cleanseHarmful(player, cleanseCount(env.multiplier()));
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(),
                16, 0.35, 0.5, 0.35, 0.0);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        }
    }

    private static int cleanseCount(float multiplier) {
        return multiplier >= 1.0f ? 3 : (multiplier >= 0.8f ? 2 : 1);
    }

    private static int cleanseHarmful(ServerPlayer player, int maxCount) {
        int removed = 0;
        for (MobEffectInstance effect : player.getActiveEffects().stream()
                .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                .limit(maxCount)
                .toList()) {
            player.removeEffect(effect.getEffect());
            removed++;
        }
        return removed;
    }

    private static int fireSeconds(CastEnv env) {
        return Math.max(1, Math.round(4.0f * env.durationMultiplier()));
    }

    private static int suppressTicks(LivingEntity target, CastEnv env) {
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        return Math.max(1, Math.round(SUPPRESS_TICKS * env.durationMultiplier() * (undead ? 1.5f : 1.0f)));
    }

    private static int sealTicks(CastEnv env) {
        return Math.max(1, Math.round(SEAL_TICKS * env.durationMultiplier()));
    }

    private static float slashThreshold(CastEnv env) {
        float base = env.multiplier() >= 1.0f ? 0.50f : (env.multiplier() >= 0.8f ? 0.25f : 0.10f);
        return Math.min(0.75f, base * env.multiplier());
    }

    private static float hurtAndMeasure(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0f || !target.isAlive()) return 0.0f;
        float before = target.getHealth();
        target.invulnerableTime = 0;
        if (!target.hurt(source, amount)) return 0.0f;
        return Math.max(0.0f, before - Math.max(0.0f, target.getHealth()));
    }

    private static void healPlayer(ServerPlayer player, float amount) {
        if (amount > 0.0f && player.isAlive()) {
            player.heal(amount);
        }
    }

    private static void healOrShieldOverflow(ServerPlayer player, float amount) {
        if (amount <= 0.0f || !player.isAlive()) return;
        float before = player.getHealth();
        player.heal(amount);
        float healed = player.getHealth() - before;
        float overflow = amount - Math.max(0.0f, healed);
        if (overflow > 0.0f) {
            SigillumShieldManager.grantUncapped(player, overflow);
        }
    }

    private static void scheduleWhile(int ticks, BooleanSupplier alive, IntConsumer tick) {
        int[] remaining = {ticks};
        SigillumEffectTicker.add(() -> {
            if (!alive.getAsBoolean()) return true;
            remaining[0]--;
            tick.accept(ticks - remaining[0]);
            return remaining[0] <= 0;
        });
    }

    private static void scheduleDelayedSuppress(ServerPlayer player, LivingEntity target, CastEnv env, int minDelay, int maxDelay) {
        int variance = Math.max(0, maxDelay - minDelay);
        int[] delay = {minDelay + (variance > 0 ? player.serverLevel().random.nextInt(variance + 1) : 0)};
        double speedThresholdSqr = 0.05 * 0.05;
        SigillumEffectTicker.add(() -> {
            if (!player.isAlive() || !target.isAlive() || target.level() != player.serverLevel()) return true;
            Vec3 delta = target.getDeltaMovement();
            boolean stopped = delta.x * delta.x + delta.z * delta.z < speedThresholdSqr;
            if (delay[0]-- <= 0 || stopped) {
                applySuppressState(player, target, env);
                return true;
            }
            return false;
        });
    }

    private static void scheduleDelayed(ServerPlayer player, LivingEntity target, int delayTicks, Runnable action) {
        int[] delay = {Math.max(1, delayTicks)};
        SigillumEffectTicker.add(() -> {
            if (!player.isAlive() || !target.isAlive() || target.level() != player.serverLevel()) return true;
            if (delay[0]-- <= 0) {
                action.run();
                return true;
            }
            return false;
        });
    }

    private static void scheduleLanding(ServerPlayer player, LivingEntity target, int maxTicks, Runnable action) {
        int[] remaining = {Math.max(1, maxTicks)};
        SigillumEffectTicker.add(() -> {
            if (!player.isAlive() || !target.isAlive() || target.level() != player.serverLevel()) return true;
            remaining[0]--;
            if (target.onGround() || remaining[0] <= 0) {
                action.run();
                return true;
            }
            return false;
        });
    }

    private static void pullToSafeFront(ServerPlayer player, LivingEntity target, CastEnv env, double distance) {
        if (target instanceof Player) return;
        Vec3 destination = player.getEyePosition(1.0f).add(player.getViewVector(1.0f).normalize().scale(distance));
        Vec3 delta = destination.subtract(target.position());
        double length = delta.length();
        if (length < 0.001) return;
        double strength = Math.min(2.0, 0.45 * length) * Math.max(0.75f, env.multiplier());
        target.setDeltaMovement(target.getDeltaMovement().scale(0.15).add(delta.normalize().scale(strength)).add(0.0, 0.2, 0.0));
        target.hurtMarked = true;
        player.serverLevel().sendParticles(ParticleTypes.PORTAL,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            20, 0.3, 0.4, 0.3, 0.35);
    }

    private static void pullEnemiesToPoint(ServerLevel level, Vec3 center, double radius, float multiplier, LivingEntity exclude) {
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != exclude && e.isAlive() && e instanceof Enemy)) {
            Vec3 delta = center.subtract(entity.position());
            double length = delta.length();
            if (length < 0.001) continue;
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.2).add(delta.normalize().scale(0.8 * Math.max(0.75f, multiplier))));
            entity.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 0.4, center.z, 32, radius * 0.2, 0.35, radius * 0.2, 0.35);
    }

    private static void applyRepelFromCenter(ServerLevel level, Vec3 center, LivingEntity target, double strength) {
        Vec3 delta = target.position().subtract(center);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.001) return;
        target.setDeltaMovement(target.getDeltaMovement().add(delta.x / horizontal * strength, 0.2, delta.z / horizontal * strength));
        target.hurtMarked = true;
        level.sendParticles(ParticleTypes.CLOUD,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            8, 0.15, 0.15, 0.15, 0.05);
    }

    private static void burnAround(ServerPlayer player, Vec3 center, double radius, CastEnv env, LivingEntity exclude) {
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != exclude && e.isAlive() && e instanceof Enemy)) {
            SigillumEffectTicker.scheduleBurn(player.serverLevel(), entity, 1.5f * env.multiplier(), Math.max(1, fireSeconds(env) / 2));
        }
        player.serverLevel().sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.3, center.z, 28, radius * 0.2, 0.2, radius * 0.2, 0.02);
    }

    private static void burnAlongLine(ServerPlayer player, Vec3 start, Vec3 end, CastEnv env, LivingEntity exclude) {
        AABB box = new AABB(start, end).inflate(1.25);
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != exclude && e.isAlive() && e instanceof Enemy)) {
            if (new AABB(entity.blockPosition()).inflate(1.25).clip(start, end).isPresent()) {
                SigillumEffectTicker.scheduleBurn(player.serverLevel(), entity, 1.2f * env.multiplier(), Math.max(1, fireSeconds(env) / 2));
            }
        }
    }

    private static void strikeThunderAlongLine(ServerPlayer player, Vec3 start, Vec3 end, CastEnv env, LivingEntity exclude) {
        Set<LivingEntity> hit = new HashSet<>();
        AABB box = new AABB(start, end).inflate(1.35);
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != exclude && e.isAlive() && e instanceof Enemy)) {
            if (hit.add(entity) && entity.getBoundingBox().inflate(1.0).clip(start, end).isPresent()) {
                strikeThunder(player, entity, env, false);
            }
        }
    }

    private static void explodeFireBudget(ServerPlayer player, Vec3 center, int seconds, CastEnv env, LivingEntity exclude) {
        float damage = Math.max(2.0f, seconds * 1.5f * env.multiplier());
        AABB box = new AABB(center, center).inflate(4.0);
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != exclude && e.isAlive() && e instanceof Enemy)) {
            hurtAndMeasure(player.serverLevel(), entity, player.serverLevel().damageSources().magic(), damage * 0.5f);
        }
        if (exclude != null && exclude.isAlive()) {
            hurtAndMeasure(player.serverLevel(), exclude, player.serverLevel().damageSources().magic(), damage);
        }
        player.serverLevel().sendParticles(ParticleTypes.EXPLOSION,
            center.x, center.y + 0.5, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        player.serverLevel().playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private static void glowAround(ServerPlayer player, Vec3 center, double radius, int ticks) {
        AABB box = new AABB(center, center).inflate(radius);
        int lit = 0;
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy)) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
            lit++;
        }
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, center.x, center.y + 0.5, center.z, 12 + lit * 2, radius * 0.1, 0.35, radius * 0.1, 0.05);
    }

    private static void holyBurst(ServerPlayer player, Vec3 center, double radius, float damage) {
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e.getType().is(EntityTypeTags.UNDEAD))) {
            hurtAndMeasure(player.serverLevel(), entity, player.serverLevel().damageSources().magic(), damage);
        }
        player.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y + 0.5, center.z, 24, radius * 0.15, 0.3, radius * 0.15, 0.01);
    }

    private static void scheduleFireDamage(ServerPlayer player, LivingEntity target, CastEnv env, Consumer<Float> onDamage) {
        ServerLevel level = player.serverLevel();
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        float dps = (undead ? 3.0f : 2.0f) * env.multiplier();
        int seconds = fireSeconds(env);
        scheduleWhile(seconds * 20, () -> player.isAlive() && target.isAlive() && target.level() == level, tick -> {
            if (tick % 20 == 0) {
                float dealt = hurtAndMeasure(level, target, level.damageSources().onFire(), dps);
                onDamage.accept(dealt);
                level.sendParticles(ParticleTypes.FLAME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    8, 0.2, 0.3, 0.2, 0.01);
            }
        });
        level.sendParticles(ParticleTypes.FLAME,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            24, 0.25, 0.4, 0.25, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    private static void strikeThunderMeasured(ServerPlayer player, LivingEntity target, CastEnv env, Consumer<Float> onDamage) {
        ServerLevel level = player.serverLevel();
        float damage = 12.0f * env.multiplier() * (level.isRainingAt(target.blockPosition()) ? 1.3f : 1.0f);
        triggerLightningTransmutation(player, target);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setCause(player);
            level.addFreshEntity(bolt);
        }
        int[] delay = {1};
        SigillumEffectTicker.add(() -> {
            if (delay[0]-- > 0) return false;
            if (!target.isAlive() || target.level() != level) return true;
            float dealt = hurtAndMeasure(level, target, level.damageSources().lightningBolt(), damage);
            onDamage.accept(dealt);
            return true;
        });
    }

    private static void applySlashWithThreshold(ServerPlayer player, LivingEntity target, float threshold) {
        ServerLevel level = player.serverLevel();
        if (target.getHealth() / target.getMaxHealth() <= threshold) {
            target.invulnerableTime = 0;
            float lethalDamage = Math.max(target.getHealth(), target.getMaxHealth()) + 1024.0f;
            target.hurt(level.damageSources().playerAttack(player), lethalDamage);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                8, 0.3, 0.3, 0.3, 0.0);
        } else {
            level.sendParticles(ParticleTypes.SMOKE,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                10, 0.2, 0.3, 0.2, 0.02);
        }
    }

    private static void removeOneBeneficial(LivingEntity target) {
        for (MobEffectInstance effect : target.getActiveEffects().stream()
                .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL)
                .toList()) {
            target.removeEffect(effect.getEffect());
            return;
        }
    }

    private static int applySuppressState(ServerPlayer player, LivingEntity target, CastEnv env) {
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        int ticks = suppressTicks(target, env);
        int strongAmp = env.multiplier() > 1.0f ? 1 : 0;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, (undead ? 2 : 1) + strongAmp));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, (undead ? 1 : 0) + strongAmp));
        return ticks;
    }

    private static void applyHolyPulse(ServerLevel level, LivingEntity target, float damage) {
        if (target.getType().is(EntityTypeTags.UNDEAD)) {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().magic(), damage);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            16, 0.25, 0.4, 0.25, 0.01);
        level.playSound(null, target.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
    }

    private static LivingEntity nearestEnemy(ServerLevel level, Vec3 center, double radius, LivingEntity exclude) {
        AABB box = new AABB(center, center).inflate(radius);
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != exclude && e.isAlive() && e instanceof Enemy)) {
            double dist = entity.position().distanceToSqr(center);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private static LivingEntity nearestGlowingEnemy(ServerPlayer player, Vec3 center) {
        ServerLevel level = player.serverLevel();
        AABB box = new AABB(center, center).inflate(LIGHT_RADIUS);
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy && e.hasEffect(MobEffects.GLOWING))) {
            double dist = entity.position().distanceToSqr(center);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private static Map<Holder<MobEffect>, Integer> harmfulEffectDurations(ServerPlayer player) {
        Map<Holder<MobEffect>, Integer> result = new HashMap<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                result.put(effect.getEffect(), effect.getDuration());
            }
        }
        return result;
    }

    private static void strikeThunder(ServerPlayer player, LivingEntity target, CastEnv env, boolean guaranteedStun) {
        ServerLevel level = player.serverLevel();
        float damage = 12.0f * env.multiplier() * (level.isRainingAt(target.blockPosition()) ? 1.3f : 1.0f);
        triggerLightningTransmutation(player, target);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setCause(player);
            level.addFreshEntity(bolt);
        }
        int[] delay = {1};
        SigillumEffectTicker.add(() -> {
            if (delay[0]-- > 0) return false;
            if (!target.isAlive() || target.level() != level) return true;
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().lightningBolt(), damage);
            return true;
        });
        if (guaranteedStun || level.random.nextFloat() < 0.5f) {
            int stun = Math.round(10.0f * env.durationMultiplier());
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 1));
        }
    }

    private static void triggerLightningTransmutation(ServerPlayer player, LivingEntity target) {
        if (target.getType() == EntityType.CREEPER
            || target.getType() == EntityType.PIG
            || target.getType() == EntityType.VILLAGER) {
            SigillumAdvancementTriggers.specialEffect(player, SigillumCriterionTrigger.Event.empty()
                .withSpecial("lightning_transmutation"));
        }
    }
}
