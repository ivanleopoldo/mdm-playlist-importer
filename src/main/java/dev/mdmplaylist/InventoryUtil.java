package dev.mdmplaylist;

import com.kuronami.musicdiscmaker.register.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class InventoryUtil {
    private InventoryUtil() {}

    public static int countBlankDiscs(ServerPlayer player) {
        return countItem(player.getInventory(), ModItems.BLANK_DISC.get());
    }

    public static int countEmptyAlbums(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (AdditionalAdditionsCompat.isEmptyAlbum(stack)) total += stack.getCount();
        }
        return total;
    }

    public static boolean consumeBlankDiscs(ServerPlayer player, int amount) {
        return consumeItem(player.getInventory(), ModItems.BLANK_DISC.get(), amount);
    }

    public static List<ItemStack> takeEmptyAlbums(ServerPlayer player, int amount) {
        Inventory inv = player.getInventory();
        List<ItemStack> taken = new ArrayList<>(amount);
        for (int i = 0; i < inv.getContainerSize() && taken.size() < amount; i++) {
            ItemStack stack = inv.getItem(i);
            if (!AdditionalAdditionsCompat.isEmptyAlbum(stack)) continue;
            while (!stack.isEmpty() && taken.size() < amount) {
                taken.add(stack.copyWithCount(1));
                stack.shrink(1);
            }
        }
        inv.setChanged();
        return taken;
    }

    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static int countItem(Inventory inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static boolean consumeItem(Inventory inv, Item item, int amount) {
        if (amount <= 0) return true;
        if (countItem(inv, item) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        inv.setChanged();
        return remaining == 0;
    }
}
