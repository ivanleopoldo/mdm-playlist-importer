package dev.vinylmusic.mixin;

import dev.vinylmusic.client.audio.VinylSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Redirect(
        method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getStream(Lnet/minecraft/client/sounds/SoundBufferLibrary;Lnet/minecraft/client/resources/sounds/Sound;Z)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<AudioStream> vinylMusic$customStream(
        SoundInstance instance,
        SoundBufferLibrary library,
        Sound sound,
        boolean looping
    ) {
        if (instance instanceof VinylSoundInstance vinyl) return vinyl.customStream();
        return instance.getStream(library, sound, looping);
    }
}
