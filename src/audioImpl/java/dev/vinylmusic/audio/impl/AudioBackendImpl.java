package dev.vinylmusic.audio.impl;

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
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.vinylmusic.audio.PcmSource;
import dev.vinylmusic.audio.api.AudioBackend;
import dev.vinylmusic.audio.api.ResolvedTrack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class AudioBackendImpl implements AudioBackend {
    private final AudioPlayerManager manager;
    private final HttpClient http;

    public AudioBackendImpl() {
        manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(new Pcm16AudioDataFormat(2, 48_000, 960, false));
        manager.registerSourceManager(new YoutubeAudioSourceManager());
        manager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        manager.registerSourceManager(new BandcampAudioSourceManager());
        manager.registerSourceManager(new HttpAudioSourceManager());

        http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public ResolvedTrack resolve(String inputUrl) {
        String url = inputUrl == null ? "" : inputUrl.trim();
        if (url.isBlank()) throw new IllegalArgumentException("URL is empty.");

        if (isSpotifyTrack(url)) {
            String[] metadata = spotifyMetadata(url);
            String query = (metadata[1].isBlank() ? "" : metadata[1] + " ") + metadata[0];
            AudioTrack playable = loadTrack("ytsearch:" + query);
            AudioTrackInfo info = playable.getInfo();

            return new ResolvedTrack(
                info.uri == null || info.uri.isBlank()
                    ? "https://www.youtube.com/watch?v=" + info.identifier
                    : info.uri,
                metadata[0],
                metadata[1],
                info.length,
                info.artworkUrl == null ? "" : info.artworkUrl
            );
        }

        AudioTrack track = loadTrack(url);
        AudioTrackInfo info = track.getInfo();

        return new ResolvedTrack(
            info.uri == null || info.uri.isBlank() ? url : info.uri,
            clean(info.title),
            clean(info.author),
            info.length,
            info.artworkUrl == null ? "" : info.artworkUrl
        );
    }

    @Override
    public PcmSource openStream(String url, long startMs) {
        AudioTrack track = loadTrack(url);
        if (startMs > 0 && track.isSeekable()) {
            track.setPosition(startMs);
        }

        AudioPlayer player = manager.createPlayer();
        player.playTrack(track);
        return new LavaPcmSource(player);
    }

    private AudioTrack loadTrack(String url) {
        CompletableFuture<AudioTrack> result = new CompletableFuture<>();

        manager.loadItem(url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                result.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getSelectedTrack();
                if (track == null && !playlist.getTracks().isEmpty()) {
                    track = playlist.getTracks().getFirst();
                }
                result.complete(track);
            }

            @Override
            public void noMatches() {
                result.complete(null);
            }

            @Override
            public void loadFailed(com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
                result.completeExceptionally(exception);
            }
        });

        try {
            AudioTrack track = result.get(30, TimeUnit.SECONDS);
            if (track == null) {
                throw new IllegalArgumentException("No playable track found.");
            }
            return track;
        } catch (Exception e) {
            throw new IllegalStateException("Could not resolve audio: " + rootMessage(e), e);
        }
    }

    private static boolean isSpotifyTrack(String url) {
        try {
            URI uri = URI.create(url);
            return "open.spotify.com".equalsIgnoreCase(uri.getHost())
                && uri.getPath().contains("/track/");
        } catch (Exception e) {
            return false;
        }
    }

    private String[] spotifyMetadata(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "facebookexternalhit/1.1")
                .header("Accept-Language", "en")
                .GET()
                .build();

            String html = http.send(request, HttpResponse.BodyHandlers.ofString()).body();
            String title = og(html, "title");
            String description = og(html, "description");

            if (title == null || title.isBlank()) {
                throw new IllegalStateException("Spotify metadata unavailable.");
            }

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
        var matcher = java.util.regex.Pattern
            .compile("(?i)content\\s*=\\s*[\\"']([^\\"']*)[\\"']")
            .matcher(tag);

        return matcher.find() ? matcher.group(1) : null;
    }

    private static String unescape(String value) {
        return value == null ? "" : value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'");
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? "Unknown" : value.trim();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getSimpleName()
            : message;
    }

    private static final class LavaPcmSource implements PcmSource {
        private final AudioPlayer player;
        private byte[] leftover;
        private int leftoverPosition;
        private volatile boolean ended;

        private LavaPcmSource(AudioPlayer player) {
            this.player = player;
        }

        @Override public int sampleRate() { return 48_000; }
        @Override public int channels() { return 1; }
        @Override public int bitsPerSample() { return 16; }
        @Override public boolean bigEndian() { return false; }

        @Override
        public int read(byte[] destination, int offset, int length) {
            if (ended && leftover == null) return -1;

            int written = 0;
            long deadline = System.currentTimeMillis() + 10_000L;

            while (written < length) {
                if (leftover != null) {
                    int amount = Math.min(length - written, leftover.length - leftoverPosition);
                    System.arraycopy(leftover, leftoverPosition, destination, offset + written, amount);
                    leftoverPosition += amount;
                    written += amount;

                    if (leftoverPosition >= leftover.length) {
                        leftover = null;
                        leftoverPosition = 0;
                    }
                    continue;
                }

                AudioFrame frame;
                try {
                    frame = player.provide(40, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    frame = null;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (frame == null) {
                    if (player.getPlayingTrack() == null) {
                        ended = true;
                        break;
                    }
                    if (written > 0) break;
                    if (System.currentTimeMillis() > deadline) {
                        ended = true;
                        break;
                    }
                    continue;
                }

                leftover = stereoToMono(frame.getData());
                leftoverPosition = 0;
            }

            return written == 0 ? (ended ? -1 : 0) : written;
        }

        private static byte[] stereoToMono(byte[] stereo) {
            int frames = stereo.length / 4;
            byte[] mono = new byte[frames * 2];

            for (int i = 0; i < frames; i++) {
                int source = i * 4;
                short left = (short) ((stereo[source] & 0xFF) | (stereo[source + 1] << 8));
                short right = (short) ((stereo[source + 2] & 0xFF) | (stereo[source + 3] << 8));
                short mixed = (short) ((left + right) / 2);

                mono[i * 2] = (byte) (mixed & 0xFF);
                mono[i * 2 + 1] = (byte) ((mixed >>> 8) & 0xFF);
            }

            return mono;
        }

        @Override
        public void close() {
            ended = true;
            try {
                player.stopTrack();
                player.destroy();
            } catch (Throwable ignored) {
            }
        }
    }
}
