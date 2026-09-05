package dev.vinylmusic.client;

import dev.vinylmusic.VinylMusic;
import dev.vinylmusic.client.audio.ClientPlaybackManager;
import dev.vinylmusic.content.ModContent;
import dev.vinylmusic.network.ClientBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = VinylMusic.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    static {
        ClientBridge.START = ClientPlaybackManager::start;
        ClientBridge.STOP = payload -> ClientPlaybackManager.stop(payload.pos());
    }

    private ClientEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.RECORD_PRESS_MENU.get(), RecordPressScreen::new);
    }
}
