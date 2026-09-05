package dev.vinylmusic.client.audio;

import dev.vinylmusic.audio.PcmSource;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PcmAudioStream implements AudioStream {
    private final PcmSource source;
    private final AudioFormat format;
    private final byte[] scratch = new byte[8192];
    private final Runnable onEnded;
    private final AtomicBoolean ended = new AtomicBoolean();

    public PcmAudioStream(PcmSource source, Runnable onEnded) {
        this.source = source;
        this.onEnded = onEnded;
        this.format = new AudioFormat(
            source.sampleRate(), source.bitsPerSample(), source.channels(), true, source.bigEndian()
        );
    }

    @Override public AudioFormat getFormat() { return format; }

    @Override
    public ByteBuffer read(int size) {
        ByteBuffer out = BufferUtils.createByteBuffer(size);
        while (out.hasRemaining()) {
            int want = Math.min(scratch.length, out.remaining());
            int read = source.read(scratch, 0, want);
            if (read < 0) {
                if (onEnded != null && ended.compareAndSet(false, true)) onEnded.run();
                break;
            }
            if (read == 0) break;
            out.put(scratch, 0, read);
        }
        out.flip();
        return out;
    }

    @Override public void close() { source.close(); }
}
