package dev.vinylmusic.playlist;

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

final class SpotifyPlaylistResolver {
    private static final int MAX_TRACKS = 128;
    private static final Pattern NEXT_DATA = Pattern.compile(
        "<script[^>]*id=[\\\"']__NEXT_DATA__[\\\"'][^>]*>(.*?)</script>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private SpotifyPlaylistResolver() {}

    static PlaylistResolver.Playlist resolvePlaylist(String id) throws IOException, InterruptedException {
        return resolve("playlist", id, "Spotify Playlist");
    }

    static PlaylistResolver.Playlist resolveAlbum(String id) throws IOException, InterruptedException {
        return resolve("album", id, "Spotify Album");
    }

    private static PlaylistResolver.Playlist resolve(String kind, String id, String fallback) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://open.spotify.com/embed/" + kind + "/" + id))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36")
            .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) throw new IOException("Spotify returned HTTP " + res.statusCode());

        Matcher m = NEXT_DATA.matcher(res.body());
        if (!m.find()) throw new IOException("Spotify embed page changed format.");

        JsonObject root = JsonParser.parseString(m.group(1)).getAsJsonObject();
        JsonObject entity = path(root, "props", "pageProps", "state", "data", "entity");
        if (entity == null) throw new IOException("Spotify playlist data missing.");

        String title = str(entity, "name", fallback);
        JsonElement tracksElement = entity.get("trackList");
        if (tracksElement == null || !tracksElement.isJsonArray()) throw new IOException("Spotify playlist contains no readable tracks.");

        List<String> urls = new ArrayList<>();
        JsonArray arr = tracksElement.getAsJsonArray();
        for (JsonElement e : arr) {
            if (urls.size() >= MAX_TRACKS) break;
            if (!e.isJsonObject()) continue;
            String uri = str(e.getAsJsonObject(), "uri", "");
            if (uri.startsWith("spotify:track:")) urls.add("https://open.spotify.com/track/" + uri.substring("spotify:track:".length()));
        }
        if (urls.isEmpty()) throw new IOException("Spotify did not expose track URLs.");
        return new PlaylistResolver.Playlist(title, List.copyOf(urls));
    }

    private static JsonObject path(JsonObject root, String... keys) {
        JsonElement cur = root;
        for (String key : keys) {
            if (cur == null || !cur.isJsonObject()) return null;
            cur = cur.getAsJsonObject().get(key);
        }
        return cur != null && cur.isJsonObject() ? cur.getAsJsonObject() : null;
    }

    private static String str(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }
}
