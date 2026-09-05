package dev.mdmplaylist.network;

import dev.mdmplaylist.PlaylistImportService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PlaylistNetwork {
    private PlaylistNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            ImportPlaylistPayload.TYPE,
            ImportPlaylistPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    String url = payload.url() == null ? "" : payload.url().trim();
                    if (url.length() > 2048) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Playlist URL is too long."));
                        return;
                    }
                    PlaylistImportService.start(player, url);
                }
            }
        );
    }
}
