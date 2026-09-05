package dev.mdmplaylist;

import com.kuronami.musicdiscmaker.audio.LoaderHolder;
import com.kuronami.musicdiscmaker.component.CustomTrackData;
import com.kuronami.musicdiscmaker.component.SilentSongs;
import com.kuronami.musicdiscmaker.lavaplayer.api.TrackInfo;
import com.kuronami.musicdiscmaker.register.ModDataComponents;
import com.kuronami.musicdiscmaker.register.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;

public final class MusicDiscFactory {
    private static final long RADIO_DURATION_THRESHOLD_MS = 43_200_000L;

    private MusicDiscFactory() {}

    public record ResolvedDisc(String title, ItemStack stack) {}

    public static ResolvedDisc resolveUrl(String sourceUrl) {
        TrackInfo track = LoaderHolder.get().resolve(sourceUrl);
        if (track == null) {
            throw new IllegalStateException("Music Disc Maker returned no track information.");
        }

        boolean radio = track.stream()
            || track.durationMs() <= 0L
            || track.durationMs() > RADIO_DURATION_THRESHOLD_MS;
        long storedDuration = radio ? 0L : track.durationMs();
        String storedUrl = track.uri() == null || track.uri().isBlank()
            ? sourceUrl
            : track.uri();
        String title = track.title() == null || track.title().isBlank()
            ? "Unknown Track"
            : track.title();
        String author = track.author() == null ? "" : track.author();
        String thumbnail = track.thumbnailUrl() == null ? "" : track.thumbnailUrl();

        CustomTrackData data =
            new CustomTrackData(storedUrl, title, author, storedDuration, thumbnail, radio);

        ItemStack disc = new ItemStack(ModItems.CUSTOM_MUSIC_DISC.get());
        disc.set(ModDataComponents.CUSTOM_TRACK.get(), data);
        disc.set(
            DataComponents.JUKEBOX_PLAYABLE,
            new JukeboxPlayable(new EitherHolder<>(SilentSongs.pick(storedDuration, radio)), false)
        );

        return new ResolvedDisc(title, disc);
    }
}
