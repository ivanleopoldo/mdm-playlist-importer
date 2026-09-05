package dev.vinylmusic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class CustomVinylItem extends Item {
    public CustomVinylItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var tracks = VinylData.readTracks(stack);
        if (!tracks.isEmpty()) {
            var track = tracks.getFirst();
            tooltip.add(Component.literal(track.displayName()).withStyle(ChatFormatting.GRAY));
            if (track.durationMs() > 0) {
                long sec = track.durationMs() / 1000;
                tooltip.add(Component.literal("%d:%02d".formatted(sec / 60, sec % 60))
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
