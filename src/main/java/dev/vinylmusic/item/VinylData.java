package dev.vinylmusic.item;

import dev.vinylmusic.content.ModContent;
import dev.vinylmusic.model.VinylTrack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

public final class VinylData {
    public static final int DEFAULT_ALBUM_CAPACITY = 24;

    private VinylData() {}

    public static ItemStack createVinyl(VinylTrack track) {
        ItemStack stack = new ItemStack(ModContent.CUSTOM_VINYL.get());
        CompoundTag root = new CompoundTag();
        root.put("track", track.toTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(track.displayName()));
        return stack;
    }

    public static ItemStack createAlbum(String title, List<VinylTrack> tracks) {
        ItemStack stack = new ItemStack(ModContent.ALBUM.get());
        CompoundTag root = new CompoundTag();
        root.putString("title", title == null || title.isBlank() ? "Record Album" : title);
        ListTag list = new ListTag();
        for (VinylTrack track : tracks) list.add(track.toTag());
        root.put("tracks", list);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.set(DataComponents.CUSTOM_NAME,
            net.minecraft.network.chat.Component.literal(root.getString("title")));
        return stack;
    }

    public static List<VinylTrack> readTracks(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return List.of();
        CompoundTag root = data.copyTag();

        if (stack.is(ModContent.CUSTOM_VINYL.get()) && root.contains("track")) {
            return List.of(VinylTrack.fromTag(root.getCompound("track")));
        }

        if (stack.is(ModContent.ALBUM.get()) && root.contains("tracks")) {
            ListTag list = root.getList("tracks", CompoundTag.TAG_COMPOUND);
            List<VinylTrack> tracks = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) tracks.add(VinylTrack.fromTag(list.getCompound(i)));
            return List.copyOf(tracks);
        }

        return List.of();
    }
}
