package com.aozainkmc.sigillum.tutorial;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class SigillumTutorialData {
    private static final String TAG_ROOT = "aozaink_sigillum";
    private static final String TAG_TUTORIAL = "tutorial";
    private static final String TAG_MENU_HINT_ACK = "menu_hint_ack";
    private static final String TAG_CRAFTED_BLANK = "crafted_blank_talisman";
    private static final String TAG_PLACE_HINT_SHOWN = "place_hint_shown";

    private SigillumTutorialData() {}

    public static boolean menuHintAck(Player player) {
        return tutorial(player).getBoolean(TAG_MENU_HINT_ACK);
    }

    public static void setMenuHintAck(Player player, boolean value) {
        CompoundTag tutorial = tutorial(player);
        tutorial.putBoolean(TAG_MENU_HINT_ACK, value);
        save(player, tutorial);
    }

    public static boolean craftedBlankTalisman(Player player) {
        return tutorial(player).getBoolean(TAG_CRAFTED_BLANK);
    }

    public static void setCraftedBlankTalisman(Player player, boolean value) {
        CompoundTag tutorial = tutorial(player);
        tutorial.putBoolean(TAG_CRAFTED_BLANK, value);
        save(player, tutorial);
    }

    public static boolean placeHintShown(Player player) {
        return tutorial(player).getBoolean(TAG_PLACE_HINT_SHOWN);
    }

    public static void setPlaceHintShown(Player player, boolean value) {
        CompoundTag tutorial = tutorial(player);
        tutorial.putBoolean(TAG_PLACE_HINT_SHOWN, value);
        save(player, tutorial);
    }

    private static CompoundTag tutorial(Player player) {
        return root(player).getCompound(TAG_TUTORIAL);
    }

    private static CompoundTag root(Player player) {
        CompoundTag root = player.getPersistentData().getCompound(TAG_ROOT);
        player.getPersistentData().put(TAG_ROOT, root);
        return root;
    }

    private static void save(Player player, CompoundTag tutorial) {
        CompoundTag root = root(player);
        root.put(TAG_TUTORIAL, tutorial);
        player.getPersistentData().put(TAG_ROOT, root);
    }
}
