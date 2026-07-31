package com.aozainkmc.sigillum.event;

import com.aozainkmc.sigillum.SigillumMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class OreRevealManager {

    private static final String REVEAL_TAG = "aozaink_ore_reveal";
    private static final long VALIDATE_INTERVAL = 20L;
    private static final double RAY_STEP = 0.5;
    private static final Map<RevealKey, RevealEntry> REVEALS = new HashMap<>();

    private OreRevealManager() {}

    public static int revealAround(ServerPlayer player, int radius, int maxBlocks, long ticks) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        List<OreMatch> matches = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (center.distSqr(pos) > radius * radius || !level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (isOre(state)) {
                matches.add(new OreMatch(pos.immutable(), state));
            }
        }
        matches.sort(Comparator.comparingDouble(m -> center.distSqr(m.pos())));
        long expiresAt = level.getGameTime() + ticks;
        int revealed = 0;
        for (OreMatch match : matches) {
            if (revealed >= maxBlocks) break;
            ensureDisplay(level, match.pos(), match.state(), expiresAt);
            revealed++;
        }
        return revealed;
    }

    public static boolean revealRayFirst(ServerPlayer player, double maxDist, long ticks) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Set<BlockPos> visited = new HashSet<>();
        for (double d = RAY_STEP; d <= maxDist; d += RAY_STEP) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
            if (!visited.add(pos)) continue;
            if (pos.getY() < level.getMinBuildHeight() || !level.hasChunkAt(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (isOre(state)) {
                ensureDisplay(level, pos, state, level.getGameTime() + ticks);
                return true;
            }
        }
        return false;
    }

    public static int revealRayAll(ServerPlayer player, double maxDist, int maxBlocks, long ticks) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Set<BlockPos> visited = new HashSet<>();
        long expiresAt = level.getGameTime() + ticks;
        int revealed = 0;
        for (double d = RAY_STEP; d <= maxDist && revealed < maxBlocks; d += RAY_STEP) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
            if (!visited.add(pos)) continue;
            if (pos.getY() < level.getMinBuildHeight() || !level.hasChunkAt(pos)) break;
            BlockState state = level.getBlockState(pos);
            if (!isOre(state)) continue;
            ensureDisplay(level, pos, state, expiresAt);
            revealed++;
        }
        return revealed;
    }

    private static boolean isOre(BlockState state) {
        return state.is(Tags.Blocks.ORES) || state.is(Blocks.ANCIENT_DEBRIS);
    }

    private static void ensureDisplay(ServerLevel level, BlockPos pos, BlockState state, long expiresAt) {
        RevealKey key = new RevealKey(level.dimension(), pos.immutable());
        RevealEntry existing = REVEALS.get(key);
        if (existing != null && existing.display.isAlive() && existing.state.equals(state)) {
            existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
            return;
        }
        removeDisplay(key);

        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) return;
        display.load(blockDisplayTag(pos, state));
        display.setPos(pos.getX(), pos.getY(), pos.getZ());
        display.setInvisible(true);
        display.setGlowingTag(true);
        display.addTag(REVEAL_TAG);
        level.addFreshEntity(display);
        REVEALS.put(key, new RevealEntry(display, state, expiresAt));
    }

    private static CompoundTag blockDisplayTag(BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(pos.getX()));
        position.add(DoubleTag.valueOf(pos.getY()));
        position.add(DoubleTag.valueOf(pos.getZ()));
        tag.put("Pos", position);

        ListTag motion = new ListTag();
        motion.add(DoubleTag.valueOf(0.0D));
        motion.add(DoubleTag.valueOf(0.0D));
        motion.add(DoubleTag.valueOf(0.0D));
        tag.put("Motion", motion);

        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(0.0F));
        rotation.add(FloatTag.valueOf(0.0F));
        tag.put("Rotation", rotation);

        tag.put("block_state", NbtUtils.writeBlockState(state));
        return tag;
    }

    private static void removeDisplay(RevealKey key) {
        RevealEntry existing = REVEALS.remove(key);
        if (existing != null && existing.display.isAlive()) {
            existing.display.discard();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % VALIDATE_INTERVAL != 0L) return;
        Iterator<Map.Entry<RevealKey, RevealEntry>> iterator = REVEALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RevealKey, RevealEntry> entry = iterator.next();
            RevealKey key = entry.getKey();
            RevealEntry reveal = entry.getValue();
            ServerLevel level = server.getLevel(key.dimension());
            boolean stale = level == null
                || !reveal.display.isAlive()
                || (level.hasChunkAt(key.pos()) && !level.getBlockState(key.pos()).equals(reveal.state));
            if (reveal.expiresAt <= now || stale) {
                if (reveal.display.isAlive()) {
                    reveal.display.discard();
                }
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
            && event.loadedFromDisk()
            && event.getEntity() instanceof Display.BlockDisplay display
            && display.getTags().contains(REVEAL_TAG)) {
            display.discard();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (RevealEntry entry : REVEALS.values()) {
            if (entry.display.isAlive()) {
                entry.display.discard();
            }
        }
        REVEALS.clear();
    }

    private record OreMatch(BlockPos pos, BlockState state) {}

    private record RevealKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final class RevealEntry {
        private final Display.BlockDisplay display;
        private final BlockState state;
        private long expiresAt;

        private RevealEntry(Display.BlockDisplay display, BlockState state, long expiresAt) {
            this.display = display;
            this.state = state;
            this.expiresAt = expiresAt;
        }
    }
}
