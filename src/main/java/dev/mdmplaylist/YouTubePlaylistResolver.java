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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YouTubePlaylistResolver {
    private static final Pattern LIST_ID =
        Pattern.compile("(?:[?&])list=([A-Za-z0-9_-]{10,100})");
    private static final int MAX_TRACKS = 64;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private YouTubePlaylistResolver() {}

    public record Playlist(String title, List<String> videoIds) {}

    public static Playlist resolve(String suppliedUrl) throws IOException, InterruptedException {
        String playlistId = extractPlaylistId(suppliedUrl)
            .orElseThrow(() -> new IllegalArgumentException("That is not a YouTube playlist URL."));

        URI uri = URI.create("https://www.youtube.com/playlist?list=" + playlistId + "&hl=en");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (compatible; MDMPlaylistImporter/0.2)")
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("YouTube returned HTTP " + response.statusCode());
        }

        JsonElement root = JsonParser.parseString(findInitialDataJson(response.body()));
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        collectVideoIds(root, ids);
        if (ids.isEmpty()) {
            throw new IOException("No playlist tracks were found. The playlist may be private or unavailable.");
        }

        List<String> limited = new ArrayList<>(Math.min(ids.size(), MAX_TRACKS));
        for (String id : ids) {
            if (limited.size() >= MAX_TRACKS) break;
            limited.add(id);
        }

        return new Playlist(
            cleanTitle(findPlaylistTitle(root).orElse("YouTube Playlist")),
            List.copyOf(limited)
        );
    }

    public static Optional<String> extractPlaylistId(String url) {
        if (url == null) return Optional.empty();
        Matcher matcher = LIST_ID.matcher(url);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String findInitialDataJson(String html) throws IOException {
        String[] markers = {
            "var ytInitialData = ",
            "window[\"ytInitialData\"] = ",
            "ytInitialData = "
        };

        for (String marker : markers) {
            int markerIndex = html.indexOf(marker);
            if (markerIndex < 0) continue;
            int start = html.indexOf('{', markerIndex + marker.length());
            if (start >= 0) {
                String json = extractBalancedObject(html, start);
                if (json != null) return json;
            }
        }

        int key = html.indexOf("ytInitialData");
        while (key >= 0) {
            int start = html.indexOf('{', key);
            if (start >= 0) {
                String json = extractBalancedObject(html, start);
                if (json != null && json.contains("playlistVideoRenderer")) return json;
            }
            key = html.indexOf("ytInitialData", key + 1);
        }

        throw new IOException("Could not read YouTube playlist metadata.");
    }

    private static String extractBalancedObject(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }

            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
                if (depth < 0) return null;
            }
        }
        return null;
    }

    private static void collectVideoIds(JsonElement element, LinkedHashSet<String> ids) {
        if (element == null || element.isJsonNull()) return;

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) collectVideoIds(child, ids);
            return;
        }

        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();

        addRendererVideoId(object, "playlistVideoRenderer", ids);
        addRendererVideoId(object, "playlistPanelVideoRenderer", ids);

        for (var entry : object.entrySet()) {
            collectVideoIds(entry.getValue(), ids);
        }
    }

    private static void addRendererVideoId(
        JsonObject object,
        String rendererKey,
        LinkedHashSet<String> ids
    ) {
        JsonElement renderer = object.get(rendererKey);
        if (renderer == null || !renderer.isJsonObject()) return;
        JsonElement id = renderer.getAsJsonObject().get("videoId");
        if (id != null && id.isJsonPrimitive()) {
            String value = id.getAsString();
            if (value.matches("[A-Za-z0-9_-]{6,20}")) ids.add(value);
        }
    }

    private static Optional<String> findPlaylistTitle(JsonElement element) {
        if (element == null || element.isJsonNull()) return Optional.empty();

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement metadata = object.get("playlistMetadataRenderer");
            if (metadata != null && metadata.isJsonObject()) {
                JsonElement title = metadata.getAsJsonObject().get("title");
                if (title != null && title.isJsonPrimitive()) {
                    return Optional.of(title.getAsString());
                }
            }
            for (var entry : object.entrySet()) {
                Optional<String> found = findPlaylistTitle(entry.getValue());
                if (found.isPresent()) return found;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                Optional<String> found = findPlaylistTitle(child);
                if (found.isPresent()) return found;
            }
        }

        return Optional.empty();
    }

    private static String cleanTitle(String title) {
        String cleaned = title
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .strip();
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80).strip();
        return cleaned.isBlank() ? "YouTube Playlist" : cleaned;
    }
}
