package dev.vinylmusic.playlist;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaylistUrlDetector {
    private static final Pattern YT_LIST = Pattern.compile("(?:^|&)list=([A-Za-z0-9_-]{10,100})(?:&|$)");
    private static final Pattern SPOTIFY = Pattern.compile("^/(?:intl-[a-z]{2}(?:-[A-Z]{2})?/)?(playlist|album)/([A-Za-z0-9]+)(?:/.*)?$");

    private PlaylistUrlDetector() {}

    public enum Kind { YOUTUBE, SPOTIFY_PLAYLIST, SPOTIFY_ALBUM, SOUNDCLOUD }
    public record Match(Kind kind, String id, String originalUrl) {}

    public static Optional<Match> detect(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String url = raw.trim();
        final URI uri;
        try { uri = URI.create(url); } catch (Exception e) { return Optional.empty(); }
        String host = uri.getHost();
        if (host == null) return Optional.empty();
        host = host.toLowerCase(Locale.ROOT);

        if (host.equals("youtube.com") || host.equals("www.youtube.com") ||
            host.equals("m.youtube.com") || host.equals("music.youtube.com") || host.equals("youtu.be")) {
            Matcher m = YT_LIST.matcher(uri.getRawQuery() == null ? "" : uri.getRawQuery());
            if (m.find()) return Optional.of(new Match(Kind.YOUTUBE, m.group(1), url));
        }

        if (host.equals("open.spotify.com")) {
            Matcher m = SPOTIFY.matcher(uri.getPath() == null ? "" : uri.getPath());
            if (m.matches()) {
                return Optional.of(new Match(
                    m.group(1).equals("album") ? Kind.SPOTIFY_ALBUM : Kind.SPOTIFY_PLAYLIST,
                    m.group(2), url
                ));
            }
        }

        if ((host.equals("soundcloud.com") || host.endsWith(".soundcloud.com")) &&
            (uri.getPath() != null && uri.getPath().contains("/sets/"))) {
            return Optional.of(new Match(Kind.SOUNDCLOUD, uri.getPath(), url));
        }

        return Optional.empty();
    }
}
