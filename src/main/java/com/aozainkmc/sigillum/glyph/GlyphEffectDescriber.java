package com.aozainkmc.sigillum.glyph;

import com.aozainkmc.core.api.GlyphDescriber;
import com.aozainkmc.sigillum.cast.LinkedComboRegistry;
import com.aozainkmc.sigillum.cast.SkillCast;
import com.aozainkmc.sigillum.client.SigillumClientConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class GlyphEffectDescriber implements GlyphDescriber {
    private final BooleanSupplier detailedSupplier;

    public GlyphEffectDescriber() {
        this(SigillumClientConfig::detailed);
    }

    GlyphEffectDescriber(BooleanSupplier detailedSupplier) {
        this.detailedSupplier = detailedSupplier;
    }

    @Override
    public List<String> describe(List<String> slots) {
        boolean detailed = detailedSupplier.getAsBoolean();
        String s0 = at(slots, 0);
        String s1 = at(slots, 1);
        String s2 = at(slots, 2);

        List<String> nonEmpty = new ArrayList<>();
        for (String s : new String[] {s0, s1, s2}) {
            if (!s.isEmpty()) nonEmpty.add(s);
        }
        if (nonEmpty.isEmpty()) return List.of();

        if (isDigit(s0) && SkillCast.isImplementedSkill(s1) && s2.isEmpty()) {
            return specified(s0, s1, detailed);
        }
        if (nonEmpty.contains("刻")) {
            return inscription(s0, s1, s2, detailed);
        }
        return combo(nonEmpty, detailed);
    }

    private List<String> specified(String digit, String skill, boolean detailed) {
        if (detailed) {
            return List.of("指定符 · 右键把「" + digit + "」绑为「" + skill + "」快速吟唱",
                "  " + skill + "：" + GlyphCodex.detail(skill));
        }
        return List.of("指定符 · " + digit + "→" + skill);
    }

    private List<String> inscription(String s0, String s1, String s2, boolean detailed) {
        String[] slots = {s0, s1, s2};
        if ("刻".equals(s2) || (!"刻".equals(s0) && !"刻".equals(s1))) {
            return List.of(header(slots) + "废符 · 刻只能写在咒位");
        }
        if (!s2.isEmpty() && !SkillCast.isModifier(s2)) {
            return List.of(header(slots) + "废符 · 尾修槽只接受修饰字");
        }

        int markCount = 0;
        List<String> skills = new ArrayList<>();
        List<String> modifiers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String slot : slots) {
            if (slot.isEmpty()) continue;
            if (!"刻".equals(slot) && !seen.add(slot)) {
                return List.of(header(slots) + "废符 · 含重复字");
            }
            if ("刻".equals(slot)) {
                markCount++;
            } else if (SkillCast.isImplementedSkill(slot)) {
                skills.add(slot);
            } else if (SkillCast.isModifier(slot)) {
                modifiers.add(slot);
            } else {
                return List.of(header(slots) + "废符 · 刻印符字无效");
            }
        }
        if (markCount != 1 || skills.size() > 1 || modifiers.size() > 2 || (skills.isEmpty() && modifiers.isEmpty())) {
            return List.of(header(slots) + "废符 · 刻印符字无效");
        }
        if (!skills.isEmpty() && modifiers.size() > 1) {
            return List.of(header(slots) + "废符 · 刻印符字无效");
        }
        if (modifiers.contains("穿")) {
            return List.of(header(slots) + "废符 · 刻印不支持穿");
        }
        if (skills.isEmpty()) {
            return List.of("刻印操作 · " + String.join("+", modifiers) + " · " + joinInscriptionOperation(modifiers),
                "  需对准已有刻印方块");
        }
        String body = String.join("+", skills)
            + (modifiers.isEmpty() ? "" : "+" + String.join("+", modifiers));
        if (detailed) {
            return List.of("刻印符 · 对方块刻下「" + body + "」",
                "  " + joinDetail(skills) + (modifiers.isEmpty() ? "" : " / " + joinModDetail(modifiers)));
        }
        return List.of("刻印符 · " + body);
    }

    private List<String> combo(List<String> glyphs, boolean detailed) {
        String head = header(glyphs.toArray(new String[0]));
        Set<String> seen = new HashSet<>();
        List<String> skills = new ArrayList<>();
        List<String> modifiers = new ArrayList<>();
        for (String g : glyphs) {
            if (!seen.add(g)) {
                return List.of(head + "废符 · 含重复字");
            }
            if (SkillCast.isImplementedSkill(g)) {
                skills.add(g);
            } else if (SkillCast.isModifier(g)) {
                modifiers.add(g);
            } else {
                return List.of(head + "废符 · 无效字「" + g + "」");
            }
        }
        if (skills.isEmpty()) {
            return List.of(head + "废符 · 缺少技能字");
        }

        boolean hasTargetSkill = false;
        for (String s : skills) {
            if (SkillCast.hasTargetEffect(s)) {
                hasTargetSkill = true;
                break;
            }
        }
        boolean guang = modifiers.contains("广");
        boolean chuan = modifiers.contains("穿");
        boolean hasWideSupportSkill = skills.size() > 1 && skills.stream().anyMatch(SkillCast::hasWideSupportEffect);
        if (guang && chuan) {
            return List.of(head + "废符 · 广/穿不能同时出现");
        }
        if (!hasTargetSkill && chuan) {
            return List.of(head + "废符 · 自施技能不支持广/穿");
        }
        if (!hasTargetSkill && guang && !hasWideSupportSkill) {
            return List.of(head + "废符 · 自施技能不支持广/穿");
        }
        SkillCast.LinkedComboSpec spec = skills.size() == 2 && modifiers.size() <= 1
            ? SkillCast.linkedComboSpec(skills.get(0), skills.get(1))
            : null;
        if (skills.size() > 1 && chuan && (spec == null || !SkillCast.supportsPiercingCombo(spec))) {
            return List.of(head + "废符 · 该二字联动不支持穿透");
        }

        String linkedCombo = linkedComboDescription(skills, modifiers, detailed);
        if (linkedCombo != null) {
            return List.of(head + "：" + linkedCombo);
        }

        String skillsPart = detailed ? joinDetail(skills) : joinBrief(skills);
        List<String> lines = new ArrayList<>();
        lines.add(head + "：" + skillsPart);
        if (!modifiers.isEmpty()) {
            lines.add("  修饰 · " + (detailed ? joinModDetail(modifiers) : joinBrief(modifiers)));
        }
        if (guang && hasWideSupportSkill) {
            lines.add("  广域支援 · 先补施法者，溢出按距离给队友");
        }
        return lines;
    }

    private String linkedComboDescription(List<String> skills, List<String> modifiers, boolean detailed) {
        if (skills.size() != 2 || modifiers.size() > 1) return null;
        LinkedComboRegistry.ComboSpec spec = LinkedComboRegistry.lookup(skills.get(0), skills.get(1));
        if (spec == null) return null;
        String base = spec.describer().describe(detailed);
        if (modifiers.isEmpty()) return base;
        String modifier = modifiers.get(0);
        String suffix = switch (modifier) {
            case "强" -> " · 强化整体联动";
            case "续" -> " · 延长联动窗口";
            case "广" -> skills.stream().anyMatch(SkillCast::hasWideSupportEffect)
                ? " · 广域联动并分发支援溢出"
                : " · 广域联动";
            case "穿" -> SkillCast.supportsPiercingCombo(new SkillCast.LinkedComboSpec(
                    spec.key().first(), spec.key().second(), spec.kind(), spec.needsTarget(), spec.label()))
                ? " · 穿透路径多目标"
                : " · 不支持穿透";
            default -> "";
        };
        return base + suffix;
    }

    private String joinDetail(List<String> glyphs) {
        List<String> parts = new ArrayList<>();
        for (String g : glyphs) parts.add(g + " " + GlyphCodex.detail(g));
        return String.join(" / ", parts);
    }

    private String joinBrief(List<String> glyphs) {
        List<String> parts = new ArrayList<>();
        for (String g : glyphs) parts.add(GlyphCodex.brief(g));
        return String.join(" / ", parts);
    }

    private String joinModDetail(List<String> modifiers) {
        List<String> parts = new ArrayList<>();
        for (String g : modifiers) parts.add(g + "(" + GlyphCodex.detail(g) + ")");
        return String.join("  ", parts);
    }

    private String joinInscriptionOperation(List<String> modifiers) {
        List<String> parts = new ArrayList<>();
        for (String mod : modifiers) {
            parts.add(switch (mod) {
                case "续" -> "续时/补能";
                case "强" -> "加强一次";
                case "广" -> "扩大范围";
                default -> "刻印操作";
            });
        }
        return String.join(" + ", parts);
    }

    private String header(String... glyphs) {
        List<String> parts = new ArrayList<>();
        for (String g : glyphs) {
            if (g != null && !g.isEmpty()) parts.add(g);
        }
        return "「" + String.join("+", parts) + "」";
    }

    private static String at(List<String> slots, int i) {
        if (slots == null || i >= slots.size()) return "";
        String s = slots.get(i);
        return s == null ? "" : s.trim();
    }

    private static boolean isDigit(String glyph) {
        return switch (glyph) {
            case "1", "2", "3", "4", "5", "6", "7", "8", "9",
                 "一", "二", "三", "四", "五", "六", "七", "八", "九" -> true;
            default -> false;
        };
    }
}
