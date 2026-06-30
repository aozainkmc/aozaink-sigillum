package com.aozainkmc.sigillum.cast;

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

    public static float absorb(ServerPlayer player, float damage) {
        if (damage <= 0.0f) return damage;
        ShieldState state = SHIELDS.get(player.getUUID());
        if (state == null || state.amount <= 0.0f) return damage;

        float blocked = Math.min(state.amount, damage);
        float remaining = damage - blocked;
        state.amount -= blocked;
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

    public static void tick(MinecraftServer server) {
        if (SHIELDS.isEmpty()) return;
        Iterator<Map.Entry<UUID, ShieldState>> iterator = SHIELDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ShieldState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || entry.getValue().amount <= 0.0f) {
                iterator.remove();
                if (player != null) {
                    sync(player, 0.0f, 0.0f);
                }
            }
        }
    }

    public static void clear(ServerPlayer player) {
        SHIELDS.remove(player.getUUID());
        sync(player, 0.0f, 0.0f);
    }

    public static void discard(ServerPlayer player) {
        SHIELDS.remove(player.getUUID());
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
