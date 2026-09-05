package dev.mdmplaylist.mixin;

import com.kuronami.musicdiscmaker.client.MusicDiscMakerScreen;
import dev.mdmplaylist.PlaylistUrlDetector;
import dev.mdmplaylist.network.ImportPlaylistPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MusicDiscMakerScreen.class, remap = false)
public abstract class MusicDiscMakerScreenMixin {
    @Shadow private EditBox urlField;
    @Shadow private String lastSentUrl;

    @Unique
    private String mdmPlaylist$lastImportedUrl = "";

    @Inject(method = "commitUrl", at = @At("HEAD"), cancellable = true)
    private void mdmPlaylist$autoImportPlaylist(CallbackInfo ci) {
        if (urlField == null) return;

        String url = urlField.getValue().trim();
        if (!PlaylistUrlDetector.isSupportedPlaylist(url)) {
            mdmPlaylist$lastImportedUrl = "";
            return;
        }

        // A playlist uses the same URL box as a normal MDM song. Committing the URL
        // automatically starts the playlist importer instead of MDM's single-track resolver.
        if (!url.equals(mdmPlaylist$lastImportedUrl)) {
            mdmPlaylist$lastImportedUrl = url;
            PacketDistributor.sendToServer(new ImportPlaylistPayload(url));
        }

        lastSentUrl = url;
        ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void mdmPlaylist$preservePlaylistField(
        GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci
    ) {
        if (urlField == null) return;
        String url = urlField.getValue().trim();
        if (PlaylistUrlDetector.isSupportedPlaylist(url)) {
            lastSentUrl = url;
        }
    }
}
