package com.aozainkmc.sigillum.cast;

import com.aozainkmc.sigillum.SigillumMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class SigillumEffectTicker {

    public interface Task {
        boolean tick();
    }

    private static final List<Task> TASKS = new CopyOnWriteArrayList<>();

    private SigillumEffectTicker() {}

    public static void add(Task task) {
        TASKS.add(Objects.requireNonNull(task, "task"));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickTasks();
        SigillumShieldManager.tick(event.getServer());
        SigillumInscriptionManager.tick(event.getServer());
    }

    /**
     * Runs only the tasks that existed at the beginning of this server tick.
     * Tasks scheduled by a callback start on the next tick instead of mutating
     * the collection while it is being traversed.
     */
    static void tickTasks() {
        for (Task task : new ArrayList<>(TASKS)) {
            try {
                if (task.tick()) {
                    TASKS.remove(task);
                }
            } catch (RuntimeException exception) {
                TASKS.remove(task);
                SigillumMod.LOGGER.error("Discarding failed Sigillum effect task", exception);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
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
