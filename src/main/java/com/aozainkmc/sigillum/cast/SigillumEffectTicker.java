package com.aozainkmc.sigillum.cast;

import com.aozainkmc.sigillum.SigillumMod;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class SigillumEffectTicker {

    public interface Task {
        boolean tick();
    }

    private static final List<Task> TASKS = new CopyOnWriteArrayList<>();

    private SigillumEffectTicker() {}

    public static void add(Task task) {
        TASKS.add(task);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!TASKS.isEmpty()) {
            TASKS.removeIf(Task::tick);
        }
        SigillumShieldManager.tick(event.getServer());
        SigillumInscriptionManager.tick(event.getServer());
    }

    public static void scheduleBurn(ServerLevel level, LivingEntity target, float dmgPerSecond, int seconds) {
        scheduleBurn(level, target, null, dmgPerSecond, seconds);
    }

    public static void scheduleBurn(ServerLevel level, LivingEntity target, ServerPlayer ownerPlayer, float dmgPerSecond, int seconds) {
        int[] remaining = {seconds};
        int[] ticks = {0};
        add(() -> {
            if (!target.isAlive() || target.level() != level) return true;
            ticks[0]++;
            if (ticks[0] % 20 == 0) {
                if (ownerPlayer != null) {
                    target.setLastHurtByPlayer(ownerPlayer);
                }
                target.hurt(ownerPlayer == null
                    ? level.damageSources().onFire()
                    : level.damageSources().indirectMagic(ownerPlayer, ownerPlayer), dmgPerSecond);
                level.sendParticles(ParticleTypes.FLAME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    8, 0.2, 0.3, 0.2, 0.01);
                if (--remaining[0] <= 0) return true;
            }
            return false;
        });
    }

}
