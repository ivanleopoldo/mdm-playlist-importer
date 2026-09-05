package dev.vinylmusic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class AlbumItem extends Item {
    public AlbumItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var tracks = VinylData.readTracks(stack);
        tooltip.add(Component.literal(tracks.size() + " tracks").withStyle(ChatFormatting.GRAY));
        int shown = Math.min(5, tracks.size());
        for (int i = 0; i < shown; i++) {
            tooltip.add(Component.literal((i + 1) + ". " + tracks.get(i).displayName())
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (tracks.size() > shown) {
            tooltip.add(Component.literal("+" + (tracks.size() - shown) + " more")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
