package dev.vinylmusic.network;

import dev.vinylmusic.playlist.PlaylistImportService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
            CreateVinylPayload.TYPE,
            CreateVinylPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    PlaylistImportService.createSingle(player, payload.url());
                }
            }
        );

        registrar.playToServer(
            ImportPlaylistPayload.TYPE,
            ImportPlaylistPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    PlaylistImportService.importPlaylist(player, payload.url());
                }
            }
        );

        registrar.playToClient(
            StartPlaybackPayload.TYPE,
            StartPlaybackPayload.STREAM_CODEC,
            (payload, context) -> ClientBridge.START.accept(payload)
        );

        registrar.playToClient(
            StopPlaybackPayload.TYPE,
            StopPlaybackPayload.STREAM_CODEC,
            (payload, context) -> ClientBridge.STOP.accept(payload)
        );
    }
}
