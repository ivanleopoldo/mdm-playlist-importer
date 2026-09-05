package dev.vinylmusic.client.audio;

import dev.vinylmusic.audio.PcmSource;
import dev.vinylmusic.content.ModContent;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VinylSoundInstance extends AbstractTickableSoundInstance {
    private final PcmSource source;
    private final int range;
    private final Runnable onEnded;
    private final AtomicBoolean closed = new AtomicBoolean();

    public VinylSoundInstance(
        BlockPos pos,
        PcmSource source,
        int range,
        int volumePercent,
        Runnable onEnded
    ) {
        super(ModContent.STREAM_SOUND.get(), SoundSource.RECORDS, RandomSource.create());
        this.source = source;
        this.range = Math.max(16, Math.min(range, 256));
        this.onEnded = onEnded;
        this.x = pos.getX() + .5;
        this.y = pos.getY() + .5;
        this.z = pos.getZ() + .5;
        this.volume = Math.max(0.0F, Math.min(volumePercent / 100.0F, 2.0F));
        this.pitch = 1.0F;
        this.looping = false;
        this.relative = false;
        this.attenuation = Attenuation.LINEAR;
    }

    @Override
    public void tick() {
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        WeighedSoundEvents result = super.resolve(manager);

        if (this.sound != null && this.sound != SoundManager.EMPTY_SOUND) {
            this.sound = new Sound(
                this.sound.getLocation(),
                this.sound.getVolume(),
                this.sound.getPitch(),
                this.sound.getWeight(),
                this.sound.getType(),
                this.sound.shouldStream(),
                this.sound.shouldPreload(),
                range
            );
        }

        return result;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(
        SoundBufferLibrary soundBuffers,
        Sound sound,
        boolean looping
    ) {
        return CompletableFuture.completedFuture(new PcmAudioStream(source, onEnded));
    }

    public void requestStop() {
        stop();
        if (closed.compareAndSet(false, true)) {
            source.close();
        }
    }
}
