package com.aozainkmc.sigillum.cast;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Whether two players are on the same side, answered from the scoreboard by name so it still works
 * while either of them is offline (an inscription keeps working after its owner logs off).
 */
public final class SigillumTeams {
    private SigillumTeams() {}

    public static boolean sameSide(MinecraftServer server, UUID first, UUID second) {
        if (first.equals(second)) return true;
        PlayerTeam team = teamOf(server, first);
        return team != null && team == teamOf(server, second);
    }

    @Nullable
    private static PlayerTeam teamOf(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        String name = online != null ? online.getScoreboardName() : cachedName(server, player);
        return name == null ? null : server.getScoreboard().getPlayersTeam(name);
    }

    @Nullable
    private static String cachedName(MinecraftServer server, UUID player) {
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? null : cache.get(player).map(GameProfile::getName).orElse(null);
    }
}
