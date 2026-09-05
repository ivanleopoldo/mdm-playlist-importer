package dev.mdmplaylist.client;

import com.kuronami.musicdiscmaker.client.MusicDiscMakerScreen;
import dev.mdmplaylist.AdditionalAdditionsCompat;
import dev.mdmplaylist.PlaylistImporterMod;
import dev.mdmplaylist.PlaylistUrlDetector;
import dev.mdmplaylist.network.ImportPlaylistPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = PlaylistImporterMod.MOD_ID, value = Dist.CLIENT)
public final class ClientGuiHooks {
    private static final int BASE_WIDTH = 200;
    private static final Map<MusicDiscMakerScreen, Controls> CONTROLS =
        new WeakHashMap<>();

    private ClientGuiHooks() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MusicDiscMakerScreen screen)) return;

        EditBox urlField = event.getListenersList().stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .findFirst()
            .orElse(null);
        if (urlField == null) return;

        int x = screen.getGuiLeft() + BASE_WIDTH + 7;
        int y = screen.getGuiTop() + 22;

        Button button = Button.builder(
            Component.literal("Import Playlist"),
            ignored -> {
                String url = urlField.getValue().trim();
                if (PlaylistUrlDetector.isSupportedPlaylist(url)) {
                    PacketDistributor.sendToServer(new ImportPlaylistPayload(url));
                }
            }
        ).bounds(x, y, 102, 20).build();

        button.active = PlaylistUrlDetector.isSupportedPlaylist(urlField.getValue());
        event.addListener(button);
        CONTROLS.put(screen, new Controls(urlField, button));
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MusicDiscMakerScreen screen)) return;
        Controls controls = CONTROLS.get(screen);
        if (controls == null) return;

        String url = controls.urlField().getValue().trim();
        boolean detected = PlaylistUrlDetector.isSupportedPlaylist(url);
        controls.importButton().active = detected;

        GuiGraphics g = event.getGuiGraphics();
        int x = screen.getGuiLeft() + BASE_WIDTH + 7;
        int y = screen.getGuiTop();

        g.drawString(
            Minecraft.getInstance().font,
            Component.literal("Playlist Import"),
            x,
            y + 8,
            0x404040,
            false
        );

        g.drawString(
            Minecraft.getInstance().font,
            Component.literal(
                detected
                    ? PlaylistUrlDetector.displayName(url)
                    : "Paste a playlist link"
            ),
            x,
            y + 49,
            detected ? 0x2D6A2D : 0x666666,
            false
        );

        g.drawString(
            Minecraft.getInstance().font,
            Component.literal(
                AdditionalAdditionsCompat.installed()
                    ? "Albums: ON (8 tracks)"
                    : "Albums: OFF"
            ),
            x,
            y + 64,
            0x666666,
            false
        );

        g.drawString(
            Minecraft.getInstance().font,
            Component.literal("YouTube / Spotify"),
            x,
            y + 92,
            0x666666,
            false
        );
        g.drawString(
            Minecraft.getInstance().font,
            Component.literal("SoundCloud"),
            x,
            y + 104,
            0x666666,
            false
        );
    }

    private record Controls(EditBox urlField, Button importButton) {}
}
