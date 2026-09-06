package dev.mdmplaylist;

import com.kuronami.musicdiscmaker.menu.MusicDiscMakerMenu;
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
                            if (!(player.containerMenu instanceof MusicDiscMakerMenu menu)) {
                                ctx.getSource().sendFailure(Component.literal(
                                    "Open a Music Disc Maker before importing a playlist."
                                ));
                                return 0;
                            }
                            if (!PlaylistImportService.start(
                                player,
                                menu.getBlockEntity().getBlockPos(),
                                url
                            )) return 0;
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
