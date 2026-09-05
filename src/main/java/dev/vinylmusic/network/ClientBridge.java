package dev.vinylmusic.network;

import java.util.function.Consumer;

public final class ClientBridge {
    public static Consumer<StartPlaybackPayload> START = payload -> {};
    public static Consumer<StopPlaybackPayload> STOP = payload -> {};

    private ClientBridge() {}
}
