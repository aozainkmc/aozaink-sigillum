package com.aozainkmc.sigillum.cast;

import com.aozainkmc.sigillum.advancement.SigillumAdvancementTriggers;
import com.aozainkmc.sigillum.advancement.SigillumCriterionTrigger;
import com.aozainkmc.sigillum.network.ShieldSyncPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SigillumShieldManager {
    private static final Map<UUID, ShieldState> SHIELDS = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> CONTINUOUS_BLOCK_UNTIL = new HashMap<>();

    private SigillumShieldManager() {}

    public static void grant(ServerPlayer player, float amount) {
        if (amount <= 0.0f) return;
        ShieldState current = SHIELDS.get(player.getUUID());
        float max = Math.max(amount, current == null ? amount : current.max);
        float shield = current == null ? amount : Math.min(max, current.amount + amount);
        ShieldState next = new ShieldState(shield, max);
        SHIELDS.put(player.getUUID(), next);
        sync(player, next);
    }

    public static float grantWithOverflow(ServerPlayer player, float amount) {
        if (amount <= 0.0f) return 0.0f;
        ShieldState current = SHIELDS.get(player.getUUID());
        float max = Math.max(amount, current == null ? amount : current.max);
        float before = current == null ? 0.0f : current.amount;
        float accepted = Math.min(amount, Math.max(0.0f, max - before));
        if (accepted <= 0.0f) return amount;
        ShieldState next = new ShieldState(before + accepted, max);
        SHIELDS.put(player.getUUID(), next);
        sync(player, next);
        return Math.max(0.0f, amount - accepted);
    }

    public static void grantUncapped(ServerPlayer player, float amount) {
        if (amount <= 0.0f) return;
        ShieldState current = SHIELDS.get(player.getUUID());
        float shield = current == null ? amount : current.amount + amount;
        float max = Math.max(shield, current == null ? amount : current.max);
        ShieldState next = new ShieldState(shield, max);
        SHIELDS.put(player.getUUID(), next);
        sync(player, next);
    }

    public static float absorb(ServerPlayer player, float damage) {
        if (damage <= 0.0f) return damage;
        ShieldState state = SHIELDS.get(player.getUUID());
        if (state == null || state.amount <= 0.0f) return damage;

        float blocked = Math.min(state.amount, damage);
        float remaining = damage - blocked;
        state.amount -= blocked;
        SigillumAdvancementTriggers.shieldEvent(player, SigillumCriterionTrigger.Event.empty()
            .withType("absorbed")
            .withCount(Math.max(1, Math.round(blocked))));
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT,
            player.getX(), player.getY() + 1.0, player.getZ(),
            16, 0.35, 0.45, 0.35, 0.35);
        player.serverLevel().playSound(null, player.blockPosition(),
            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.45f, 1.35f);

        if (state.amount <= 0.0f) {
            clear(player);
        } else {
            sync(player, state);
        }
        return Math.max(0.0f, remaining);
    }

    public static float absorbContinuous(ServerPlayer player, float damage, String key, int cooldownTicks) {
        if (damage <= 0.0f) return damage;
        ShieldState state = SHIELDS.get(player.getUUID());
        if (state == null || state.amount <= 0.0f) return damage;

        UUID uuid = player.getUUID();
        long now = player.serverLevel().getGameTime();
        Map<String, Long> cooldowns = CONTINUOUS_BLOCK_UNTIL.get(uuid);
        if (cooldowns != null && cooldowns.getOrDefault(key, 0L) > now) {
            return 0.0f;
        }

        float remaining = absorb(player, damage);
        if (remaining < damage && SHIELDS.containsKey(uuid)) {
            CONTINUOUS_BLOCK_UNTIL.computeIfAbsent(uuid, ignored -> new HashMap<>())
                .put(key, now + Math.max(1, cooldownTicks));
        }
        return remaining;
    }

    public static void tick(MinecraftServer server) {
        if (SHIELDS.isEmpty()) return;
        Iterator<Map.Entry<UUID, ShieldState>> iterator = SHIELDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ShieldState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || entry.getValue().amount <= 0.0f) {
                iterator.remove();
                CONTINUOUS_BLOCK_UNTIL.remove(entry.getKey());
                if (player != null) {
                    sync(player, 0.0f, 0.0f);
                }
            }
        }
    }

    public static void clear(ServerPlayer player) {
        SHIELDS.remove(player.getUUID());
        CONTINUOUS_BLOCK_UNTIL.remove(player.getUUID());
        sync(player, 0.0f, 0.0f);
    }

    public static void discard(ServerPlayer player) {
        SHIELDS.remove(player.getUUID());
        CONTINUOUS_BLOCK_UNTIL.remove(player.getUUID());
    }

    public static void sync(ServerPlayer player) {
        ShieldState state = SHIELDS.get(player.getUUID());
        if (state == null) {
            sync(player, 0.0f, 0.0f);
        } else {
            sync(player, state);
        }
    }

    private static void sync(ServerPlayer player, ShieldState state) {
        sync(player, state.amount, state.max);
    }

    private static void sync(ServerPlayer player, float amount, float max) {
        PacketDistributor.sendToPlayer(player, new ShieldSyncPayload(amount, max));
    }

    private static final class ShieldState {
        private float amount;
        private final float max;

        private ShieldState(float amount, float max) {
            this.amount = amount;
            this.max = max;
        }
    }
}
