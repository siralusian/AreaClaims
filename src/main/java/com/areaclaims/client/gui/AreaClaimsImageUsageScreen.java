package com.areaclaims.client.gui;

import com.areaclaims.client.ClientImageManager;
import com.areaclaims.client.data.ClientImageUsageCache;
import com.areaclaims.client.data.ClientNetworkUtil;
import com.areaclaims.network.DeleteImagePacket;
import com.areaclaims.network.ImageUsageEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Punkt 8 (Nachtrag 5): OP4-Admin-Übersicht "welches hochgeladene Bild wird von welchen Claims
 * genutzt" - Bilder ohne jeden Treffer werden auffällig als "ungenutzt" markiert, damit ein Admin
 * sie als Aufräum-Kandidaten erkennt.
 *
 * <p><b>Punkt 2 (Nachtrag 6): tatsächliches Löschen jetzt ergänzt</b> (Nachtrag 5 war bewusst NUR
 * Sichtbarkeit) - roter "✗"-{@link GlyphButton} je Zeile, gleiches Klick-zum-Bestätigen-Muster wie
 * die Claim-Löschung in {@code AreaClaimsEditorScreen#deleteGlyphButton} (erster Klick "bewaffnet"
 * für {@link #DELETE_CONFIRM_WINDOW_MS}, zweiter Klick innerhalb des Fensters bestätigt tatsächlich)
 * - bewusst DIESELBE Zeitspanne/Deneselbe Interaktion statt eines neuen Musters. Löschen funktioniert
 * UNABHÄNGIG vom Nutzungsstatus (auch für gerade genutzte Bilder) - siehe
 * {@code ServerImageStore#delete}-Klassenkommentar für die Begründung.
 */
public class AreaClaimsImageUsageScreen extends FixedScaleScreen {

    private static final int MARGIN = 10;
    private static final int TITLE_Y = 8;
    private static final int LIST_Y = 28;
    private static final int ROW_HEIGHT = 40;
    private static final int THUMB_SIZE = 32;
    private static final int CLOSE_BUTTON_WIDTH = 80;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 10;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_TRACK_COLOR = 0x40FFFFFF;
    private static final int SCROLLBAR_THUMB_COLOR = 0xFFAAAAAA;
    private static final double SCROLL_PIXELS_PER_NOTCH = 24.0;
    private static final int UNUSED_COLOR = 0xFFFF5555;
    private static final int USED_COLOR = 0xFFAAAAAA;
    private static final int DELETE_BUTTON_SIZE = 16;
    private static final long DELETE_CONFIRM_WINDOW_MS = 4000;
    private static final int RED = 0xFFFF5555;
    private static final int BACK_BUTTON_WIDTH = 80;
    private static final int BACK_BUTTON_GAP = 4;

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private String deleteArmedHash;
    private long deleteArmedUntil = 0;

    /** Punkt 3 (Nachtrag 6, "Zurück"-Button): siehe {@code AreaClaimsServerConfigScreen#parentScreen}-Kommentar. */
    private final net.minecraft.client.gui.screens.Screen parentScreen;

    public AreaClaimsImageUsageScreen(net.minecraft.client.gui.screens.Screen parentScreen) {
        super(Component.translatable("areaclaims.imageusage.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void initScaled() {
        buildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (deleteArmedHash != null && System.currentTimeMillis() >= deleteArmedUntil) {
            deleteArmedHash = null;
            clearWidgets();
            buildWidgets();
        }
    }

    private int viewportBottom() {
        return this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN - 6;
    }

    private void buildWidgets() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        List<ImageUsageEntry> entries = ClientImageUsageCache.get();
        int contentHeight = entries.size() * ROW_HEIGHT;
        int viewportBottom = viewportBottom();
        maxScrollOffset = Math.max(0, contentHeight - (viewportBottom - LIST_Y));

        int y = LIST_Y - scrollOffset;
        for (ImageUsageEntry entry : entries) {
            // Wie im Bild-Auswahl-Raster (Nachtrag 5, Punkt 7): keine Widgets für Zeilen bauen, die
            // komplett aus dem Sichtbereich gescrollt sind (kein hartes Scissor-Clipping in diesem Mod).
            if (y + ROW_HEIGHT >= LIST_Y && y <= viewportBottom) {
                addRenderableWidget(deleteGlyphButton(entry.hash, this.width - DELETE_BUTTON_SIZE - MARGIN - (int) SCROLLBAR_WIDTH - 4, y, DELETE_BUTTON_SIZE, DELETE_BUTTON_SIZE));
            }
            y += ROW_HEIGHT;
        }

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.close"), b -> onClose())
            .bounds(this.width - CLOSE_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.back"),
                b -> this.minecraft.setScreen(parentScreen))
            .bounds(this.width - CLOSE_BUTTON_WIDTH - BACK_BUTTON_GAP - BACK_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                BACK_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());
    }

    private GlyphButton deleteGlyphButton(String hash, int x, int y, int width, int height) {
        boolean armed = hash.equals(deleteArmedHash) && System.currentTimeMillis() < deleteArmedUntil;
        Component label = armed ? Component.translatable("areaclaims.editor.delete_confirm") : Component.literal("✗");
        int color = armed ? 0xFFFFAA00 : RED;
        return new GlyphButton(x, y, width, height, label, color, () -> handleDeleteClick(hash));
    }

    private void handleDeleteClick(String hash) {
        long now = System.currentTimeMillis();
        if (hash.equals(deleteArmedHash) && now < deleteArmedUntil) {
            deleteArmedHash = null;
            DeleteImagePacket packet = new DeleteImagePacket(hash);
            if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
                PacketDistributor.sendToServer(packet);
            }
        } else {
            deleteArmedHash = hash;
            deleteArmedUntil = now + DELETE_CONFIRM_WINDOW_MS;
        }
        clearWidgets();
        buildWidgets();
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
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderScaled(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);

        List<ImageUsageEntry> entries = ClientImageUsageCache.get();
        if (entries.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("areaclaims.imageusage.empty"), MARGIN, LIST_Y, 0xFFAAAAAA, false);
            return;
        }

        int viewportBottom = viewportBottom();
        int y = LIST_Y - scrollOffset;
        for (ImageUsageEntry entry : entries) {
            if (y + ROW_HEIGHT >= LIST_Y && y <= viewportBottom) {
                renderEntry(graphics, entry, y);
            }
            y += ROW_HEIGHT;
        }

        renderScrollbar(graphics, viewportBottom);
    }

    private void renderEntry(GuiGraphics graphics, ImageUsageEntry entry, int y) {
        ResourceLocation thumb = ClientImageManager.resolveTexture(entry.hash);
        graphics.fill(MARGIN, y, MARGIN + THUMB_SIZE, y + THUMB_SIZE, 0xFF333333);
        if (thumb != null) {
            graphics.blit(thumb, MARGIN, y, THUMB_SIZE, THUMB_SIZE, 0f, 0f, entry.width, entry.height, entry.width, entry.height);
        }

        int textX = MARGIN + THUMB_SIZE + 8;
        String shortHash = entry.hash != null && entry.hash.length() > 10 ? entry.hash.substring(0, 10) + "..." : entry.hash;
        graphics.drawString(this.font, shortHash + " (" + entry.width + "x" + entry.height + ")", textX, y, 0xFFFFFF, false);
        graphics.drawString(this.font, Component.translatable("areaclaims.imageusage.uploaded_by", entry.uploaderName == null ? "?" : entry.uploaderName),
            textX, y + 11, 0xFFAAAAAA, false);

        if (entry.usedBy.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("areaclaims.imageusage.unused"), textX, y + 22, UNUSED_COLOR, false);
        } else {
            StringBuilder usage = new StringBuilder();
            for (int i = 0; i < entry.usedBy.size(); i++) {
                if (i > 0) usage.append(", ");
                if (i >= 4) {
                    usage.append("+").append(entry.usedBy.size() - 4);
                    break;
                }
                ImageUsageEntry.ClaimRef ref = entry.usedBy.get(i);
                usage.append(ref.claimName).append(" (").append(ref.ownerName).append(")");
            }
            graphics.drawString(this.font, Component.translatable("areaclaims.imageusage.used_by", usage.toString()), textX, y + 22, USED_COLOR, false);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int viewportBottom) {
        if (maxScrollOffset <= 0) return;
        int trackTop = LIST_Y - 4;
        int trackHeight = Math.max(1, viewportBottom - trackTop);
        int trackX = this.width - MARGIN - SCROLLBAR_WIDTH;
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, viewportBottom, SCROLLBAR_TRACK_COLOR);

        int contentHeight = trackHeight + maxScrollOffset;
        int thumbHeight = Math.max(12, trackHeight * trackHeight / contentHeight);
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = trackTop + thumbTravel * scrollOffset / maxScrollOffset;
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLLBAR_THUMB_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
