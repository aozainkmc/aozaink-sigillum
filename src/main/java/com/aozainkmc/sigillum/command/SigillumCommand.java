package com.aozainkmc.sigillum.command;

import com.aozainkmc.sigillum.binding.GlyphBinding;
import com.aozainkmc.sigillum.dev.SigillumDevMode;
import com.aozainkmc.sigillum.network.SigillumNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SigillumCommand {
    private SigillumCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("sigillum")
                .then(Commands.literal("dev")
                    .requires(source -> source.hasPermission(2))
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        boolean enabled = SigillumDevMode.toggle(player);
                        ctx.getSource().sendSuccess(() ->
                            Component.literal("[Sigillum] 开发模式: " + (enabled ? "开启" : "关闭")), false);
                        return 1;
                    }))
                .then(Commands.literal("bind")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                        .then(Commands.argument("glyph", StringArgumentType.string())
                            .executes(ctx -> {
                                if (!requireDevMode(ctx)) return 0;
                                int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                String glyph = StringArgumentType.getString(ctx, "glyph");
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                String chinese = GlyphBinding.toChineseDigit(slot);
                                if (chinese.isEmpty()) {
                                    ctx.getSource().sendFailure(Component.literal("无效数字"));
                                    return 0;
                                }
                                GlyphBinding.bind(player, chinese, glyph);
                                ctx.getSource().sendSuccess(() ->
                                    Component.literal("[Sigillum] 已绑定 " + chinese + " -> " + glyph), false);
                                return 1;
                            }))))
                .then(Commands.literal("unbind")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                        .executes(ctx -> {
                            if (!requireDevMode(ctx)) return 0;
                            int slot = IntegerArgumentType.getInteger(ctx, "slot");
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String chinese = GlyphBinding.toChineseDigit(slot);
                            GlyphBinding.bind(player, chinese, "");
                            ctx.getSource().sendSuccess(() ->
                                Component.literal("[Sigillum] 已解除绑定 " + chinese), false);
                            return 1;
                        })))
                .then(Commands.literal("list")
                    .requires(source -> source.hasPermission(2))
                    .executes(ctx -> {
                        if (!requireDevMode(ctx)) return 0;
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        StringBuilder builder = new StringBuilder("[Sigillum] 绑定列表:");
                        for (int i = 1; i <= 9; i++) {
                            final int slot = i;
                            String chinese = GlyphBinding.toChineseDigit(slot);
                            GlyphBinding.getBoundGlyph(player, chinese).ifPresent(glyph ->
                                builder.append(" ").append(chinese).append("->").append(glyph));
                        }
                        String msg = builder.toString();
                        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                        return 1;
                    }))
                .then(Commands.literal("menu")
                    .executes(SigillumCommand::openMenu))
        );
    }

    private static int openMenu(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            SigillumNetworking.sendMenu(player);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("[Sigillum] 该命令只能由玩家执行"));
        }
        return 1;
    }

    private static boolean requireDevMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (SigillumDevMode.isEnabled(player)) {
            return true;
        }
        ctx.getSource().sendFailure(Component.literal("[Sigillum] 请先使用 /sigillum dev 开启开发模式"));
        return false;
    }
}
