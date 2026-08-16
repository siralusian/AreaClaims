package com.areaclaims.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * "Regenbogen"-Farbwähler (ROADMAP.md Phase 7-Nachtrag, Punkt 1/5 - Nutzer-Vorgabe: "a gradient/hue
 * field the player can click on" ZUSÄTZLICH zum Hex-Textfeld). Zeichnet einen horizontalen
 * Farbton-Streifen (Sättigung/Helligkeit fest auf Maximum, nur der Farbton 0-360° variiert über die
 * Breite) - ein Klick errechnet aus der X-Position den Farbton und liefert die passende RGB-Farbe.
 *
 * <p><b>Bewusste Vereinfachung (Judgment call):</b> ein EINDIMENSIONALER Farbton-Streifen statt
 * eines vollen 2D-Farbrads/Sättigung-Helligkeit-Quadrats - deckt zusammen mit dem parallel
 * angebotenen Hex-Textfeld (für exakte/entsättigte/abgedunkelte Farbwerte) den praktischen
 * Bedarf ab, ohne die deutlich aufwändigere 2D-Auswahl-Logik (weiteres Widget, weitere Klick-
 * Trefferprüfung) zu benötigen - eine übliche, vereinfachte Form eines "Regenbogen-Wählers".
 */
public class HueBarWidget extends AbstractWidget {

    /** Reiner Math-Helfer, keine AWT-Abhängigkeit (siehe Klassenkommentar - bewusst selbst geschrieben statt java.awt.Color#HSBtoRGB). */
    public interface OnPick {
        void pick(int rgb);
    }

    private final OnPick onPick;

    public HueBarWidget(int x, int y, int width, int height, OnPick onPick) {
        super(x, y, width, height, Component.empty());
        this.onPick = onPick;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int w = getWidth();
        int h = getHeight();
        for (int i = 0; i < w; i++) {
            float hue = (float) i / w;
            int rgb = hsvToRgb(hue, 1f, 1f);
            graphics.fill(getX() + i, getY(), getX() + i + 1, getY() + h, 0xFF000000 | rgb);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        float hue = (float) Mth.clamp((mouseX - getX()) / Math.max(1, getWidth()), 0.0, 1.0);
        onPick.pick(hsvToRgb(hue, 1f, 1f));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /** hue/sat/val jeweils [0,1] -> 0xRRGGBB. Standard-HSV-zu-RGB-Umrechnung. */
    public static int hsvToRgb(float hue, float saturation, float value) {
        float h = hue * 6f;
        int sector = (int) h % 6;
        float f = h - (int) h;
        float p = value * (1f - saturation);
        float q = value * (1f - saturation * f);
        float t = value * (1f - saturation * (1f - f));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        int ri = Math.round(r * 255f);
        int gi = Math.round(g * 255f);
        int bi = Math.round(b * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }
}
