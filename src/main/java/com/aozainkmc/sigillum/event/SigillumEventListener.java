package com.aozainkmc.sigillum.event;

import com.aozainkmc.sigillum.advancement.SigillumAdvancementTriggers;
import com.aozainkmc.sigillum.SigillumMod;
import com.aozainkmc.sigillum.binding.GlyphBinding;
import com.aozainkmc.sigillum.cast.SkillCast;
import com.aozainkmc.sigillum.glyph.GlyphSemantics;
import com.aozainkmc.core.api.InkRecognitionResult;
import com.aozainkmc.core.api.InkRecognizedEvent;
import com.aozainkmc.core.api.InkSource;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = SigillumMod.MOD_ID)
public final class SigillumEventListener {

    private SigillumEventListener() {}

    @SubscribeEvent
    public static void onGlyphRecognized(InkRecognizedEvent event) {
        InkRecognitionResult result = event.result();
        InkSource source = event.source();
        String word = result.topGlyph();

        String talismanType = (String) source.extra().get("talisman_type");
        if (talismanType != null) {
            onTalismanRecognized(event, talismanType);
            return;
        }

        if ("classic_taiji_traj".equals(source.sourceId())) {
            onPaperCasting(event);
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("[Sigillum] 字: ").append(word);
        builder.append(" | 源: ").append(source.sourceId());
        builder.append(" | 置信度: ").append(String.format("%.1f%%", result.confidence() * 100f));
        builder.append(" | 倍率: ").append(source.powerMultiplier());
        builder.append(" | 品质: ").append(source.tierLabel());

        List<String> tags = GlyphSemantics.tagsFor(word);
        if (tags.isEmpty()) {
            builder.append(" | 未知字");
        } else {
            builder.append(" | 标签: ").append(String.join(", ", tags));
        }

        event.player().displayClientMessage(Component.literal(builder.toString()), false);
    }

    private static void onTalismanRecognized(InkRecognizedEvent event, String type) {
        InkRecognitionResult result = event.result();
        InkSource source = event.source();
        String slot = (String) source.extra().get("slot");

        StringBuilder builder = new StringBuilder();
        builder.append("[Sigillum] 黄符成符");
        builder.append(" | 类型: ").append(type);
        builder.append(" | 格: ").append(slot);
        builder.append(" | 字: ").append(result.topGlyph());
        builder.append(" | 置信度: ").append(String.format("%.1f%%", result.confidence() * 100f));

        if ("specified".equals(type)) {
            builder.append(" | 指定数字: ").append(source.extra().get("specified_number"));
            builder.append(" | 指定字: ").append(source.extra().get("specified_glyph"));
        }

        event.player().displayClientMessage(Component.literal(builder.toString()), false);
    }

    private static void onPaperCasting(InkRecognizedEvent event) {
        InkRecognitionResult result = event.result();
        InkSource source = event.source();
        String word = normalizePaperDigit(result);

        if (!GlyphBinding.isChineseDigit(word)) {
            event.setCanceled(true);
            event.player().displayClientMessage(
                Component.literal("[Sigillum] 白纸指定技只接受一至九"), false);
            return;
        }

        Optional<String> bound = GlyphBinding.getBoundGlyph(event.player(), word);
        if (bound.isEmpty()) {
            event.setCanceled(true);
            event.player().displayClientMessage(
                Component.literal("[Sigillum] 数字 " + word + " 未指定"), false);
            return;
        }

        String glyph = bound.get();
        if (!(event.player() instanceof ServerPlayer serverPlayer)) {
            event.setCanceled(true);
            return;
        }
        if (!SkillCast.isImplementedSkill(glyph)) {
            event.setCanceled(true);
            event.player().displayClientMessage(
                Component.literal("[Sigillum] 指定字 " + glyph + " 不是可用术式"), false);
            return;
        }

        SkillCast.Outcome outcome = SkillCast.castQuick(serverPlayer, glyph, source.powerMultiplier());
        event.setCanceled(true);
        if (outcome == SkillCast.Outcome.MISS) {
            event.player().displayClientMessage(
                Component.literal("[Sigillum] 快速吟唱: " + word + " -> " + glyph + " · 未命中"), false);
            return;
        }
        SigillumAdvancementTriggers.quickCast(serverPlayer, glyph);
        event.player().displayClientMessage(
            Component.literal("[Sigillum] 快速吟唱: " + word + " -> " + glyph
                + " | 倍率: " + source.powerMultiplier()), false);
    }

    private static String normalizePaperDigit(InkRecognitionResult result) {
        String word = result.topGlyph() == null ? "" : result.topGlyph().trim();
        int strokes = result.simplifiedStrokeCount();
        if (strokes == 1) {
            return "一";
        }
        if (strokes == 2 && (!GlyphBinding.isChineseDigit(word) || "三".equals(word))) {
            return "二";
        }
        if (strokes == 3 && (!GlyphBinding.isChineseDigit(word) || "二".equals(word))) {
            return "三";
        }
        return word;
    }
}
