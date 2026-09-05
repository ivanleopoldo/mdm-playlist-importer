package dev.vinylmusic.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class LavaPcmSource implements PcmSource {
    private final AudioPlayer player;
    private byte[] leftover;
    private int leftoverPos;
    private volatile boolean ended;

    LavaPcmSource(AudioPlayer player) {
        this.player = player;
    }

    @Override public int sampleRate() { return 48_000; }
    @Override public int channels() { return 1; }
    @Override public int bitsPerSample() { return 16; }
    @Override public boolean bigEndian() { return false; }

    @Override
    public int read(byte[] dst, int off, int len) {
        if (ended && leftover == null) return -1;
        int written = 0;
        long deadline = System.currentTimeMillis() + 10_000L;

        while (written < len) {
            if (leftover != null) {
                int n = Math.min(len - written, leftover.length - leftoverPos);
                System.arraycopy(leftover, leftoverPos, dst, off + written, n);
                leftoverPos += n;
                written += n;
                if (leftoverPos >= leftover.length) {
                    leftover = null;
                    leftoverPos = 0;
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
            leftoverPos = 0;
        }

        return written == 0 ? (ended ? -1 : 0) : written;
    }

    private static byte[] stereoToMono(byte[] stereo) {
        int frames = stereo.length / 4;
        byte[] mono = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            int s = i * 4;
            short l = (short) ((stereo[s] & 0xFF) | (stereo[s + 1] << 8));
            short r = (short) ((stereo[s + 2] & 0xFF) | (stereo[s + 3] << 8));
            short m = (short) ((l + r) / 2);
            mono[i * 2] = (byte) (m & 0xFF);
            mono[i * 2 + 1] = (byte) ((m >>> 8) & 0xFF);
        }
        return mono;
    }

    @Override
    public void close() {
        ended = true;
        try {
            player.stopTrack();
            player.destroy();
        } catch (Throwable ignored) {}
    }
}
