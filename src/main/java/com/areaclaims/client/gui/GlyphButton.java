package com.areaclaims.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Klickbarer, einfarbiger Text-Glyph OHNE Button-Hintergrund/-Rahmen - für den CobbleCompanion-
 * Stil "roter ✗"/"grüner ✓" (ROADMAP.md Phase 7-Nachtrag "Punkt 3/4: rote-X-/Häkchen-Stil"), 1:1
 * visuell abgeleitet von {@code CompanionScreen}s Freundesliste/Anfragen-Zeilen (dort literale
 * "✓"/"✗"-Textglyphen in Grün {@code 0xFF55FF55}/Rot, siehe Referenz-Kommentar an den jeweiligen
 * Verwendungsstellen).
 *
 * <p><b>Implementierungs-Judgment-call:</b> CompanionScreen zeichnet diese Glyphen dort NICHT als
 * echte Widgets, sondern als reinen Text + manuelle Klick-Trefferprüfung in einer eigenen
 * {@code handleFriendsClicks}-Methode. Diese Klasse bildet stattdessen eine ECHTE
 * {@link AbstractWidget}-Unterklasse, die sich normal über {@code addRenderableWidget} registriert -
 * das visuelle Ergebnis ist identisch (farbiger Glyph, kein Button-Rahmen), aber die Klick-
 * Erkennung läuft über Vanillas bereits vorhandene, mit {@link FixedScaleScreen}s Koordinaten-
 * Umrechnung bereits verifiziert zusammenarbeitende Widget-Maschinerie, statt eine zweite, eigene
 * Trefferprüfung parallel zu pflegen (die bei einem skalierten Screen leicht divergieren könnte).
 */
public class GlyphButton extends AbstractWidget {

    /** Leichte Hervorhebung beim Überfahren mit der Maus - GlyphButton hat sonst KEINE Button-Rahmen-Andeutung, siehe Klassenkommentar. */
    private static final int HOVER_BG = 0x30FFFFFF;

    private final int color;
    private final Runnable onPress;

    /**
     * ROADMAP.md Phase 7-Nachtrag 3, Punkt 4 ("Häkchen dicker/kräftiger, ähnliches Gewicht wie das
     * X"): der Nachrichtentext wird hier IMMER fett dargestellt (statt nur für einzelne
     * Verwendungsstellen) - macht sowohl das Häkchen kräftiger UND hält alle Glyph-Verwendungen
     * (Löschen-X, Regel-Häkchen/-Kreuz, Stift-Symbol) konsistent im selben, etwas kräftigeren Stil.
     */
    public GlyphButton(int x, int y, int width, int height, Component message, int color, Runnable onPress) {
        super(x, y, width, height, message.copy().withStyle(net.minecraft.ChatFormatting.BOLD));
        this.color = color;
        this.onPress = onPress;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        // Nachtrag 7, Punkt 4 (Regel-Freikauf-Sperre): gesperrte Glyphen (siehe AreaClaimsEditorScreen
        // #buildRulePanel, {@code active = false} solange eine Regel noch nicht freigekauft ist)
        // werden sichtbar abgedunkelt gezeichnet, kein Hover-Effekt, damit "gesperrt" auch optisch
        // erkennbar ist, nicht nur unsichtbar unklickbar.
        if (this.active && this.isHovered()) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), HOVER_BG);
        }
        int drawColor = this.active ? color : dim(color);
        int textWidth = font.width(getMessage());
        int textX = getX() + Math.max(0, (getWidth() - textWidth) / 2);
        int textY = getY() + Math.max(0, (getHeight() - font.lineHeight) / 2);
        graphics.drawString(font, getMessage(), textX, textY, drawColor, false);
    }

    /** Halbiert Helligkeit je Farbkanal, Alpha unverändert - für den "gesperrt"-Zustand. */
    private static int dim(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >>> 16) & 0xFF) / 2;
        int g = ((argb >>> 8) & 0xFF) / 2;
        int b = (argb & 0xFF) / 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
