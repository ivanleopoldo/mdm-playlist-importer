package dev.mdmplaylist;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            PlaylistImporterMod.LOGGER.error("Could not create Additional Additions album contents", e);
            return false;
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
