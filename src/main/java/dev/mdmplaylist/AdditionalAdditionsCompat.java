package dev.mdmplaylist;

import com.kuronami.musicdiscmaker.component.CustomTrackData;
import com.kuronami.musicdiscmaker.component.SilentSongs;
import com.kuronami.musicdiscmaker.register.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

public final class AdditionalAdditionsCompat {
    private static final String MOD_ID = "additionaladditions";
    private static final ResourceLocation CONTENTS_ID =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "album_contents");

    private AdditionalAdditionsCompat() {}

    public static boolean installed() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isAlbumItem(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MOD_ID.equals(id.getNamespace())
            && (id.getPath().equals("album") || id.getPath().endsWith("_album"));
    }

    public static boolean isEmptyAlbum(ItemStack stack) {
        if (!isAlbumItem(stack)) return false;
        DataComponentType<?> type = albumContentsComponent();
        if (type == null) return false;
        Object contents = getRawComponent(stack, type);
        if (contents == null) return true;
        try {
            Method itemsMethod = contents.getClass().getMethod("items");
            Object value = itemsMethod.invoke(contents);
            return value instanceof List<?> list && list.isEmpty();
        } catch (ReflectiveOperationException e) {
            PlaylistImporterMod.LOGGER.warn("Could not inspect Additional Additions album contents", e);
            return false;
        }
    }

    public static boolean fillAlbum(ItemStack album, List<ItemStack> discs) {
        DataComponentType<?> type = albumContentsComponent();
        if (type == null) return false;
        try {
            Class<?> cls = Class.forName("one.dqu.additionaladditions.feature.album.AlbumContents");
            Constructor<?> ctor = cls.getConstructor(List.class);
            Object contents = ctor.newInstance(List.copyOf(discs));
            setRawComponent(album, type, contents);
            applySsvPlayableBridge(album, discs);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            PlaylistImporterMod.LOGGER.error("Could not create Additional Additions album contents", e);
            return false;
        }
    }

    /**
     * Somewhat Sophisticated Vinyl Decor treats anything with JUKEBOX_PLAYABLE
     * as a disc. Additional Additions albums normally keep that component only
     * on the discs inside the album, so SSV cannot see the album itself.
     *
     * For albums created by this addon, attach a silent jukebox marker whose
     * duration matches the whole MDM playlist chunk. Additional Additions still
     * owns the real album playback; this marker only makes SSV recognize the
     * album and gives autoplay a sensible total duration.
     */
    private static void applySsvPlayableBridge(ItemStack album, List<ItemStack> discs) {
        if (discs.isEmpty()) return;

        long totalDurationMs = 0L;
        boolean radio = false;
        boolean foundMdmTrack = false;

        for (ItemStack disc : discs) {
            CustomTrackData track = disc.get(ModDataComponents.CUSTOM_TRACK.get());
            if (track == null || track.isEmpty()) continue;

            foundMdmTrack = true;
            if (track.radio() || track.durationMs() <= 0L) {
                radio = true;
                break;
            }

            long duration = track.durationMs();
            if (Long.MAX_VALUE - totalDurationMs < duration) {
                radio = true;
                break;
            }
            totalDurationMs += duration;
        }

        if (foundMdmTrack) {
            album.set(
                DataComponents.JUKEBOX_PLAYABLE,
                new JukeboxPlayable(
                    new EitherHolder<>(SilentSongs.pick(totalDurationMs, radio)),
                    false
                )
            );
            return;
        }

        // Generic fallback for non-MDM discs: copying the first playable marker
        // is enough for SSV to recognize the album as a disc-like item.
        for (ItemStack disc : discs) {
            JukeboxPlayable playable = disc.get(DataComponents.JUKEBOX_PLAYABLE);
            if (playable != null) {
                album.set(DataComponents.JUKEBOX_PLAYABLE, playable);
                return;
            }
        }
    }

    private static DataComponentType<?> albumContentsComponent() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(CONTENTS_ID).orElse(null);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static Object getRawComponent(ItemStack stack, DataComponentType<?> type) {
        return stack.get((DataComponentType) type);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void setRawComponent(ItemStack stack, DataComponentType<?> type, Object value) {
        stack.set((DataComponentType) type, value);
    }
}
