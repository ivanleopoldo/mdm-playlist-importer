package dev.mdmplaylist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpotifyPlaylistResolver {
    private static final int MAX_TRACKS = 64;
    private static final Pattern NEXT_DATA = Pattern.compile(
        "<script[^>]*id=[\\\"']__NEXT_DATA__[\\\"'][^>]*>(.*?)</script>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private SpotifyPlaylistResolver() {}

    public static PlaylistResolver.Playlist resolvePlaylist(String id)
        throws IOException, InterruptedException {
        return resolve("playlist", id, "Spotify Playlist");
    }

    public static PlaylistResolver.Playlist resolveAlbum(String id)
        throws IOException, InterruptedException {
        return resolve("album", id, "Spotify Album");
    }

    private static PlaylistResolver.Playlist resolve(String kind, String id, String fallbackTitle)
        throws IOException, InterruptedException {
        URI uri = URI.create("https://open.spotify.com/embed/" + kind + "/" + id);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                "Spotify returned HTTP " + response.statusCode()
                    + ". The playlist may be private or unavailable."
            );
        }

        Matcher nextData = NEXT_DATA.matcher(response.body());
        if (!nextData.find()) {
            throw new IOException("Spotify's public embed page changed format.");
        }

        JsonObject root = JsonParser.parseString(nextData.group(1)).getAsJsonObject();
        JsonObject entity = objectPath(root, "props", "pageProps", "state", "data", "entity");
        if (entity == null) {
            throw new IOException("Spotify embed page did not contain playlist data.");
        }

        String title = string(entity, "name", fallbackTitle);
        JsonArray trackList = array(entity, "trackList");
        if (trackList == null || trackList.isEmpty()) {
            throw new IOException("No Spotify tracks were found. The playlist may be empty or private.");
        }

        List<String> urls = new ArrayList<>(Math.min(trackList.size(), MAX_TRACKS));
        for (JsonElement element : trackList) {
            if (urls.size() >= MAX_TRACKS) break;
            if (!element.isJsonObject()) continue;

            String spotifyUri = string(element.getAsJsonObject(), "uri", "");
            String trackId = spotifyUri.startsWith("spotify:track:")
                ? spotifyUri.substring("spotify:track:".length())
                : "";

            if (!trackId.isBlank()) {
                urls.add("https://open.spotify.com/track/" + trackId);
            }
        }

        if (urls.isEmpty()) {
            throw new IOException("Spotify playlist did not expose playable tracks.");
        }

        return new PlaylistResolver.Playlist(cleanTitle(title, fallbackTitle), List.copyOf(urls));
    }

    private static JsonObject objectPath(JsonObject root, String... path) {
        JsonElement current = root;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static String cleanTitle(String title, String fallback) {
        String cleaned = title == null ? "" : title.strip();
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80).strip();
        return cleaned.isBlank() ? fallback : cleaned;
    }
}
