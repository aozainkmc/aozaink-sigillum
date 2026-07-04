package com.aozainkmc.sigillum.event;

import com.aozainkmc.sigillum.advancement.SigillumAdvancementTriggers;
import com.aozainkmc.sigillum.SigillumMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class SoulRecallHandler {

    private static final long WINDOW_TICKS = 20L * 60L * 10L;
    private static final double RECOVERY_RADIUS = 48.0;
    private static final float XP_RESERVE_RATIO = 0.5F;
    private static final Map<UUID, SoulRecovery> RECOVERIES = new HashMap<>();

    private SoulRecallHandler() {}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getDrops().isEmpty()) {
            return;
        }
        long gameTime = player.level().getGameTime();
        SoulRecovery recovery = ensureRecovery(player, gameTime);
        recovery.items.clear();
        for (ItemEntity item : event.getDrops()) {
            if (!item.getItem().isEmpty()) {
                item.setTarget(player.getUUID());
                recovery.items.add(item.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getDroppedExperience() <= 0) {
            return;
        }
        int reserved = Math.max(1, Math.round(event.getDroppedExperience() * XP_RESERVE_RATIO));
        event.setDroppedExperience(Math.max(0, event.getDroppedExperience() - reserved));
        long gameTime = player.level().getGameTime();
        SoulRecovery recovery = ensureRecovery(player, gameTime);
        recovery.experience += reserved;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        RECOVERIES.entrySet().removeIf(e -> e.getValue().expiresAt <= now || e.getValue().consumed);
    }

    public record RecoveryResult(boolean recovered, int itemCount, int experience,
            ResourceKey<Level> deathDimension, BlockPos deathPos) {}

    private enum Delivery { INVENTORY, FEET }

    public static RecoveryResult recover(ServerPlayer player, float ratio) {
        return recover(player, ratio, Delivery.INVENTORY);
    }

    public static RecoveryResult recoverToFeet(ServerPlayer player, float ratio) {
        return recover(player, ratio, Delivery.FEET);
    }

    private static RecoveryResult recover(ServerPlayer player, float ratio, Delivery delivery) {
        SoulRecovery recovery = RECOVERIES.get(player.getUUID());
        long gameTime = player.level().getGameTime();
        if (recovery == null || recovery.expiresAt <= gameTime || recovery.consumed) {
            player.displayClientMessage(Component.literal("魄: 没有可牵回的魄印"), true);
            return new RecoveryResult(false, 0, 0, null, null);
        }

        ResourceKey<Level> deathDimension = recovery.deathDimension;
        BlockPos deathPos = recovery.deathPos;
        int xpRecovered = Math.round(recovery.experience * ratio);
        if (xpRecovered > 0) {
            player.giveExperiencePoints(xpRecovered);
        }

        int itemCount = recoverItems(player, recovery, ratio, delivery);
        recovery.consumed = true;
        RECOVERIES.remove(player.getUUID());

        if (itemCount > 0 || xpRecovered > 0) {
            player.displayClientMessage(Component.literal("魄: 牵回 " + itemCount + " 件物品与 " + xpRecovered + " 点经验"), true);
            SigillumAdvancementTriggers.soulRecalled(player, itemCount + xpRecovered);
            return new RecoveryResult(true, itemCount, xpRecovered, deathDimension, deathPos);
        }
        player.displayClientMessage(Component.literal("魄: 魄印已散，死亡掉落可能已消失"), true);
        return new RecoveryResult(false, 0, 0, deathDimension, deathPos);
    }

    private static SoulRecovery ensureRecovery(ServerPlayer player, long gameTime) {
        SoulRecovery recovery = RECOVERIES.computeIfAbsent(
            player.getUUID(),
            ignored -> new SoulRecovery(gameTime + WINDOW_TICKS, player.level().dimension(), player.blockPosition())
        );
        recovery.expiresAt = gameTime + WINDOW_TICKS;
        recovery.deathDimension = player.level().dimension();
        recovery.deathPos = player.blockPosition();
        recovery.consumed = false;
        return recovery;
    }

    private static int recoverItems(ServerPlayer player, SoulRecovery recovery, float ratio, Delivery delivery) {
        if (recovery.items.isEmpty() || ratio <= 0.0f) {
            return 0;
        }
        ServerLevel level = player.getServer().getLevel(recovery.deathDimension);
        if (level == null) {
            return 0;
        }

        List<SoulItemCategory> categories = new ArrayList<>();
        java.util.HashSet<UUID> seen = new java.util.HashSet<>();
        for (UUID id : recovery.items) {
            Entity entity = level.getEntity(id);
            if (entity instanceof ItemEntity item && seen.add(item.getUUID()) && isRecoverableSoulItem(player, item)) {
                addSoulItemCategory(categories, new SoulItemEntry(item, item.getItem()));
            }
        }
        AABB recoveryArea = new AABB(recovery.deathPos).inflate(RECOVERY_RADIUS);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, recoveryArea,
                item -> seen.add(item.getUUID()) && isRecoverableSoulItem(player, item))) {
            addSoulItemCategory(categories, new SoulItemEntry(item, item.getItem()));
        }

        if (categories.isEmpty()) {
            return 0;
        }

        Collections.shuffle(categories);
        int targetCategories = Math.max(1, Math.min(categories.size(), (int) Math.ceil(categories.size() * ratio)));
        int recoveredCount = 0;
        for (int i = 0; i < targetCategories; i++) {
            SoulItemCategory category = categories.get(i);
            int categoryTarget = Math.max(1, Math.min(category.totalCount(), (int) Math.ceil(category.totalCount() * ratio)));
            int categoryRecovered = 0;
            Collections.shuffle(category.entries);
            for (SoulItemEntry entry : category.entries) {
                if (categoryRecovered >= categoryTarget) {
                    break;
                }
                ItemEntity item = entry.entity();
                if (!item.isAlive() || item.getItem().isEmpty()) {
                    continue;
                }
                ItemStack groundStack = item.getItem();
                int take = Math.min(groundStack.getCount(), categoryTarget - categoryRecovered);
                ItemStack recovered = groundStack.copyWithCount(take);
                if (delivery == Delivery.FEET) {
                    player.drop(recovered, false);
                } else {
                    giveOrDrop(player, recovered);
                }
                groundStack.shrink(take);
                categoryRecovered += take;
                recoveredCount += take;
                if (groundStack.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(groundStack);
                }
            }
        }
        return recoveredCount;
    }

    private static boolean isRecoverableSoulItem(ServerPlayer player, ItemEntity item) {
        return item.isAlive()
            && !item.getItem().isEmpty()
            && player.getUUID().equals(item.getTarget());
    }

    private static void addSoulItemCategory(List<SoulItemCategory> categories, SoulItemEntry entry) {
        for (SoulItemCategory category : categories) {
            if (ItemStack.isSameItemSameComponents(category.sample, entry.stack())) {
                category.entries.add(entry);
                return;
            }
        }
        SoulItemCategory category = new SoulItemCategory(entry.stack().copyWithCount(1));
        category.entries.add(entry);
        categories.add(category);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static final class SoulRecovery {
        private long expiresAt;
        private ResourceKey<Level> deathDimension;
        private BlockPos deathPos;
        private int experience;
        private boolean consumed;
        private final List<UUID> items = new ArrayList<>();

        private SoulRecovery(long expiresAt, ResourceKey<Level> deathDimension, BlockPos deathPos) {
            this.expiresAt = expiresAt;
            this.deathDimension = deathDimension;
            this.deathPos = deathPos;
        }
    }

    private static final class SoulItemCategory {
        private final ItemStack sample;
        private final List<SoulItemEntry> entries = new ArrayList<>();

        private SoulItemCategory(ItemStack sample) {
            this.sample = sample;
        }

        private int totalCount() {
            int total = 0;
            for (SoulItemEntry entry : entries) {
                ItemEntity item = entry.entity();
                if (item.isAlive() && !item.getItem().isEmpty()) {
                    total += item.getItem().getCount();
                }
            }
            return total;
        }
    }

    private record SoulItemEntry(ItemEntity entity, ItemStack stack) {}
}
