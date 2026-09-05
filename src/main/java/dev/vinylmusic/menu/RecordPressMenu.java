package dev.vinylmusic.menu;

import dev.vinylmusic.content.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public final class RecordPressMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;

    public RecordPressMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public RecordPressMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModContent.RECORD_PRESS_MENU.get(), id);
        this.access = access;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModContent.RECORD_PRESS.get());
    }
}
