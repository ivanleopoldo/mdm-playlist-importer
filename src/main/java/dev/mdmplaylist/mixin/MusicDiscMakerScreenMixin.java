package dev.mdmplaylist.mixin;

import com.kuronami.musicdiscmaker.MusicDiscMaker;
import com.kuronami.musicdiscmaker.client.MusicDiscMakerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MusicDiscMakerScreen.class, remap = false)
public abstract class MusicDiscMakerScreenMixin {
    @Unique private static final int MDM_PLAYLIST$BASE_WIDTH = 200;
    @Unique private static final int MDM_PLAYLIST$PANEL_WIDTH = 116;
    @Unique private static final int MDM_PLAYLIST$TOTAL_WIDTH =
        MDM_PLAYLIST$BASE_WIDTH + MDM_PLAYLIST$PANEL_WIDTH;

    @Unique
    private static final ResourceLocation MDM_PLAYLIST$TEXTURE =
        ResourceLocation.fromNamespaceAndPath(
            MusicDiscMaker.MODID,
            "textures/gui/music_disc_maker.png"
        );

    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @Inject(method = "init", at = @At("HEAD"))
    private void mdmPlaylist$expandGui(CallbackInfo ci) {
        // Expanding imageWidth before AbstractContainerScreen#init runs makes the
        // entire MDM + playlist panel center as one proper GUI.
        this.imageWidth = MDM_PLAYLIST$TOTAL_WIDTH;
    }

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void mdmPlaylist$renderConnectedPanel(
        GuiGraphics g, float partialTick, int mouseX, int mouseY, CallbackInfo ci
    ) {
        // Original MDM texture remains exactly 200x166.
        g.blit(
            MDM_PLAYLIST$TEXTURE,
            leftPos,
            topPos,
            0.0F,
            0.0F,
            MDM_PLAYLIST$BASE_WIDTH,
            imageHeight,
            256,
            256
        );

        // Draw an attached vanilla-style panel, rather than a floating button.
        int x = leftPos + MDM_PLAYLIST$BASE_WIDTH;
        int y = topPos;
        int right = x + MDM_PLAYLIST$PANEL_WIDTH;
        int bottom = y + imageHeight;

        g.fill(x, y, right, bottom, 0xFF373737);
        g.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, right - 2, bottom - 2, 0xFFC6C6C6);
        g.fill(x + 2, y + 2, x + 3, bottom - 2, 0xFFFFFFFF);
        g.fill(x + 3, bottom - 3, right - 2, bottom - 2, 0xFF555555);

        ci.cancel();
    }
}
