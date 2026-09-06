package dev.mdmplaylist;

import com.kuronami.musicdiscmaker.block.MusicDiscMakerBlockEntity;
import com.kuronami.musicdiscmaker.register.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlaylistImportService {
    private static final int MAX_CONCURRENT_IMPORTS = 2;
    private static final AtomicInteger ACTIVE_IMPORTS = new AtomicInteger();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "mdm-playlist-importer");
        thread.setDaemon(true);
        return thread;
    });

    private PlaylistImportService() {}

    public static boolean start(ServerPlayer player, BlockPos makerPos, String url) {
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

        MusicDiscMakerBlockEntity maker = findMaker(player, makerPos);
        if (maker == null) {
            player.sendSystemMessage(Component.literal(
                "Music Disc Maker not found. Reopen the machine and try again."
            ));
            return false;
        }

        if (maker.isResolving()) {
            player.sendSystemMessage(Component.literal(
                "Music Disc Maker is already resolving a single track. Wait for it to finish, then import the playlist."
            ));
            return false;
        }

        ItemStack input = maker.getItem(MusicDiscMakerBlockEntity.SLOT_INPUT);
        if (!input.is(ModItems.BLANK_DISC.get()) || input.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                "Put Blank Discs in the Music Disc Maker input slot first."
            ));
            return false;
        }

        // Playlist import owns this URL. Clear MDM's normal single-track URL so
        // it does not also fabricate one track while the playlist is resolving.
        maker.setCurrentUrl("");

        MinecraftServer server = player.getServer();
        if (server == null) return false;

        UUID playerId = player.getUUID();
        BlockPos pos = makerPos.immutable();

        ACTIVE_IMPORTS.incrementAndGet();
        WORKERS.execute(() -> {
            try {
                importPlaylist(server, playerId, pos, url);
            } finally {
                ACTIVE_IMPORTS.decrementAndGet();
            }
        });
        return true;
    }

    private static void importPlaylist(
        MinecraftServer server,
        UUID playerId,
        BlockPos makerPos,
        String url
    ) {
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
                () -> deliver(server, playerId, makerPos, discs, failedTracks)
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
        BlockPos makerPos,
        List<ItemStack> discs,
        int failedTracks
    ) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;

        MusicDiscMakerBlockEntity maker = findMaker(player, makerPos);
        if (maker == null) {
            player.sendSystemMessage(Component.literal(
                "Import finished, but the Music Disc Maker is no longer available. Nothing was consumed."
            ));
            return;
        }

        int blankNeeded = discs.size();
        ItemStack input = maker.getItem(MusicDiscMakerBlockEntity.SLOT_INPUT);
        int blanksAvailable = input.is(ModItems.BLANK_DISC.get()) ? input.getCount() : 0;

        if (blanksAvailable < blankNeeded) {
            player.sendSystemMessage(Component.literal(
                "Need " + blankNeeded + " Blank Disc(s) in the Music Disc Maker input slot, but only "
                    + blanksAvailable + " are available. Nothing was consumed."
            ));
            return;
        }

        ItemStack consumed = maker.removeItem(MusicDiscMakerBlockEntity.SLOT_INPUT, blankNeeded);
        if (consumed.getCount() != blankNeeded) {
            if (!consumed.isEmpty()) {
                ItemStack refund = consumed.copy();
                ItemStack current = maker.getItem(MusicDiscMakerBlockEntity.SLOT_INPUT);
                if (current.isEmpty()) {
                    maker.setItem(MusicDiscMakerBlockEntity.SLOT_INPUT, refund);
                } else if (ItemStack.isSameItemSameComponents(current, refund)) {
                    current.grow(refund.getCount());
                    maker.setItem(MusicDiscMakerBlockEntity.SLOT_INPUT, current);
                } else {
                    InventoryUtil.giveOrDrop(player, refund);
                }
            }
            player.sendSystemMessage(Component.literal(
                "Music Disc Maker input changed during delivery. Nothing was created."
            ));
            return;
        }

        for (ItemStack disc : discs) {
            InventoryUtil.giveOrDrop(player, disc.copy());
        }

        player.sendSystemMessage(Component.literal(
            "Imported " + discs.size() + " loose MDM disc(s)"
                + (failedTracks > 0 ? "; " + failedTracks + " track(s) failed." : ".")
        ));
    }

    private static MusicDiscMakerBlockEntity findMaker(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5
        ) > 64.0) {
            return null;
        }

        BlockEntity blockEntity = player.serverLevel().getBlockEntity(pos);
        return blockEntity instanceof MusicDiscMakerBlockEntity maker ? maker : null;
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
