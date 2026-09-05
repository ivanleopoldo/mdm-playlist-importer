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

public final class SoundCloudPlaylistResolver {
    private static final int MAX_TRACKS = 64;
    private static final String MARKER = "window.__sc_hydration = ";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private SoundCloudPlaylistResolver() {}

    public static PlaylistResolver.Playlist resolve(String url)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (compatible; MDMPlaylistImporter/0.3)")
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("SoundCloud returned HTTP " + response.statusCode());
        }

        String json = hydrationJson(response.body());
        JsonArray hydration = JsonParser.parseString(json).getAsJsonArray();

        JsonObject playlist = null;
        for (JsonElement element : hydration) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            if ("playlist".equals(string(entry, "hydratable", ""))) {
                JsonElement data = entry.get("data");
                if (data != null && data.isJsonObject()) {
                    playlist = data.getAsJsonObject();
                    break;
                }
            }
        }

        if (playlist == null) {
            throw new IOException("SoundCloud playlist metadata was not found.");
        }

        String title = string(playlist, "title", "SoundCloud Playlist");
        JsonElement tracksElement = playlist.get("tracks");
        if (tracksElement == null || !tracksElement.isJsonArray()) {
            throw new IOException("No SoundCloud tracks were found.");
        }

        List<String> urls = new ArrayList<>();
        for (JsonElement trackElement : tracksElement.getAsJsonArray()) {
            if (urls.size() >= MAX_TRACKS) break;
            if (!trackElement.isJsonObject()) continue;
            String permalink = string(trackElement.getAsJsonObject(), "permalink_url", "");
            if (!permalink.isBlank()) urls.add(permalink);
        }

        if (urls.isEmpty()) {
            throw new IOException(
                "SoundCloud found the set, but its page did not expose track links."
            );
        }

        return new PlaylistResolver.Playlist(cleanTitle(title), List.copyOf(urls));
    }

    private static String hydrationJson(String html) throws IOException {
        int marker = html.indexOf(MARKER);
        if (marker < 0) throw new IOException("SoundCloud page changed format.");
        int start = html.indexOf('[', marker + MARKER.length());
        if (start < 0) throw new IOException("SoundCloud hydration data was missing.");

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return html.substring(start, i + 1);
            }
        }
        throw new IOException("SoundCloud hydration data was incomplete.");
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static String cleanTitle(String title) {
        String cleaned = title == null ? "" : title.strip();
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80).strip();
        return cleaned.isBlank() ? "SoundCloud Playlist" : cleaned;
    }
}
