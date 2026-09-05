package dev.mdmplaylist;

import dev.mdmplaylist.network.PlaylistNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PlaylistImporterMod.MOD_ID)
public final class PlaylistImporterMod {
    public static final String MOD_ID = "mdm_playlist_importer";
    public static final Logger LOGGER = LoggerFactory.getLogger("MDM Playlist Importer");

    public PlaylistImporterMod(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(PlaylistNetwork::register);
        NeoForge.EVENT_BUS.addListener(PlaylistCommands::register);
    }
}
