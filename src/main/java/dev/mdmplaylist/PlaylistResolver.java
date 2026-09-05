package dev.mdmplaylist;

import java.io.IOException;
import java.util.List;

public final class PlaylistResolver {
    private PlaylistResolver() {}

    public record Playlist(String title, List<String> trackUrls) {}

    public static Playlist resolve(String url) throws IOException, InterruptedException {
        PlaylistUrlDetector.Match match = PlaylistUrlDetector.detect(url)
            .orElseThrow(() -> new IllegalArgumentException(
                "Supported playlist URLs: YouTube playlists, Spotify playlists, and Spotify albums."
            ));

        return switch (match.kind()) {
            case YOUTUBE_PLAYLIST -> YouTubePlaylistResolver.resolve(match.id());
            case SPOTIFY_PLAYLIST -> SpotifyPlaylistResolver.resolvePlaylist(match.id());
            case SPOTIFY_ALBUM -> SpotifyPlaylistResolver.resolveAlbum(match.id());
        };
    }
}
