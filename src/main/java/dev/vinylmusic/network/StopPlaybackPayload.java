package dev.vinylmusic.network;

import dev.vinylmusic.VinylMusic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StopPlaybackPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<StopPlaybackPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VinylMusic.MOD_ID, "stop_playback"));

    public static final StreamCodec<FriendlyByteBuf, StopPlaybackPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBlockPos(payload.pos()),
        buf -> new StopPlaybackPayload(buf.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
