package dev.vinylmusic.network;

import dev.vinylmusic.VinylMusic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreateVinylPayload(String url) implements CustomPacketPayload {
    public static final Type<CreateVinylPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VinylMusic.MOD_ID, "create_vinyl"));

    public static final StreamCodec<ByteBuf, CreateVinylPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CreateVinylPayload::url, CreateVinylPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
