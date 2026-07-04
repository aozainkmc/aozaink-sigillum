package com.aozainkmc.sigillum.cast;

import com.aozainkmc.sigillum.advancement.SigillumAdvancementTriggers;
import com.aozainkmc.sigillum.advancement.SigillumCriterionTrigger;
import com.aozainkmc.sigillum.event.SoulRecallHandler;
import com.aozainkmc.sigillum.network.InscriptionStatusPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
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
    private static final DustParticleOptions SHIELD_GRID_PARTICLE =
        new DustParticleOptions(new Vector3f(0.12f, 0.72f, 1.0f), 0.8f);

    private SigillumInscriptionManager() {}

    public record ActivationResult(boolean consume, String message) {}

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
            return applyModifierOnly(data, existing, modifiers, multiplier);
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
        return new ActivationResult(true, "刻印 · " + entry.name() + " · " + initialTicks + "tick");
    }

    public static boolean hasInscription(ServerLevel level, BlockPos pos) {
        return data(level).entries.containsKey(pos.asLong());
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
            return new ActivationResult(true, "刻印续时 · " + existing.remainingTicks + "tick");
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
            return new ActivationResult(true, "刻印广域 · 半径" + String.format("%.1f", existing.radius));
        }
        return new ActivationResult(true, "废符");
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

        private boolean tick(ServerLevel level) {
            decrementCooldowns();
            boolean changed = false;
            Vec3 center = Vec3.atCenterOf(pos);
            AABB box = new AABB(pos).inflate(radius + 1.0);
            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive);
            Set<UUID> present = new HashSet<>();
            for (LivingEntity entity : entities) {
                if (entity.getBoundingBox().distanceToSqr(center) > radius * radius) continue;
                present.add(entity.getUUID());
                for (String skill : skills) {
                    if (energy <= 0) return true;
                    CooldownKey key = new CooldownKey(entity.getUUID(), skill);
                    if (cooldowns.getOrDefault(key, 0) > 0) continue;
                    int cooldown = cooldown(skill, entity);
                    if (apply(level, entity, skill, center)) {
                        energy--;
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
                case "退" -> repelAtEdge(entity, center, power, 1.25, false);
                case "引" -> lure(entity, center, power);
                case "火" -> burn(level, entity, power);
                case "雷" -> thunder(level, ownerPlayer, entity, power);
                case "护" -> ward(level, ownerPlayer, entity, center, power);
                case "净" -> purify(level, entity, power);
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

        private boolean repelAtEdge(LivingEntity target, Vec3 center, float power, double strengthBase, boolean strictBarrier) {
            Vec3 delta = target.position().subtract(center);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double edge = strictBarrier ? radius - 0.35 : radius - 0.9;
            if (horizontal < edge) return false;
            double strength = strengthBase * Math.max(0.5, power);
            double dx = horizontal < 0.001 ? 0.0 : delta.x / horizontal;
            double dz = horizontal < 0.001 ? 0.0 : delta.z / horizontal;
            target.setDeltaMovement(target.getDeltaMovement().add(dx * strength, 0.25, dz * strength));
            target.hurtMarked = true;
            return true;
        }

        private boolean lure(LivingEntity target, Vec3 center, float power) {
            if (target instanceof Player) return false;
            Vec3 delta = center.subtract(target.position());
            double length = delta.length();
            if (length < 0.001) return false;
            target.setDeltaMovement(target.getDeltaMovement().scale(0.4).add(delta.normalize().scale(0.35 * power)));
            target.hurtMarked = true;
            return true;
        }

        private boolean burn(ServerLevel level, LivingEntity target, float power) {
            boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
            float dps = (undead ? 3.0f : 2.0f) * power;
            SigillumEffectTicker.scheduleBurn(level, target, dps, 4);
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
                target.hurt(level.damageSources().lightningBolt(), damage);
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
            wardBarrier(entity, center, power);
            entity.invulnerableTime = 0;
            entity.hurt(level.damageSources().magic(), 3.0f * Math.max(1.0f, power));
            return true;
        }

        private void wardBarrier(LivingEntity target, Vec3 center, float power) {
            Vec3 entityCenter = target.getBoundingBox().getCenter();
            Vec3 horizontal = new Vec3(entityCenter.x - center.x, 0.0, entityCenter.z - center.z);
            if (horizontal.lengthSqr() < 0.0001) {
                horizontal = new Vec3(1.0, 0.0, 0.0);
            }
            Vec3 outward = horizontal.normalize();
            double targetRadius = radius + Math.max(0.9, target.getBbWidth() * 0.5 + 0.5);
            double x = center.x + outward.x * targetRadius;
            double z = center.z + outward.z * targetRadius;
            target.teleportTo(x, target.getY(), z);
            target.setDeltaMovement(outward.scale(1.2 * Math.max(1.0f, power)).add(0.0, 0.18, 0.0));
            target.hurtMarked = true;
        }

        private boolean purify(ServerLevel level, LivingEntity entity, float power) {
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
                entity.hurt(level.damageSources().magic(), 4.0f * power);
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
                target.hurt(level.damageSources().playerAttack(ownerPlayer), lethalDamage);
            } else {
                target.hurt(level.damageSources().magic(), lethalDamage);
            }
            return true;
        }

        private boolean reveal(LivingEntity entity, float power) {
            int ticks = Math.round(200 * Math.max(1.0f, power));
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
            target.hurt(level.damageSources().magic(), amount);
            if (ownerPlayer != null && ownerPlayer.isAlive()) {
                ownerPlayer.heal(amount);
            }
            return true;
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
            if ("护".equals(skill) && entity instanceof Enemy) return 1;
            return switch (skill) {
                case "镇", "封", "吸", "净" -> 40;
                case "退", "护" -> 20;
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
                return;
            }
            drawGroundCircle(level, center);
        }

        private void drawGroundCircle(ServerLevel level, Vec3 center) {
            int points = Math.max(32, (int)Math.round(radius * 10.0));
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0 * i) / points;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.WITCH, x, center.y + 0.2, z, 1, 0.0, 0.03, 0.0, 0.0);
            }
            level.sendParticles(ParticleTypes.WITCH, center.x, center.y + 0.7, center.z,
                10, 0.35, 0.45, 0.35, 0.0);
        }

        private void drawShieldGrid(ServerLevel level, Vec3 anchor) {
            double verticalRadius = Math.max(3.0, Math.min(radius * 0.7, 8.0));
            double sphereY = anchor.y + verticalRadius * 0.62;
            double phase = (level.getGameTime() % 240L) * (Math.PI * 2.0 / 240.0);
            int latitudeBands = strong ? 6 : 5;
            int meridians = strong ? 10 : 8;

            for (int band = 1; band <= latitudeBands; band++) {
                double latitude = -Math.PI * 0.42 + band * (Math.PI * 0.84 / (latitudeBands + 1));
                double ringRadius = radius * Math.cos(latitude);
                double y = sphereY + verticalRadius * Math.sin(latitude);
                int samples = Math.max(8, Math.min(18, (int)Math.round(ringRadius * 1.25)));
                for (int i = 0; i < samples; i++) {
                    double angle = phase * 0.35 + (Math.PI * 2.0 * i) / samples;
                    double x = anchor.x + Math.cos(angle) * ringRadius;
                    double z = anchor.z + Math.sin(angle) * ringRadius;
                    level.sendParticles(SHIELD_GRID_PARTICLE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }

            int verticalSamples = strong ? 8 : 7;
            for (int meridian = 0; meridian < meridians; meridian++) {
                double angle = phase + (Math.PI * 2.0 * meridian) / meridians;
                for (int i = 1; i <= verticalSamples; i++) {
                    double latitude = -Math.PI * 0.46 + i * (Math.PI * 0.92 / (verticalSamples + 1));
                    double ringRadius = radius * Math.cos(latitude);
                    double x = anchor.x + Math.cos(angle) * ringRadius;
                    double y = sphereY + verticalRadius * Math.sin(latitude);
                    double z = anchor.z + Math.sin(angle) * ringRadius;
                    level.sendParticles(SHIELD_GRID_PARTICLE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }

        private void syncStatus(ServerLevel level, Vec3 center) {
            InscriptionStatusPayload payload = new InscriptionStatusPayload(
                List.of(new InscriptionStatusPayload.Entry(pos.asLong(), progress()))
            );
            double maxDistanceSqr = statusSyncDistanceSqr(level);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(center) <= maxDistanceSqr) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
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
