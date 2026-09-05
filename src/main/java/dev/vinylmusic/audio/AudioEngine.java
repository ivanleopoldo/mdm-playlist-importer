package dev.vinylmusic.audio;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.vinylmusic.model.VinylTrack;
import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class AudioEngine {
    private static final AudioPlayerManager MANAGER = new DefaultAudioPlayerManager();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static volatile boolean bootstrapped;

    private AudioEngine() {}

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        MANAGER.getConfiguration().setOutputFormat(new Pcm16AudioDataFormat(2, 48_000, 960, false));
        MANAGER.registerSourceManager(new YoutubeAudioSourceManager());
        MANAGER.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        MANAGER.registerSourceManager(new BandcampAudioSourceManager());
        MANAGER.registerSourceManager(new HttpAudioSourceManager());
        bootstrapped = true;
    }

    public static VinylTrack resolveTrack(String inputUrl) {
        bootstrap();
        String url = inputUrl == null ? "" : inputUrl.trim();
        if (url.isBlank()) throw new IllegalArgumentException("URL is empty.");

        if (isSpotifyTrack(url)) {
            String[] meta = spotifyMeta(url);
            String query = (meta[1].isBlank() ? "" : meta[1] + " ") + meta[0];
            AudioTrack playable = loadTrack("ytsearch:" + query);
            AudioTrackInfo p = playable.getInfo();
            return new VinylTrack(
                p.uri == null || p.uri.isBlank() ? "https://www.youtube.com/watch?v=" + p.identifier : p.uri,
                meta[0],
                meta[1],
                p.length,
                p.artworkUrl == null ? "" : p.artworkUrl
            );
        }

        AudioTrack track = loadTrack(url);
        AudioTrackInfo info = track.getInfo();
        return new VinylTrack(
            info.uri == null || info.uri.isBlank() ? url : info.uri,
            clean(info.title),
            clean(info.author),
            info.length,
            info.artworkUrl == null ? "" : info.artworkUrl
        );
    }

    public static PcmSource openStream(String url, long startMs) {
        bootstrap();
        AudioTrack track = loadTrack(url);
        if (startMs > 0 && track.isSeekable()) track.setPosition(startMs);
        AudioPlayer player = MANAGER.createPlayer();
        player.playTrack(track);
        return new LavaPcmSource(player);
    }

    private static AudioTrack loadTrack(String url) {
        CompletableFuture<AudioTrack> result = new CompletableFuture<>();
        MANAGER.loadItem(url, new AudioLoadResultHandler() {
            @Override public void trackLoaded(AudioTrack track) { result.complete(track); }
            @Override public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getSelectedTrack();
                if (track == null && !playlist.getTracks().isEmpty()) track = playlist.getTracks().getFirst();
                result.complete(track);
            }
            @Override public void noMatches() { result.complete(null); }
            @Override public void loadFailed(com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
                result.completeExceptionally(exception);
            }
        });

        try {
            AudioTrack track = result.get(30, TimeUnit.SECONDS);
            if (track == null) throw new IllegalArgumentException("No playable track found.");
            return track;
        } catch (Exception e) {
            throw new IllegalStateException("Could not resolve audio: " + rootMessage(e), e);
        }
    }

    private static boolean isSpotifyTrack(String url) {
        try {
            URI uri = URI.create(url);
            return "open.spotify.com".equalsIgnoreCase(uri.getHost()) && uri.getPath().contains("/track/");
        } catch (Exception e) {
            return false;
        }
    }

    private static String[] spotifyMeta(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "facebookexternalhit/1.1")
                .header("Accept-Language", "en")
                .GET().build();
            String html = HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
            String title = og(html, "title");
            String description = og(html, "description");
            if (title == null || title.isBlank()) throw new IllegalStateException("Spotify metadata unavailable.");
            String artist = "";
            if (description != null && !description.isBlank()) {
                String[] parts = description.split(" · ");
                if (parts.length > 0) artist = parts[0].trim();
            }
            return new String[]{unescape(title).trim(), unescape(artist).trim()};
        } catch (Exception e) {
            throw new IllegalStateException("Could not read Spotify track metadata.", e);
        }
    }

    private static String og(String html, String property) {
        String lower = html.toLowerCase(Locale.ROOT);
        String needle = "property=\"og:" + property.toLowerCase(Locale.ROOT) + "\"";
        int start = lower.indexOf(needle);
        if (start < 0) return null;
        int tagStart = html.lastIndexOf("<meta", start);
        int tagEnd = html.indexOf('>', start);
        if (tagStart < 0 || tagEnd < 0) return null;
        String tag = html.substring(tagStart, tagEnd + 1);
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?i)content\\s*=\\s*[\\"']([^\\"']*)[\\"']")
            .matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    private static String unescape(String s) {
        return s == null ? "" : s.replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'");
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? "Unknown" : s.trim();
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        String m = cur.getMessage();
        return m == null || m.isBlank() ? cur.getClass().getSimpleName() : m;
    }
}
