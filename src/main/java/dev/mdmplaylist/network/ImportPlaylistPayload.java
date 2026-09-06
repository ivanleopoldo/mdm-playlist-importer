package dev.mdmplaylist.network;

import dev.mdmplaylist.PlaylistImporterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImportPlaylistPayload(BlockPos makerPos, String url) implements CustomPacketPayload {
    public static final Type<ImportPlaylistPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(PlaylistImporterMod.MOD_ID, "import_playlist")
    );

    public static final StreamCodec<ByteBuf, ImportPlaylistPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        ImportPlaylistPayload::makerPos,
        ByteBufCodecs.STRING_UTF8,
        ImportPlaylistPayload::url,
        ImportPlaylistPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
