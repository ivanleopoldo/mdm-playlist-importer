package dev.vinylmusic.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VinylMusicConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue ALBUM_CAPACITY;
    private static final ModConfigSpec.IntValue PLAYER_RANGE;
    private static final ModConfigSpec.IntValue PLAYER_VOLUME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("album");
        ALBUM_CAPACITY = builder
            .comment("Maximum tracks stored in one Record Album")
            .defineInRange("capacity", 24, 1, 128);
        builder.pop();

        builder.push("recordPlayer");
        PLAYER_RANGE = builder
            .comment("Audible range in blocks")
            .defineInRange("range", 64, 16, 256);
        PLAYER_VOLUME = builder
            .comment("Playback volume percent")
            .defineInRange("volumePercent", 100, 0, 200);
        builder.pop();

        SPEC = builder.build();
    }

    private VinylMusicConfig() {}

    public static int albumCapacity() {
        return SPEC.isLoaded() ? ALBUM_CAPACITY.get() : ALBUM_CAPACITY.getDefault();
    }

    public static int playerRange() {
        return SPEC.isLoaded() ? PLAYER_RANGE.get() : PLAYER_RANGE.getDefault();
    }

    public static int playerVolume() {
        return SPEC.isLoaded() ? PLAYER_VOLUME.get() : PLAYER_VOLUME.getDefault();
    }
}
