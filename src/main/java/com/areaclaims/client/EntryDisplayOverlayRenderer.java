package com.areaclaims.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client-seitiger Renderer für die neue, eigene Betreten-Nachricht-Anzeige (ROADMAP.md Phase
 * 7-Nachtrag 3, Punkte 13-15 "Betreten-Nachricht-Overhaul"; Sequenzierung überarbeitet in Nachtrag
 * 4, Punkt 2) - zeichnet bis zu 3 unabhängige "Slots" (Hauptbereichsname/Unterbereichsname/
 * Willkommensnachricht), jeder mit eigener Position (Versatz zur Bildschirmmitte, siehe
 * {@link com.areaclaims.display.PlayerDisplayPreferences}s Klassenkommentar) und eigener Farbe.
 *
 * <p><b>Punkt 2 (Nachtrag 4) - "zeitgesteuert DANN dauerhaft", keine Alternative:</b> ein Slot
 * durchläuft bis zu zwei Phasen: TIMED (immer zuerst, normale Position, feste Dauer) und - NUR
 * falls der Betrachter "dauerhaft anzeigen" für diesen Slot aktiviert hat - anschließend PERMANENT
 * (eigene Position, bleibt bis der Bereich verlassen/gewechselt wird). Beide Phasen laufen rein
 * lokal anhand des EINEN beim Betreten empfangenen Pakets ab (siehe {@link #onClientTick}) - kein
 * zweites Serverpaket zum Umschaltzeitpunkt nötig.
 *
 * <p><b>Verifizierter Render-Hook:</b> {@link RenderGuiEvent.Post} (dekompilierte NeoForge-Sourcen,
 * {@code net/neoforged/neoforge/client/event/RenderGuiEvent.java}, geprüft VOR Verwendung wie vom
 * Nutzer explizit gefordert) - "fired AFTER the HUD is rendered to the screen", auf
 * {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, nur clientseitig. Ersetzt komplett
 * Vanillas {@code ClientboundSetTitleTextPacket}/{@code ClientboundSetSubtitleTextPacket}/simulierte
 * Actionbar-Wiederholung (siehe {@code EntryDisplaySlotPacket}-Klassenkommentar für die Begründung).
 *
 * <p><b>Ablauf-Zeitgeber:</b> läuft rein über einen lokalen, pro Client-Tick hochgezählten Zähler
 * ({@link #clientTicks}) statt über Wanduhrzeit - konsistent mit der restlichen "Ticks"-Terminologie
 * dieses Mods, unempfindlich gegen Systemzeit-Sprünge.
 */
public class EntryDisplayOverlayRenderer {

    private static final Gson GSON = new Gson();

    private enum Slot { MAIN, SUB, WELCOME }

    private enum Phase { HIDDEN, TIMED, PERMANENT }

    private static class SlotState {
        Phase phase = Phase.HIDDEN;
        /** Roher Text/Farbe statt eines fertigen {@link Component} - Punkt 3 (Nachtrag 4): der Stil
         * (fett/kursiv/Schriftart/...) wird ERST beim Zeichnen aus den AKTUELLEN lokalen Einstellungen
         * gebaut (siehe {@link #styledComponent}), damit eine Änderung im Anzeige-Einstellungen-
         * Screen sich sofort auf eine bereits laufende Anzeige auswirkt, ohne auf das nächste
         * Server-Paket warten zu müssen. */
        String rawText = "";
        int color = 0xFFFFFF;
        /** Aktuell aktive Position - während TIMED die normale, während PERMANENT die Dauerhaft-Position. */
        int posX;
        int posY;
        /** Nur während TIMED gültig - wann die Phase zu PERMANENT wechselt (falls {@link #willBecomePermanent}) oder ausblendet. */
        long expiryTick = -1;
        boolean willBecomePermanent;
        int permanentPosX;
        int permanentPosY;
        /** Punkt 6 (Nachtrag 4): Inhalts-Hash eines vom Besitzer zugewiesenen Bilds - leer = kein Bild, immer Textname (siehe {@link #onRenderGui}). */
        String imageHash = "";
    }

    private static final Map<Slot, SlotState> states = new EnumMap<>(Slot.class);

    static {
        for (Slot slot : Slot.values()) states.put(slot, new SlotState());
    }

    private static long clientTicks = 0;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        clientTicks++;
        for (SlotState state : states.values()) {
            if (state.phase == Phase.TIMED && state.expiryTick >= 0 && clientTicks >= state.expiryTick) {
                if (state.willBecomePermanent) {
                    // Punkt 2 (Nachtrag 4): Übergang TIMED -> PERMANENT - Position springt auf die
                    // Dauerhaft-Position, keine weitere Ausblend-Frist mehr.
                    state.phase = Phase.PERMANENT;
                    state.posX = state.permanentPosX;
                    state.posY = state.permanentPosY;
                    state.expiryTick = -1;
                } else {
                    state.phase = Phase.HIDDEN;
                }
            }
        }
    }

    /** Aufgerufen vom {@code EntryDisplaySlotPacket}-Handler (Netzwerk-Thread -> Client-Objekt, hier bewusst simpel gehalten statt über einen zusätzlichen Tick-Warteschlangen-Umweg, da nur einfache Feld-Zuweisungen passieren). */
    public static void applyFromServer(String json) {
        com.areaclaims.network.EntryDisplaySlotPacket.Dto dto;
        try {
            dto = GSON.fromJson(json, com.areaclaims.network.EntryDisplaySlotPacket.Dto.class);
        } catch (RuntimeException e) {
            return;
        }
        if (dto == null || dto.slot == null) return;
        Slot slot;
        try {
            slot = Slot.valueOf(dto.slot);
        } catch (IllegalArgumentException e) {
            return;
        }
        SlotState state = states.get(slot);
        if (!dto.visible) {
            state.phase = Phase.HIDDEN;
            state.expiryTick = -1;
            return;
        }
        // Jedes neu empfangene Paket startet IMMER wieder bei der zeitgesteuerten Phase (Punkt 2:
        // "when a player enters a claim, the NORMAL timed display always plays first") - auch wenn
        // die vorherige Anzeige gerade in PERMANENT war (z. B. Wechsel zwischen zwei Claims mit
        // jeweils aktivierter Dauerhaft-Anzeige).
        state.phase = Phase.TIMED;
        state.rawText = dto.text;
        state.color = dto.color;
        state.posX = dto.posX;
        state.posY = dto.posY;
        state.expiryTick = clientTicks + Math.max(1, dto.durationTicks);
        state.willBecomePermanent = dto.permanentAfter;
        state.permanentPosX = dto.permanentPosX;
        state.permanentPosY = dto.permanentPosY;
        state.imageHash = dto.imageHash == null ? "" : dto.imageHash;
    }

    /** Beim Trennen/Weltwechsel alles zurücksetzen, damit keine Anzeige aus der vorherigen Sitzung "hängen bleibt" (gleiches Vorsichtsprinzip wie {@code ClaimShowcaseManager#stop} serverseitig beim Abmelden). */
    public static void clearAll() {
        for (SlotState state : states.values()) {
            state.phase = Phase.HIDDEN;
            state.expiryTick = -1;
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = mc.getWindow().getGuiScaledWidth() / 2;
        int centerY = mc.getWindow().getGuiScaledHeight() / 2;

        var prefs = com.areaclaims.client.data.ClientDisplayPrefsCache.get();
        renderNameOrImageSlot(graphics, states.get(Slot.MAIN), centerX, centerY, prefs.mainClaim);
        renderNameOrImageSlot(graphics, states.get(Slot.SUB), centerX, centerY, prefs.subClaim);
        renderSlot(graphics, states.get(Slot.WELCOME), centerX, centerY, prefs.welcome.style);
    }

    /**
     * Punkt 6 (Nachtrag 4, "Bild statt Text"), Umschalter-Aufteilung überarbeitet in Nachtrag 5
     * Punkt 4: entscheidet je Slot, ob ein zugewiesenes Bild ODER der normale Textname gezeigt wird
     * - Bild NUR wenn (a) der Besitzer diesem Claim überhaupt eins zugewiesen hat ({@link
     * SlotState#imageHash} nicht leer) UND (b) der Betrachter für die AKTUELL AKTIVE Phase seinen
     * eigenen Bild-Umschalter aktiviert hat ({@link com.areaclaims.display.PlayerDisplayPreferences.NameDisplayPrefs#showImageTimed}
     * während TIMED, {@link com.areaclaims.display.PlayerDisplayPreferences.NameDisplayPrefs#showImagePermanent}
     * während PERMANENT - Nachtrag 4s dritter, GLOBALER "permanentShowImage"-Umschalter wurde durch
     * dieses per-Slot-Paar ersetzt, siehe {@code NameDisplayPrefs}-Klassenkommentar). Fällt in jedem
     * anderen Fall auf den normalen Textpfad zurück (auch während das Bild noch lädt - {@link
     * ClientImageManager#resolveTexture} liefert dann {@code null}, siehe unten).
     */
    private void renderNameOrImageSlot(GuiGraphics graphics, SlotState state, int centerX, int centerY,
                                        com.areaclaims.display.PlayerDisplayPreferences.NameDisplayPrefs prefs) {
        boolean showImageForPhase = state.phase == Phase.PERMANENT ? prefs.showImagePermanent : prefs.showImageTimed;
        boolean wantsImage = !state.imageHash.isBlank() && showImageForPhase;
        if (wantsImage) {
            net.minecraft.resources.ResourceLocation texture = ClientImageManager.resolveTexture(state.imageHash, prefs.pixelArtFiltering);
            if (texture != null) {
                renderImageSlot(graphics, state, centerX, centerY, texture, prefs.imageZoomPercent);
                return;
            }
            // Noch nicht geladen (siehe ClientImageManager-Klassenkommentar "null = noch nicht
            // bereit") - für DIESEN Frame auf Text zurückfallen, sobald geladen greift der obige
            // Zweig automatisch (kein Zustand hier zu pflegen, resolveTexture cached selbst).
        }
        renderSlot(graphics, state, centerX, centerY, prefs.style);
    }

    /** Zeichnet das zugewiesene Bild an der aktuell aktiven Slot-Position, Basis-Größe mal dem persönlichen Zoom-Prozentsatz (Nachtrag 5, Punkt 10). */
    private void renderImageSlot(GuiGraphics graphics, SlotState state, int centerX, int centerY,
                                  net.minecraft.resources.ResourceLocation texture, int zoomPercent) {
        if (state.phase == Phase.HIDDEN) return;
        int[] dims = ClientImageManager.dimensions(state.imageHash);
        int baseWidth = dims != null ? dims[0] : com.areaclaims.network.ImageUploadPacket.BASELINE_HEIGHT;
        int baseHeight = dims != null ? dims[1] : com.areaclaims.network.ImageUploadPacket.BASELINE_HEIGHT;
        float z = zoomPercent <= 0 ? 1f : zoomPercent / 100f;
        int drawWidth = Math.round(baseWidth * z);
        int drawHeight = Math.round(baseHeight * z);
        int x = centerX + state.posX - drawWidth / 2;
        int y = centerY + state.posY - drawHeight / 2;
        graphics.blit(texture, x, y, drawWidth, drawHeight, 0f, 0f, baseWidth, baseHeight, baseWidth, baseHeight);
    }

    /**
     * Punkt 3 (Nachtrag 4): EIN gemeinsamer, stil-bewusster Zeichenpfad für alle 3 Slots (vorher
     * zwei fast identische Methoden mit fest verdrahteter 2x- bzw. 1x-Skalierung) - Größe/Fett/
     * Kursiv/Unterstrichen/Schriftart/Schatten/Umriss kommen jetzt alle aus den AKTUELLEN lokalen
     * {@link com.areaclaims.display.PlayerDisplayPreferences.TextStyle}-Einstellungen des
     * Betrachters (siehe Klassenkommentar {@link SlotState#rawText}), nicht mehr aus einem fest
     * gebauten {@link Component}.
     */
    private void renderSlot(GuiGraphics graphics, SlotState state, int centerX, int centerY,
                             com.areaclaims.display.PlayerDisplayPreferences.TextStyle style) {
        if (state.phase == Phase.HIDDEN || state.rawText.isBlank()) return;
        drawStyledText(graphics, state.rawText, state.color, style, centerX + state.posX, centerY + state.posY);
    }

    /**
     * Punkt 3 (Nachtrag 4), als eigenständige STATISCHE Methode herausgezogen in Nachtrag 5 (Punkt 2
     * "Große Vorschau"): der EINE stil-bewusste Zeichenpfad für Text (Größe/Fett/Kursiv/
     * Unterstrichen/Schriftart/Schatten/Umriss) - wird sowohl vom eigentlichen Betreten-Overlay
     * (siehe {@link #renderSlot}) als auch vom neuen {@code AreaClaimsDisplayPreviewScreen} genutzt,
     * damit die große Vorschau GARANTIERT exakt so aussieht wie die echte Anzeige (dieselbe Methode,
     * kein zweiter, potenziell abweichender Zeichenpfad).
     *
     * @param screenX/screenY Bildschirm-Mittelpunkt DES TEXTES (nicht Bildschirmmitte + Versatz -
     *                        das rechnet der Aufrufer bereits selbst zusammen).
     */
    public static void drawStyledText(GuiGraphics graphics, String rawText, int color,
                                       com.areaclaims.display.PlayerDisplayPreferences.TextStyle style, int screenX, int screenY) {
        if (rawText == null || rawText.isBlank()) return;
        var font = Minecraft.getInstance().font;

        Style textStyle = Style.EMPTY.withColor(TextColor.fromRgb(color))
            .withBold(style.bold)
            .withItalic(style.italic)
            .withUnderlined(style.underline);
        net.minecraft.resources.ResourceLocation fontId = net.minecraft.resources.ResourceLocation.tryParse(style.font);
        if (fontId != null) textStyle = textStyle.withFont(fontId);
        Component text = Component.literal(rawText).withStyle(textStyle);

        // Nachtrag 6, Punkt 4 (Pixelierung-Fund): Vanillas eigene Titel/Untertitel-Anzeige nutzt
        // GENAU dieselbe Ganzzahl-Position-Übersetzen + pose().scale()-Technik wie hier (verifiziert
        // in den dekompilierten Sourcen, Gui.java#renderTitle: 4.0F fürs Titel, 2.0F fürs Untertitel,
        // BEIDE glatte Ganzzahlen) - die Technik selbst war also nicht der Fehler. Der eigentliche
        // Fehler: {@link com.areaclaims.display.PlayerDisplayPreferences.TextStyle#scale} akzeptierte
        // beliebige Fließkommawerte (z. B. über das Textdesign-Skalierungsfeld) OHNE auf eine
        // Ganzzahl zu runden - Minecrafts Bitmap-Schriftglyphen werden bei NICHT-ganzzahligen
        // Skalierungsfaktoren uneinheitlich auf Zielpixel abgebildet (manche Quellpixel decken 2
        // Zielpixel ab, andere 3, je nach Position) - das erzeugt sichtbar "blockige"/uneinheitliche
        // Kanten, die bei einem glatten Ganzzahl-Faktor (wie Vanillas 4x/2x) nicht auftreten. Fix:
        // hier auf die nächste Ganzzahl runden (mindestens 1), GENAU wie vom Nutzer selbst vermutet
        // ("using integer-friendly scale factors").
        float scale = style.scale <= 0 ? 1f : Math.max(1f, Math.round(style.scale));
        int textWidth = font.width(text);

        graphics.pose().pushPose();
        graphics.pose().translate(screenX, screenY, 0);
        graphics.pose().scale(scale, scale, 1f);
        int drawX = -textWidth / 2;
        int drawY = -font.lineHeight / 2;

        if (style.outline) {
            // Punkt 3 (Nachtrag 4): kein natives Vanilla-Umriss-Textrender-Feature (verifiziert -
            // GuiGraphics/Font bieten nur den einfachen "dropShadow"-Versatz) - manueller Mehrfach-
            // Durchlauf: dieselbe Zeile 8x um je 1px versetzt in Schwarz OHNE Schatten zeichnen,
            // danach der eigentliche Text obendrauf.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    graphics.drawString(font, text, drawX + dx, drawY + dy, 0xFF000000, false);
                }
            }
        }
        graphics.drawString(font, text, drawX, drawY, 0xFFFFFF, style.shadow);
        graphics.pose().popPose();
    }
}
