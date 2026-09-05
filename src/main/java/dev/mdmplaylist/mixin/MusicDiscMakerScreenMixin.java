package dev.mdmplaylist.mixin;

import com.kuronami.musicdiscmaker.client.MusicDiscMakerScreen;
import dev.mdmplaylist.YouTubePlaylistResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MusicDiscMakerScreen.class, remap = false)
public abstract class MusicDiscMakerScreenMixin {
    @Shadow private EditBox urlField;
    @Shadow private String lastSentUrl;

    @Inject(method = "commitUrl", at = @At("HEAD"), cancellable = true)
    private void mdmPlaylist$skipSingleTrackResolver(CallbackInfo ci) {
        if (!hasPlaylistUrl()) return;
        MusicDiscMakerScreen screen = (MusicDiscMakerScreen) (Object) this;
        this.lastSentUrl = screen.getMenu().getBlockEntity().getCurrentUrl();
        ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void mdmPlaylist$preservePlaylistField(
        GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci
    ) {
        if (!hasPlaylistUrl()) return;
        MusicDiscMakerScreen screen = (MusicDiscMakerScreen) (Object) this;
        this.lastSentUrl = screen.getMenu().getBlockEntity().getCurrentUrl();
    }

    private boolean hasPlaylistUrl() {
        return urlField != null
            && YouTubePlaylistResolver.extractPlaylistId(urlField.getValue().trim()).isPresent();
    }
}
