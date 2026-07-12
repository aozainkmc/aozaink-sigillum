package com.aozainkmc.sigillum.cast;

import com.aozainkmc.sigillum.advancement.SigillumAdvancementTriggers;
import com.aozainkmc.sigillum.advancement.SigillumCriterionTrigger;
import com.aozainkmc.sigillum.event.SoulRecallHandler;
import com.aozainkmc.sigillum.network.InscriptionStatusPayload;
import com.aozainkmc.sigillum.network.InscriptionRevealPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public final class SigillumInscriptionManager {
    private static final String DATA_ID = "aozaink_sigillum_inscriptions";
    private static final int BASE_TICKS = 36_000;
    private static final int MAX_TICKS = 20 * 60 * 60 * 24;
    private static final int SHIELD_ENERGY_FACTOR = 5;
    private static final int STATUS_SYNC_BUFFER_BLOCKS = 16;
    private static final DustParticleOptions SHIELD_GOLD_PARTICLE =
        new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.22f), 0.75f);
    private static final DustParticleOptions SHIELD_CINNABAR_PARTICLE =
        new DustParticleOptions(new Vector3f(0.78f, 0.12f, 0.05f), 0.55f);

    private SigillumInscriptionManager() {}

    public record ActivationResult(boolean consume, String message) {}

    public record MenuInscription(String dimension, BlockPos pos, String name, float progress, double radius, boolean strong) {}

    public static ActivationResult activate(ServerPlayer player, BlockPos pos, List<String> skills,
            List<String> modifiers, float multiplier) {
        if (pos == null) {
            return new ActivationResult(false, "刻印符需对准方块使用");
        }
        ServerLevel level = player.serverLevel();
        if (level.getBlockState(pos).isAir()) {
            return new ActivationResult(false, "刻印符目标方块无效");
        }
        if (modifiers.contains("穿")) {
            return new ActivationResult(true, "废符");
        }

        Data data = data(level);
        Entry existing = data.entries.get(pos.asLong());
        if (skills.isEmpty()) {
            double oldRadius = existing == null ? 0.0D : existing.radius;
            ActivationResult result = applyModifierOnly(data, existing, modifiers, multiplier);
            if (result.consume() && existing != null && existing.radius > oldRadius + 0.001D) {
                broadcastReveal(level, player, existing, oldRadius);
            }
            return result;
        }
        String modifier = modifiers.isEmpty() ? "" : modifiers.get(0);
        if (existing != null) {
            return new ActivationResult(false, "此处已有刻印：" + existing.name());
        }

        int initialTicks = scaledTicks(multiplier);
        boolean strong = "强".equals(modifier);
        if ("续".equals(modifier)) {
            initialTicks = capTicks(initialTicks + scaledTicks(multiplier));
        }
        if (strong) {
            initialTicks = capTicks(initialTicks * 2);
        }

        int energyFactor = skills.contains("护") ? SHIELD_ENERGY_FACTOR : 1;
        int initialEnergy = capEnergy(initialTicks * energyFactor, energyFactor);
        double radius = radiusFor(modifier, multiplier);
        Entry entry = new Entry(pos.immutable(), player.getUUID(), List.copyOf(skills),
            multiplier, radius, initialTicks, initialEnergy, initialTicks, initialEnergy, energyFactor, strong);
        data.entries.put(pos.asLong(), entry);
        data.setDirty();
        broadcastReveal(level, player, entry, 0.0D);
        return new ActivationResult(true, "刻印 · " + entry.name() + " · 持续约" + formatDuration(initialTicks));
    }

    public static boolean hasInscription(ServerLevel level, BlockPos pos) {
        return data(level).entries.containsKey(pos.asLong());
    }

    public static List<MenuInscription> ownedInscriptions(MinecraftServer server, UUID owner, int limit) {
        if (server == null || owner == null || limit <= 0) {
            return List.of();
        }
        List<MenuInscription> result = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            for (Entry entry : data(level).entries.values()) {
                if (!owner.equals(entry.owner)) {
                    continue;
                }
                result.add(new MenuInscription(dimension, entry.pos, entry.name(), entry.progress(), entry.radius, entry.strong));
            }
        }
        result.sort(Comparator.comparing(MenuInscription::dimension).thenComparingLong(entry -> entry.pos().asLong()));
        if (result.size() > limit) {
            return List.copyOf(result.subList(0, limit));
        }
        return List.copyOf(result);
    }

    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            data(level).tick(level);
        }
    }

    private static ActivationResult applyModifierOnly(Data data, Entry existing, List<String> modifiers, float multiplier) {
        if (existing == null) {
            return new ActivationResult(false, "需要对准带刻印的方块");
        }
        boolean consumed = false;
        List<String> messages = new ArrayList<>();
        for (String modifier : modifiers) {
            ActivationResult result = applySingleModifier(data, existing, modifier, multiplier);
            consumed |= result.consume();
            messages.add(result.message());
        }
        return new ActivationResult(consumed, String.join(" / ", messages));
    }

    private static ActivationResult applySingleModifier(Data data, Entry existing, String modifier, float multiplier) {
        if ("续".equals(modifier)) {
            int addTicks = scaledTicks(multiplier);
            existing.remainingTicks = capTicks(existing.remainingTicks + addTicks);
            existing.energy = capEnergy(existing.energy + addTicks * existing.energyFactor, existing.energyFactor);
            existing.visualMaxTicks = Math.max(existing.visualMaxTicks, existing.remainingTicks);
            existing.visualMaxEnergy = Math.max(existing.visualMaxEnergy, existing.energy);
            data.setDirty();
            return new ActivationResult(true, "刻印续时 · 剩余约" + formatDuration(existing.remainingTicks));
        }
        if ("强".equals(modifier)) {
            if (existing.strong) {
                return new ActivationResult(false, "刻印已达加强上限");
            }
            existing.strong = true;
            existing.remainingTicks = capTicks(existing.remainingTicks * 2);
            existing.energy = capEnergy(existing.energy * 2, existing.energyFactor);
            existing.visualMaxTicks = Math.max(existing.visualMaxTicks, existing.remainingTicks);
            existing.visualMaxEnergy = Math.max(existing.visualMaxEnergy, existing.energy);
            data.setDirty();
            return new ActivationResult(true, "刻印加强 · " + existing.name());
        }
        if ("广".equals(modifier)) {
            double nextRadius = radiusFor("广", multiplier);
            if (existing.radius >= nextRadius) {
                return new ActivationResult(false, "刻印范围已不低于此符");
            }
            existing.radius = nextRadius;
            data.setDirty();
            return new ActivationResult(true, "刻印广域 · 半径" + formatRadius(existing.radius));
        }
        return new ActivationResult(true, "废符");
    }

    private static String formatDuration(int ticks) {
        int seconds = Math.max(1, Math.round(ticks / 20.0f));
        if (seconds < 60) {
            return seconds + "秒";
        }
        int minutes = Math.max(1, Math.round(seconds / 60.0f));
        return minutes + "分钟";
    }

    private static void broadcastReveal(ServerLevel level, ServerPlayer owner, Entry entry, double startRadius) {
        InscriptionRevealPayload payload = new InscriptionRevealPayload(
            entry.pos, owner.getUUID(), (float) startRadius, (float) entry.radius, entry.skills.contains("护")
        );
        double x = entry.pos.getX() + 0.5D;
        double y = entry.pos.getY() + 0.5D;
        double z = entry.pos.getZ() + 0.5D;
        double rangeSqr = 96.0D * 96.0D;
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(x, y, z) <= rangeSqr) PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    private static String formatRadius(double radius) {
        if (Math.abs(radius - Math.rint(radius)) < 0.001) {
            return (int)Math.rint(radius) + "格";
        }
        return String.format(Locale.ROOT, "%.1f格", radius);
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data.FACTORY, DATA_ID);
    }

    private static int scaledTicks(float multiplier) {
        return Math.max(1, Math.round(BASE_TICKS * Math.max(0.0f, multiplier)));
    }

    private static int capTicks(int ticks) {
        return Math.max(0, Math.min(MAX_TICKS, ticks));
    }

    private static int capEnergy(int energy, int factor) {
        return Math.max(0, Math.min(MAX_TICKS * Math.max(1, factor), energy));
    }

    private static double radiusFor(String modifier, float multiplier) {
        double base = baseRadius(multiplier);
        return "广".equals(modifier) ? base * 2.0 : base;
    }

    private static double baseRadius(float multiplier) {
        if (multiplier >= 0.95f) return 10.0;
        if (multiplier >= 0.75f) return 8.0;
        return 5.0;
    }

    private static final class Data extends SavedData {
        private static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load);
        private final Map<Long, Entry> entries = new HashMap<>();

        private static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data data = new Data();
            ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
            for (Tag raw : list) {
                if (raw instanceof CompoundTag entryTag) {
                    Entry entry = Entry.load(entryTag);
                    if (entry != null) {
                        data.entries.put(entry.pos.asLong(), entry);
                    }
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (Entry entry : entries.values()) {
                list.add(entry.save());
            }
            tag.put("entries", list);
            return tag;
        }

        private void tick(ServerLevel level) {
            if (entries.isEmpty()) return;
            boolean changed = false;
            Iterator<Entry> iterator = entries.values().iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (!level.hasChunkAt(entry.pos)) {
                    continue;
                }
                if (level.getBlockState(entry.pos).isAir()) {
                    iterator.remove();
                    changed = true;
                    continue;
                }
                entry.remainingTicks--;
                entry.energy--;
                changed = true;
                if (entry.remainingTicks <= 0 || entry.energy <= 0) {
                    entry.triggerDepleted(level);
                    iterator.remove();
                    continue;
                }
                if (entry.blockProjectiles(level, Vec3.atCenterOf(entry.pos))) {
                    changed = true;
                }
                if (entry.tick(level)) {
                    changed = true;
                }
                if (entry.remainingTicks <= 0 || entry.energy <= 0) {
                    entry.triggerDepleted(level);
                    iterator.remove();
                }
            }
            if (changed) setDirty();
        }
    }

    private static final class Entry {
        private final BlockPos pos;
        private final UUID owner;
        private final List<String> skills;
        private final float multiplier;
        private int remainingTicks;
        private int energy;
        private int visualMaxTicks;
        private int visualMaxEnergy;
        private final int energyFactor;
        private boolean strong;
        private double radius;
        private final Map<CooldownKey, Integer> cooldowns = new HashMap<>();

        private Entry(BlockPos pos, UUID owner, List<String> skills, float multiplier, double radius,
                int remainingTicks, int energy, int visualMaxTicks, int visualMaxEnergy, int energyFactor, boolean strong) {
            this.pos = pos;
            this.owner = owner;
            this.skills = skills;
            this.multiplier = multiplier;
            this.radius = radius;
            this.remainingTicks = remainingTicks;
            this.energy = energy;
            this.visualMaxTicks = Math.max(1, visualMaxTicks);
            this.visualMaxEnergy = Math.max(1, visualMaxEnergy);
            this.energyFactor = energyFactor;
            this.strong = strong;
        }

        private String name() {
            return String.join("+", skills);
        }

        private boolean blockProjectiles(ServerLevel level, Vec3 center) {
            if (!skills.contains("护")) return false;
            AABB box = new AABB(pos).inflate(radius + 1.0);
            List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, box, Projectile::isAlive);
            boolean changed = false;
            double radiusSqr = radius * radius;
            for (Projectile projectile : projectiles) {
                if (isPlayerProjectile(projectile)) continue;
                Vec3 projectilePos = projectile.position();
                if (projectilePos.distanceToSqr(center) > radiusSqr) continue;
                projectile.discard();
                energy = Math.max(0, energy - 5);
                level.sendParticles(SHIELD_GOLD_PARTICLE, projectilePos.x, projectilePos.y, projectilePos.z,
                    12, 0.25, 0.25, 0.25, 0.02);
                level.sendParticles(SHIELD_CINNABAR_PARTICLE, projectilePos.x, projectilePos.y, projectilePos.z,
                    2, 0.08, 0.08, 0.08, 0.0);
                changed = true;
            }
            return changed;
        }

        private boolean isPlayerProjectile(Projectile projectile) {
            Entity owner = projectile.getOwner();
            return owner instanceof Player;
        }

        private boolean tick(ServerLevel level) {
            decrementCooldowns();
            boolean changed = false;
            Vec3 center = Vec3.atCenterOf(pos);
            AABB box = new AABB(pos).inflate(radius + 1.0);
            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive);
            Set<UUID> present = new HashSet<>();
            float power = multiplier * (strong ? 2.0f : 1.0f);
            for (LivingEntity entity : entities) {
                if (entity.getBoundingBox().distanceToSqr(center) > radius * radius) continue;
                present.add(entity.getUUID());
                if (skills.contains("护") && entity instanceof Enemy) {
                    wardBoundary(entity, center);
                }
                if (skills.contains("引")) {
                    continuousLure(entity, center, power);
                }
                for (String skill : skills) {
                    if (energy <= 0) return true;
                    CooldownKey key = new CooldownKey(entity.getUUID(), skill);
                    if (cooldowns.getOrDefault(key, 0) > 0) continue;
                    int cooldown = cooldown(skill, entity);
                    if (apply(level, entity, skill, center)) {
                        energy = Math.max(0, energy - energyCost(skill, entity, center));
                        cooldowns.put(key, cooldown);
                        changed = true;
                    }
                }
            }
            cooldowns.keySet().removeIf(key -> !present.contains(key.entityId()));
            if (level.getGameTime() % 10L == 0L) {
                draw(level, center);
                syncStatus(level, center);
            }
            return changed;
        }

        private void decrementCooldowns() {
            Iterator<Map.Entry<CooldownKey, Integer>> iterator = cooldowns.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<CooldownKey, Integer> entry = iterator.next();
                int next = entry.getValue() - 1;
                if (next <= 0) {
                    iterator.remove();
                } else {
                    entry.setValue(next);
                }
            }
        }

        private boolean apply(ServerLevel level, LivingEntity entity, String skill, Vec3 center) {
            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
            float power = multiplier * (strong ? 2.0f : 1.0f);
            if (entity instanceof Player && !("护".equals(skill) || "净".equals(skill) || "明".equals(skill) || "魄".equals(skill))) {
                return false;
            }
            return switch (skill) {
                case "镇" -> suppress(entity, power);
                case "封" -> seal(entity, power);
                case "退" -> repel(entity, center, power);
                case "引" -> lure(entity, center, power);
                case "火" -> burn(level, ownerPlayer, entity, power);
                case "雷" -> thunder(level, ownerPlayer, entity, power);
                case "护" -> ward(level, ownerPlayer, entity, center, power);
                case "净" -> purify(level, ownerPlayer, entity, power);
                case "斩" -> slash(level, ownerPlayer, entity, power);
                case "明" -> reveal(entity, power);
                case "吸" -> drain(level, ownerPlayer, entity, power);
                case "魄" -> soulRecall(ownerPlayer, entity, power);
                default -> false;
            };
        }

        private boolean suppress(LivingEntity target, float power) {
            int amp = power >= 1.5f ? 2 : 1;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, amp));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, Math.max(0, amp - 1)));
            return true;
        }

        private boolean seal(LivingEntity target, float power) {
            int amp = power >= 1.5f ? 3 : 2;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, amp));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 2));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 25, 1));
            return true;
        }

        private boolean lure(LivingEntity target, Vec3 center, float power) {
            if (target instanceof Player) return false;
            return applyHorizontalForce(target, center, false, 0.18 * Math.max(1.0f, power), 0.70 * Math.max(1.0f, power));
        }

        private void continuousLure(LivingEntity target, Vec3 center, float power) {
            if (target instanceof Player) return;
            applyHorizontalForce(target, center, false, 0.045 * Math.max(1.0f, power), 0.58 * Math.max(1.0f, power));
        }

        private boolean repel(LivingEntity target, Vec3 center, float power) {
            if (target instanceof Player) return false;
            Vec3 delta = target.position().subtract(center);
            Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
            if (horizontal.lengthSqr() < 0.0001) {
                horizontal = new Vec3(1.0, 0.0, 0.0);
            }
            Vec3 outward = horizontal.normalize();
            double strength = 1.25 * Math.max(0.5, power);
            target.setDeltaMovement(target.getDeltaMovement().add(outward.x * strength, 0.25, outward.z * strength));
            target.hurtMarked = true;
            return true;
        }

        private boolean applyHorizontalForce(LivingEntity target, Vec3 center, boolean outward, double force, double maxSpeed) {
            Vec3 delta = target.position().subtract(center);
            Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
            if (horizontal.lengthSqr() < 0.0001) {
                horizontal = new Vec3(1.0, 0.0, 0.0);
            }
            Vec3 direction = horizontal.normalize();
            if (!outward) {
                direction = direction.scale(-1.0);
            }
            Vec3 motion = target.getDeltaMovement().add(direction.scale(force));
            Vec3 planar = new Vec3(motion.x, 0.0, motion.z);
            double speed = planar.length();
            if (speed > maxSpeed) {
                Vec3 capped = planar.normalize().scale(maxSpeed);
                motion = new Vec3(capped.x, motion.y, capped.z);
            }
            target.setDeltaMovement(motion);
            target.hurtMarked = true;
            return true;
        }

        private boolean burn(ServerLevel level, ServerPlayer ownerPlayer, LivingEntity target, float power) {
            boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
            float dps = (undead ? 3.0f : 2.0f) * power;
            SigillumEffectTicker.scheduleBurn(level, target, ownerPlayer, dps, 4);
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5,
                target.getZ(), 10, 0.2, 0.3, 0.2, 0.01);
            return true;
        }

        private boolean thunder(ServerLevel level, ServerPlayer ownerPlayer, LivingEntity target, float power) {
            if (ownerPlayer != null && (target.getType() == EntityType.CREEPER
                    || target.getType() == EntityType.PIG
                    || target.getType() == EntityType.VILLAGER)) {
                SigillumAdvancementTriggers.specialEffect(ownerPlayer, SigillumCriterionTrigger.Event.empty()
                    .withSpecial("lightning_transmutation"));
            }
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                if (ownerPlayer != null) bolt.setCause(ownerPlayer);
                level.addFreshEntity(bolt);
            }
            float damage = 12.0f * power * (level.isRainingAt(target.blockPosition()) ? 1.3f : 1.0f);
            int[] delay = {1};
            SigillumEffectTicker.add(() -> {
                if (delay[0]-- > 0) return false;
                if (!target.isAlive() || target.level() != level) return true;
                target.invulnerableTime = 0;
                hurtWithOwner(level, ownerPlayer, target, damage);
                return true;
            });
            return true;
        }

        private boolean ward(ServerLevel level, ServerPlayer ownerPlayer, LivingEntity entity, Vec3 center, float power) {
            if (entity instanceof ServerPlayer player) {
                SigillumShieldManager.grant(player, SkillCast.shieldAmount(power));
                return true;
            }
            if (!(entity instanceof Enemy)) return false;
            entity.invulnerableTime = 0;
            hurtWithOwner(level, ownerPlayer, entity, wardDamage(entity, center, power));
            drawWardRipple(level, center, entity);
            return true;
        }

        private void wardBoundary(LivingEntity target, Vec3 center) {
            Vec3 entityCenter = target.getBoundingBox().getCenter();
            Vec3 horizontal = new Vec3(entityCenter.x - center.x, 0.0, entityCenter.z - center.z);
            double distance = horizontal.length();
            if (horizontal.lengthSqr() < 0.0001) {
                horizontal = new Vec3(1.0, 0.0, 0.0);
                distance = 0.0;
            }
            Vec3 outward = horizontal.normalize();
            double wallRadius = radius + Math.max(0.45, target.getBbWidth() * 0.5);
            double boundaryBand = Math.max(1.25, target.getBbWidth() + 0.75);
            boolean onBoundary = distance >= radius - boundaryBand;

            Vec3 motion = target.getDeltaMovement();
            if (onBoundary) {
                if (distance < wallRadius) {
                    double x = center.x + outward.x * wallRadius;
                    double z = center.z + outward.z * wallRadius;
                    target.teleportTo(x, target.getY(), z);
                }
                double radial = motion.x * outward.x + motion.z * outward.z;
                Vec3 radialMotion = outward.scale(Math.max(0.0, radial));
                Vec3 planarMotion = new Vec3(motion.x, 0.0, motion.z);
                Vec3 tangential = planarMotion.subtract(outward.scale(radial));
                motion = new Vec3(
                    radialMotion.x + tangential.x * 0.35,
                    motion.y,
                    radialMotion.z + tangential.z * 0.35
                );
            } else {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 6, false, false));
                motion = Vec3.ZERO;
            }
            target.setDeltaMovement(motion);
            target.hurtMarked = true;
        }

        private float wardDamage(LivingEntity target, Vec3 center, float power) {
            double distance = target.getBoundingBox().getCenter().distanceTo(center);
            double proximity = 1.0 - Math.min(1.0, distance / Math.max(1.0, radius));
            double pressure = 1.0 + proximity;
            return (float)(3.0 * Math.max(1.0f, power) * pressure);
        }

        private int energyCost(String skill, LivingEntity entity, Vec3 center) {
            if (!"护".equals(skill) || !(entity instanceof Enemy)) return 1;
            double distance = entity.getBoundingBox().getCenter().distanceTo(center);
            double proximity = 1.0 - Math.min(1.0, distance / Math.max(1.0, radius));
            return proximity >= 0.5 ? 2 : 1;
        }

        private void drawWardRipple(ServerLevel level, Vec3 center, LivingEntity target) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(center);
            if (delta.lengthSqr() < 0.0001) {
                delta = new Vec3(1.0, 0.0, 0.0);
            }
            Vec3 normal = delta.normalize();
            Vec3 surface = center.add(normal.scale(radius));
            Vec3 tangentA = new Vec3(-normal.z, 0.0, normal.x);
            if (tangentA.lengthSqr() < 0.0001) {
                tangentA = new Vec3(1.0, 0.0, 0.0);
            } else {
                tangentA = tangentA.normalize();
            }
            Vec3 tangentB = new Vec3(
                normal.y * tangentA.z - normal.z * tangentA.y,
                normal.z * tangentA.x - normal.x * tangentA.z,
                normal.x * tangentA.y - normal.y * tangentA.x
            ).normalize();
            for (int ring = 0; ring < 2; ring++) {
                double ringRadius = 0.45 + ring * 0.35;
                int points = ring == 0 ? 10 : 16;
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    Vec3 point = surface
                        .add(tangentA.scale(Math.cos(angle) * ringRadius))
                        .add(tangentB.scale(Math.sin(angle) * ringRadius));
                    level.sendParticles(ring == 0 ? SHIELD_GOLD_PARTICLE : SHIELD_CINNABAR_PARTICLE,
                        point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }

        private boolean purify(ServerLevel level, ServerPlayer ownerPlayer, LivingEntity entity, float power) {
            boolean applied = false;
            if (entity instanceof ServerPlayer player) {
                int cleanse = power >= 1.0f ? 3 : (power >= 0.8f ? 2 : 1);
                for (MobEffectInstance effect : player.getActiveEffects().stream()
                        .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                        .limit(cleanse)
                        .toList()) {
                    player.removeEffect(effect.getEffect());
                    applied = true;
                }
            }
            if (entity.getType().is(EntityTypeTags.UNDEAD)) {
                entity.invulnerableTime = 0;
                hurtWithOwner(level, ownerPlayer, entity, 4.0f * power);
                applied = true;
            }
            return applied;
        }

        private boolean slash(ServerLevel level, ServerPlayer ownerPlayer, LivingEntity target, float power) {
            if (target instanceof Player) return false;
            float base = power >= 1.0f ? 0.50f : (power >= 0.8f ? 0.25f : 0.10f);
            float threshold = Math.min(0.75f, base * power);
            if (target.getHealth() / target.getMaxHealth() > threshold) return false;
            target.invulnerableTime = 0;
            float lethalDamage = Math.max(target.getHealth(), target.getMaxHealth()) + 1024.0f;
            if (ownerPlayer != null) {
                target.setLastHurtByPlayer(ownerPlayer);
                target.hurt(level.damageSources().playerAttack(ownerPlayer), lethalDamage);
            } else {
                target.hurt(level.damageSources().magic(), lethalDamage);
            }
            return true;
        }

        private boolean reveal(LivingEntity entity, float power) {
            int ticks = Math.round(600 * Math.max(1.0f, power));
            if (entity instanceof ServerPlayer player) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0));
                return true;
            }
            if (entity instanceof Enemy) {
                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
                return true;
            }
            return false;
        }

        private boolean drain(ServerLevel level, ServerPlayer ownerPlayer, LivingEntity target, float power) {
            if (target instanceof Player) return false;
            float hearts = power >= 1.0f ? 1.5f : (power >= 0.8f ? 1.0f : 0.5f);
            float amount = hearts * 2.0f * Math.max(1.0f, power);
            target.invulnerableTime = 0;
            hurtWithOwner(level, ownerPlayer, target, amount);
            if (ownerPlayer != null && ownerPlayer.isAlive()) {
                ownerPlayer.heal(amount);
            }
            return true;
        }

        private void hurtWithOwner(ServerLevel level, ServerPlayer ownerPlayer, LivingEntity target, float amount) {
            if (ownerPlayer != null) {
                target.setLastHurtByPlayer(ownerPlayer);
                target.hurt(level.damageSources().indirectMagic(ownerPlayer, ownerPlayer), amount);
            } else {
                target.hurt(level.damageSources().magic(), amount);
            }
        }

        private boolean soulRecall(ServerPlayer ownerPlayer, LivingEntity entity, float power) {
            if (ownerPlayer == null || entity != ownerPlayer) return false;
            float ratio = power >= 1.0f ? 1.0f : (power >= 0.8f ? 0.75f : 0.5f);
            SoulRecallHandler.recover(ownerPlayer, ratio);
            return true;
        }

        private void triggerDepleted(ServerLevel level) {
            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
                SigillumAdvancementTriggers.inscriptionChanged(ownerPlayer, SigillumCriterionTrigger.Event.empty()
                    .withType("depleted"));
            }
        }

        private int cooldown(String skill, LivingEntity entity) {
            if ("护".equals(skill) && entity instanceof Enemy) return 20;
            return switch (skill) {
                case "镇", "封", "吸", "净" -> 40;
                case "退" -> 10;
                case "护" -> 20;
                case "引" -> 15;
                case "火" -> 80;
                case "雷" -> 100;
                case "斩" -> 20;
                case "明" -> 100;
                case "魄" -> 20 * 60;
                default -> 40;
            };
        }

        private void draw(ServerLevel level, Vec3 center) {
            if (skills.contains("护")) {
                drawShieldGrid(level, center);
            }
        }

        private void drawShieldGrid(ServerLevel level, Vec3 anchor) {
            for (int i = 0; i < 3; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double height = anchor.y + (level.random.nextDouble() * 2.0 - 1.0) * radius;
                double shell = radius * Math.sqrt(level.random.nextDouble());
                double x = anchor.x + Math.cos(angle) * shell;
                double z = anchor.z + Math.sin(angle) * shell;
                level.sendParticles(level.random.nextInt(5) == 0 ? SHIELD_CINNABAR_PARTICLE : SHIELD_GOLD_PARTICLE,
                    x, height, z, 1, 0.0, -0.02, 0.0, 0.0);
            }
        }

        private void syncStatus(ServerLevel level, Vec3 center) {
            InscriptionStatusPayload payload = new InscriptionStatusPayload(
                List.of(new InscriptionStatusPayload.Entry(pos.asLong(), progress(), (float) radius, statusStyle()))
            );
            double maxDistanceSqr = statusSyncDistanceSqr(level);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(center) <= maxDistanceSqr) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }

        private int statusStyle() {
            if (skills.contains("护")) {
                return InscriptionStatusPayload.Entry.STYLE_WARD;
            }
            if (skills.contains("净") || skills.contains("明") || skills.contains("魄")) {
                return InscriptionStatusPayload.Entry.STYLE_SELF;
            }
            return InscriptionStatusPayload.Entry.STYLE_HOSTILE;
        }

        private static double statusSyncDistanceSqr(ServerLevel level) {
            int viewChunks = level.getServer().getPlayerList().getViewDistance();
            int simulationChunks = level.getServer().getPlayerList().getSimulationDistance();
            int chunks = Math.max(2, Math.min(viewChunks, simulationChunks));
            double blocks = chunks * 16.0 + STATUS_SYNC_BUFFER_BLOCKS;
            return blocks * blocks;
        }

        private float progress() {
            double timeRatio = visualMaxTicks <= 0 ? 0.0 : (double) remainingTicks / visualMaxTicks;
            double energyRatio = visualMaxEnergy <= 0 ? 0.0 : (double) energy / visualMaxEnergy;
            return (float)Math.max(0.0, Math.min(1.0, Math.min(timeRatio, energyRatio)));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", pos.asLong());
            tag.putString("owner", owner.toString());
            ListTag skillTags = new ListTag();
            for (String skill : skills) {
                skillTags.add(StringTag.valueOf(skill));
            }
            tag.put("skills", skillTags);
            tag.putFloat("multiplier", multiplier);
            tag.putInt("remainingTicks", remainingTicks);
            tag.putInt("energy", energy);
            tag.putInt("visualMaxTicks", visualMaxTicks);
            tag.putInt("visualMaxEnergy", visualMaxEnergy);
            tag.putInt("energyFactor", energyFactor);
            tag.putBoolean("strong", strong);
            tag.putDouble("radius", radius);
            return tag;
        }

        private static Entry load(CompoundTag tag) {
            try {
                BlockPos pos = BlockPos.of(tag.getLong("pos"));
                UUID owner = UUID.fromString(tag.getString("owner"));
                List<String> skills = new ArrayList<>();
                ListTag skillTags = tag.getList("skills", Tag.TAG_STRING);
                for (Tag skillTag : skillTags) {
                    skills.add(skillTag.getAsString());
                }
                if (skills.isEmpty()) return null;
                float multiplier = tag.getFloat("multiplier");
                int remainingTicks = tag.getInt("remainingTicks");
                int energy = tag.getInt("energy");
                int visualMaxTicks = tag.contains("visualMaxTicks") ? tag.getInt("visualMaxTicks") : remainingTicks;
                int visualMaxEnergy = tag.contains("visualMaxEnergy") ? tag.getInt("visualMaxEnergy") : energy;
                int energyFactor = Math.max(1, tag.getInt("energyFactor"));
                boolean strong = tag.getBoolean("strong");
                double radius = tag.getDouble("radius");
                return new Entry(pos, owner, List.copyOf(skills), multiplier, radius,
                    remainingTicks, energy, visualMaxTicks, visualMaxEnergy, energyFactor, strong);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private record CooldownKey(UUID entityId, String skill) {}
}
