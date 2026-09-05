package dev.vinylmusic.audio.api;

import dev.vinylmusic.audio.PcmSource;

public interface AudioBackend {
    ResolvedTrack resolve(String inputUrl);
    PcmSource openStream(String url, long startMs);
}
