package dev.mdmplaylist.client;

import com.kuronami.musicdiscmaker.client.MusicDiscMakerScreen;
import dev.mdmplaylist.AdditionalAdditionsCompat;
import dev.mdmplaylist.PlaylistImporterMod;
import dev.mdmplaylist.YouTubePlaylistResolver;
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
    private static final Map<MusicDiscMakerScreen, Controls> CONTROLS = new WeakHashMap<>();

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

        int x = panelX(screen);
        int y = screen.getGuiTop() + 20;
        Button button = Button.builder(
            Component.literal("Import Playlist"),
            ignored -> importPlaylist(urlField)
        ).bounds(x, y, 112, 20).build();

        button.active = isPlaylist(urlField.getValue());
        event.addListener(button);
        CONTROLS.put(screen, new Controls(urlField, button));
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MusicDiscMakerScreen screen)) return;
        Controls controls = CONTROLS.get(screen);
        if (controls == null) return;

        boolean playlist = isPlaylist(controls.urlField().getValue());
        controls.importButton().active = playlist;

        int x = panelX(screen);
        int y = screen.getGuiTop() + 44;
        GuiGraphics g = event.getGuiGraphics();
        g.drawString(
            Minecraft.getInstance().font,
            playlist ? Component.literal("YouTube playlist detected")
                     : Component.literal("Paste a YouTube playlist URL"),
            x, y, playlist ? 0x55AA55 : 0x777777, false
        );
        g.drawString(
            Minecraft.getInstance().font,
            AdditionalAdditionsCompat.installed()
                ? Component.literal("Albums: ON")
                : Component.literal("Albums: OFF"),
            x, y + 12, 0x777777, false
        );
    }

    private static void importPlaylist(EditBox field) {
        String url = field.getValue().trim();
        if (isPlaylist(url)) PacketDistributor.sendToServer(new ImportPlaylistPayload(url));
    }

    private static boolean isPlaylist(String url) {
        return YouTubePlaylistResolver.extractPlaylistId(url == null ? "" : url.trim()).isPresent();
    }

    private static int panelX(MusicDiscMakerScreen screen) {
        int right = screen.getGuiLeft() + screen.getXSize() + 4;
        return right + 112 <= screen.width ? right : Math.max(4, screen.getGuiLeft() - 116);
    }

    private record Controls(EditBox urlField, Button importButton) {}
}
