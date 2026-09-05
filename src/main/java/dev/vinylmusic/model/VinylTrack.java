package dev.vinylmusic.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record VinylTrack(
    String url,
    String title,
    String artist,
    long durationMs,
    String thumbnailUrl
) {
    public String displayName() {
        return artist == null || artist.isBlank() ? title : artist + " - " + title;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("url", safe(url));
        tag.putString("title", safe(title));
        tag.putString("artist", safe(artist));
        tag.putLong("duration", durationMs);
        tag.putString("thumbnail", safe(thumbnailUrl));
        return tag;
    }

    public static VinylTrack fromTag(CompoundTag tag) {
        return new VinylTrack(
            tag.getString("url"),
            tag.getString("title"),
            tag.getString("artist"),
            tag.getLong("duration"),
            tag.getString("thumbnail")
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(safe(url), 4096);
        buf.writeUtf(safe(title), 512);
        buf.writeUtf(safe(artist), 512);
        buf.writeLong(durationMs);
        buf.writeUtf(safe(thumbnailUrl), 4096);
    }

    public static VinylTrack read(FriendlyByteBuf buf) {
        return new VinylTrack(
            buf.readUtf(4096),
            buf.readUtf(512),
            buf.readUtf(512),
            buf.readLong(),
            buf.readUtf(4096)
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
