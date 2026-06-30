package com.aozainkmc.sigillum.event;

import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.binding.GlyphBinding;
import com.aozainkmc.sigillum.cast.SkillCast;
import com.aozainkmc.sigillum.grade.TalismanGrade;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class TalismanUseHandler {

    private static final String TAG_TYPE = "aozaink:talisman_type";
    private static final String TAG_SLOT1 = "aozaink:slot1";
    private static final String TAG_SLOT2 = "aozaink:slot2";
    private static final String TAG_SLOT3 = "aozaink:slot3";
    private static final ResourceLocation YELLOW_TALISMAN_ITEM =
        ResourceLocation.fromNamespaceAndPath("aozaink_input", "yellow_talisman");

    private TalismanUseHandler() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = player.getMainHandItem();
        if (!isYellowTalisman(stack)) return;

        CompoundTag tag = talismanTag(stack);
        if (!tag.contains(TAG_TYPE)) return;

        String type = tag.getString(TAG_TYPE);
        if ("inscription".equals(type)) return;

        event.setCanceled(true);
        dispatchUse(player, tag, null);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = player.getMainHandItem();
        if (!isYellowTalisman(stack)) return;

        CompoundTag tag = talismanTag(stack);
        if (!tag.contains(TAG_TYPE)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        dispatchUse(player, tag, event.getPos());
    }

    private static void dispatchUse(ServerPlayer player, CompoundTag tag, BlockPos blockPos) {
        String type = tag.getString(TAG_TYPE);
        String slot1 = tag.getString(TAG_SLOT1);
        String slot2 = tag.getString(TAG_SLOT2);
        String slot3 = tag.getString(TAG_SLOT3);

        switch (type) {
            case "specified" -> useSpecified(player, slot1, slot2);
            case "inscription" -> useInscription(player, slot1, slot2, blockPos);
            case "combo" -> useCombo(player, tag, slot1, slot2, slot3);
            default -> useWaste(player);
        }
    }

    private static void notice(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    private static void useWaste(ServerPlayer player) {
        notice(player, "废符");
        consumeOne(player);
    }

    private static void useSpecified(ServerPlayer player, String number, String glyph) {
        if (!GlyphBinding.isChineseDigit(number)) {
            notice(player, "指定符数字无效");
            return;
        }

        GlyphBinding.bind(player, number, glyph);
        notice(player, "已指定 " + number + " → " + glyph);
        spawnFlameBurst(player.serverLevel(), player.position());
        consumeOne(player);
    }

    private static void useInscription(ServerPlayer player, String slot1, String glyph, BlockPos blockPos) {
        if (blockPos == null) {
            notice(player, "刻印符需对准方块使用");
            return;
        }

        if (player.serverLevel().getBlockState(blockPos).isAir()) {
            notice(player, "刻印符目标方块无效");
            return;
        }

        notice(player, "刻印 · " + glyph);
        spawnRedstoneOnFaces(player.serverLevel(), blockPos);
        spawnFlameBurst(player.serverLevel(), player.position());
        consumeOne(player);
    }

    private static void useCombo(ServerPlayer player, CompoundTag tag, String slot1, String slot2, String slot3) {
        String[] slots = {slot1, slot2, slot3};
        int skillIndex = -1;
        int nonEmpty = 0;
        for (int i = 0; i < 3; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) {
                nonEmpty++;
                if (SkillCast.isImplementedSkill(slots[i])) {
                    skillIndex = i;
                }
            }
        }

        if (nonEmpty == 1 && skillIndex >= 0) {
            castSingle(player, tag, slots[skillIndex], skillIndex + 1);
            return;
        }

        notice(player, "组合符 [" + slot1 + ", " + slot2 + ", " + slot3 + "]");
        spawnFlameBurst(player.serverLevel(), player.position());
        consumeOne(player);
    }

    private static void castSingle(ServerPlayer player, CompoundTag tag, String glyph, int slot) {
        TalismanGrade grade = TalismanGrade.byName(tag.getString("aozaink:grade" + slot));
        float multiplier;
        String label;
        if (grade != null) {
            multiplier = grade.multiplier();
            label = grade.display();
            if (multiplier <= 0.0f) {
                notice(player, glyph + " · " + label + "，术式溃散");
                consumeOne(player);
                return;
            }
        } else {
            multiplier = 1.0f;
            label = "未评级";
        }

        SkillCast.Outcome outcome = SkillCast.cast(player, glyph, multiplier);
        if (outcome == SkillCast.Outcome.MISS) {
            notice(player, glyph + "符 · 未命中");
            dropTalismanAt(player, SkillCast.landPoint(player));
            consumeOne(player);
            return;
        }

        notice(player, glyph + " · " + label + "  ×" + String.format("%.2f", multiplier));
        consumeOne(player);
    }

    private static void dropTalismanAt(ServerPlayer player, net.minecraft.world.phys.Vec3 point) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        ItemEntity entity = new ItemEntity(player.serverLevel(), point.x, point.y, point.z, held.copyWithCount(1));
        entity.setDefaultPickUpDelay();
        player.serverLevel().addFreshEntity(entity);
    }

    private static void consumeOne(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.isEmpty()) {
            stack.shrink(1);
        }
    }

    private static void spawnFlameBurst(ServerLevel level, Vec3 center) {
        RandomSource random = level.random;
        for (int i = 0; i < 48; i++) {
            double x = center.x + (random.nextDouble() - 0.5) * 1.5;
            double y = center.y + random.nextDouble() * 1.5;
            double z = center.z + (random.nextDouble() - 0.5) * 1.5;
            double vx = (random.nextDouble() - 0.5) * 0.1;
            double vy = random.nextDouble() * 0.1;
            double vz = (random.nextDouble() - 0.5) * 0.1;
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, vx, vy, vz, 0.0);
        }
        for (int i = 0; i < 24; i++) {
            double x = center.x + (random.nextDouble() - 0.5) * 1.2;
            double y = center.y + random.nextDouble() * 1.0;
            double z = center.z + (random.nextDouble() - 0.5) * 1.2;
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.0, 0.05, 0.0, 0.0);
        }
    }

    private static void spawnRedstoneOnFaces(ServerLevel level, BlockPos pos) {
        RandomSource random = level.random;
        for (Direction dir : Direction.values()) {
            for (int i = 0; i < 8; i++) {
                Vec3 face = Vec3.atCenterOf(pos).add(
                    (dir.getStepX() * 0.55),
                    (dir.getStepY() * 0.55),
                    (dir.getStepZ() * 0.55)
                );
                double x = face.x + (dir.getStepX() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
                double y = face.y + (dir.getStepY() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
                double z = face.z + (dir.getStepZ() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
                level.sendParticles(ParticleTypes.WITCH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static boolean isYellowTalisman(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return YELLOW_TALISMAN_ITEM.equals(id);
    }

    private static CompoundTag talismanTag(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }
}
