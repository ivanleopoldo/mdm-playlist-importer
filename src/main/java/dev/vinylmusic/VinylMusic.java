package dev.vinylmusic;

import dev.vinylmusic.audio.AudioEngine;
import dev.vinylmusic.content.ModContent;
import dev.vinylmusic.network.ModNetwork;
import dev.vinylmusic.playlist.PlaylistImportService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(VinylMusic.MOD_ID)
public final class VinylMusic {
    public static final String MOD_ID = "vinyl_music";

    public VinylMusic(IEventBus modBus) {
        ModContent.register(modBus);
        modBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        AudioEngine.bootstrap();
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getLevel().getBlockState(event.getPos()).is(ModContent.RECORD_PRESS.get())) {
            event.getEntity().openMenu(new SimpleMenuProvider(
                (id, inv, player) -> new dev.vinylmusic.menu.RecordPressMenu(
                    id, inv, ContainerLevelAccess.create(event.getLevel(), event.getPos())
                ),
                Component.translatable("container.vinyl_music.record_press")
            ));
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (!event.getLevel().getBlockState(event.getPos()).is(ModContent.RECORD_PLAYER.get())) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;

        ItemStack stack = event.getItemStack();
        BlockPos pos = event.getPos();

        if (stack.isEmpty()) {
            PacketDistributor.sendToPlayersNear(
                level, null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 128,
                new dev.vinylmusic.network.StopPlaybackPayload(pos)
            );
            player.displayClientMessage(Component.literal("Record player stopped."), true);
        } else {
            var tracks = dev.vinylmusic.item.VinylData.readTracks(stack);
            if (!tracks.isEmpty()) {
                PacketDistributor.sendToPlayersNear(
                    level, null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 128,
                    new dev.vinylmusic.network.StartPlaybackPayload(pos, tracks, 64, 100)
                );
                player.displayClientMessage(
                    Component.literal(tracks.size() == 1
                        ? "Playing " + tracks.getFirst().displayName()
                        : "Playing album: " + tracks.size() + " tracks"),
                    true
                );
            }
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
