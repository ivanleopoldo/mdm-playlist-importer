package dev.vinylmusic.client;

import dev.vinylmusic.VinylMusic;
import dev.vinylmusic.menu.RecordPressMenu;
import dev.vinylmusic.network.CreateVinylPayload;
import dev.vinylmusic.network.ImportPlaylistPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class RecordPressScreen extends AbstractContainerScreen<RecordPressMenu> {
    private static final ResourceLocation TEX =
        ResourceLocation.fromNamespaceAndPath(VinylMusic.MOD_ID, "textures/gui/record_press.png");
    private EditBox urlField;

    public RecordPressScreen(RecordPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        urlField = new EditBox(font, leftPos + 14, topPos + 38, 228, 18, Component.translatable("vinyl_music.gui.url"));
        urlField.setMaxLength(4096);
        urlField.setHint(Component.translatable("vinyl_music.gui.url"));
        addRenderableWidget(urlField);

        addRenderableWidget(Button.builder(Component.translatable("vinyl_music.gui.create"), b -> {
            String url = urlField.getValue().trim();
            if (!url.isBlank()) ClientPacketDistributor.sendToServer(new CreateVinylPayload(url));
        }).bounds(leftPos + 14, topPos + 64, 108, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("vinyl_music.gui.import_playlist"), b -> {
            String url = urlField.getValue().trim();
            if (!url.isBlank()) ClientPacketDistributor.sendToServer(new ImportPlaylistPayload(url));
        }).bounds(leftPos + 134, topPos + 64, 108, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("vinyl_music.gui.clear"), b -> urlField.setValue(""))
            .bounds(leftPos + 14, topPos + 92, 70, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFB8B8B8);
        g.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFFD8D8D8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 14, 12, 0x303030, false);
        g.drawString(font, Component.literal("1 Blank Vinyl per track"), 14, 121, 0x606060, false);
        g.drawString(font, Component.literal("Playlists also create Record Albums"), 14, 134, 0x606060, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
