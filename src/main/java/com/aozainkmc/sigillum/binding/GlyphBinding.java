package com.aozainkmc.sigillum.binding;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class GlyphBinding {
    private static final String TAG_ROOT = "aozaink_sigillum";
    private static final String TAG_BINDINGS = "bindings";

    private static final Map<String, Integer> CHINESE_TO_ARABIC = new HashMap<>();
    private static final Map<Integer, String> ARABIC_TO_CHINESE = new HashMap<>();

    static {
        String[] chinese = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
        for (int i = 0; i < chinese.length; i++) {
            CHINESE_TO_ARABIC.put(chinese[i], i + 1);
            ARABIC_TO_CHINESE.put(i + 1, chinese[i]);
        }
    }

    private GlyphBinding() {}

    public static void bind(ServerPlayer player, String chineseDigit, String glyph) {
        if (!isChineseDigit(chineseDigit)) return;
        CompoundTag root = player.getPersistentData().getCompound(TAG_ROOT);
        CompoundTag bindings = root.getCompound(TAG_BINDINGS);
        bindings.putString(chineseDigit, glyph);
        root.put(TAG_BINDINGS, bindings);
        player.getPersistentData().put(TAG_ROOT, root);
    }

    public static Optional<String> getBoundGlyph(Player player, String chineseDigit) {
        if (!isChineseDigit(chineseDigit)) return Optional.empty();
        CompoundTag root = player.getPersistentData().getCompound(TAG_ROOT);
        CompoundTag bindings = root.getCompound(TAG_BINDINGS);
        if (!bindings.contains(chineseDigit)) return Optional.empty();
        String glyph = bindings.getString(chineseDigit);
        return glyph.isBlank() ? Optional.empty() : Optional.of(glyph);
    }

    public static boolean isChineseDigit(String glyph) {
        return CHINESE_TO_ARABIC.containsKey(glyph);
    }

    public static String toChineseDigit(int arabic) {
        return ARABIC_TO_CHINESE.getOrDefault(arabic, "");
    }

    public static int toArabic(String chineseDigit) {
        return CHINESE_TO_ARABIC.getOrDefault(chineseDigit, 0);
    }
}
