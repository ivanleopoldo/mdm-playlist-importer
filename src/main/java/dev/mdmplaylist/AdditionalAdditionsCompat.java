package dev.mdmplaylist;

import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AdditionalAdditionsCompat {
    private static final String MOD_ID = "additionaladditions";
    private static volatile boolean warnedCapacityRead;

    private AdditionalAdditionsCompat() {}

    public static boolean installed() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Reads Additional Additions' live album capacity instead of hardcoding 8.
     * Config.ALBUM.get().capacity() is used through reflection so Additional
     * Additions remains an optional dependency of this addon.
     *
     * @return current configured capacity, or -1 if unavailable/not installed
     */
    public static int albumCapacity() {
        if (!installed()) return -1;

        try {
            Class<?> configClass =
                Class.forName("one.dqu.additionaladditions.config.Config");
            Field albumField = configClass.getField("ALBUM");
            Object configProperty = albumField.get(null);

            Method getMethod = configProperty.getClass().getMethod("get");
            Object albumConfig = getMethod.invoke(configProperty);
            if (albumConfig == null) return -1;

            Method capacityMethod = albumConfig.getClass().getMethod("capacity");
            Object value = capacityMethod.invoke(albumConfig);
            if (value instanceof Number number) {
                return Math.max(1, number.intValue());
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warnedCapacityRead) {
                warnedCapacityRead = true;
                PlaylistImporterMod.LOGGER.warn(
                    "Could not read Additional Additions album capacity dynamically", e
                );
            }
        }

        return -1;
    }
}
