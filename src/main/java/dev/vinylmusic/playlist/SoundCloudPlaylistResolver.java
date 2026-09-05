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

final class SoundCloudPlaylistResolver {
    private static final int MAX_TRACKS = 128;
    private static final String MARKER = "window.__sc_hydration = ";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private SoundCloudPlaylistResolver() {}

    static PlaylistResolver.Playlist resolve(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (compatible; VinylMusic/0.1)")
            .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) throw new IOException("SoundCloud returned HTTP " + res.statusCode());

        JsonArray hydration = JsonParser.parseString(hydrationJson(res.body())).getAsJsonArray();
        JsonObject playlist = null;
        for (JsonElement e : hydration) {
            if (!e.isJsonObject()) continue;
            JsonObject entry = e.getAsJsonObject();
            if ("playlist".equals(str(entry, "hydratable", "")) && entry.get("data") != null && entry.get("data").isJsonObject()) {
                playlist = entry.getAsJsonObject("data");
                break;
            }
        }
        if (playlist == null) throw new IOException("SoundCloud playlist metadata missing.");

        String title = str(playlist, "title", "SoundCloud Playlist");
        JsonElement tracks = playlist.get("tracks");
        if (tracks == null || !tracks.isJsonArray()) throw new IOException("SoundCloud tracks missing.");

        List<String> urls = new ArrayList<>();
        for (JsonElement e : tracks.getAsJsonArray()) {
            if (urls.size() >= MAX_TRACKS) break;
            if (!e.isJsonObject()) continue;
            String permalink = str(e.getAsJsonObject(), "permalink_url", "");
            if (!permalink.isBlank()) urls.add(permalink);
        }
        if (urls.isEmpty()) throw new IOException("SoundCloud did not expose track links.");
        return new PlaylistResolver.Playlist(title, List.copyOf(urls));
    }

    private static String hydrationJson(String html) throws IOException {
        int marker = html.indexOf(MARKER);
        if (marker < 0) throw new IOException("SoundCloud page format changed.");
        int start = html.indexOf('[', marker + MARKER.length());
        int depth = 0;
        boolean string = false, escaped = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') string = false;
                continue;
            }
            if (c == '"') string = true;
            else if (c == '[') depth++;
            else if (c == ']' && --depth == 0) return html.substring(start, i + 1);
        }
        throw new IOException("SoundCloud hydration data incomplete.");
    }

    private static String str(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }
}
