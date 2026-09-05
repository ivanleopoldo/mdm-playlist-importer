package dev.vinylmusic.network;

import dev.vinylmusic.VinylMusic;
import dev.vinylmusic.model.VinylTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record StartPlaybackPayload(
    BlockPos pos,
    List<VinylTrack> tracks,
    int rangeBlocks,
    int volumePercent
) implements CustomPacketPayload {
    public static final Type<StartPlaybackPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VinylMusic.MOD_ID, "start_playback"));

    public static final StreamCodec<FriendlyByteBuf, StartPlaybackPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.pos());
            buf.writeVarInt(payload.tracks().size());
            for (VinylTrack track : payload.tracks()) track.write(buf);
            buf.writeVarInt(payload.rangeBlocks());
            buf.writeVarInt(payload.volumePercent());
        },
        buf -> {
            BlockPos pos = buf.readBlockPos();
            int size = Math.min(buf.readVarInt(), 128);
            List<VinylTrack> tracks = new ArrayList<>(size);
            for (int i = 0; i < size; i++) tracks.add(VinylTrack.read(buf));
            return new StartPlaybackPayload(pos, List.copyOf(tracks), buf.readVarInt(), buf.readVarInt());
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
