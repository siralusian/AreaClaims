package com.areaclaims.client.gui;

import com.areaclaims.AreaClaims;
import com.areaclaims.client.ClientImageManager;
import com.areaclaims.client.ImageUploader;
import com.areaclaims.client.NativeImageDecoder;
import com.areaclaims.client.data.ClientImageGalleryCache;
import com.areaclaims.client.data.ClientNetworkUtil;
import com.areaclaims.image.ImageMetadata;
import com.areaclaims.network.ImageGalleryRequestPacket;
import com.areaclaims.network.ImageUploadResultPacket;
import com.areaclaims.network.SetClaimImagePacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Punkt 6 (Nachtrag 4, großes exploratives Bild-Feature): lässt den Claim-BESITZER ein Bild statt
 * des Textnamens für einen Haupt- ODER Unterbereich zuweisen - entweder eine neue Datei hochladen
 * (natives Datei-Dialog über {@code TinyFileDialogs}, wie in CopycatSigns bereits bewährter
 * {@code PictureEditorScreen}, dort als Vorlage benannt) oder ein BEREITS hochgeladenes Bild aus
 * der durchstöberbaren Galerie wiederverwenden (siehe {@code ServerImageStore#getAll}-
 * Klassenkommentar - der Teil, den CopycatSign selbst noch nicht hat).
 *
 * <p>Öffnet sich über einen neuen "Bild wählen"-Button im Name-Bearbeiten-Popup des Editors
 * (siehe {@link AreaClaimsEditorScreen#buildNameEditPopup}). Schickt bei Erfolg
 * {@link SetClaimImagePacket} und schließt sich wieder.
 *
 * <p><b>Nachtrag 5, Punkt 7 (Layout-Überarbeitung nach echtem Test):</b> die Galerie ist jetzt ein
 * ECHTES, scrollbares Kachel-Raster mit großen Vorschaubildern (angelehnt an Windows Explorers
 * "Extra große Symbole"-Ansicht, Nutzer-Vorgabe) statt einer schmalen Liste mit winzigen Icons -
 * "Verwenden" sitzt jetzt UNTER dem jeweiligen Vorschaubild statt weit rechts daneben. Scrollen
 * nach demselben Muster wie {@link AreaClaimsServerConfigScreen} (Phase 7-Nachtrag 3, Punkt 10) -
 * geklemmter Offset + sichtbarer Balken, bewusst OHNE hartes Scissor-Clipping (siehe dortigen
 * Klassenkommentar für die Begründung).
 */
public class AreaClaimsImagePickerScreen extends FixedScaleScreen {

    private static final ResourceLocation PREVIEW_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "image_picker_preview");

    private static AreaClaimsImagePickerScreen activeInstance;

    private final String claimId;

    private byte[] pendingFileBytes;
    private ResourceLocation previewTexture;
    private int previewWidth;
    private int previewHeight;
    private boolean uploading;
    private Component statusMessage;

    private Button uploadButton;

    private static final int MARGIN = 10;
    private static final int TITLE_Y = 8;
    private static final int PREVIEW_BOX_SIZE = 80;
    private static final int PREVIEW_BOX_Y = 28;
    private static final int GALLERY_Y = PREVIEW_BOX_Y + PREVIEW_BOX_SIZE + 60;

    // -- Punkt 7 (Nachtrag 5): großes Kachel-Raster statt schmaler Liste --
    private static final int GRID_THUMB_SIZE = 64;
    private static final int GRID_CELL_WIDTH = 76;
    private static final int GRID_CELL_HEIGHT = GRID_THUMB_SIZE + 12 + 20 + 6;
    private static final int GRID_USE_BUTTON_WIDTH = 70;
    private static final int GRID_USE_BUTTON_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_TRACK_COLOR = 0x40FFFFFF;
    private static final int SCROLLBAR_THUMB_COLOR = 0xFFAAAAAA;
    private static final double SCROLL_PIXELS_PER_NOTCH = 24.0;

    private static final int CLOSE_BUTTON_WIDTH = 80;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 10;
    private static final int BACK_BUTTON_WIDTH = 80;
    private static final int BACK_BUTTON_GAP = 4;

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

    /**
     * Punkt 3 (Nachtrag 6, "Zurück"-Button): DIREKT bekannt (anders als die drei Server-Rundreise-
     * Screens, siehe {@code AreaClaimsServerConfigScreen#parentScreen}-Kommentar) - dieser Screen
     * wird synchron aus dem "Bild wählen"-Button des Namens-Bearbeiten-Popups heraus konstruiert,
     * der Editor (samt weiterhin offenem Popup-Zustand, nur nicht mehr der AKTIVE Screen) ist zu
     * diesem Zeitpunkt bereits vollständig bekannt.
     */
    private final net.minecraft.client.gui.screens.Screen parentScreen;

    public AreaClaimsImagePickerScreen(String claimId, net.minecraft.client.gui.screens.Screen parentScreen) {
        super(Component.translatable("areaclaims.imagepicker.title"));
        this.claimId = claimId;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void initScaled() {
        activeInstance = this;
        // Punkt 6: bei jedem Öffnen frisch die Galerie anfragen, damit ein zwischenzeitlich von
        // einem anderen Spieler hochgeladenes Bild sofort sichtbar ist.
        ImageGalleryRequestPacket packet = new ImageGalleryRequestPacket();
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
        buildWidgets();
    }

    private int galleryViewportBottom() {
        return this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN - 6;
    }

    private int gridColumns() {
        return Math.max(1, (this.width - 2 * MARGIN) / GRID_CELL_WIDTH);
    }

    private void buildWidgets() {
        int centerX = this.width / 2;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.imagepicker.choose_file"), b -> pickFile())
            .bounds(centerX - 100, PREVIEW_BOX_Y + PREVIEW_BOX_SIZE + 6, 200, 20)
            .build());

        uploadButton = Button.builder(Component.translatable("areaclaims.imagepicker.upload"), b -> upload())
            .bounds(centerX - 100, PREVIEW_BOX_Y + PREVIEW_BOX_SIZE + 28, 200, 20)
            .build();
        uploadButton.active = pendingFileBytes != null && !uploading;
        addRenderableWidget(uploadButton);

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.imagepicker.clear"), b -> {
                SetClaimImagePacket clearPacket = new SetClaimImagePacket(claimId, "");
                if (ClientNetworkUtil.canSendToServerOrWarn(clearPacket.type().id())) {
                    PacketDistributor.sendToServer(clearPacket);
                }
                returnToParentOrClose();
            })
            .bounds(this.width - 150 - MARGIN, PREVIEW_BOX_Y, 150, 20)
            .build());

        List<ImageMetadata> gallery = ClientImageGalleryCache.get();
        int columns = gridColumns();
        int viewportBottom = galleryViewportBottom();
        int rows = (gallery.size() + columns - 1) / columns;
        for (int i = 0; i < gallery.size(); i++) {
            ImageMetadata meta = gallery.get(i);
            int col = i % columns;
            int row = i / columns;
            int cellX = MARGIN + col * GRID_CELL_WIDTH;
            int cellY = GALLERY_Y + row * GRID_CELL_HEIGHT - scrollOffset;
            // Nur Widgets für tatsächlich (teilweise) sichtbare Zeilen bauen - Zeilen, die durch den
            // Scroll-Offset komplett aus dem Sichtbereich gewandert sind, bekommen bewusst KEINEN
            // Button (kein hartes Scissor-Clipping, siehe Klassenkommentar - so bleiben trotzdem
            // keine unsichtbaren, aber klickbaren Buttons oberhalb/unterhalb liegen).
            if (cellY + GRID_CELL_HEIGHT < GALLERY_Y || cellY > viewportBottom) continue;
            int buttonY = cellY + GRID_THUMB_SIZE + 14;
            addRenderableWidget(Button.builder(Component.translatable("areaclaims.imagepicker.use"), b -> {
                    SetClaimImagePacket usePacket = new SetClaimImagePacket(claimId, meta.hash);
                    if (ClientNetworkUtil.canSendToServerOrWarn(usePacket.type().id())) {
                        PacketDistributor.sendToServer(usePacket);
                    }
                    returnToParentOrClose();
                })
                .bounds(cellX + (GRID_CELL_WIDTH - GRID_USE_BUTTON_WIDTH) / 2, buttonY, GRID_USE_BUTTON_WIDTH, GRID_USE_BUTTON_HEIGHT)
                .build());
            ClientImageManager.resolveTexture(meta.hash);
        }

        int contentBottom = GALLERY_Y + rows * GRID_CELL_HEIGHT + scrollOffset;
        maxScrollOffset = Math.max(0, contentBottom - viewportBottom);

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.close"), b -> onClose())
            .bounds(this.width - CLOSE_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.back"), b -> returnToParentOrClose())
            .bounds(this.width - CLOSE_BUTTON_WIDTH - BACK_BUTTON_GAP - BACK_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                BACK_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());
    }

    @Override
    protected boolean mouseScrolledScaled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScrollOffset <= 0 || scrollY == 0) {
            return super.mouseScrolledScaled(mouseX, mouseY, scrollX, scrollY);
        }
        int newOffset = (int) Math.round(Math.max(0, Math.min(maxScrollOffset, scrollOffset - scrollY * SCROLL_PIXELS_PER_NOTCH)));
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            clearWidgets();
            buildWidgets();
        }
        return true;
    }

    @Override
    public void onClose() {
        releasePreviewTexture();
        super.onClose();
    }

    /**
     * Punkt 3 (Nachtrag 6, "Zurück"-Button): wie {@link #onClose()}, aber kehrt zum Editor zurück
     * statt das gesamte GUI zu verlassen - genutzt von den "Erfolg"-Pfaden (Bild verwenden/entfernt,
     * erfolgreicher Upload) UND dem neuen expliziten "Zurück"-Button. Räumt die Vorschau-Textur
     * GENAUSO auf wie {@link #onClose()} - {@code setScreen} löst dessen Aufräumcode NICHT selbst
     * aus (bypassed {@code onClose()} komplett), das müsste sonst hier vergessen werden können.
     */
    private void returnToParentOrClose() {
        if (activeInstance == this) activeInstance = null;
        releasePreviewTexture();
        if (parentScreen != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    private void releasePreviewTexture() {
        if (activeInstance == this) activeInstance = null;
        if (previewTexture != null) {
            Minecraft.getInstance().getTextureManager().release(previewTexture);
            previewTexture = null;
        }
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderScaled(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);

        int centerX = this.width / 2;
        int boxX = centerX - PREVIEW_BOX_SIZE / 2;
        graphics.fill(boxX - 1, PREVIEW_BOX_Y - 1, boxX + PREVIEW_BOX_SIZE + 1, PREVIEW_BOX_Y + PREVIEW_BOX_SIZE + 1, 0xFF555555);
        if (previewTexture != null) {
            graphics.blit(previewTexture, boxX, PREVIEW_BOX_Y, PREVIEW_BOX_SIZE, PREVIEW_BOX_SIZE, 0f, 0f, previewWidth, previewHeight, previewWidth, previewHeight);
        } else {
            graphics.fill(boxX, PREVIEW_BOX_Y, boxX + PREVIEW_BOX_SIZE, PREVIEW_BOX_Y + PREVIEW_BOX_SIZE, 0xFF222222);
        }
        if (statusMessage != null) {
            graphics.drawCenteredString(this.font, statusMessage, centerX, PREVIEW_BOX_Y + PREVIEW_BOX_SIZE + 52, 0xFFFF55);
        }

        graphics.drawString(this.font, Component.translatable("areaclaims.imagepicker.gallery_header"), MARGIN, GALLERY_Y - 12, 0xFFFFFF, true);

        List<ImageMetadata> gallery = ClientImageGalleryCache.get();
        int columns = gridColumns();
        int viewportBottom = galleryViewportBottom();
        for (int i = 0; i < gallery.size(); i++) {
            ImageMetadata meta = gallery.get(i);
            int col = i % columns;
            int row = i / columns;
            int cellX = MARGIN + col * GRID_CELL_WIDTH;
            int cellY = GALLERY_Y + row * GRID_CELL_HEIGHT - scrollOffset;
            if (cellY + GRID_CELL_HEIGHT < GALLERY_Y || cellY > viewportBottom) continue;

            ResourceLocation thumb = ClientImageManager.resolveTexture(meta.hash);
            graphics.fill(cellX, cellY, cellX + GRID_THUMB_SIZE, cellY + GRID_THUMB_SIZE, 0xFF333333);
            if (thumb != null) {
                graphics.blit(thumb, cellX, cellY, GRID_THUMB_SIZE, GRID_THUMB_SIZE, 0f, 0f, meta.width, meta.height, meta.width, meta.height);
            }
            Component name = Component.literal(meta.uploaderName == null ? "?" : meta.uploaderName);
            int nameWidth = this.font.width(name);
            int maxNameWidth = GRID_CELL_WIDTH - 4;
            if (nameWidth > maxNameWidth) {
                graphics.drawString(this.font, truncate(meta.uploaderName == null ? "?" : meta.uploaderName, 9), cellX, cellY + GRID_THUMB_SIZE + 2, 0xFFAAAAAA, false);
            } else {
                graphics.drawCenteredString(this.font, name, cellX + GRID_THUMB_SIZE / 2, cellY + GRID_THUMB_SIZE + 2, 0xFFAAAAAA);
            }
        }
        if (gallery.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("areaclaims.imagepicker.gallery_empty"), MARGIN, GALLERY_Y, 0xFFAAAAAA, false);
        }

        renderScrollbar(graphics, viewportBottom);
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }

    private void renderScrollbar(GuiGraphics graphics, int viewportBottom) {
        if (maxScrollOffset <= 0) return;
        int trackTop = GALLERY_Y - 4;
        int trackHeight = Math.max(1, viewportBottom - trackTop);
        int trackX = this.width - MARGIN - SCROLLBAR_WIDTH;
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, viewportBottom, SCROLLBAR_TRACK_COLOR);

        int contentHeight = trackHeight + maxScrollOffset;
        int thumbHeight = Math.max(12, trackHeight * trackHeight / contentHeight);
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = trackTop + thumbTravel * scrollOffset / maxScrollOffset;
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLLBAR_THUMB_COLOR);
    }

    private void pickFile() {
        Thread thread = new Thread(this::runFileDialog, "areaclaims-file-dialog");
        thread.setDaemon(true);
        thread.start();
    }

    private void runFileDialog() {
        String path;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(3);
            filters.put(stack.UTF8("*.png"));
            filters.put(stack.UTF8("*.jpg"));
            filters.put(stack.UTF8("*.jpeg"));
            filters.flip();
            path = TinyFileDialogs.tinyfd_openFileDialog(
                "AreaClaims - " + Component.translatable("areaclaims.imagepicker.choose_file").getString(),
                null, filters, "PNG/JPEG", false);
        } catch (Exception e) {
            AreaClaims.LOGGER.error("AreaClaims image file dialog failed", e);
            return;
        }
        if (path == null) return;
        Path filePath = Path.of(path);

        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            AreaClaims.LOGGER.error("could not stat picked AreaClaims image file {}", path, e);
            return;
        }
        // Grobe clientseitige Vorabsperre nach Dateigröße - die eigentliche, maßgebliche Prüfung
        // läuft ohnehin nochmal serverseitig (siehe ImageUploadPacket#finish), das hier ist nur eine
        // frühe Nutzer-Rückmeldung, kein Sicherheitsmerkmal.
        if (fileSize > 12 * 1024 * 1024) {
            Minecraft.getInstance().execute(() -> statusMessage = Component.translatable("areaclaims.image.upload.too_large"));
            return;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(filePath);
        } catch (IOException e) {
            AreaClaims.LOGGER.error("could not read picked AreaClaims image file {}", path, e);
            return;
        }

        NativeImage image;
        try {
            image = NativeImageDecoder.read(bytes);
        } catch (IOException e) {
            Minecraft.getInstance().execute(() -> statusMessage = Component.translatable("areaclaims.image.upload.invalid"));
            return;
        }

        Minecraft.getInstance().execute(() -> onFilePicked(bytes, image));
    }

    private void onFilePicked(byte[] bytes, NativeImage image) {
        if (previewTexture != null) {
            Minecraft.getInstance().getTextureManager().release(previewTexture);
        }
        Minecraft.getInstance().getTextureManager().register(PREVIEW_TEXTURE_ID, new DynamicTexture(image));
        previewTexture = PREVIEW_TEXTURE_ID;
        previewWidth = image.getWidth();
        previewHeight = image.getHeight();
        pendingFileBytes = bytes;
        statusMessage = null;
        if (uploadButton != null) uploadButton.active = true;
    }

    private void upload() {
        if (pendingFileBytes == null || uploading) return;
        uploading = true;
        if (uploadButton != null) uploadButton.active = false;
        statusMessage = Component.translatable("areaclaims.imagepicker.uploading");
        ImageUploader.send(pendingFileBytes);
    }

    /** Aufgerufen vom {@link ImageUploadResultPacket}-Handler, falls dieser Screen noch offen ist. */
    public static void onUploadResult(ImageUploadResultPacket packet) {
        AreaClaimsImagePickerScreen instance = activeInstance;
        if (instance == null) return;
        instance.uploading = false;
        if (!packet.success()) {
            instance.statusMessage = Component.translatable(packet.hashOrErrorKey());
            if (instance.uploadButton != null) instance.uploadButton.active = instance.pendingFileBytes != null;
            return;
        }
        instance.statusMessage = Component.translatable("areaclaims.imagepicker.upload_done");
        SetClaimImagePacket assignPacket = new SetClaimImagePacket(instance.claimId, packet.hashOrErrorKey());
        if (ClientNetworkUtil.canSendToServerOrWarn(assignPacket.type().id())) {
            PacketDistributor.sendToServer(assignPacket);
        }
        instance.returnToParentOrClose();
    }
}
