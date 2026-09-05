package dev.vinylmusic.util;

import dev.vinylmusic.content.ModContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class InventoryUtil {
    private InventoryUtil() {}

    public static int countBlankVinyl(ServerPlayer player) {
        int count = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModContent.BLANK_VINYL.get())) count += stack.getCount();
        }
        return count;
    }

    public static boolean consumeBlankVinyl(ServerPlayer player, int amount) {
        if (countBlankVinyl(player) < amount) return false;
        int remaining = amount;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(ModContent.BLANK_VINYL.get())) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        inv.setChanged();
        return remaining == 0;
    }

    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
