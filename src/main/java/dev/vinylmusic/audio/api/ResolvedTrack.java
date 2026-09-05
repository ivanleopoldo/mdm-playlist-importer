package dev.vinylmusic.audio.api;

public record ResolvedTrack(
    String url,
    String title,
    String artist,
    long durationMs,
    String thumbnailUrl
) {}
