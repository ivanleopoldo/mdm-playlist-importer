package dev.mdmplaylist;

import com.kuronami.musicdiscmaker.register.ModItems;
import dev.mdmplaylist.network.PlaylistNetwork;
import net.minecraft.core.component.DataComponents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PlaylistImporterMod.MOD_ID)
public final class PlaylistImporterMod {
    public static final String MOD_ID = "mdm_playlist_importer";
    public static final Logger LOGGER = LoggerFactory.getLogger("MDM Playlist Importer");

    public PlaylistImporterMod(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(PlaylistNetwork::register);
        modBus.addListener(this::modifyMdmBlankDisc);
        NeoForge.EVENT_BUS.addListener(PlaylistCommands::register);
    }

    /**
     * MDM normally caps Blank Discs at 16. Raise that to a full stack so the
     * Music Disc Maker input slot can hold multiple blanks at once.
     *
     * The maker's normal input slot already respects the item's max stack size,
     * so no invasive mixin into MDM's menu/block entity is needed.
     */
    private void modifyMdmBlankDisc(ModifyDefaultComponentsEvent event) {
        event.modify(ModItems.BLANK_DISC.get(), builder ->
            builder.set(DataComponents.MAX_STACK_SIZE, 64)
        );
    }
}
