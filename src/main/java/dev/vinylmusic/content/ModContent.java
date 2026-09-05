package dev.vinylmusic.content;

import dev.vinylmusic.VinylMusic;
import dev.vinylmusic.item.AlbumItem;
import dev.vinylmusic.item.CustomVinylItem;
import dev.vinylmusic.menu.RecordPressMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VinylMusic.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VinylMusic.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(BuiltInRegistries.MENU, VinylMusic.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, VinylMusic.MOD_ID);

    public static final DeferredItem<Item> BLANK_VINYL =
        ITEMS.registerSimpleItem("blank_vinyl", () -> new Item.Properties().stacksTo(64));
    public static final DeferredItem<CustomVinylItem> CUSTOM_VINYL =
        ITEMS.registerItem("custom_vinyl", p -> new CustomVinylItem(p.stacksTo(1)));
    public static final DeferredItem<AlbumItem> ALBUM =
        ITEMS.registerItem("album", p -> new AlbumItem(p.stacksTo(1)));

    public static final DeferredBlock<Block> RECORD_PRESS =
        BLOCKS.registerBlock("record_press", Block::new,
            () -> BlockBehaviour.Properties.of().strength(3.0F).sound(SoundType.METAL));
    public static final DeferredBlock<Block> RECORD_PLAYER =
        BLOCKS.registerBlock("record_player", Block::new,
            () -> BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> RECORD_PRESS_ITEM = ITEMS.registerSimpleBlockItem(RECORD_PRESS);
    public static final DeferredItem<BlockItem> RECORD_PLAYER_ITEM = ITEMS.registerSimpleBlockItem(RECORD_PLAYER);

    public static final DeferredHolder<MenuType<?>, MenuType<RecordPressMenu>> RECORD_PRESS_MENU =
        MENUS.register("record_press", () -> IMenuTypeExtension.create(RecordPressMenu::new));

    public static final DeferredHolder<SoundEvent, SoundEvent> STREAM_SOUND =
        SOUNDS.register("stream", () -> SoundEvent.createVariableRangeEvent(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(VinylMusic.MOD_ID, "stream")
        ));

    private ModContent() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        MENUS.register(bus);
        SOUNDS.register(bus);
    }
}
