package com.aozainkmc.sigillum.event;

import com.aozainkmc.sigillum.network.BindingRitualPayload;
import com.aozainkmc.sigillum.util.SigillumTexts;
import com.aozainkmc.sigillum.advancement.SigillumAdvancementTriggers;
import com.aozainkmc.sigillum.advancement.SigillumCriterionTrigger;
import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.binding.GlyphBinding;
import com.aozainkmc.sigillum.cast.SigillumInscriptionManager;
import com.aozainkmc.sigillum.cast.SkillCast;
import com.aozainkmc.sigillum.grade.TalismanGrade;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class TalismanUseHandler {

    private static final String TAG_TYPE = "aozaink:talisman_type";
    private static final String TAG_SLOT1 = "aozaink:slot1";
    private static final String TAG_SLOT2 = "aozaink:slot2";
    private static final String TAG_SLOT3 = "aozaink:slot3";
    private static final ResourceLocation YELLOW_TALISMAN_ITEM =
        ResourceLocation.fromNamespaceAndPath("aozaink_input", "yellow_talisman");
    private static final DustParticleOptions MISS_CINNABAR_PARTICLE =
        new DustParticleOptions(new Vector3f(0.52f, 0.07f, 0.035f), 0.42f);

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
        if (shouldLetBlockHandle(player, event.getPos())) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        dispatchUse(player, tag, event.getPos());
    }

    private static boolean shouldLetBlockHandle(ServerPlayer player, BlockPos pos) {
        if (player.isSecondaryUseActive()) return false;
        return player.serverLevel().getBlockState(pos).getMenuProvider(player.serverLevel(), pos) != null;
    }

    private static void dispatchUse(ServerPlayer player, CompoundTag tag, BlockPos blockPos) {
        String type = tag.getString(TAG_TYPE);
        String slot1 = tag.getString(TAG_SLOT1);
        String slot2 = tag.getString(TAG_SLOT2);
        String slot3 = tag.getString(TAG_SLOT3);

        switch (type) {
            case "specified" -> useSpecified(player, slot1, slot2);
            case "inscription" -> useInscription(player, slot1, slot2, slot3, blockPos);
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
            SigillumTexts.actionbar(player, "指定符数字无效", SigillumTexts.CINNABAR);
            return;
        }

        GlyphBinding.bind(player, number, glyph);
        SigillumAdvancementTriggers.specifiedBound(player, glyph);

        SigillumTexts.actionbar(player,
            SigillumTexts.colored(number + " → " + glyph + " 绑定完成", SigillumTexts.GOLD));

        Component message = Component.empty()
            .append(SigillumTexts.colored("已指定 ", SigillumTexts.CREAM))
            .append(SigillumTexts.colored(number, SigillumTexts.GOLD))
            .append(SigillumTexts.colored(" → ", SigillumTexts.CREAM))
            .append(SigillumTexts.colored(glyph, SigillumTexts.GOLD))
            .append(SigillumTexts.colored("（" + elementName(glyph) + "） ", SigillumTexts.CREAM))
            .append(Component.literal("[点击查看]")
                .withStyle(Style.EMPTY
                    .withColor(SigillumTexts.GOLD)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sigillum menu"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("打开快速吟唱设置")))));
        player.sendSystemMessage(message, false);

        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
            SoundSource.PLAYERS, 0.7f, 1.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS, 0.3f, 1.3f);
        PacketDistributor.sendToPlayer(player, new BindingRitualPayload(center.x, center.y, center.z));

        consumeOne(player);
    }

    private static String elementName(String glyph) {
        return switch (glyph) {
            case "火" -> "离火";
            case "雷" -> "震雷";
            case "水", "净" -> "坎水";
            case "护" -> "艮山";
            case "斩" -> "兑金";
            case "镇", "封" -> "坤土";
            case "退", "引" -> "巽风";
            case "明" -> "乾光";
            case "吸" -> "噬灵";
            case "魄" -> "归魂";
            default -> "符咒";
        };
    }

    private static void useInscription(ServerPlayer player, String slot1, String slot2, String slot3, BlockPos blockPos) {
        if ("刻".equals(slot3) || (!"刻".equals(slot1) && !"刻".equals(slot2))) {
            useWaste(player);
            return;
        }
        if (slot3 != null && !slot3.isEmpty() && !SkillCast.isModifier(slot3)) {
            useWaste(player);
            return;
        }

        List<String> skills = new ArrayList<>();
        List<String> modifiers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int markCount = 0;
        for (String slot : new String[] {slot1, slot2, slot3}) {
            if (slot == null || slot.isEmpty()) continue;
            if (!"刻".equals(slot) && !seen.add(slot)) {
                useWaste(player);
                return;
            }
            if ("刻".equals(slot)) {
                markCount++;
            } else if (SkillCast.isImplementedSkill(slot)) {
                skills.add(slot);
            } else if (SkillCast.isModifier(slot)) {
                modifiers.add(slot);
            } else {
                useWaste(player);
                return;
            }
        }
        if (markCount != 1 || skills.size() > 1 || modifiers.size() > 2) {
            useWaste(player);
            return;
        }
        if (!skills.isEmpty() && modifiers.size() > 1) {
            useWaste(player);
            return;
        }
        if (skills.isEmpty() && modifiers.isEmpty()) {
            useWaste(player);
            return;
        }
        if (modifiers.contains("穿")) {
            useWaste(player);
            return;
        }
        if (blockPos == null) {
            notice(player, "刻印符需对准方块使用");
            return;
        }

        if (player.serverLevel().getBlockState(blockPos).isAir()) {
            notice(player, "刻印符目标方块无效");
            return;
        }

        CompoundTag heldTag = talismanTag(player.getMainHandItem());
        float overallM = overallMultiplier(heldTag, new String[] {slot1, slot2, slot3});
        if (overallM <= 0.0f) {
            useWaste(player);
            return;
        }
        SigillumInscriptionManager.ActivationResult result =
            SigillumInscriptionManager.activate(player, blockPos, skills, modifiers, overallM);
        notice(player, result.message());
        if (result.consume()) {
            triggerInscription(player, skills, modifiers);
            if (result.message().contains("1728000tick")) {
                SigillumAdvancementTriggers.inscriptionChanged(player, SigillumCriterionTrigger.Event.empty()
                    .withType("full_day"));
            }
            spawnRedstoneOnFaces(player.serverLevel(), blockPos);
            spawnFlameBurst(player.serverLevel(), player.position());
            consumeOne(player);
        }
    }

    private static void useCombo(ServerPlayer player, CompoundTag tag, String slot1, String slot2, String slot3) {
        String[] slots = {slot1, slot2, slot3};
        List<String> skills = new ArrayList<>();
        List<String> modifiers = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String s : slots) {
            if (s == null || s.isEmpty()) continue;
            if (!seen.add(s)) {
                useWaste(player);
                return;
            }
            if (SkillCast.isImplementedSkill(s)) {
                skills.add(s);
            } else if (SkillCast.isModifier(s)) {
                modifiers.add(s);
            } else {
                useWaste(player);
                return;
            }
        }

        if (skills.isEmpty()) {
            useWaste(player);
            return;
        }

        boolean hasTargetSkill = false;
        for (String s : skills) {
            if (SkillCast.hasTargetEffect(s)) {
                hasTargetSkill = true;
                break;
            }
        }
        boolean hasWideSupportSkill = skills.size() > 1 && skills.stream().anyMatch(SkillCast::hasWideSupportEffect);
        if (!hasTargetSkill && modifiers.contains("穿")) {
            useWaste(player);
            return;
        }
        if (!hasTargetSkill && modifiers.contains("广") && !hasWideSupportSkill) {
            useWaste(player);
            return;
        }
        if (modifiers.contains("广") && modifiers.contains("穿")) {
            useWaste(player);
            return;
        }

        float overallM = overallMultiplier(tag, slots);
        String overallLabel = overallLabel(tag, slots);
        if (overallM <= 0.0f) {
            notice(player, "组合符 · " + overallLabel + "，术式溃散");
            consumeOne(player);
            return;
        }

        if (skills.size() > 1) {
            boolean strong = modifiers.contains("强");
            boolean xu = modifiers.contains("续");
            boolean guang = modifiers.contains("广");
            boolean chuan = modifiers.contains("穿");

            SkillCast.LinkedComboSpec spec = null;
            if (skills.size() == 2 && modifiers.size() <= 1) {
                spec = SkillCast.linkedComboSpec(skills.get(0), skills.get(1));
            }

            boolean needsTarget = spec != null ? spec.needsTarget() : hasRequiredTargetSkill(skills);
            LivingEntity primary = null;
            if (needsTarget && !chuan) {
                primary = SkillCast.targetLiving(player);
                if (primary == null) {
                    Vec3 missPoint = SkillCast.landPoint(player);
                    miss(player, "组合符 · 未着", missPoint);
                    dropStackAt(player, player.getMainHandItem().copyWithCount(1), missPoint);
                    consumeOne(player);
                    return;
                }
            }

            float comboPower = overallM * (strong ? 2.0f : 1.0f);
            float comboDuration = xu ? 1.0f + overallM : 1.0f;
            SkillCast.CastEnv comboEnv = new SkillCast.CastEnv(comboPower, comboDuration);

            if (spec != null) {
                if (chuan) {
                    if (!SkillCast.supportsPiercingCombo(spec)) {
                        notice(player, spec.label() + " · 不支持穿透");
                        consumeOne(player);
                        return;
                    }
                    int maxHits = overallM >= 1.0f ? 3 : (overallM >= 0.8f ? 2 : 1);
                    int hits = castPiercingLinkedCombo(player, spec, comboEnv, maxHits);
                    if (hits == 0) {
                        Vec3 missPoint = SkillCast.landPoint(player);
                        miss(player, spec.label() + " · 未着", missPoint);
                        dropStackAt(player, player.getMainHandItem().copyWithCount(1), missPoint);
                        consumeOne(player);
                        return;
                    }
                    notice(player, spec.label() + " · " + overallLabel + " 穿透命中 " + hits + " 个");
                    triggerSuccessfulCast(player, skills, modifiers, true, true, hits);
                    consumeOne(player);
                    return;
                }
                if (guang) {
                    int hits = applyWideLinkedCombo(player, primary, spec, comboEnv);
                    notice(player, spec.label() + " · " + overallLabel + " 广域命中 " + hits + " 个");
                    triggerSuccessfulCast(player, skills, modifiers, true, spec.needsTarget(), hits);
                    consumeOne(player);
                    return;
                }
                SkillCast.applyLinkedCombo(player, primary, spec, comboEnv);
                notice(player, spec.label() + " · " + overallLabel);
                triggerSuccessfulCast(player, skills, modifiers, true, spec.needsTarget(), 1);
                consumeOne(player);
                return;
            }

            if (chuan) {
                notice(player, "多字组合暂不支持穿透");
                consumeOne(player);
                return;
            }

            int applied = 0;
            boolean affectedTarget = false;
            for (String s : skills) {
                if (overallM <= 0.0f) continue;
                SkillCast.CastEnv sEnv = comboEnv;
                boolean didApply = false;
                if (guang && SkillCast.hasWideSupportEffect(s)) {
                    applied += SkillCast.applyWideSupport(player, s, sEnv);
                    didApply = true;
                } else if (SkillCast.hasSelfEffect(s) && !(guang && "明".equals(s))) {
                    SkillCast.applySelf(player, s, sEnv);
                    didApply = true;
                }
                if (guang && "明".equals(s)) {
                    SkillCast.applyWideLight(player, sEnv);
                    didApply = true;
                } else if (guang && SkillCast.hasTargetEffect(s) && primary != null) {
                    SkillCast.CastEnv aoeEnv = sEnv.withMultiplier(0.7f);
                    AABB box = new AABB(primary.blockPosition()).inflate(SkillCast.AOE_RADIUS);
                    for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                            e -> e != player && e.isAlive())) {
                        SkillCast.applyToTarget(player, target, s, aoeEnv);
                        applied++;
                    }
                    didApply = true;
                    affectedTarget = true;
                } else if (primary != null && SkillCast.hasTargetEffect(s)) {
                    SkillCast.applyToTarget(player, primary, s, sEnv);
                    didApply = true;
                    affectedTarget = true;
                }
                if (didApply) applied++;
            }
            if (applied > 0) {
                notice(player, "组合 · " + String.join("/", skills) + " · " + overallLabel);
                triggerSuccessfulCast(player, skills, modifiers, false, affectedTarget, applied);
            } else {
                notice(player, "组合符 · 术式溃散");
            }
            consumeOne(player);
            return;
        }

        String skill = skills.get(0);
        int skillSlot = skillSlot(skill, slots);

        boolean strong = modifiers.contains("强");
        boolean xu = modifiers.contains("续");
        boolean guang = modifiers.contains("广");
        boolean chuan = modifiers.contains("穿");

        float powerM = overallM * (strong ? 2.0f : 1.0f);
        float durationM = xu ? 1.0f + overallM : 1.0f;
        SkillCast.CastEnv env = new SkillCast.CastEnv(powerM, durationM);

        if (chuan && SkillCast.hasTargetEffect(skill)) {
            int maxHits = overallM >= 1.0f ? 3 : (overallM >= 0.8f ? 2 : 1);
            int hits = castPiercing(player, skill, env, maxHits);
            if (hits == 0) {
                Vec3 missPoint = SkillCast.landPoint(player);
                miss(player, skill + "符 · 未着", missPoint);
                dropStackAt(player, player.getMainHandItem().copyWithCount(1), missPoint);
                consumeOne(player);
                return;
            }
            if (SkillCast.hasSelfEffect(skill)) {
                SkillCast.applySelf(player, skill, env);
            }
            ItemStack degraded = downgradedStack(player, tag, skillSlot, hits);
            consumeOne(player);
            if (!degraded.isEmpty()) {
                giveOrDrop(player, degraded);
            }
            notice(player, skill + " · " + overallLabel + " 穿透命中 " + hits + " 个");
            triggerSuccessfulCast(player, skills, modifiers, false, true, hits);
            return;
        }

        if (guang && "明".equals(skill)) {
            int hit = SkillCast.applyWideLight(player, env);
            consumeOne(player);
            notice(player, skill + " · " + overallLabel + " 广域照妖 " + hit + " 个");
            triggerSuccessfulCast(player, skills, modifiers, false, true, hit);
            return;
        }

        if (guang && SkillCast.hasTargetEffect(skill)) {
            LivingEntity primary = SkillCast.targetLiving(player);
            if (primary == null) {
                Vec3 missPoint = SkillCast.landPoint(player);
                miss(player, skill + "符 · 未着", missPoint);
                dropStackAt(player, player.getMainHandItem().copyWithCount(1), missPoint);
                consumeOne(player);
                return;
            }
            SkillCast.CastEnv aoeEnv = env.withMultiplier(0.7f);
            AABB box = new AABB(primary.blockPosition()).inflate(SkillCast.AOE_RADIUS);
            int hit = 0;
            for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive())) {
                SkillCast.applyToTarget(player, target, skill, aoeEnv);
                hit++;
            }
            consumeOne(player);
            notice(player, skill + " · " + overallLabel + " 广域命中 " + hit + " 个");
            triggerSuccessfulCast(player, skills, modifiers, false, true, hit);
            return;
        }

        SkillCast.Outcome outcome = SkillCast.cast(player, skill, env);
        if (outcome == SkillCast.Outcome.MISS) {
            Vec3 missPoint = SkillCast.landPoint(player);
            miss(player, skill + "符 · 未着", missPoint);
            dropStackAt(player, player.getMainHandItem().copyWithCount(1), missPoint);
            consumeOne(player);
            return;
        }

        notice(player, skill + " · " + overallLabel + "  ×" + String.format("%.2f", powerM));
        triggerSuccessfulCast(player, skills, modifiers, false, SkillCast.hasTargetEffect(skill), 1);
        consumeOne(player);
    }

    private static void triggerInscription(ServerPlayer player, List<String> skills, List<String> modifiers) {
        if (!skills.isEmpty()) {
            for (String skill : skills) {
                SigillumAdvancementTriggers.inscriptionChanged(player, SigillumCriterionTrigger.Event.empty()
                    .withType("created")
                    .withSkill(skill));
            }
        }
        for (String modifier : modifiers) {
            String type = switch (modifier) {
                case "续" -> "extended";
                case "强" -> "strengthened";
                case "广" -> "widened";
                default -> "modified";
            };
            SigillumAdvancementTriggers.inscriptionChanged(player, SigillumCriterionTrigger.Event.empty()
                .withType(type)
                .withModifier(modifier));
        }
    }

    private static void triggerSuccessfulCast(ServerPlayer player, List<String> skills, List<String> modifiers,
            boolean linked, boolean target, int count) {
        SigillumCriterionTrigger.Event base = SigillumCriterionTrigger.Event.empty()
            .withType(target ? "target" : "self")
            .withLinked(linked)
            .withCount(count);
        SigillumAdvancementTriggers.talismanCast(player, base);
        if (skills.size() == 1 && modifiers.size() == 2) {
            SigillumAdvancementTriggers.talismanCast(player, base.withSpecial("single_skill_double_modifier"));
        }
        for (String skill : skills) {
            SigillumAdvancementTriggers.talismanCast(player, base.withSkill(skill));
        }
        for (String modifier : modifiers) {
            SigillumAdvancementTriggers.talismanCast(player, base.withModifier(modifier));
        }
        if (linked && skills.contains("火") && skills.contains("雷")) {
            SigillumAdvancementTriggers.specialEffect(player, SigillumCriterionTrigger.Event.empty()
                .withSpecial("fire_thunder_chain"));
        }
    }

    private static boolean hasRequiredTargetSkill(List<String> skills) {
        for (String skill : skills) {
            if (SkillCast.requiresTarget(skill)) return true;
        }
        return false;
    }

    private static int skillSlot(String skill, String[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (skill.equals(slots[i])) return i + 1;
        }
        return 1;
    }

    private static float overallMultiplier(CompoundTag tag, String[] slots) {
        TalismanGrade worst = null;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null || slots[i].isEmpty()) continue;
            TalismanGrade g = TalismanGrade.byName(tag.getString("aozaink:grade" + (i + 1)));
            if (g == null) continue;
            if (worst == null || g.ordinal() > worst.ordinal()) worst = g;
        }
        return worst == null ? 1.0f : worst.multiplier();
    }

    private static String overallLabel(CompoundTag tag, String[] slots) {
        TalismanGrade worst = null;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null || slots[i].isEmpty()) continue;
            TalismanGrade g = TalismanGrade.byName(tag.getString("aozaink:grade" + (i + 1)));
            if (g == null) continue;
            if (worst == null || g.ordinal() > worst.ordinal()) worst = g;
        }
        return worst == null ? "未评级" : worst.display();
    }

    private static int castPiercing(ServerPlayer player, String skill, SkillCast.CastEnv env, int maxHits) {
        Set<UUID> hit = new HashSet<>();
        int hits = 0;
        for (int i = 0; i < maxHits; i++) {
            EntityHitResult result = nextPierceTarget(player, hit);
            if (result == null || !(result.getEntity() instanceof LivingEntity target)) break;
            SkillCast.applyToTarget(player, target, skill, env);
            hit.add(target.getUUID());
            hits++;
        }
        return hits;
    }

    private static int castPiercingLinkedCombo(ServerPlayer player, SkillCast.LinkedComboSpec spec,
                                               SkillCast.CastEnv env, int maxHits) {
        Set<UUID> hit = new HashSet<>();
        int hits = 0;
        for (int i = 0; i < maxHits; i++) {
            EntityHitResult result = nextPierceTarget(player, hit);
            if (result == null || !(result.getEntity() instanceof LivingEntity target)) break;
            SkillCast.applyLinkedCombo(player, target, spec, env);
            hit.add(target.getUUID());
            hits++;
        }
        return hits;
    }

    private static int applyWideLinkedCombo(ServerPlayer player, LivingEntity primary,
                                            SkillCast.LinkedComboSpec spec, SkillCast.CastEnv env) {
        SkillCast.CastEnv supportEnv = env.withWideSupport();
        if (!spec.needsTarget()) {
            SkillCast.applyLinkedCombo(player, null, spec, supportEnv);
            return 0;
        }
        if (primary == null) return 0;

        int hits = 0;
        Set<UUID> applied = new HashSet<>();
        SkillCast.applyLinkedCombo(player, primary, spec, supportEnv);
        applied.add(primary.getUUID());
        hits++;

        SkillCast.CastEnv areaEnv = env.withMultiplier(0.7f).withoutSelfSupport();
        AABB box = new AABB(primary.blockPosition()).inflate(SkillCast.AOE_RADIUS);
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive())) {
            if (!applied.add(target.getUUID())) continue;
            SkillCast.applyLinkedCombo(player, target, spec, areaEnv);
            hits++;
        }
        return hits;
    }

    private static EntityHitResult nextPierceTarget(ServerPlayer player, Set<UUID> excluded) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = start.add(player.getViewVector(1.0f).scale(SkillCast.RANGE));
        AABB box = player.getBoundingBox().expandTowards(end).inflate(1.0);
        return net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
            player.serverLevel(), player, start, end, box,
            e -> e instanceof LivingEntity && e != player && e.isAlive() && !excluded.contains(e.getUUID()));
    }

    private static ItemStack downgradedStack(ServerPlayer player, CompoundTag tag, int skillSlot, int hits) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return ItemStack.EMPTY;
        TalismanGrade grade = TalismanGrade.byName(tag.getString("aozaink:grade" + skillSlot));
        for (int i = 0; i < hits && grade != null && grade != TalismanGrade.WASTE; i++) {
            grade = grade.nextLower();
        }
        if (grade == null || grade == TalismanGrade.WASTE) return ItemStack.EMPTY;
        CompoundTag newTag = tag.copy();
        newTag.putString("aozaink:grade" + skillSlot, grade.name());
        ItemStack stack = held.copyWithCount(1);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(newTag));
        return stack;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            dropStackAt(player, stack, player.position());
        }
    }

    private static void dropStackAt(ServerPlayer player, ItemStack stack, Vec3 point) {
        if (stack.isEmpty()) return;
        ItemEntity entity = new ItemEntity(player.serverLevel(), point.x, point.y, point.z, stack);
        entity.setDefaultPickUpDelay();
        player.serverLevel().addFreshEntity(entity);
    }

    private static void miss(ServerPlayer player, String text, Vec3 point) {
        notice(player, text);
        spawnMissFeedback(player.serverLevel(), point);
    }

    static void spawnMissFeedback(ServerLevel level, Vec3 point) {
        RandomSource random = level.random;
        for (int i = 0; i < 6; i++) {
            double x = point.x + (random.nextDouble() - 0.5) * 0.28;
            double y = point.y + random.nextDouble() * 0.18;
            double z = point.z + (random.nextDouble() - 0.5) * 0.28;
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1,
                (random.nextDouble() - 0.5) * 0.015, 0.012, (random.nextDouble() - 0.5) * 0.015, 0.0);
        }
        for (int i = 0; i < 3; i++) {
            double x = point.x + (random.nextDouble() - 0.5) * 0.22;
            double y = point.y + random.nextDouble() * 0.14;
            double z = point.z + (random.nextDouble() - 0.5) * 0.22;
            level.sendParticles(MISS_CINNABAR_PARTICLE, x, y, z, 1,
                (random.nextDouble() - 0.5) * 0.01, 0.006, (random.nextDouble() - 0.5) * 0.01, 0.0);
        }
        level.playSound(null, BlockPos.containing(point), SoundEvents.BRUSH_SAND, SoundSource.PLAYERS, 0.28f, 0.65f);
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
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }
}
