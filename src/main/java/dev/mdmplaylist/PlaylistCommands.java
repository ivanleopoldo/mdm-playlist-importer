package dev.mdmplaylist;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class PlaylistCommands {
    private PlaylistCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("mdmplaylist")
                .then(Commands.literal("import")
                    .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String url = StringArgumentType.getString(ctx, "url").trim();
                            if (!PlaylistImportService.start(player, url)) return 0;
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("Playlist import started. You can keep playing while it resolves tracks."),
                                false
                            );
                            return 1;
                        })
                    )
                )
        );
    }
}
