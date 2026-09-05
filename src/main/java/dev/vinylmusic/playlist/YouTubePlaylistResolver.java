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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class YouTubePlaylistResolver {
    private static final int MAX_TRACKS = 128;
    private static final Pattern RENDERER_ID = Pattern.compile(
        "\\\"(?:playlistVideoRenderer|playlistPanelVideoRenderer)\\\"\\s*:\\s*\\{.*?\\\"videoId\\\"\\s*:\\s*\\\"([A-Za-z0-9_-]{11})\\\"",
        Pattern.DOTALL
    );
    private static final Pattern XML_ID = Pattern.compile("<yt:videoId>([A-Za-z0-9_-]{11})</yt:videoId>");
    private static final Pattern XML_TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private YouTubePlaylistResolver() {}

    static PlaylistResolver.Playlist resolve(String listId, String originalUrl) throws IOException, InterruptedException {
        String html = get(originalUrl);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String title = listId.startsWith("RD") ? "YouTube Mix" : "YouTube Playlist";

        try {
            JsonElement root = JsonParser.parseString(initialData(html));
            collect(root, ids);
            title = clean(findTitle(root).orElse(title));
        } catch (Throwable ignored) {}

        if (ids.isEmpty()) {
            Matcher m = RENDERER_ID.matcher(html);
            while (m.find() && ids.size() < MAX_TRACKS) ids.add(m.group(1));
        }

        if (ids.isEmpty()) {
            try {
                String feed = get("https://www.youtube.com/feeds/videos.xml?playlist_id=" + listId);
                Matcher m = XML_ID.matcher(feed);
                while (m.find() && ids.size() < MAX_TRACKS) ids.add(m.group(1));
                Matcher tm = XML_TITLE.matcher(feed);
                if (tm.find() && !listId.startsWith("RD")) title = clean(unescape(tm.group(1)));
            } catch (IOException ignored) {}
        }

        if (ids.isEmpty()) throw new IOException("YouTube did not expose any tracks for this playlist/mix.");

        List<String> urls = new ArrayList<>();
        for (String id : ids) {
            if (urls.size() >= MAX_TRACKS) break;
            urls.add("https://www.youtube.com/watch?v=" + id);
        }
        return new PlaylistResolver.Playlist(title, List.copyOf(urls));
    }

    private static String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9").GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) throw new IOException("YouTube returned HTTP " + res.statusCode());
        return res.body();
    }

    private static String initialData(String html) throws IOException {
        for (String marker : new String[]{"var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = "}) {
            int i = html.indexOf(marker);
            if (i < 0) continue;
            int start = html.indexOf('{', i + marker.length());
            String obj = balancedObject(html, start);
            if (obj != null) return obj;
        }
        throw new IOException("ytInitialData not found.");
    }

    private static String balancedObject(String text, int start) {
        if (start < 0) return null;
        int depth = 0;
        boolean string = false, escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') string = false;
                continue;
            }
            if (c == '"') string = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return text.substring(start, i + 1);
        }
        return null;
    }

    private static void collect(JsonElement e, LinkedHashSet<String> ids) {
        if (e == null || e.isJsonNull() || ids.size() >= MAX_TRACKS) return;
        if (e.isJsonArray()) {
            for (JsonElement child : e.getAsJsonArray()) collect(child, ids);
            return;
        }
        if (!e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        addRenderer(o, "playlistVideoRenderer", ids);
        addRenderer(o, "playlistPanelVideoRenderer", ids);
        for (var entry : o.entrySet()) collect(entry.getValue(), ids);
    }

    private static void addRenderer(JsonObject o, String key, LinkedHashSet<String> ids) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonObject()) return;
        JsonElement id = e.getAsJsonObject().get("videoId");
        if (id != null && id.isJsonPrimitive() && id.getAsString().matches("[A-Za-z0-9_-]{11}")) ids.add(id.getAsString());
    }

    private static Optional<String> findTitle(JsonElement e) {
        if (e == null || e.isJsonNull()) return Optional.empty();
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            JsonElement metadata = o.get("playlistMetadataRenderer");
            if (metadata != null && metadata.isJsonObject()) {
                JsonElement title = metadata.getAsJsonObject().get("title");
                if (title != null && title.isJsonPrimitive()) return Optional.of(title.getAsString());
            }
            for (var entry : o.entrySet()) {
                Optional<String> found = findTitle(entry.getValue());
                if (found.isPresent()) return found;
            }
        } else if (e.isJsonArray()) {
            for (JsonElement child : e.getAsJsonArray()) {
                Optional<String> found = findTitle(child);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }

    private static String clean(String s) {
        String v = unescape(s == null ? "" : s).strip();
        return v.isBlank() ? "YouTube Playlist" : v.substring(0, Math.min(v.length(), 80));
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }
}
