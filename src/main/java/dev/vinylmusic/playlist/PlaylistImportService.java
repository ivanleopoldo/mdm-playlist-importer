package dev.vinylmusic.playlist;

import dev.vinylmusic.audio.AudioEngine;
import dev.vinylmusic.item.VinylData;
import dev.vinylmusic.model.VinylTrack;
import dev.vinylmusic.util.InventoryUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlaylistImportService {
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "vinyl-music-import");
        t.setDaemon(true);
        return t;
    });

    private PlaylistImportService() {}

    public static void createSingle(ServerPlayer player, String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isBlank()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID id = player.getUUID();

        message(server, id, "Resolving track...");
        WORKERS.submit(() -> {
            try {
                VinylTrack track = AudioEngine.resolveTrack(url);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p == null) return;
                    if (!InventoryUtil.consumeBlankVinyl(p, 1)) {
                        p.sendSystemMessage(Component.literal("Need 1 Blank Vinyl. Nothing was consumed."));
                        return;
                    }
                    InventoryUtil.giveOrDrop(p, VinylData.createVinyl(track));
                    p.sendSystemMessage(Component.literal("Created: " + track.displayName()));
                });
            } catch (Throwable t) {
                message(server, id, "Could not create record: " + shortMessage(t));
            }
        });
    }

    public static void importPlaylist(ServerPlayer player, String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isBlank()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID id = player.getUUID();

        message(server, id, "Reading playlist...");
        WORKERS.submit(() -> {
            try {
                PlaylistResolver.Playlist playlist = PlaylistResolver.resolve(url);
                List<VinylTrack> resolved = new ArrayList<>();
                int failed = 0;
                int i = 0;

                for (String trackUrl : playlist.trackUrls()) {
                    i++;
                    try {
                        resolved.add(AudioEngine.resolveTrack(trackUrl));
                    } catch (Throwable t) {
                        failed++;
                    }
                    if (i == 1 || i % 5 == 0 || i == playlist.trackUrls().size()) {
                        message(server, id, "Resolved " + i + "/" + playlist.trackUrls().size() + "...");
                    }
                }

                if (resolved.isEmpty()) {
                    message(server, id, "Playlist found, but no tracks could be resolved.");
                    return;
                }

                int albumsNeeded = (resolved.size() + VinylData.albumCapacity() - 1)
                    / VinylData.albumCapacity();
                int failures = failed;

                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p == null) return;

                    if (InventoryUtil.countBlankVinyl(p) < resolved.size()) {
                        p.sendSystemMessage(Component.literal(
                            "Need " + resolved.size() + " Blank Vinyl(s). Nothing was consumed."
                        ));
                        return;
                    }

                    InventoryUtil.consumeBlankVinyl(p, resolved.size());

                    for (VinylTrack track : resolved) {
                        InventoryUtil.giveOrDrop(p, VinylData.createVinyl(track));
                    }

                    for (int part = 0; part < albumsNeeded; part++) {
                        int from = part * VinylData.albumCapacity();
                        int to = Math.min(from + VinylData.albumCapacity(), resolved.size());
                        String name = albumsNeeded == 1
                            ? playlist.title()
                            : playlist.title() + " (" + (part + 1) + "/" + albumsNeeded + ")";
                        InventoryUtil.giveOrDrop(p, VinylData.createAlbum(name, resolved.subList(from, to)));
                    }

                    p.sendSystemMessage(Component.literal(
                        "Imported " + resolved.size() + " track(s) into " + albumsNeeded + " album(s)"
                            + (failures > 0 ? "; " + failures + " failed." : ".")
                    ));
                });
            } catch (Throwable t) {
                message(server, id, "Could not import playlist: " + shortMessage(t));
            }
        });
    }

    private static void message(MinecraftServer server, UUID id, String text) {
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(Component.literal(text));
        });
    }

    private static String shortMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        String m = cur.getMessage();
        if (m == null || m.isBlank()) m = cur.getClass().getSimpleName();
        return m.length() > 180 ? m.substring(0, 180) : m;
    }
}
