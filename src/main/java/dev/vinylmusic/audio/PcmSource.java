package dev.vinylmusic.audio;

public interface PcmSource extends AutoCloseable {
    int sampleRate();
    int channels();
    int bitsPerSample();
    boolean bigEndian();
    int read(byte[] dst, int off, int len);
    @Override void close();
}
