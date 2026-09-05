package dev.vinylmusic.audio;

import dev.vinylmusic.audio.api.ResolvedTrack;
import dev.vinylmusic.model.VinylTrack;

public final class AudioEngine {
    private AudioEngine() {}

    public static VinylTrack resolveTrack(String inputUrl) {
        ResolvedTrack track = AudioBackendLoader.get().resolve(inputUrl);
        return new VinylTrack(
            track.url(),
            track.title(),
            track.artist(),
            track.durationMs(),
            track.thumbnailUrl()
        );
    }

    public static PcmSource openStream(String url, long startMs) {
        return AudioBackendLoader.get().openStream(url, startMs);
    }
}
