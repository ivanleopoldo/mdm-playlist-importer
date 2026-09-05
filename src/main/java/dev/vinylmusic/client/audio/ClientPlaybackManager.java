package dev.vinylmusic.client.audio;

import dev.vinylmusic.audio.AudioEngine;
import dev.vinylmusic.audio.PcmSource;
import dev.vinylmusic.model.VinylTrack;
import dev.vinylmusic.network.StartPlaybackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClientPlaybackManager {
    private static final Map<BlockPos, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "vinyl-music-playback");
        t.setDaemon(true);
        return t;
    });

    private ClientPlaybackManager() {}

    public static void start(StartPlaybackPayload payload) {
        stop(payload.pos());
        if (payload.tracks().isEmpty()) return;
        Session session = new Session(
            payload.pos().immutable(),
            payload.tracks(),
            payload.rangeBlocks(),
            payload.volumePercent()
        );
        SESSIONS.put(session.pos, session);
        playIndex(session, 0);
    }

    public static void stop(BlockPos pos) {
        Session session = SESSIONS.remove(pos);
        if (session != null) {
            session.stopped = true;
            VinylSoundInstance sound = session.sound;
            if (sound != null) {
                sound.requestStop();
                Minecraft.getInstance().getSoundManager().stop(sound);
            }
        }
    }

    private static void playIndex(Session session, int index) {
        if (session.stopped || index >= session.tracks.size()) {
            SESSIONS.remove(session.pos, session);
            return;
        }
        session.index = index;
        VinylTrack track = session.tracks.get(index);

        WORKERS.submit(() -> {
            PcmSource source;
            try {
                source = AudioEngine.openStream(track.url(), 0);
            } catch (Throwable t) {
                Minecraft.getInstance().execute(() -> playIndex(session, index + 1));
                return;
            }

            Minecraft.getInstance().execute(() -> {
                if (session.stopped || SESSIONS.get(session.pos) != session) {
                    source.close();
                    return;
                }

                VinylSoundInstance sound = new VinylSoundInstance(
                    session.pos, source, session.range, session.volume,
                    () -> Minecraft.getInstance().execute(() -> playIndex(session, index + 1))
                );
                session.sound = sound;
                Minecraft.getInstance().getSoundManager().play(sound);
                Minecraft.getInstance().gui.setNowPlaying(Component.literal(track.displayName()));
            });
        });
    }

    private static final class Session {
        final BlockPos pos;
        final List<VinylTrack> tracks;
        final int range;
        final int volume;
        volatile int index;
        volatile boolean stopped;
        volatile VinylSoundInstance sound;

        Session(BlockPos pos, List<VinylTrack> tracks, int range, int volume) {
            this.pos = pos;
            this.tracks = tracks;
            this.range = range;
            this.volume = volume;
        }
    }
}
