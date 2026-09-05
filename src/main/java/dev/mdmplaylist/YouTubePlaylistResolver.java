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
    private static final int MAX_TRACKS = 64;

    private static final Pattern PLAYLIST_RENDERER_VIDEO_ID = Pattern.compile(
        "\\\"(?:playlistVideoRenderer|playlistPanelVideoRenderer)\\\"\\s*:\\s*\\{.*?"
            + "\\\"videoId\\\"\\s*:\\s*\\\"([A-Za-z0-9_-]{11})\\\"",
        Pattern.DOTALL
    );
    private static final Pattern XML_VIDEO_ID =
        Pattern.compile("<yt:videoId>([A-Za-z0-9_-]{11})</yt:videoId>");
    private static final Pattern XML_TITLE =
        Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private YouTubePlaylistResolver() {}

    public static PlaylistResolver.Playlist resolve(String playlistId, String originalUrl)
        throws IOException, InterruptedException {

        // Important for YouTube Mix/Radio URLs (RD..., RDEM..., start_radio=1):
        // resolve the exact watch URL the user pasted. /playlist?list=... is not always
        // a usable page for generated mixes.
        String pageUrl = originalUrl;
        String html = get(
            pageUrl,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
        );

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String title = playlistId.startsWith("RD") ? "YouTube Mix" : "YouTube Playlist";

        try {
            JsonElement root = JsonParser.parseString(findInitialDataJson(html));
            collectVideoIds(root, ids);
            title = cleanTitle(findPlaylistTitle(root).orElse(title));
        } catch (Throwable ignored) {
            // Continue with a renderer-specific raw-page fallback.
        }

        if (ids.isEmpty()) {
            Matcher fallback = PLAYLIST_RENDERER_VIDEO_ID.matcher(html);
            while (fallback.find() && ids.size() < MAX_TRACKS) {
                ids.add(fallback.group(1));
            }
        }

        // Static public playlists also expose an Atom feed. Generated YouTube
        // Mix/Radio playlists often do not, so failure here is intentionally ignored.
        if (ids.isEmpty()) {
            try {
                String feed = get(
                    "https://www.youtube.com/feeds/videos.xml?playlist_id=" + playlistId,
                    "Mozilla/5.0 (compatible; MDMPlaylistImporter/0.4)"
                );
                Matcher idMatcher = XML_VIDEO_ID.matcher(feed);
                while (idMatcher.find() && ids.size() < MAX_TRACKS) {
                    ids.add(idMatcher.group(1));
                }
                if ("YouTube Playlist".equals(title)) {
                    Matcher titleMatcher = XML_TITLE.matcher(feed);
                    if (titleMatcher.find()) {
                        title = cleanTitle(unescapeXml(titleMatcher.group(1)));
                    }
                }
            } catch (IOException ignored) {
                // Expected for some generated mixes.
            }
        }

        if (ids.isEmpty()) {
            throw new IOException(
                "YouTube detected the playlist/mix URL, but did not expose its track queue."
            );
        }

        List<String> urls = new ArrayList<>(Math.min(ids.size(), MAX_TRACKS));
        for (String id : ids) {
            if (urls.size() >= MAX_TRACKS) break;
            urls.add("https://www.youtube.com/watch?v=" + id);
        }

        return new PlaylistResolver.Playlist(title, List.copyOf(urls));
    }

    private static String get(String url, String userAgent)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", userAgent)
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("YouTube returned HTTP " + response.statusCode());
        }
        return response.body();
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
        throw new IOException("ytInitialData was not present.");
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
        if (element == null || element.isJsonNull() || ids.size() >= MAX_TRACKS) return;

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
            if (value.matches("[A-Za-z0-9_-]{11}")) ids.add(value);
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

            JsonElement panel = object.get("playlistPanelRenderer");
            if (panel != null && panel.isJsonObject()) {
                JsonElement panelTitle = panel.getAsJsonObject().get("title");
                Optional<String> text = textFromRuns(panelTitle);
                if (text.isPresent()) return text;
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

    private static Optional<String> textFromRuns(JsonElement value) {
        if (value == null || !value.isJsonObject()) return Optional.empty();
        JsonObject obj = value.getAsJsonObject();

        JsonElement simple = obj.get("simpleText");
        if (simple != null && simple.isJsonPrimitive()) {
            return Optional.of(simple.getAsString());
        }

        JsonElement runs = obj.get("runs");
        if (runs != null && runs.isJsonArray()) {
            StringBuilder out = new StringBuilder();
            for (JsonElement run : runs.getAsJsonArray()) {
                if (!run.isJsonObject()) continue;
                JsonElement text = run.getAsJsonObject().get("text");
                if (text != null && text.isJsonPrimitive()) out.append(text.getAsString());
            }
            if (!out.isEmpty()) return Optional.of(out.toString());
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

    private static String unescapeXml(String text) {
        return text.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">");
    }
}
