package com.aozainkmc.sigillum.glyph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GlyphCodex {
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("镇", "镇压·减速与压制");
        DESCRIPTIONS.put("封", "封印·沉默禁锢");
        DESCRIPTIONS.put("退", "击退·推开目标");
        DESCRIPTIONS.put("引", "牵引·拉近目标");
        DESCRIPTIONS.put("火", "点燃·持续火焰伤害");
        DESCRIPTIONS.put("雷", "雷击·爆发雷电伤害");
        DESCRIPTIONS.put("护", "护盾·吸收伤害");
        DESCRIPTIONS.put("净", "净化·清负面克亡灵");
        DESCRIPTIONS.put("强", "强化·提升效果强度");
        DESCRIPTIONS.put("续", "延时·延长持续时间");
        DESCRIPTIONS.put("疾", "疾速·缩短前摇");
        DESCRIPTIONS.put("广", "广域·扩大作用范围");
    }

    private GlyphCodex() {}

    public static String describe(String glyph) {
        return glyph == null ? "" : DESCRIPTIONS.getOrDefault(glyph, "");
    }

    public static List<String> glyphs() {
        return List.copyOf(DESCRIPTIONS.keySet());
    }

    public static Map<String, String> all() {
        return Collections.unmodifiableMap(DESCRIPTIONS);
    }
}
