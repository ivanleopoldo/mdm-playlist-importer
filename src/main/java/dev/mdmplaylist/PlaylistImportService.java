package dev.mdmplaylist;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlaylistImportService {
    private static final int TRACKS_PER_ALBUM = 8;
    private static final int MAX_CONCURRENT_IMPORTS = 2;
    private static final AtomicInteger ACTIVE_IMPORTS = new AtomicInteger();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "mdm-playlist-importer");
        thread.setDaemon(true);
        return thread;
    });

    private PlaylistImportService() {}

    public static boolean start(ServerPlayer player, String url) {
        if (!PlaylistUrlDetector.isSupportedPlaylist(url)) {
            player.sendSystemMessage(Component.literal(
                "That URL is not a supported playlist. Use a YouTube playlist, Spotify playlist/album, or SoundCloud set."
            ));
            return false;
        }
        if (ACTIVE_IMPORTS.get() >= MAX_CONCURRENT_IMPORTS) {
            player.sendSystemMessage(Component.literal(
                "Two playlist imports are already running. Try again shortly."
            ));
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return false;

        UUID playerId = player.getUUID();
        ACTIVE_IMPORTS.incrementAndGet();
        WORKERS.execute(() -> {
            try {
                importPlaylist(server, playerId, url);
            } finally {
                ACTIVE_IMPORTS.decrementAndGet();
            }
        });
        return true;
    }

    private static void importPlaylist(MinecraftServer server, UUID playerId, String url) {
        try {
            PlaylistResolver.Playlist playlist = PlaylistResolver.resolve(url);
            send(
                server,
                playerId,
                "Found \"" + playlist.title() + "\" with "
                    + playlist.trackUrls().size() + " track(s). Resolving..."
            );

            List<ItemStack> discs = new ArrayList<>();
            int failures = 0;
            int index = 0;

            for (String trackUrl : playlist.trackUrls()) {
                index++;
                try {
                    discs.add(MusicDiscFactory.resolveUrl(trackUrl).stack());
                    if (index == 1 || index % 5 == 0 || index == playlist.trackUrls().size()) {
                        send(
                            server,
                            playerId,
                            "Resolved " + index + "/" + playlist.trackUrls().size() + " tracks..."
                        );
                    }
                } catch (Throwable t) {
                    failures++;
                    PlaylistImporterMod.LOGGER.warn(
                        "Failed to resolve playlist track {}", trackUrl, t
                    );
                }
            }

            if (discs.isEmpty()) {
                send(
                    server,
                    playerId,
                    "Import failed: the playlist was found, but none of its tracks could be resolved."
                );
                return;
            }

            final int failedTracks = failures;
            server.execute(
                () -> deliver(server, playerId, playlist.title(), discs, failedTracks)
            );
        } catch (IllegalArgumentException e) {
            send(server, playerId, "Import failed: " + e.getMessage());
        } catch (Throwable t) {
            PlaylistImporterMod.LOGGER.error("Playlist import failed", t);
            send(server, playerId, "Import failed: " + safeMessage(t));
        }
    }

    private static void deliver(
        MinecraftServer server,
        UUID playerId,
        String playlistTitle,
        List<ItemStack> discs,
        int failedTracks
    ) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;

        int blankNeeded = discs.size();
        boolean useAlbums = AdditionalAdditionsCompat.installed();
        int albumNeeded =
            useAlbums ? (discs.size() + TRACKS_PER_ALBUM - 1) / TRACKS_PER_ALBUM : 0;

        int blanksAvailable = InventoryUtil.countBlankDiscs(player);
        int albumsAvailable = useAlbums ? InventoryUtil.countEmptyAlbums(player) : 0;

        if (blanksAvailable < blankNeeded || (useAlbums && albumsAvailable < albumNeeded)) {
            String msg = "Need " + blankNeeded + " blank disc(s)"
                + (useAlbums ? " and " + albumNeeded + " empty album(s)" : "")
                + ". Nothing was consumed.";
            player.sendSystemMessage(Component.literal(msg));
            return;
        }

        if (!InventoryUtil.consumeBlankDiscs(player, blankNeeded)) {
            player.sendSystemMessage(
                Component.literal("Inventory changed before delivery; nothing was created.")
            );
            return;
        }

        if (!useAlbums) {
            for (ItemStack disc : discs) InventoryUtil.giveOrDrop(player, disc.copy());
            player.sendSystemMessage(Component.literal(
                "Imported " + discs.size() + " loose disc(s)"
                    + (failedTracks > 0 ? "; " + failedTracks + " track(s) failed." : ".")
            ));
            return;
        }

        List<ItemStack> albums = InventoryUtil.takeEmptyAlbums(player, albumNeeded);
        if (albums.size() != albumNeeded) {
            refundBlankDiscs(player, blankNeeded);
            for (ItemStack album : albums) InventoryUtil.giveOrDrop(player, album);
            player.sendSystemMessage(Component.literal(
                "Inventory changed before album delivery. Materials were refunded."
            ));
            return;
        }

        int packed = 0;
        int loose = 0;
        for (int part = 0; part < albums.size(); part++) {
            int from = part * TRACKS_PER_ALBUM;
            int to = Math.min(from + TRACKS_PER_ALBUM, discs.size());
            List<ItemStack> tracks = discs.subList(from, to);
            ItemStack album = albums.get(part);

            if (!AdditionalAdditionsCompat.fillAlbum(album, tracks)) {
                InventoryUtil.giveOrDrop(player, album);
                for (ItemStack disc : tracks) InventoryUtil.giveOrDrop(player, disc.copy());
                loose += tracks.size();
                continue;
            }

            String name = albums.size() == 1
                ? playlistTitle
                : playlistTitle + " (" + (part + 1) + "/" + albums.size() + ")";
            album.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            InventoryUtil.giveOrDrop(player, album);
            packed++;
        }

        String result = "Imported " + discs.size() + " track(s) into " + packed + " album(s)"
            + (loose > 0 ? "; " + loose + " delivered loose" : "")
            + (failedTracks > 0 ? "; " + failedTracks + " failed" : "") + ".";
        player.sendSystemMessage(Component.literal(result));
    }

    private static void refundBlankDiscs(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            InventoryUtil.giveOrDrop(
                player,
                new ItemStack(com.kuronami.musicdiscmaker.register.ModItems.BLANK_DISC.get())
            );
        }
    }

    private static void send(MinecraftServer server, UUID playerId, String message) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) player.sendSystemMessage(Component.literal(message));
        });
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.isBlank()) message = t.getClass().getSimpleName();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
