package dev.mdmplaylist;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaylistUrlDetector {
    private static final Pattern YOUTUBE_LIST =
        Pattern.compile("(?:^|[?&])list=([A-Za-z0-9_-]{10,100})(?:&|$)");
    private static final Pattern SPOTIFY =
        Pattern.compile("^/(?:intl-[a-z]{2}(?:-[A-Z]{2})?/)?(playlist|album)/([A-Za-z0-9]+)(?:/.*)?$");

    private PlaylistUrlDetector() {}

    public enum Kind {
        YOUTUBE_PLAYLIST,
        SPOTIFY_PLAYLIST,
        SPOTIFY_ALBUM
    }

    public record Match(Kind kind, String id) {}

    public static boolean isSupportedPlaylist(String url) {
        return detect(url).isPresent();
    }

    public static Optional<Match> detect(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return Optional.empty();

        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        String host = uri.getHost();
        if (host == null) return Optional.empty();
        host = host.toLowerCase(Locale.ROOT);

        if (isYouTubeHost(host)) {
            String path = uri.getPath() == null ? "" : uri.getPath();
            // Auto-import only explicit playlist pages. A watch URL copied while browsing
            // a playlist may also contain list=, but should remain a normal single-track URL.
            if (!"/playlist".equals(path)) return Optional.empty();

            Matcher matcher = YOUTUBE_LIST.matcher(uri.getRawQuery() == null ? "" : uri.getRawQuery());
            if (matcher.find()) {
                return Optional.of(new Match(Kind.YOUTUBE_PLAYLIST, matcher.group(1)));
            }
            return Optional.empty();
        }

        if ("open.spotify.com".equals(host)) {
            Matcher matcher = SPOTIFY.matcher(uri.getPath() == null ? "" : uri.getPath());
            if (matcher.matches()) {
                Kind kind = "album".equals(matcher.group(1))
                    ? Kind.SPOTIFY_ALBUM
                    : Kind.SPOTIFY_PLAYLIST;
                return Optional.of(new Match(kind, matcher.group(2)));
            }
        }

        return Optional.empty();
    }

    private static boolean isYouTubeHost(String host) {
        return "youtube.com".equals(host)
            || "www.youtube.com".equals(host)
            || "m.youtube.com".equals(host)
            || "music.youtube.com".equals(host);
    }
}
