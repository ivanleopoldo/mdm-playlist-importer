package dev.vinylmusic.playlist;

import java.io.IOException;
import java.util.List;

public final class PlaylistResolver {
    private PlaylistResolver() {}

    public record Playlist(String title, List<String> trackUrls) {}

    public static Playlist resolve(String url) throws IOException, InterruptedException {
        PlaylistUrlDetector.Match match = PlaylistUrlDetector.detect(url)
            .orElseThrow(() -> new IllegalArgumentException("No supported playlist found in that URL."));

        return switch (match.kind()) {
            case YOUTUBE -> YouTubePlaylistResolver.resolve(match.id(), match.originalUrl());
            case SPOTIFY_PLAYLIST -> SpotifyPlaylistResolver.resolvePlaylist(match.id());
            case SPOTIFY_ALBUM -> SpotifyPlaylistResolver.resolveAlbum(match.id());
            case SOUNDCLOUD -> SoundCloudPlaylistResolver.resolve(match.originalUrl());
        };
    }
}
