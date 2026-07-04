package com.aozainkmc.sigillum.glyph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GlyphCodex {
    private static final Map<String, String> BRIEF = new LinkedHashMap<>();
    private static final Map<String, String> DETAIL = new LinkedHashMap<>();

    static {
        put("镇", "减速压制", "减速+虚弱3秒(亡灵更久更强)");
        put("封", "沉默禁锢", "沉默禁锢3秒(亡灵额外定身)");
        put("退", "击退", "击退+减速1秒(亡灵击退×2)");
        put("引", "牵引", "牵引拉近目标");
        put("火", "点燃", "点燃4秒·每秒2点(亡灵3点)");
        put("雷", "雷击", "原版落雷+12点(雨天×1.3)·50%硬直");
        put("护", "护盾", "额外护盾条·极品30点·只受击消耗");
        put("净", "净化", "清负面至多3个·亡灵额外4点圣化");
        put("斩", "斩杀", "斩杀·目标≤50%血玩家处决");
        put("明", "照妖", "夜视5分钟；瞄准生物则单体显形");
        put("吸", "吸血", "吸血3点并回等量生命");
        put("魄", "招魄", "召回上次死亡掉落");
        put("强", "强化", "效果×2");
        put("续", "延时", "持续时间+品质倍率");
        put("广", "广域", "半径4格范围·效果×0.7");
        put("穿", "穿透", "穿透多个目标·命中后降档");
    }

    private static void put(String glyph, String brief, String detail) {
        BRIEF.put(glyph, brief);
        DETAIL.put(glyph, detail);
    }

    private GlyphCodex() {}

    public static String describe(String glyph) {
        return detail(glyph);
    }

    public static String describe(String glyph, boolean detailed) {
        return detailed ? detail(glyph) : brief(glyph);
    }

    public static String brief(String glyph) {
        return glyph == null ? "" : BRIEF.getOrDefault(glyph, "");
    }

    public static String detail(String glyph) {
        return glyph == null ? "" : DETAIL.getOrDefault(glyph, "");
    }

    public static List<String> glyphs() {
        return List.copyOf(BRIEF.keySet());
    }

    public static Map<String, String> all() {
        return Collections.unmodifiableMap(DETAIL);
    }
}
