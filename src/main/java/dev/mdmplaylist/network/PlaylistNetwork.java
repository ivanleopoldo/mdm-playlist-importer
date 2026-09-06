package dev.mdmplaylist.network;

import com.kuronami.musicdiscmaker.menu.MusicDiscMakerMenu;
import dev.mdmplaylist.PlaylistImportService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PlaylistNetwork {
    private PlaylistNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(
            ImportPlaylistPayload.TYPE,
            ImportPlaylistPayload.STREAM_CODEC,
            (payload, context) -> {
                if (!(context.player() instanceof ServerPlayer player)) return;

                String url = payload.url() == null ? "" : payload.url().trim();
                if (url.length() > 2048) {
                    player.sendSystemMessage(Component.literal("Playlist URL is too long."));
                    return;
                }

                if (!(player.containerMenu instanceof MusicDiscMakerMenu menu)
                    || !menu.getBlockEntity().getBlockPos().equals(payload.makerPos())) {
                    player.sendSystemMessage(Component.literal(
                        "Open the Music Disc Maker before importing a playlist."
                    ));
                    return;
                }

                PlaylistImportService.start(player, payload.makerPos(), url);
            }
        );
    }
}
