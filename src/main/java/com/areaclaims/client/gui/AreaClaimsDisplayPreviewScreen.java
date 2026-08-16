package com.areaclaims.client.gui;

import com.areaclaims.client.EntryDisplayOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Punkt 2 (Nachtrag 5, "Große Vorschau"): Vollbild-Mockup, wie die aktuell im
 * {@link AreaClaimsDisplayPrefsScreen} konfigurierten Texte TATSÄCHLICH aussehen würden - anders
 * als die kleine Punkt-Vorschau (Punkt 5, Nachtrag 4) zeigt dieser Screen echten (Platzhalter-)Text
 * in echter Größe/Position, mit dem echten konfigurierten Stil (Schriftart/Fett/Kursiv/
 * Unterstrichen/Schatten/Umriss/Skalierung).
 *
 * <p><b>Bewusst KEIN {@link FixedScaleScreen}</b> (anders als praktisch jeder andere Screen dieses
 * Mods) - die konfigurierten Positionen sind Versätze im REALEN, tatsächlichen GUI-skalierten
 * Bildschirmkoordinatensystem (siehe {@code EntryDisplayOverlayRenderer}, das dieselben Werte über
 * {@code Minecraft#getWindow()#getGuiScaledWidth/Height} auswertet) - ein normaler {@link Screen}s
 * {@code this.width}/{@code this.height} entsprechen GENAU diesem Koordinatensystem, eine
 * FixedScaleScreen-virtuelle Auflösung würde die Vorschau verfälschen ("was du hier siehst" muss
 * exakt "was du im Spiel siehst" entsprechen).
 *
 * <p>Nutzt {@link EntryDisplayOverlayRenderer#drawStyledText} - denselben Zeichenpfad wie die
 * ECHTE Betreten-Anzeige (siehe dortigen Klassenkommentar), damit diese Vorschau garantiert nicht
 * von der echten Anzeige abweichen kann.
 *
 * <p><b>Punkt 4 (Nachtrag 6, "Ziehen fehlt in der großen Vorschau"):</b> jeder gezeichnete
 * Platzhaltertext ist jetzt GENAUSO ziehbar wie die Punkte in der kleinen Vorschau
 * ({@code AreaClaimsDisplayPrefsScreen#collectPreviewDots}) - Trefftest gegen die TATSÄCHLICH
 * gerenderte Textbreite/-höhe (nicht nur einen kleinen Punkt, da hier echter Text in echter Größe
 * steht), schreibt beim Ziehen live in dieselben {@link net.minecraft.client.gui.components.EditBox}-
 * Felder des übergeordneten {@link AreaClaimsDisplayPrefsScreen} zurück (siehe
 * {@code AreaClaimsDisplayPrefsScreen.LargePreviewEntry}).
 *
 * <p><b>Nachtrag 6, Folgepunkt 1 ("badly blurred/ghosted" - erster Ursachenfund):</b> zwei
 * getrennte Ursachen, KEINE davon war die vom Nutzer vermutete Umriss-Mehrfach-Durchlauf-Mathematik
 * (die Versätze dort sind harmlose ±1px im bereits skalierten Koordinatensystem, siehe
 * {@link EntryDisplayOverlayRenderer#drawStyledText}):
 * <ol>
 *   <li><b>"Verschwommener Hintergrund":</b> {@code render()} rief bis hierhin
 *   {@code this.renderBackground(...)} auf - das ist Vanillas STANDARD Menü-Hintergrund
 *   (dekompiliert verifiziert, {@code Screen#renderBackground}): blurt die Welt per
 *   Post-Processing-Shader UND legt zusätzlich eine dunkle, halbtransparente Kacheltextur
 *   (Pause-Menü-Look) darüber - widerspricht dem eigentlichen ZWECK dieses Screens: eine
 *   1:1-Vorschau der ECHTEN Betreten-Anzeige, die im Spiel NIE einen verschwommenen/abgedunkelten
 *   Hintergrund hat. Fix (damals): der explizite {@code renderBackground()}-Aufruf entfernt.</li>
 *   <li><b>"Doppelter/geisterhafter Text":</b> KEIN Rendering-Bug, sondern zwei tatsächlich
 *   verschiedene Einträge (zeitgesteuerte UND dauerhafte Variante desselben Namens-Slots), die -
 *   solange "Dauerhaft" aktiviert ist - GLEICHZEITIG mit dem IDENTISCHEN Platzhaltertext
 *   gezeichnet wurden. Behoben durch fünf unterscheidbare Platzhaltertexte statt zwei
 *   wiederverwendeten.</li>
 * </ol>
 *
 * <p><b>Nachtrag 7, Punkt 7 ("Pixel Suppe" trotz obigem Fix WEITERHIN gemeldet - zweiter,
 * tatsächlicher Ursachenfund):</b> der erste Fix hatte den EXPLIZITEN {@code this.renderBackground(...)}-
 * Aufruf am ANFANG von {@code render()} entfernt - aber am ENDE stand weiterhin
 * {@code super.render(graphics, mouseX, mouseY, partialTick)}. Dekompiliert verifiziert
 * ({@code Screen#render}, Vanilla-Standardimplementierung): DIE ruft INTERN selbst NOCHMAL
 * {@code this.renderBackground(...)} auf, bevor sie die registrierten Widgets zeichnet:
 * <pre>
 * public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
 *     this.renderBackground(g, mouseX, mouseY, partialTick);   // <- genau der Aufruf, den wir "entfernt" hatten
 *     for (Renderable r : this.renderables) r.render(g, mouseX, mouseY, partialTick);
 * }
 * </pre>
 * Der Blur/die Abdunklung liefen also die ganze Zeit WEITER - nur jetzt NACH statt VOR dem eigenen
 * Text (der {@code super.render()}-Aufruf steht in dieser Klasse GANZ AM ENDE, nach dem
 * Textzeichnen) - GENAU die vom Nutzer selbst vermutete falsche Reihenfolge, nur eine Ebene tiefer
 * versteckt als beim ersten Fund. Endgültiger Fix: {@code super.render(...)} wird NICHT mehr
 * aufgerufen - stattdessen wird die (kurze, hier direkt kopierte) "nur Widgets zeichnen"-Hälfte von
 * Vanillas Standardimplementierung manuell nachgebildet, OHNE den Hintergrund-Aufruf. Kein Blur/
 * keine Abdunklung mehr an IRGENDEINER Stelle in diesem Screen - exakt die vom Nutzer explizit
 * freigegebene pragmatische Lösung ("fine with just removing any background blur/pixelation effect
 * entirely").
 */
public class AreaClaimsDisplayPreviewScreen extends Screen {

    private static final int CLOSE_BUTTON_WIDTH = 80;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 10;
    private static final int MARGIN = 10;

    private final AreaClaimsDisplayPrefsScreen parent;

    /** Punkt 4 (Nachtrag 6): welcher Eintrag (falls einer) gerade per Maus gezogen wird. */
    private AreaClaimsDisplayPrefsScreen.LargePreviewEntry draggingEntry;

    public AreaClaimsDisplayPreviewScreen(AreaClaimsDisplayPrefsScreen parent) {
        super(Component.translatable("areaclaims.displayprefs.large_preview_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.close"), b -> onClose())
            .bounds(this.width - CLOSE_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Nachtrag 6, Folgepunkt 1: bewusst KEIN this.renderBackground(...) mehr - siehe
        // Klassenkommentar. Die Welt rendert bereits unverändert im Hintergrund (isPauseScreen() ==
        // false), dieser Screen zeichnet rein additiv darüber, exakt wie die echte
        // Betreten-Anzeige (EntryDisplayOverlayRenderer) es im echten Spiel auch tut.
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFAAAAAA);

        List<AreaClaimsDisplayPrefsScreen.LargePreviewEntry> entries = parent.collectLargePreviewEntries(this.width, this.height);
        for (AreaClaimsDisplayPrefsScreen.LargePreviewEntry entry : entries) {
            EntryDisplayOverlayRenderer.drawStyledText(graphics, entry.text(), 0xFFFFFF, entry.style(), entry.screenX(), entry.screenY());
        }

        // Nachtrag 7, Punkt 7: bewusst KEIN super.render(...) - dessen Vanilla-Standardimplementierung
        // ruft INTERN selbst this.renderBackground(...) auf (dekompiliert verifiziert, siehe
        // Klassenkommentar), was den hier absichtlich entfernten Blur/die Abdunklung über den
        // Umweg wieder eingeschleppt hätte, nur diesmal NACH statt VOR dem eigenen Text. Stattdessen
        // wird nur die "Widgets zeichnen"-Hälfte von Screen#render manuell nachgebildet.
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** Rechnet die tatsächliche Bildschirm-Bounding-Box eines Eintrags aus - dieselbe Größenformel wie {@link EntryDisplayOverlayRenderer#drawStyledText} (Textbreite/Zeilenhöhe mal Skalierung), damit der Trefftest exakt zum sichtbaren Text passt. */
    private int[] boundingBox(AreaClaimsDisplayPrefsScreen.LargePreviewEntry entry) {
        var font = Minecraft.getInstance().font;
        float scale = entry.style().scale <= 0 ? 1f : Math.max(1f, Math.round(entry.style().scale));
        int textWidth = Math.round(font.width(entry.text()) * scale);
        int textHeight = Math.round(font.lineHeight * scale);
        int left = entry.screenX() - textWidth / 2;
        int top = entry.screenY() - textHeight / 2;
        return new int[] {left, top, textWidth, textHeight};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (AreaClaimsDisplayPrefsScreen.LargePreviewEntry entry : parent.collectLargePreviewEntries(this.width, this.height)) {
                int[] box = boundingBox(entry);
                if (mouseX >= box[0] && mouseX <= box[0] + box[2] && mouseY >= box[1] && mouseY <= box[1] + box[3]) {
                    draggingEntry = entry;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingEntry != null) {
            int centerX = parent.centerXFor(this.width);
            int centerY = parent.centerYFor(this.height);
            int rawX = (int) Math.round(mouseX - centerX);
            int rawY = (int) Math.round(mouseY - centerY);
            draggingEntry.xField().setValue(String.valueOf(rawX));
            draggingEntry.yField().setValue(String.valueOf(rawY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingEntry != null) {
            draggingEntry = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
