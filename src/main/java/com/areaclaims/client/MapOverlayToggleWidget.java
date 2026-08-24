package com.areaclaims.client;

import com.areaclaims.client.data.ClientMapOverlayPrefs;
import com.areaclaims.integration.JourneyMapClientBridge;
import com.mojang.blaze3d.platform.Window;
import journeymap.api.v2.client.display.Context.UI;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Eigener Umschalt-Knopf auf JourneyMaps Vollbildkarte (Nutzer-Vorgabe 2026-08-17: "so wie es
 * Create auch macht" - siehe Create's {@code JourneyTrainMap#renderToggleWidgetAndTooltip} für das
 * Vorbild, dekompiliert gegen create-1.21.1-6.0.10.jar). Reine Anzeige-Präferenz
 * ({@link ClientMapOverlayPrefs}), gilt nur für DIESEN Spieler - schaltet
 * {@link JourneyMapClientBridge} lokal ab/an, ohne den Server-Admin-Schalter zu berühren.
 *
 * <p>Anders als Create (das gegen das volle {@code journeymap-neoforge}-Jar kompiliert und daher
 * direkt {@code mc.screen instanceof Fullscreen} prüfen kann) nutzt diese Klasse bewusst NUR die
 * schlanke {@code journeymap-api}-Schnittstelle (gleiches Isolations-Muster wie der Rest dieser
 * Integration) - "ist die Vollbildkarte gerade offen" wird stattdessen über einen einfachen
 * Referenz-Vergleich mit dem zuletzt in {@link #onRender} gesehenen {@link Screen} ermittelt, der
 * jeden Client-Tick VOR den Render-Events zurückgesetzt wird.
 */
public final class MapOverlayToggleWidget {

    private static final int X = 3;
    private static final int Y = 60;
    private static final int WIDTH = 90;
    private static final int HEIGHT = 20;

    private static volatile Screen lastFullscreenScreen;

    private MapOverlayToggleWidget() {}

    public static void register() {
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe("areaclaims", MapOverlayToggleWidget::onRender);
        NeoForge.EVENT_BUS.addListener(MapOverlayToggleWidget::onClientTick);
        NeoForge.EVENT_BUS.addListener(MapOverlayToggleWidget::onMouseClick);
    }

    // Vor jedem Tick zurückgesetzt - bleibt nur "frisch", solange onRender diesen Tick tatsächlich
    // erneut aufgerufen wurde (= Vollbildkarte ist offen und aktiv).
    private static void onClientTick(ClientTickEvent.Pre event) {
        lastFullscreenScreen = null;
    }

    private static void onRender(FullscreenRenderEvent event) {
        IFullscreen fullscreen = event.getFullscreen();
        UIState state = fullscreen.getUiState();
        if (state == null || state.ui != UI.Fullscreen || !state.active) return;
        lastFullscreenScreen = fullscreen.getScreen();

        GuiGraphics graphics = event.getGraphics();
        boolean enabled = ClientMapOverlayPrefs.showOnMap();

        int background = enabled ? 0xCC1E5C2E : 0xCC5C1E1E;
        int border = enabled ? 0xFF55DD55 : 0xFFDD5555;
        graphics.fill(X, Y, X + WIDTH, Y + HEIGHT, background);
        graphics.fill(X, Y, X + WIDTH, Y + 1, border);
        graphics.fill(X, Y + HEIGHT - 1, X + WIDTH, Y + HEIGHT, border);
        graphics.fill(X, Y, X + 1, Y + HEIGHT, border);
        graphics.fill(X + WIDTH - 1, Y, X + WIDTH, Y + HEIGHT, border);

        Minecraft mc = Minecraft.getInstance();
        Component label = Component.translatable(enabled ? "areaclaims.map.overlay_on" : "areaclaims.map.overlay_off");
        int textWidth = mc.font.width(label);
        graphics.drawString(mc.font, label, X + (WIDTH - textWidth) / 2, Y + (HEIGHT - 8) / 2, 0xFFFFFFFF);

        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        if (isHovered(mouseX, mouseY)) {
            graphics.renderTooltip(mc.font, Component.translatable("areaclaims.map.overlay_toggle_tooltip"), mouseX, mouseY + 20);
        }
    }

    private static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != 0 || event.getAction() != 1) return; // nur linke Maustaste, nur Press
        Screen fullscreen = lastFullscreenScreen;
        Minecraft mc = Minecraft.getInstance();
        if (fullscreen == null || mc.screen != fullscreen) return;

        Window window = mc.getWindow();
        double mouseX = mc.mouseHandler.xpos() * (double) window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) window.getGuiScaledHeight() / (double) window.getScreenHeight();
        if (isHovered((int) mouseX, (int) mouseY)) {
            ClientMapOverlayPrefs.toggle();
            JourneyMapClientBridge.onOverlayPrefChanged();
            event.setCanceled(true);
        }
    }

    private static boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= X && mouseX < X + WIDTH && mouseY >= Y && mouseY < Y + HEIGHT;
    }
}
