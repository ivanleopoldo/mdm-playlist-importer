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
    private static final int PANEL_WIDTH = 116;
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

        int panelX = panelX(screen);
        int x = panelX + 7;
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
    public static void onScreenBackground(ScreenEvent.Render.Background event) {
        if (!(event.getScreen() instanceof MusicDiscMakerScreen screen)) return;
        Controls controls = CONTROLS.get(screen);
        if (controls == null) return;

        String url = controls.urlField().getValue().trim();
        boolean detected = PlaylistUrlDetector.isSupportedPlaylist(url);
        controls.importButton().active = detected;

        GuiGraphics g = event.getGuiGraphics();
        int x = panelX(screen);
        int y = screen.getGuiTop();
        int right = x + PANEL_WIDTH;
        int bottom = y + screen.getYSize();

        // Attached vanilla-style panel. It touches the Music Disc Maker window
        // directly, so the playlist controls read as part of the same GUI.
        g.fill(x, y, right, bottom, 0xFF373737);
        g.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, right - 2, bottom - 2, 0xFFC6C6C6);
        g.fill(x + 2, y + 2, x + 3, bottom - 2, 0xFFFFFFFF);
        g.fill(x + 3, bottom - 3, right - 2, bottom - 2, 0xFF555555);

        int tx = x + 7;
        g.drawString(
            Minecraft.getInstance().font,
            Component.literal("Playlist Import"),
            tx, y + 8, 0x404040, false
        );

        g.drawString(
            Minecraft.getInstance().font,
            Component.literal(
                detected
                    ? PlaylistUrlDetector.displayName(url)
                    : "Paste a playlist link"
            ),
            tx, y + 49, detected ? 0x2D6A2D : 0x666666, false
        );

        g.drawString(
            Minecraft.getInstance().font,
            Component.literal(
                AdditionalAdditionsCompat.installed()
                    ? "Albums: ON (8 tracks)"
                    : "Albums: OFF"
            ),
            tx, y + 64, 0x666666, false
        );

        g.drawString(
            Minecraft.getInstance().font,
            Component.literal("YouTube / Spotify"),
            tx, y + 92, 0x666666, false
        );
        g.drawString(
            Minecraft.getInstance().font,
            Component.literal("SoundCloud"),
            tx, y + 104, 0x666666, false
        );
    }

    private static int panelX(MusicDiscMakerScreen screen) {
        int right = screen.getGuiLeft() + screen.getXSize() - 1;
        if (right + PANEL_WIDTH <= screen.width - 4) {
            return right;
        }
        return screen.getGuiLeft() - PANEL_WIDTH + 1;
    }

    private record Controls(EditBox urlField, Button importButton) {}
}
