package com.areaclaims.client.gui;

import com.areaclaims.claim.ClaimRole;
import com.areaclaims.claim.RuleType;
import com.areaclaims.client.data.ClientClaimCache;
import com.areaclaims.client.data.ClientNetworkUtil;
import com.areaclaims.client.data.ClientPriceConfirmCache;
import com.areaclaims.data.ActiveSelectionManager;
import com.areaclaims.network.BeginSelectionPacket;
import com.areaclaims.network.BuyoutRulePacket;
import com.areaclaims.network.ClaimEditorSnapshot;
import com.areaclaims.network.ConfirmPendingActionPacket;
import com.areaclaims.network.DeleteClaimPacket;
import com.areaclaims.network.DiscardPendingActionPacket;
import com.areaclaims.network.PriceConfirmSnapshot;
import com.areaclaims.network.RenameClaimPacket;
import com.areaclaims.network.RequestServerConfigPacket;
import com.areaclaims.network.ResumePendingActionPacket;
import com.areaclaims.network.ServerConfigSnapshot;
import com.areaclaims.network.SetBoundaryColorOverridePacket;
import com.areaclaims.network.SetEntryMessagePacket;
import com.areaclaims.network.SetRolePacket;
import com.areaclaims.network.SetRulePacket;
import com.areaclaims.network.SetShowcaseModePacket;
import com.areaclaims.network.ShowClaimPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase-5-GUI-Editor. Daten kommen NICHT live vom Server, sondern aus dem zuletzt empfangenen
 * {@link ClientClaimCache}-Snapshot (siehe ClaimSyncPacket) - jede Mutation schickt ein Paket und
 * wartet auf den frischen Sync-Snapshot, statt lokal zu raten, wie das Ergebnis aussieht.
 *
 * <p><b>3-Spalten-Grundlayout, Spalten 1+2 seit ROADMAP.md Phase 7 ("Klick-zum-Abstecken") um
 * Aktions-Buttons erweitert (unverändert gegenüber der vorherigen Runde):</b> Spalte 1 = Haupt-
 * bereiche + "Neuer Claim"/"Erweitern", Spalte 2 = Unterbereiche + "Neuer SubClaim"/"Erweitern",
 * Spalte 3 = Detail-Panel.
 *
 * <p><b>Detail-Panel-Neugliederung (ROADMAP.md Phase 7-Nachtrag 2, Punkt 2):</b> bei fokussiertem
 * HAUPTbereich: Name+Stift, "SubClaims:"-Überschrift, Namensliste ALLER Unterbereiche (je mit
 * eigenem Stift), Willkommensnachricht-Vorschau+Stift. Bei fokussiertem UNTERbereich: nur eine
 * schreibgeschützte Referenzzeile zum Hauptbereich, dann NUR dieses Unterbereichs eigener Name+
 * Stift und Willkommensnachricht+Stift. Darunter Mitglieder (schreibgeschützte Liste + Stift zum
 * neuen {@link AreaClaimsMemberScreen}) und rechtsbündig die Regeln ("Erlaube", siehe
 * {@link #buildRulePanel}).
 *
 * <p><b>Popups (Punkt 1/4/5) - alle als Overlay INNERHALB dieses Screens, keine separaten Screens</b>
 * (Ausnahme: der Mitglieder-Screen IST bewusst ein echter Screen-Wechsel, siehe Punkt 3-Vorgabe):
 * Name-Bearbeiten ({@link #buildNameEditPopup}, Name+Textfarbe+Dauer+Grenzfarbe, inkl. Regenbogen-
 * Farbwähler {@link HueBarWidget}), Willkommensnachricht-Bearbeiten ({@link #buildWelcomeEditPopup}),
 * Freikauf-Bestätigung ({@link #buildBuyoutConfirmPopup}), Grenzen-Anzeige-Einstellungen
 * ({@link #buildSettingsPopup}). Preisbestätigung ({@link #buildPriceConfirmOverlay}) bleibt aus
 * der vorherigen Runde unverändert. Jeweils nur EIN Popup gleichzeitig - Priorität siehe
 * {@link #buildWidgets()}.
 *
 * <p><b>Klick-zum-Abstecken (ROADMAP.md Phase 7-8):</b> "Neuer Claim"/"Erweitern"/"Neuer SubClaim"
 * senden NUR ein {@link BeginSelectionPacket} - der Server entscheidet, ob eine gemerkte Auswahl
 * direkt zur Preisbestätigung führt oder der Spieler in den Punkte-Platzier-Modus wechselt.
 *
 * <p><b>Rote-X-/Häkchen-Glyphen (ROADMAP.md Phase 7-Nachtrag 2, Punkt 3/4):</b> {@link GlyphButton}
 * - siehe dessen Klassenkommentar für den CobbleCompanion-Referenz-Abgleich und den bewussten
 * Implementierungs-Unterschied (echtes Widget statt manueller Klick-Trefferprüfung).
 *
 * <p><b>Nutzer-Fund (Klick-Text-Verschwinde-Bug, ROADMAP.md Phase 6-Nachtrag "Layout-Fix"):</b> ein
 * Button, dessen Text nicht in seine Breite passt, löst vanillas eingebautes Scrolling-Text-
 * Rendering aus - das ruft {@code enableScissor()} mit Koordinaten auf, die in einem
 * {@link FixedScaleScreen} NICHT korrekt umgerechnet werden. Fix: Buttons werden auf Basis der
 * tatsächlichen Textbreite dimensioniert (siehe {@link #measuredButtonWidth}) - {@link GlyphButton}
 * ist von diesem Bug strukturell ausgenommen (kein Scrolling-Mechanismus vorhanden).
 */
public class AreaClaimsEditorScreen extends FixedScaleScreen {

    // ---------------------------------------------------------------- Justierschrauben: Titel
    private static final int TITLE_Y = 8;

    // ---------------------------------------------------------------- Justierschrauben: Grundraster
    private static final int MARGIN = 10;
    private static final int COLUMN_GAP = 10;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xA0A0A0;
    private static final boolean HEADER_BOLD = true;
    private static final int BUTTON_TEXT_PADDING = 12;
    private static final String PENCIL_GLYPH = "✎"; // "✎" - siehe Klassenkommentar
    private static final int PENCIL_COLOR = 0xFFAAAAAA;
    private static final int PENCIL_SIZE = 12;
    private static final int PENCIL_GAP = 4;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;

    // ---------------------------------------------------------------- Justierschrauben: Spalte 1 (Hauptbereiche)
    private static final int COL1_X = MARGIN;
    private static final int NEW_CLAIM_BUTTON_Y = 26;
    private static final int NEW_CLAIM_BUTTON_HEIGHT = 16;
    private static final int COL1_Y = NEW_CLAIM_BUTTON_Y + NEW_CLAIM_BUTTON_HEIGHT + 8;
    private static final int COL1_WIDTH = 112;
    private static final int MAIN_ROW_BUTTON_HEIGHT = 16;
    // Nachtrag 7 (Folgepunkt: "Blockzahl ragt in die SubClaim-Spalte hinein"): war 10 (EINE Zeile,
    // "Besitzer • X Blöcke²" kombiniert) - diese kombinierte Zeile wurde bei längeren Besitzernamen/
    // größeren Flächen breiter als COL1_WIDTH und lief sichtbar in COL2s eigene Blockzahl-Zeile
    // hinein. Jetzt ZWEI Zeilen (Besitzername oben, Blockzahl darunter, siehe
    // #renderMainColumnInfo) - jede Zeile für sich bleibt deutlich schmaler als vorher die
    // kombinierte Zeile, dadurch kein Hineinragen mehr in COL2.
    private static final int MAIN_ROW_INFO_HEIGHT = 20;
    private static final int MAIN_ROW_EXPAND_HEIGHT = 16;
    private static final int MAIN_ROW_GAP = 6;
    private static final int MAIN_ROW_ADVANCE = MAIN_ROW_BUTTON_HEIGHT + MAIN_ROW_INFO_HEIGHT + MAIN_ROW_EXPAND_HEIGHT + MAIN_ROW_GAP;
    /** Breiter als eine bloße 18px-Glyphenbreite (Punkt 4: Löschen als roter Glyph statt Button, siehe {@link #handleDeleteClick}) - muss den "Sicher?"-Bestätigungstext ohne Überlauf fassen. */
    private static final int MAIN_DELETE_BUTTON_WIDTH = 40;
    private static final int ROW_BUTTON_GAP = 4;
    private static final int MAIN_NAME_BUTTON_WIDTH = COL1_WIDTH - MAIN_DELETE_BUTTON_WIDTH - ROW_BUTTON_GAP;
    /** Wie CreativeMenus resetButton-Muster: zweiter Klick innerhalb dieses Fensters bestätigt. */
    private static final long DELETE_CONFIRM_WINDOW_MS = 4000;

    // ---------------------------------------------------------------- Justierschrauben: Spalte 2 (Unterbereiche)
    private static final int COL2_X = COL1_X + COL1_WIDTH + COLUMN_GAP;
    private static final int COL2_WIDTH = 100;
    private static final int SUB_ROW_BUTTON_HEIGHT = 16;
    private static final int SUB_ROW_INFO_HEIGHT = 10;
    private static final int SUB_ROW_EXPAND_HEIGHT = 16;
    private static final int SUB_ROW_GAP = 6;
    private static final int SUB_ROW_ADVANCE = SUB_ROW_BUTTON_HEIGHT + SUB_ROW_INFO_HEIGHT + SUB_ROW_EXPAND_HEIGHT + SUB_ROW_GAP;
    private static final int SUB_DELETE_BUTTON_WIDTH = 40;
    private static final int SUB_NAME_BUTTON_WIDTH = COL2_WIDTH - SUB_DELETE_BUTTON_WIDTH - ROW_BUTTON_GAP;
    private static final int NEW_SUBCLAIM_BUTTON_HEIGHT = 16;

    // ---------------------------------------------------------------- Justierschrauben: Spalte 3 (Detail-Panel)
    private static final int COL3_SEPARATOR_GAP = 18;
    private static final int COL3_X = COL2_X + COL2_WIDTH + COLUMN_GAP + COL3_SEPARATOR_GAP;
    private static final int SEPARATOR_X_OFFSET = COL3_SEPARATOR_GAP / 2;
    private static final int SEPARATOR_COLOR = 0x40FFFFFF;
    private static final int DETAIL_HEADING_Y = 26;
    private static final int DETAIL_ROW_HEIGHT = 12;
    private static final int DETAIL_ROW_HEIGHT_LARGE = 14;
    private static final int MAX_SUBCLAIM_NAME_ROWS = 6;
    private static final int WELCOME_PREVIEW_MAX_CHARS = 24;

    private int detailBottomY;

    // -- Mitglieder-Unterspalte (linke Hälfte von Spalte 3) --
    private static final int MEMBERS_PANEL_X = COL3_X;
    private static final int MEMBERS_PANEL_WIDTH = 150;
    private static final int MEMBER_LINE_HEIGHT = 11;
    private static final int MAX_MEMBER_ROWS_SHOWN = 6;
    /** Punkt 7 (Nachtrag 3): "Noch keine Mitglieder"-Platzhalter saß zu tief, etwas höher rücken. */
    private static final int NO_MEMBERS_TEXT_Y_NUDGE = -3;

    // -- Regel-Unterspalte (rechts, rechtsbündig - Punkt 4/Rules-Neupositionierung) --
    private static final int RULE_CHECK_WIDTH = 14;
    private static final int RULE_NAME_WIDTH = 150;
    private static final int RULE_BUTTON_GAP = 4;
    private static final int RULE_ROW_HEIGHT = 20;

    // ---------------------------------------------------------------- Justierschrauben: unterer Rand (Punkt 5: Grenzen-Steuerung nach unten)
    private static final int BOTTOM_MARGIN = 10;
    private static final int CLOSE_BUTTON_WIDTH = 80;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final int BOUNDARY_ROW_HEIGHT = 20;
    private static final int SHOW_BOUNDARY_BUTTON_WIDTH = 130;
    /** Grenzen-anzeigen/Einstellungen-Zeile sitzt direkt ÜBER der Schließen-Zeile, nahe der Trennlinie (Nutzer-Vorgabe) - siehe {@link #boundaryRowY()}, dynamisch aus {@code this.height} berechnet. */
    private static final int BOUNDARY_ROW_GAP = 6;

    // ---------------------------------------------------------------- Justierschrauben: generisches Overlay-Popup (Preisbestätigung, Name/Willkommen-Bearbeiten, Einstellungen, Freikauf)
    private static final int OVERLAY_BG_COLOR = 0xE0101010;
    private static final int OVERLAY_BUTTON_HEIGHT = 20;
    private static final int OVERLAY_BUTTON_GAP = 6;
    private static final int OVERLAY_ROW_HEIGHT = 20;
    private static final int OVERLAY_FIELD_WIDTH = 110;
    private static final int OVERLAY_HUE_WIDTH = 90;
    private static final int OVERLAY_HUE_GAP = 4;

    private String selectedMainId;
    private String selectedSubId;
    private int lastSeenCacheGeneration = -1;
    private int lastSeenPriceConfirmGeneration = -1;

    private String deleteArmedClaimId;
    private long deleteArmedUntil = 0;

    private boolean focusedIsMain = true;

    // -- Name-Bearbeiten-Popup (Punkt 1) --
    private String nameEditClaimId;
    private EditBox nameEditNameField;
    private EditBox nameEditColorField;
    private EditBox nameEditBoundaryColorField;
    private boolean nameEditLinkBoundary;

    // -- Willkommensnachricht-Bearbeiten-Popup (Punkt 1, Variante) --
    private String welcomeEditClaimId;
    private EditBox welcomeEditTextField;
    private EditBox welcomeEditColorField;

    // -- Einstellungen-Popup (Punkt 5) --
    private boolean settingsPopupOpen;
    private boolean settingsUseClaimColor;
    private EditBox settingsColorField;

    // -- Freikauf-Bestätigung-Popup (Punkt 4) --
    private String buyoutConfirmClaimId;
    private String buyoutConfirmRule;

    /** Vorbelegung für {@link #selectedMainId}/{@link #selectedSubId}, siehe {@link #AreaClaimsEditorScreen(String, String)}. */
    private final String preselectMainId;
    private final String preselectSubId;

    public AreaClaimsEditorScreen() {
        this(null, null);
    }

    /**
     * Punkt 5 (Nachtrag 3): {@link AreaClaimsMemberScreen#returnToEditor} baute den Editor bisher
     * über den parameterlosen Konstruktor NEU auf, wodurch die zuvor gewählte Spalte-1/2-Auswahl
     * verloren ging - der Nutzer landete beim Verlassen des Mitglieder-Screens u.U. wieder auf dem
     * ERSTEN Hauptbereich statt auf dem gerade bearbeiteten Claim, was fälschlich wie ein Kaskadierungs-
     * Bug aussah (Mitglied schien im Hauptbereich zu fehlen, weil der Hauptbereich-Screen den
     * FALSCHEN Claim anzeigte). Dieser Konstruktor erlaubt es, die Auswahl gezielt vorzubelegen;
     * {@link #ensureSelection()} validiert sie trotzdem noch (falls der Claim inzwischen gelöscht wurde).
     */
    public AreaClaimsEditorScreen(String preselectMainId, String preselectSubId) {
        super(Component.translatable("areaclaims.editor.title"));
        this.preselectMainId = preselectMainId;
        this.preselectSubId = preselectSubId;
    }

    @Override
    protected void initScaled() {
        lastSeenCacheGeneration = ClientClaimCache.generation();
        lastSeenPriceConfirmGeneration = ClientPriceConfirmCache.generation();
        if (preselectMainId != null) {
            selectedMainId = preselectMainId;
            selectedSubId = preselectSubId;
        }
        ensureSelection();
        buildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        boolean needsRebuild = false;

        if (ClientClaimCache.generation() != lastSeenCacheGeneration) {
            lastSeenCacheGeneration = ClientClaimCache.generation();
            ensureSelection();
            needsRebuild = true;
        }
        if (ClientPriceConfirmCache.generation() != lastSeenPriceConfirmGeneration) {
            lastSeenPriceConfirmGeneration = ClientPriceConfirmCache.generation();
            needsRebuild = true;
        }
        if (deleteArmedClaimId != null && System.currentTimeMillis() >= deleteArmedUntil) {
            deleteArmedClaimId = null;
            needsRebuild = true;
        }

        if (needsRebuild) {
            clearWidgets();
            buildWidgets();
        }
    }

    // ---------------------------------------------------------------- Auswahl-Zustand

    private List<ClaimEditorSnapshot.ClaimEntry> mainClaims() {
        List<ClaimEditorSnapshot.ClaimEntry> result = new ArrayList<>();
        for (ClaimEditorSnapshot.ClaimEntry c : ClientClaimCache.get().claims) {
            if (c.main) result.add(c);
        }
        return result;
    }

    private List<ClaimEditorSnapshot.ClaimEntry> subClaimsOf(String mainId) {
        List<ClaimEditorSnapshot.ClaimEntry> result = new ArrayList<>();
        for (ClaimEditorSnapshot.ClaimEntry c : ClientClaimCache.get().claims) {
            if (!c.main && mainId.equals(c.parentId)) result.add(c);
        }
        return result;
    }

    private void ensureSelection() {
        List<ClaimEditorSnapshot.ClaimEntry> mains = mainClaims();
        if (mains.isEmpty()) {
            selectedMainId = null;
            selectedSubId = null;
            return;
        }
        boolean mainStillExists = mains.stream().anyMatch(c -> c.id.equals(selectedMainId));
        if (!mainStillExists) {
            selectedMainId = mains.get(0).id;
            selectedSubId = null;
        }
        if (selectedSubId != null) {
            boolean subStillValid = subClaimsOf(selectedMainId).stream().anyMatch(c -> c.id.equals(selectedSubId));
            if (!subStillValid) selectedSubId = null;
        }
    }

    private ClaimEditorSnapshot.ClaimEntry focusedClaim() {
        String focusedId = selectedSubId != null ? selectedSubId : selectedMainId;
        return byId(focusedId);
    }

    private ClaimEditorSnapshot.ClaimEntry byId(String id) {
        if (id == null) return null;
        return ClientClaimCache.get().claims.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- Textbreiten-Hilfe (siehe Klassenkommentar: Scrolling-Text-Bug)

    private int roleButtonWidth() {
        int max = 0;
        for (ClaimRole role : ClaimRole.values()) {
            max = Math.max(max, this.font.width(role.translatable()));
        }
        return max + BUTTON_TEXT_PADDING;
    }

    private int measuredButtonWidth(String... translationKeys) {
        int max = 0;
        for (String key : translationKeys) {
            max = Math.max(max, this.font.width(Component.translatable(key)));
        }
        return max + BUTTON_TEXT_PADDING;
    }

    private int modeButtonWidth() {
        return measuredButtonWidth("areaclaims.editor.mode.particles", "areaclaims.editor.mode.tint");
    }

    private int kaufenButtonWidth() {
        return measuredButtonWidth("areaclaims.editor.buyout_button");
    }

    /**
     * Nachtrag 7, Punkt 6 ("SubClaim anlegen"-Button in Englisch komplett leer): Ursachen-Recherche
     * ergab - ANDERS als vom Koordinator vermutet - dass der Lang-Key {@code areaclaims.editor.
     * create_subclaim} in BEIDEN Sprachdateien vorhanden und nicht leer ist (per Skript verglichen,
     * kein fehlender/vertauschter Schlüssel). Tatsächliche Ursache: derselbe Scrolling-Text-Bug wie
     * Punkt 5 - der Button war fest auf {@link #COL2_WIDTH} (100px) verdrahtet, worauf die deutsche
     * Beschriftung "SubClaim anlegen" gerade noch passt, die längere/großbuchstabenreichere
     * englische "Create as Sub-Claim" aber nicht mehr. Fix nach demselben, bereits etablierten
     * Muster wie {@link #kaufenButtonWidth()} - NIE schmaler als die bisherige Spaltenbreite
     * (visuell unverändert für Deutsch), aber breit genug für jede tatsächlich gerenderte Sprache.
     */
    private int createSubclaimButtonWidth() {
        return Math.max(COL2_WIDTH, measuredButtonWidth("areaclaims.editor.create_subclaim"));
    }

    /**
     * Punkt 9 (Nachtrag 3, Screenshot-bestätigter Bug): war vorher ein fester Wert (90px), der bei
     * längeren Labels wie "Titel-Dauer (Ticks)" nicht ausreichte - der Text lief dadurch rechts in
     * das Eingabefeld hinein. Jetzt wie überall sonst in dieser Klasse über {@link #measuredButtonWidth}
     * anhand der tatsächlich gerenderten Breite ALLER Overlay-Labels berechnet (name/welcome-Popups
     * teilen sich dieselbe Spalte, siehe {@link #drawOverlayLabel}-Aufrufstellen).
     */
    private int overlayLabelWidth() {
        return measuredButtonWidth(
            "areaclaims.nameedit.name",
            "areaclaims.entrymsg.title_color",
            "areaclaims.entrymsg.boundary_color",
            "areaclaims.entrymsg.welcome_text",
            "areaclaims.entrymsg.welcome_color");
    }

    /**
     * Linke Kante des dunklen Hintergrund-Kastens für Name-/Willkommen-Popups - bewusst NICHT
     * mehr identisch mit der Feld-X-Position (siehe {@link #overlayPopupFieldX}); vorher wurde
     * derselbe Wert für beides verwendet, wodurch der Kasten die Labels links gar nicht abdeckte
     * (Punkt 9, Nachtrag 3). Bleibt exakt auf der Bildschirmmitte zentriert, da {@link #overlayWidth()}
     * die Label-Breite bereits einrechnet.
     */
    private int overlayPopupBoxLeft() {
        return (this.width - overlayWidth()) / 2;
    }

    /**
     * X-Position von Eingabefeld/Hue-Bar in Name-/Willkommen-Popups - rückt gegenüber dem Kasten-
     * Rand um Labelbreite + 10px Rand nach rechts ein, damit die (jetzt dynamisch breite) Label-
     * Spalte komplett INNERHALB des dunklen Kastens Platz hat statt links davon überzustehen.
     * Build- UND Render-Methoden MÜSSEN diese eine Stelle nutzen, sonst laufen Widget- und Text-
     * Positionen wieder auseinander (bekanntes Muster in dieser Klasse, siehe Klassenkommentar).
     */
    private int overlayPopupFieldX() {
        return overlayPopupBoxLeft() + overlayLabelWidth() + 10;
    }

    private int boundaryRowY() {
        return this.height - BOTTOM_MARGIN - CLOSE_BUTTON_HEIGHT - BOUNDARY_ROW_GAP - BOUNDARY_ROW_HEIGHT;
    }

    // ---------------------------------------------------------------- Aufbau

    private void buildWidgets() {
        // Popups haben strikte Priorität - jeweils nur EINS gleichzeitig, der Rest der
        // Oberfläche wird währenddessen nicht aufgebaut (verhindert widersprüchliche Klicks).
        PriceConfirmSnapshot confirm = ClientPriceConfirmCache.get();
        if (confirm != null) {
            buildPriceConfirmOverlay(confirm);
            return;
        }
        if (nameEditClaimId != null) {
            buildNameEditPopup();
            return;
        }
        if (welcomeEditClaimId != null) {
            buildWelcomeEditPopup();
            return;
        }
        if (settingsPopupOpen) {
            buildSettingsPopup();
            return;
        }
        if (buyoutConfirmClaimId != null) {
            buildBuyoutConfirmPopup();
            return;
        }

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.new_claim"), b -> sendBeginSelection(ActiveSelectionManager.ActionType.NEW_CLAIM, null))
            .bounds(COL1_X, NEW_CLAIM_BUTTON_Y, COL1_WIDTH, NEW_CLAIM_BUTTON_HEIGHT)
            .build());

        buildMainColumn();
        buildSubColumn();
        ClaimEditorSnapshot.ClaimEntry focused = focusedClaim();
        if (focused != null) {
            focusedIsMain = focused.main;
            buildDetailPanel(focused);
            buildRulePanel(focused);
            buildBoundaryRow(focused);
        }
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.close"), b -> onClose())
            .bounds(this.width - CLOSE_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());

        boolean isAdmin = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(4);
        if (isAdmin) {
            addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.server_config"), b -> sendRequestServerConfig())
                .bounds(MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN, 150, CLOSE_BUTTON_HEIGHT)
                .build());
            // Punkt 8 (Nachtrag 5): Admin-Übersicht "welche Claims nutzen welches Bild" - gestapelt
            // über dem Server-Konfiguration-Button, gleiches OP4-only-Prinzip.
            addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.image_usage"), b -> sendRequestImageUsage())
                .bounds(MARGIN, this.height - CLOSE_BUTTON_HEIGHT * 2 - BOTTOM_MARGIN - 4, 150, CLOSE_BUTTON_HEIGHT)
                .build());
        }
        // Punkt 13-15 (Nachtrag 3): JEDER Spieler (nicht nur Admins) darf seine eigenen, rein
        // persönlichen Anzeige-Einstellungen öffnen - steht deshalb ohne Berechtigungsprüfung immer
        // an derselben Stelle, eine Zeile höher gestapelt pro zusätzlichem Admin-Button daneben
        // (sonst würden sich die Buttons überlappen).
        int displayPrefsY = this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN - (isAdmin ? (CLOSE_BUTTON_HEIGHT + 4) * 2 : 0);
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.display_prefs"), b -> sendRequestDisplayPrefs())
            .bounds(MARGIN, displayPrefsY, 150, CLOSE_BUTTON_HEIGHT)
            .build());
    }

    private void sendRequestServerConfig() {
        // Punkt 3 (Nachtrag 6, "Zurück"-Button): merkt sich DIESEN Editor als Rückkehrziel, BEVOR
        // die Anfrage rausgeht - siehe ClientServerConfigCache-Klassenkommentar.
        com.areaclaims.client.data.ClientServerConfigCache.openFrom(this);
        RequestServerConfigPacket packet = new RequestServerConfigPacket();
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendRequestImageUsage() {
        com.areaclaims.client.data.ClientImageUsageCache.openFrom(this);
        com.areaclaims.network.ImageUsageRequestPacket packet = new com.areaclaims.network.ImageUsageRequestPacket();
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendRequestDisplayPrefs() {
        com.areaclaims.client.data.ClientDisplayPrefsCache.openFrom(this);
        com.areaclaims.network.RequestDisplayPrefsPacket packet = new com.areaclaims.network.RequestDisplayPrefsPacket();
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    // ---------------------------------------------------------------- Spalte 1: Hauptbereiche

    private void buildMainColumn() {
        int expandWidth = measuredButtonWidth("areaclaims.editor.expand");
        int adjustWidth = measuredButtonWidth("areaclaims.editor.adjust");
        int y = COL1_Y;
        for (ClaimEditorSnapshot.ClaimEntry claim : mainClaims()) {
            boolean selected = claim.id.equals(selectedMainId);
            String prefix = selected ? "> " : "";
            int rowY = y;
            addRenderableWidget(new GlyphButton(COL1_X, rowY, MAIN_NAME_BUTTON_WIDTH, MAIN_ROW_BUTTON_HEIGHT,
                Component.literal(prefix + claim.name), claim.titleColor, () -> {
                    selectedMainId = claim.id;
                    selectedSubId = null;
                    deleteArmedClaimId = null;
                    clearWidgets();
                    buildWidgets();
                }));

            addRenderableWidget(deleteGlyphButton(claim.id, COL1_X + MAIN_NAME_BUTTON_WIDTH + ROW_BUTTON_GAP, rowY, MAIN_DELETE_BUTTON_WIDTH, MAIN_ROW_BUTTON_HEIGHT));

            // Nur für den FOKUSSIERTEN Claim anzeigen (ROADMAP.md Phase 7-Nachtrag 3, Punkt 1
            // "Entrümpeln" - vorher zeigte JEDE Zeile ihren eigenen Erweitern-Button gleichzeitig).
            if (selected) {
                int expandY = rowY + MAIN_ROW_BUTTON_HEIGHT + MAIN_ROW_INFO_HEIGHT;
                addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.expand"),
                        b -> sendBeginSelection(ActiveSelectionManager.ActionType.EXPAND_MAIN, claim.id))
                    .bounds(COL1_X, expandY, expandWidth, MAIN_ROW_EXPAND_HEIGHT)
                    .build());
                // Nutzer-Vorgabe (2026-08-18): "Anpassen" NEBEN "Erweitern" statt eines Gedrückt-
                // Halten-Ziehens an der Grenze - eigener Modus, siehe ActiveSelectionManager.ActionType#ADJUST_MAIN.
                // Nutzer-Vorgabe (2026-08-19): bündig an "Erweitern" statt mit dem üblichen
                // ROW_BUTTON_GAP - dichter beieinander, da beide zur selben Aktion gehören.
                addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.adjust"),
                        b -> sendBeginSelection(ActiveSelectionManager.ActionType.ADJUST_MAIN, claim.id))
                    .bounds(COL1_X + expandWidth, expandY, adjustWidth, MAIN_ROW_EXPAND_HEIGHT)
                    .build());
            }

            y += MAIN_ROW_ADVANCE;
        }
    }

    /** Index des aktuell ausgewählten Hauptbereichs in der Liste, oder 0 (ROADMAP.md Phase 7-Nachtrag 3, Punkt 2 "Spalte-2-Ausrichtung"). */
    private int selectedMainIndex() {
        List<ClaimEditorSnapshot.ClaimEntry> mains = mainClaims();
        for (int i = 0; i < mains.size(); i++) {
            if (mains.get(i).id.equals(selectedMainId)) return i;
        }
        return 0;
    }

    /**
     * Startet die Unterbereichs-Liste auf DERSELBEN Zeilenhöhe wie der ausgewählte Hauptbereich in
     * Spalte 1 (Nutzer-Fund: vorher begann Spalte 2 immer ganz oben, unabhängig davon, wo in Spalte
     * 1 der fokussierte Eintrag tatsächlich stand).
     */
    private int col2Y() {
        return COL1_Y + selectedMainIndex() * MAIN_ROW_ADVANCE;
    }

    /**
     * Roter Löschen-Glyph statt Vanilla-Button (ROADMAP.md Phase 7-Nachtrag 2, Punkt 4 "same
     * red-X style" - galt bisher NUR für den neu ergänzten Unterbereich-Löschen-Button, jetzt auch
     * rückwirkend auf den Hauptbereich-Löschen-Button angewandt, wie vom Nutzer angefragt
     * nachgeprüft). Klick-zum-Bestätigen-Zustand (siehe {@link #deleteArmedClaimId}) zeigt
     * stattdessen den übersetzten "Sicher?"-Text in Gelb statt des roten "✗".
     */
    private GlyphButton deleteGlyphButton(String claimId, int x, int y, int width, int height) {
        boolean armed = claimId.equals(deleteArmedClaimId) && System.currentTimeMillis() < deleteArmedUntil;
        Component label = armed ? Component.translatable("areaclaims.editor.delete_confirm") : Component.literal("✗");
        int color = armed ? 0xFFFFAA00 : RED;
        return new GlyphButton(x, y, width, height, label, color, () -> handleDeleteClick(claimId));
    }

    private void handleDeleteClick(String claimId) {
        long now = System.currentTimeMillis();
        if (claimId.equals(deleteArmedClaimId) && now < deleteArmedUntil) {
            deleteArmedClaimId = null;
            sendDeleteClaim(claimId);
        } else {
            deleteArmedClaimId = claimId;
            deleteArmedUntil = now + DELETE_CONFIRM_WINDOW_MS;
        }
        clearWidgets();
        buildWidgets();
    }

    // ---------------------------------------------------------------- Spalte 2: Unterbereiche

    private void buildSubColumn() {
        if (selectedMainId == null) return;
        int expandWidth = measuredButtonWidth("areaclaims.editor.expand");
        int adjustWidth = measuredButtonWidth("areaclaims.editor.adjust");
        List<ClaimEditorSnapshot.ClaimEntry> subs = subClaimsOf(selectedMainId);
        int y = col2Y();
        for (ClaimEditorSnapshot.ClaimEntry sub : subs) {
            boolean selected = sub.id.equals(selectedSubId);
            String prefix = selected ? "> " : "";
            addRenderableWidget(new GlyphButton(COL2_X, y, SUB_NAME_BUTTON_WIDTH, SUB_ROW_BUTTON_HEIGHT,
                Component.literal(prefix + sub.name), sub.titleColor, () -> {
                    selectedSubId = sub.id;
                    deleteArmedClaimId = null;
                    clearWidgets();
                    buildWidgets();
                }));

            addRenderableWidget(deleteGlyphButton(sub.id, COL2_X + SUB_NAME_BUTTON_WIDTH + ROW_BUTTON_GAP, y, SUB_DELETE_BUTTON_WIDTH, SUB_ROW_BUTTON_HEIGHT));

            // Nur für den fokussierten Unterbereich (ROADMAP.md Phase 7-Nachtrag 3, Punkt 1 - siehe buildMainColumn).
            if (selected) {
                int expandY = y + SUB_ROW_BUTTON_HEIGHT + SUB_ROW_INFO_HEIGHT;
                addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.expand"),
                        b -> sendBeginSelection(ActiveSelectionManager.ActionType.EXPAND_SUB, sub.id))
                    .bounds(COL2_X, expandY, expandWidth, SUB_ROW_EXPAND_HEIGHT)
                    .build());
                addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.adjust"),
                        b -> sendBeginSelection(ActiveSelectionManager.ActionType.ADJUST_SUB, sub.id))
                    .bounds(COL2_X + expandWidth, expandY, adjustWidth, SUB_ROW_EXPAND_HEIGHT)
                    .build());
            }

            y += SUB_ROW_ADVANCE;
        }

        // Punkt 6: bei leerer Liste stand der Button vorher auf derselben Y-Höhe wie der
        // "Keine Unterbereiche"-Platzhaltertext (siehe renderSubColumnInfo) und überlappte ihn.
        // Fix: Button auf Höhe der "Erweitern"-Zeile des Hauptbereichs ausrichten, statt eine
        // eigene Reservierung zu erfinden - liest sich sauberer als den Platzhaltertext zu verschieben.
        int newSubButtonY = subs.isEmpty() ? y + SUB_ROW_BUTTON_HEIGHT + SUB_ROW_INFO_HEIGHT : y;
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.create_subclaim"),
                b -> sendBeginSelection(ActiveSelectionManager.ActionType.NEW_SUBCLAIM, selectedMainId))
            .bounds(COL2_X, newSubButtonY, createSubclaimButtonWidth(), NEW_SUBCLAIM_BUTTON_HEIGHT)
            .build());
    }

    // ---------------------------------------------------------------- Spalte 3: Detail-Panel (Punkt 2: Neugliederung)

    /**
     * Baut die textlichen/Stift-Zeilen des Detail-Panels UND merkt sich deren gezeichnete Y-
     * Positionen für {@link #renderDetailPanelText} (Widgets und reiner Text werden getrennt
     * gebaut/gezeichnet, wie im ganzen Screen üblich - siehe Klassenkommentar zu Vanilla-Buttons).
     * Anschließend Mitglieder-Bereich (schreibgeschützt + Stift zum Mitglieder-Screen).
     */
    private void buildDetailPanel(ClaimEditorSnapshot.ClaimEntry claim) {
        int y = DETAIL_HEADING_Y;

        if (claim.main) {
            addRenderableWidget(pencilButton(COL3_X + nameTextWidth(claim.name), y, () -> openNameEdit(claim.id)));
            y += DETAIL_ROW_HEIGHT_LARGE;

            y += DETAIL_ROW_HEIGHT; // "SubClaims:"-Überschrift (reiner Text, siehe renderDetailPanelText)

            int shown = 0;
            for (ClaimEditorSnapshot.ClaimEntry sub : subClaimsOf(claim.id)) {
                if (shown >= MAX_SUBCLAIM_NAME_ROWS) break;
                addRenderableWidget(pencilButton(COL3_X + nameTextWidth(sub.name), y, () -> openNameEdit(sub.id)));
                y += DETAIL_ROW_HEIGHT;
                shown++;
            }
            // Punkt 8: bei leerer Unterbereichsliste muss trotzdem eine Zeile reserviert werden (der
            // "Keine Unterbereiche"-Platzhaltertext braucht denselben Platz wie eine echte Namenszeile),
            // sonst rückt der Willkommensnachricht-Stift-Button zu weit nach oben - siehe
            // renderDetailPanelText/memberListStartY, die diese Reservierung bereits korrekt vornehmen.
            if (shown == 0) {
                y += DETAIL_ROW_HEIGHT;
            }

            addRenderableWidget(pencilButton(COL3_X + welcomePreviewWidth(claim), y, () -> openWelcomeEdit(claim.id)));
            y += DETAIL_ROW_HEIGHT_LARGE;
        } else {
            y += DETAIL_ROW_HEIGHT_LARGE; // "Hauptbereich: ..."-Referenzzeile, schreibgeschützt, KEIN Stift

            addRenderableWidget(pencilButton(COL3_X + nameTextWidth(claim.name), y, () -> openNameEdit(claim.id)));
            y += DETAIL_ROW_HEIGHT_LARGE;

            addRenderableWidget(pencilButton(COL3_X + welcomePreviewWidth(claim), y, () -> openWelcomeEdit(claim.id)));
            y += DETAIL_ROW_HEIGHT_LARGE;
        }

        y += 6;
        addRenderableWidget(pencilButton(MEMBERS_PANEL_X + this.font.width(Component.translatable("areaclaims.editor.members_header")), y,
            () -> openMemberScreen(claim.id)));
        y += DETAIL_ROW_HEIGHT + 2;

        int shown = 0;
        for (ClaimEditorSnapshot.MemberEntry ignored : claim.members) {
            if (shown >= MAX_MEMBER_ROWS_SHOWN) break;
            y += MEMBER_LINE_HEIGHT;
            shown++;
        }
        if (claim.members.isEmpty()) y += MEMBER_LINE_HEIGHT;

        detailBottomY = y;
    }

    private GlyphButton pencilButton(int x, int y, Runnable onPress) {
        return new GlyphButton(x + PENCIL_GAP, y, PENCIL_SIZE, PENCIL_SIZE, Component.literal(PENCIL_GLYPH), PENCIL_COLOR, onPress);
    }

    private int nameTextWidth(String name) {
        return this.font.width(Component.literal(name));
    }

    private int welcomePreviewWidth(ClaimEditorSnapshot.ClaimEntry claim) {
        return this.font.width(welcomePreviewLabel(claim));
    }

    private Component welcomePreviewLabel(ClaimEditorSnapshot.ClaimEntry claim) {
        String text = claim.welcomeMessage == null || claim.welcomeMessage.isBlank()
            ? "-" : truncate(claim.welcomeMessage, WELCOME_PREVIEW_MAX_CHARS);
        return Component.translatable("areaclaims.editor.welcome_prefix", text);
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }

    private Component currentModeLabel() {
        boolean tint = "TINT".equals(ClientClaimCache.get().showcaseMode);
        return Component.translatable(tint ? "areaclaims.editor.mode.tint" : "areaclaims.editor.mode.particles");
    }

    // ---------------------------------------------------------------- Grenzen-Anzeige-Steuerung (Punkt 5: an den unteren Rand verschoben)

    private void buildBoundaryRow(ClaimEditorSnapshot.ClaimEntry claim) {
        int y = boundaryRowY();
        int settingsWidth = measuredButtonWidth("areaclaims.editor.settings");
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.show_boundary"),
                b -> sendShowClaim(claim.id))
            .bounds(COL3_X, y, SHOW_BOUNDARY_BUTTON_WIDTH, BOUNDARY_ROW_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.settings"), b -> openSettingsPopup())
            .bounds(COL3_X + SHOW_BOUNDARY_BUTTON_WIDTH + DETAIL_ROW_HEIGHT, y, settingsWidth, BOUNDARY_ROW_HEIGHT)
            .build());
    }

    // ---------------------------------------------------------------- Spalte 3: Mitglieder-Screen-Navigation

    private void openMemberScreen(String claimId) {
        Minecraft.getInstance().setScreen(new AreaClaimsMemberScreen(claimId));
    }

    // ---------------------------------------------------------------- Regeln (rechtsbündig, "Erlaube" - Punkt 4)

    private int rulesPanelWidth() {
        int roleWidth = Math.max(roleButtonWidth(), kaufenButtonWidth());
        return RULE_CHECK_WIDTH + RULE_BUTTON_GAP + RULE_NAME_WIDTH + RULE_BUTTON_GAP + roleWidth;
    }

    private int rulesPanelX() {
        return this.width - rulesPanelWidth() - MARGIN;
    }

    private void buildRulePanel(ClaimEditorSnapshot.ClaimEntry claim) {
        int roleWidth = Math.max(roleButtonWidth(), kaufenButtonWidth());
        int panelX = rulesPanelX();
        int roleColumnX = panelX + RULE_CHECK_WIDTH + RULE_BUTTON_GAP + RULE_NAME_WIDTH + RULE_BUTTON_GAP;
        int y = DETAIL_HEADING_Y + 24; // unter "Erlaube"-Überschrift + "Ignoriert ab"-Spaltenkopf

        for (ClaimEditorSnapshot.RuleEntry rule : claim.rules) {
            int rowY = y;
            boolean enabled = rule.enabled;
            boolean allowed = !enabled;
            ClaimRole minRole = parseRoleOrNone(rule.minRole);

            // Grünes Häkchen ("erlaubt", Regel deaktiviert) / rotes Kreuz ("nicht erlaubt", Regel
            // aktiv) statt des alten "Aktiv"/"Deaktiviert"-Textbuttons - EXAKT derselbe visuelle
            // Bezug wie CompanionScreens Freundschaftsanfragen-Zeile (✓ grün 0xFF55FF55 / ✗ rot).
            //
            // Nachtrag 7, Punkt 4 (Exploit-Fund): solange rule.buyoutPrice != null ist (Regel hat
            // einen konfigurierten, noch NICHT bezahlten Freikauf-Preis - siehe ClaimSnapshotBuilder,
            // dasselbe Feld, das rechts den Rollen-Button durch "Kaufen" ersetzt), MUSS dieser
            // Umschalter gesperrt sein - vorher blieb er trotz "Kaufen"-Button rechts uneingeschränkt
            // klickbar. Rein clientseitiges Sperren allein wäre kein echter Schutz (ein direkt
            // gesendetes SetRulePacket hätte weiterhin funktioniert) - die eigentliche Durchsetzung
            // liegt in ClaimEditService#applyRule (RULE_NOT_BOUGHT_OUT), hier nur zusätzlich für
            // sofortiges visuelles Feedback/bessere UX.
            boolean locked = rule.buyoutPrice != null;
            GlyphButton ruleToggle = new GlyphButton(panelX, rowY, RULE_CHECK_WIDTH, RULE_ROW_HEIGHT - 2,
                Component.literal(allowed ? "✓" : "✗"), allowed ? GREEN : RED,
                () -> sendSetRule(claim.id, rule.rule, !enabled, minRole));
            ruleToggle.active = !locked;
            addRenderableWidget(ruleToggle);

            if (rule.buyoutPrice != null) {
                addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.buyout_button"),
                        b -> openBuyoutConfirm(claim.id, rule.rule))
                    .bounds(roleColumnX, rowY, roleWidth, RULE_ROW_HEIGHT - 2)
                    .build());
            } else if (!"MOB_SPAWNING".equals(rule.rule)) {
                // Nutzer-Vorgabe (2026-08-18): MOB_SPAWNING hat keinen handelnden Spieler (siehe
                // ClaimProtectionManager#isRuleActive/-checkClaimRule - der rollenbasierte Ignorieren-
                // Mechanismus wird für diese Regel nie konsultiert, feindliche Mobs stehen nie in der
                // Mitgliederliste), der Rollen-Button hätte also ohnehin nie eine Wirkung.
                addRenderableWidget(Button.builder(minRole.translatable(), b -> {
                        ClaimRole next = cycleRole(minRole);
                        sendSetRule(claim.id, rule.rule, enabled, next);
                    })
                    .bounds(roleColumnX, rowY, roleWidth, RULE_ROW_HEIGHT - 2)
                    .build());
            }

            y += RULE_ROW_HEIGHT;
        }
    }

    private ClaimRole cycleRole(ClaimRole current) {
        return switch (current) {
            case NONE -> ClaimRole.MEMBER;
            case MEMBER -> ClaimRole.STAFF;
            case STAFF -> ClaimRole.COOWNER;
            case COOWNER -> ClaimRole.MEMBER;
        };
    }

    private ClaimRole parseRoleOrNone(String name) {
        try {
            return ClaimRole.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ClaimRole.NONE;
        }
    }

    // ---------------------------------------------------------------- Popup: Name bearbeiten (Punkt 1)

    private void openNameEdit(String claimId) {
        ClaimEditorSnapshot.ClaimEntry claim = byId(claimId);
        if (claim == null) return;
        nameEditClaimId = claimId;
        nameEditLinkBoundary = claim.linkBoundaryColorToTitle;
        clearWidgets();
        buildWidgets();
        // Felder erst NACH buildWidgets() befüllen - buildNameEditPopup() legt sie mit dem
        // aktuellen Server-Stand an, wenn nameEditNameField noch null ist (erstes Öffnen).
    }

    private void buildNameEditPopup() {
        ClaimEditorSnapshot.ClaimEntry claim = byId(nameEditClaimId);
        if (claim == null) {
            nameEditClaimId = null;
            buildWidgets();
            return;
        }
        boolean firstBuild = nameEditNameField == null;
        int x = overlayPopupFieldX();
        int y = (this.height - nameEditOverlayHeight()) / 2;
        int row = y + 26;

        nameEditNameField = overlayField(x, row, firstBuild ? claim.name : nameEditNameField.getValue());
        row += OVERLAY_ROW_HEIGHT;

        nameEditColorField = overlayField(x, row, firstBuild ? hex(claim.titleColor) : nameEditColorField.getValue());
        addRenderableWidget(new HueBarWidget(x + OVERLAY_FIELD_WIDTH + OVERLAY_HUE_GAP, row, OVERLAY_HUE_WIDTH, OVERLAY_ROW_HEIGHT - 2,
            rgb -> nameEditColorField.setValue(hex(rgb))));
        row += OVERLAY_ROW_HEIGHT;

        // Punkt 13-15 (Nachtrag 3): Anzeigedauer ist keine Besitzer-Einstellung mehr - jeder
        // Betrachter legt seine EIGENE Dauer im neuen, persönlichen Anzeige-Einstellungen-Screen
        // fest (siehe AreaClaimsDisplayPrefsScreen/PlayerDisplayPreferences-Klassenkommentar). Das
        // Dauer-Feld wurde deshalb hier ersatzlos entfernt.

        nameEditBoundaryColorField = overlayField(x, row, firstBuild ? hex(claim.boundaryColor) : nameEditBoundaryColorField.getValue());
        addRenderableWidget(new HueBarWidget(x + OVERLAY_FIELD_WIDTH + OVERLAY_HUE_GAP, row, OVERLAY_HUE_WIDTH, OVERLAY_ROW_HEIGHT - 2,
            rgb -> nameEditBoundaryColorField.setValue(hex(rgb))));
        row += OVERLAY_ROW_HEIGHT;

        addRenderableWidget(Button.builder(linkBoundaryLabel(), b -> {
                nameEditLinkBoundary = !nameEditLinkBoundary;
                clearWidgets();
                buildWidgets();
            })
            .bounds(x, row, OVERLAY_FIELD_WIDTH + OVERLAY_HUE_GAP + OVERLAY_HUE_WIDTH, OVERLAY_ROW_HEIGHT - 2)
            .build());
        row += OVERLAY_ROW_HEIGHT;

        // Punkt 6 (Nachtrag 4, "Bild statt Text"): Besitzer darf hier ein Bild statt des Textnamens
        // zuweisen - öffnet einen eigenen Screen (Upload + durchstöberbare Galerie, siehe
        // AreaClaimsImagePickerScreen-Klassenkommentar), statt diesen ohnehin schon vollen Popup
        // noch weiter zu überladen.
        String pickerClaimId = nameEditClaimId;
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.imagepicker.open_button"),
                b -> this.minecraft.setScreen(new AreaClaimsImagePickerScreen(pickerClaimId, this)))
            .bounds(x, row, OVERLAY_FIELD_WIDTH + OVERLAY_HUE_GAP + OVERLAY_HUE_WIDTH, OVERLAY_ROW_HEIGHT - 2)
            .build());
        row += OVERLAY_ROW_HEIGHT + 6;

        addOverlayButtons(x, row, this::applyNameEdit, () -> {
            nameEditClaimId = null;
            nameEditNameField = null;
        });
    }

    private Component linkBoundaryLabel() {
        return Component.translatable(nameEditLinkBoundary ? "areaclaims.entrymsg.link_on" : "areaclaims.entrymsg.link_off");
    }

    private void applyNameEdit() {
        String claimId = nameEditClaimId;
        String newName = nameEditNameField.getValue().trim();
        if (!newName.isEmpty()) sendRenameClaim(claimId, newName);
        sendEntryMsgField(claimId, "color", nameEditColorField.getValue().trim(), 0);
        // "boundarycolor" setzt serverseitig "verknüpft" automatisch aus - "linkboundarycolor"
        // wird deshalb bewusst ZULETZT geschickt, damit die Checkbox das letzte Wort hat
        // (identische Reihenfolge-Überlegung wie im vorherigen AreaClaimsEntryMessageScreen).
        sendEntryMsgField(claimId, "boundarycolor", nameEditBoundaryColorField.getValue().trim(), 0);
        sendEntryMsgField(claimId, "linkboundarycolor", "", nameEditLinkBoundary ? 1 : 0);
        nameEditClaimId = null;
        nameEditNameField = null;
        clearWidgets();
        buildWidgets();
    }

    private int nameEditOverlayHeight() {
        // Punkt 6 (Nachtrag 4): +1 Zeile für den neuen "Bild wählen"-Button.
        return 26 + OVERLAY_ROW_HEIGHT * 5 + 10 + OVERLAY_BUTTON_HEIGHT + 20;
    }

    // ---------------------------------------------------------------- Popup: Willkommensnachricht bearbeiten (Punkt 1, Variante)

    private void openWelcomeEdit(String claimId) {
        if (byId(claimId) == null) return;
        welcomeEditClaimId = claimId;
        clearWidgets();
        buildWidgets();
    }

    private void buildWelcomeEditPopup() {
        ClaimEditorSnapshot.ClaimEntry claim = byId(welcomeEditClaimId);
        if (claim == null) {
            welcomeEditClaimId = null;
            buildWidgets();
            return;
        }
        boolean firstBuild = welcomeEditTextField == null;
        int x = overlayPopupFieldX();
        int y = (this.height - welcomeEditOverlayHeight()) / 2;
        int row = y + 26;

        welcomeEditTextField = overlayField(x, row, firstBuild ? nullToEmpty(claim.welcomeMessage) : welcomeEditTextField.getValue());
        row += OVERLAY_ROW_HEIGHT;

        welcomeEditColorField = overlayField(x, row, firstBuild ? hex(claim.welcomeColor) : welcomeEditColorField.getValue());
        addRenderableWidget(new HueBarWidget(x + OVERLAY_FIELD_WIDTH + OVERLAY_HUE_GAP, row, OVERLAY_HUE_WIDTH, OVERLAY_ROW_HEIGHT - 2,
            rgb -> welcomeEditColorField.setValue(hex(rgb))));
        row += OVERLAY_ROW_HEIGHT + 6;

        // Punkt 13-15 (Nachtrag 3): Dauer-Feld entfernt, siehe Kommentar in buildNameEditPopup().

        addOverlayButtons(x, row, this::applyWelcomeEdit, () -> {
            welcomeEditClaimId = null;
            welcomeEditTextField = null;
        });
    }

    private void applyWelcomeEdit() {
        String claimId = welcomeEditClaimId;
        sendEntryMsgField(claimId, "welcome", welcomeEditTextField.getValue(), 0);
        sendEntryMsgField(claimId, "welcomecolor", welcomeEditColorField.getValue().trim(), 0);
        welcomeEditClaimId = null;
        welcomeEditTextField = null;
        clearWidgets();
        buildWidgets();
    }

    private int welcomeEditOverlayHeight() {
        return 26 + OVERLAY_ROW_HEIGHT * 2 + 10 + OVERLAY_BUTTON_HEIGHT + 20;
    }

    // ---------------------------------------------------------------- Popup: Einstellungen (Punkt 5)

    private void openSettingsPopup() {
        String override = ClientClaimCache.get().boundaryColorOverride;
        settingsUseClaimColor = override == null || override.isBlank();
        settingsPopupOpen = true;
        settingsColorField = null;
        clearWidgets();
        buildWidgets();
    }

    private void buildSettingsPopup() {
        int x = (this.width - overlayWidth()) / 2;
        int y = (this.height - settingsOverlayHeight()) / 2;
        int row = y + 26;

        addRenderableWidget(Button.builder(
                Component.translatable(settingsUseClaimColor ? "areaclaims.settings.use_claim_color_on" : "areaclaims.settings.use_claim_color_off"),
                b -> {
                    settingsUseClaimColor = !settingsUseClaimColor;
                    clearWidgets();
                    buildWidgets();
                })
            .bounds(x, row, OVERLAY_FIELD_WIDTH + OVERLAY_HUE_GAP + OVERLAY_HUE_WIDTH, OVERLAY_ROW_HEIGHT - 2)
            .build());
        row += OVERLAY_ROW_HEIGHT;

        if (!settingsUseClaimColor) {
            String initial = settingsColorField != null ? settingsColorField.getValue()
                : (ClientClaimCache.get().boundaryColorOverride.isBlank() ? "FFFFFF" : ClientClaimCache.get().boundaryColorOverride);
            settingsColorField = overlayField(x, row, initial);
            addRenderableWidget(new HueBarWidget(x + OVERLAY_FIELD_WIDTH + OVERLAY_HUE_GAP, row, OVERLAY_HUE_WIDTH, OVERLAY_ROW_HEIGHT - 2,
                rgb -> settingsColorField.setValue(hex(rgb))));
            row += OVERLAY_ROW_HEIGHT;
        }

        addRenderableWidget(Button.builder(currentModeLabel(), b -> sendCycleShowcaseMode())
            .bounds(x, row, OVERLAY_FIELD_WIDTH, OVERLAY_ROW_HEIGHT - 2)
            .build());
        row += OVERLAY_ROW_HEIGHT + 6;

        addOverlayButtons(x, row, this::applySettings, () -> settingsPopupOpen = false);
    }

    private void applySettings() {
        String hex = settingsUseClaimColor ? "" : settingsColorField.getValue().trim();
        SetBoundaryColorOverridePacket packet = new SetBoundaryColorOverridePacket(hex);
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
        settingsPopupOpen = false;
        clearWidgets();
        buildWidgets();
    }

    private int settingsOverlayHeight() {
        int rows = settingsUseClaimColor ? 2 : 3;
        return 26 + OVERLAY_ROW_HEIGHT * rows + 10 + OVERLAY_BUTTON_HEIGHT + 20;
    }

    // ---------------------------------------------------------------- Popup: Freikauf-Bestätigung (Punkt 4)

    private void openBuyoutConfirm(String claimId, String rule) {
        buyoutConfirmClaimId = claimId;
        buyoutConfirmRule = rule;
        clearWidgets();
        buildWidgets();
    }

    private void buildBuyoutConfirmPopup() {
        ClaimEditorSnapshot.ClaimEntry claim = byId(buyoutConfirmClaimId);
        ClaimEditorSnapshot.RuleEntry rule = claim == null ? null : claim.rules.stream()
            .filter(r -> r.rule.equals(buyoutConfirmRule)).findFirst().orElse(null);
        if (claim == null || rule == null || rule.buyoutPrice == null) {
            buyoutConfirmClaimId = null;
            buyoutConfirmRule = null;
            buildWidgets();
            return;
        }
        int x = (this.width - overlayWidth()) / 2;
        int y = (this.height - buyoutOverlayHeight()) / 2;
        int buttonY = y + buyoutOverlayHeight() - OVERLAY_BUTTON_HEIGHT - 10;
        int buttonWidth = (overlayWidth() - 30 - OVERLAY_BUTTON_GAP) / 2;

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.confirm.confirm"), b -> {
                BuyoutRulePacket packet = new BuyoutRulePacket(buyoutConfirmClaimId, buyoutConfirmRule);
                if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
                    PacketDistributor.sendToServer(packet);
                }
                buyoutConfirmClaimId = null;
                buyoutConfirmRule = null;
                clearWidgets();
                buildWidgets();
            })
            .bounds(x + 10, buttonY, buttonWidth, OVERLAY_BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.confirm.cancel"), b -> {
                buyoutConfirmClaimId = null;
                buyoutConfirmRule = null;
                clearWidgets();
                buildWidgets();
            })
            .bounds(x + 10 + buttonWidth + OVERLAY_BUTTON_GAP, buttonY, buttonWidth, OVERLAY_BUTTON_HEIGHT)
            .build());
    }

    private int buyoutOverlayHeight() {
        return 90;
    }

    // ---------------------------------------------------------------- Popup-Bausteine (gemeinsam)

    private int overlayWidth() {
        return overlayLabelWidth() + OVERLAY_FIELD_WIDTH + OVERLAY_HUE_GAP + OVERLAY_HUE_WIDTH + 20;
    }

    private EditBox overlayField(int x, int y, String value) {
        EditBox field = new EditBox(this.font, x, y, OVERLAY_FIELD_WIDTH, OVERLAY_ROW_HEIGHT - 2, Component.empty());
        field.setMaxLength(256);
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private void addOverlayButtons(int x, int y, Runnable onApply, Runnable onCancel) {
        int width = (OVERLAY_FIELD_WIDTH + OVERLAY_HUE_GAP + OVERLAY_HUE_WIDTH - OVERLAY_BUTTON_GAP) / 2;
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.serverconfig.apply"), b -> onApply.run())
            .bounds(x, y, width, OVERLAY_BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.confirm.cancel"), b -> {
                onCancel.run();
                clearWidgets();
                buildWidgets();
            })
            .bounds(x + width + OVERLAY_BUTTON_GAP, y, width, OVERLAY_BUTTON_HEIGHT)
            .build());
    }

    private static String hex(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void sendEntryMsgField(String claimId, String field, String stringArg, long longArg) {
        SetEntryMessagePacket packet = new SetEntryMessagePacket(claimId, field, stringArg, longArg);
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    // ---------------------------------------------------------------- Preisbestätigung-Overlay (ROADMAP.md Phase 8, unverändert)

    /**
     * Nachtrag 7, Punkt 5 (GUI-Skalierung 3, 3 linke Buttons leer): war vorher ein fester Viertel-
     * der-Popup-Breite-Wert (260-30-3*Gap)/4 = 53px - deutlich zu schmal für "Bestätigen"(10)/
     * "Abbrechen"(9)/"Auswahl merken"(14 inkl. Leerzeichen), NUR "Beenden"(7) passte zufällig noch
     * hinein. Zu schmaler Text löst Vanillas eingebautes Scrolling-Text-Rendering aus, das bei jeder
     * GUI-Größe außer 2 wegen falsch umgerechneter Scissor-Koordinaten in einem
     * {@link FixedScaleScreen} komplett LEER rendert - derselbe, schon mehrfach in dieser
     * Modfamilie gefundene Bug (siehe Phase 6-Nachtrag, Punkt 1: Rollen-/Kaufen-Buttons). Jetzt wie
     * dort über {@link #measuredButtonWidth} anhand der TATSÄCHLICH gerenderten Breite aller 4
     * Labels berechnet (funktioniert automatisch für jede Sprache, nicht nur Deutsch/Englisch -
     * z. B. ist Englisch "Remember Selection" sogar noch länger als "Auswahl merken").
     */
    private int priceConfirmButtonWidth() {
        return measuredButtonWidth("areaclaims.confirm.confirm", "areaclaims.confirm.cancel",
            "areaclaims.confirm.remember", "areaclaims.confirm.discard");
    }

    /** Von {@link #buildPriceConfirmOverlay}, {@link #renderPriceConfirmOverlay} UND {@link #renderActivePopupBackground} genutzt - MUSS an allen drei Stellen identisch sein (siehe dortige Kommentare), sonst laufen Hintergrund-Kasten/Text-Zentrierung/Buttons auseinander. */
    private int priceConfirmWidth() {
        return Math.max(260, priceConfirmButtonWidth() * 4 + OVERLAY_BUTTON_GAP * 3 + 20);
    }

    private void buildPriceConfirmOverlay(PriceConfirmSnapshot confirm) {
        int buttonWidth = priceConfirmButtonWidth();
        int width = priceConfirmWidth();
        int height = 120;
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;

        int buttonY = y + height - OVERLAY_BUTTON_HEIGHT - 10;

        Button confirmButton = Button.builder(Component.translatable("areaclaims.confirm.confirm"), b -> {
                ClientPriceConfirmCache.clear();
                clearWidgets();
                buildWidgets();
                sendConfirmPending();
            })
            .bounds(x + 10, buttonY, buttonWidth, OVERLAY_BUTTON_HEIGHT)
            .build();
        confirmButton.active = confirm.affordable;
        addRenderableWidget(confirmButton);

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.confirm.cancel"), b -> {
                sendResumePending();
                Minecraft.getInstance().setScreen(null);
            })
            .bounds(x + 10 + (buttonWidth + OVERLAY_BUTTON_GAP), buttonY, buttonWidth, OVERLAY_BUTTON_HEIGHT)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.confirm.remember"), b ->
                Minecraft.getInstance().setScreen(null))
            .bounds(x + 10 + (buttonWidth + OVERLAY_BUTTON_GAP) * 2, buttonY, buttonWidth, OVERLAY_BUTTON_HEIGHT)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.confirm.discard"), b -> {
                ClientPriceConfirmCache.clear();
                sendDiscardPending();
                clearWidgets();
                buildWidgets();
            })
            .bounds(x + 10 + (buttonWidth + OVERLAY_BUTTON_GAP) * 3, buttonY, buttonWidth, OVERLAY_BUTTON_HEIGHT)
            .build());
    }

    private Component describePrice(ServerConfigSnapshot.PriceEntry price) {
        List<Component> parts = new ArrayList<>();
        if (price.dollars != null && !"0".equals(price.dollars) && !price.dollars.isBlank()) {
            parts.add(Component.translatable("areaclaims.command.config.price_dollars", price.dollars));
        }
        if (price.items != null) {
            for (ServerConfigSnapshot.ItemEntry item : price.items) {
                parts.add(Component.translatable("areaclaims.command.config.price_item", item.amount, item.itemId));
            }
        }
        if (parts.isEmpty()) return Component.translatable("areaclaims.command.config.price_none");
        List<String> combinators = price.combinators != null ? price.combinators : List.of();
        MutableComponent result = parts.get(0).copy();
        for (int i = 1; i < parts.size(); i++) {
            String comboKey = (i - 1) < combinators.size() && "OR".equals(combinators.get(i - 1))
                ? "areaclaims.serverconfig.combinator.or" : "areaclaims.serverconfig.combinator.and";
            result = result.append(Component.literal(" ")).append(Component.translatable(comboKey)).append(Component.literal(" ")).append(parts.get(i));
        }
        return result;
    }

    // ---------------------------------------------------------------- Netzwerk (kanal-geprüft, siehe ClientNetworkUtil)

    private void sendSetRole(String claimId, String playerName, ClaimRole role) {
        SetRolePacket packet = new SetRolePacket(claimId, playerName, role.name());
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendSetRule(String claimId, String rule, boolean enabled, ClaimRole minRole) {
        SetRulePacket packet = new SetRulePacket(claimId, rule, enabled, minRole.name());
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendDeleteClaim(String claimId) {
        DeleteClaimPacket packet = new DeleteClaimPacket(claimId);
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendShowClaim(String claimId) {
        ShowClaimPacket packet = new ShowClaimPacket(claimId);
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendBeginSelection(ActiveSelectionManager.ActionType action, String targetClaimId) {
        BeginSelectionPacket packet = new BeginSelectionPacket(action.name(), targetClaimId == null ? "" : targetClaimId);
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendRenameClaim(String claimId, String newName) {
        RenameClaimPacket packet = new RenameClaimPacket(claimId, newName);
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendCycleShowcaseMode() {
        SetShowcaseModePacket packet = new SetShowcaseModePacket();
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendConfirmPending() {
        ConfirmPendingActionPacket packet = new ConfirmPendingActionPacket();
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendResumePending() {
        ResumePendingActionPacket packet = new ResumePendingActionPacket();
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendDiscardPending() {
        DiscardPendingActionPacket packet = new DiscardPendingActionPacket();
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    // ---------------------------------------------------------------- Rendern

    /**
     * Zeichnet NUR den dunklen Hintergrund-Kasten des aktuell aktiven Popups (falls eines aktiv
     * ist) - MUSS vor {@link #renderWidgets} aufgerufen werden, siehe Kommentar in
     * {@link #renderScaled}. Dieselbe Prioritäts-Reihenfolge wie die Text-Dispatch-Kette weiter
     * unten in {@link #renderScaled} (nur EIN Popup kann gleichzeitig aktiv sein).
     */
    private void renderActivePopupBackground(GuiGraphics graphics) {
        if (ClientPriceConfirmCache.get() != null) {
            int width = priceConfirmWidth();
            int height = 120;
            int x = (this.width - width) / 2;
            int y = (this.height - height) / 2;
            graphics.fill(x, y, x + width, y + height, OVERLAY_BG_COLOR);
        } else if (nameEditClaimId != null) {
            int y = (this.height - nameEditOverlayHeight()) / 2;
            int boxLeft = overlayPopupBoxLeft();
            graphics.fill(boxLeft, y, boxLeft + overlayWidth(), y + nameEditOverlayHeight(), OVERLAY_BG_COLOR);
        } else if (welcomeEditClaimId != null) {
            int y = (this.height - welcomeEditOverlayHeight()) / 2;
            int boxLeft = overlayPopupBoxLeft();
            graphics.fill(boxLeft, y, boxLeft + overlayWidth(), y + welcomeEditOverlayHeight(), OVERLAY_BG_COLOR);
        } else if (settingsPopupOpen) {
            int x = (this.width - overlayWidth()) / 2;
            int y = (this.height - settingsOverlayHeight()) / 2;
            graphics.fill(x - 10, y, x + overlayWidth() + 10, y + settingsOverlayHeight(), OVERLAY_BG_COLOR);
        } else if (buyoutConfirmClaimId != null) {
            int x = (this.width - overlayWidth()) / 2;
            int y = (this.height - buyoutOverlayHeight()) / 2;
            graphics.fill(x, y, x + overlayWidth(), y + buyoutOverlayHeight(), OVERLAY_BG_COLOR);
        }
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Punkt 4 (Nachtrag 4, Nutzer-Fund): der dunkle Popup-Hintergrund wurde bisher NACH den
        // Widgets gezeichnet (renderXPopup() lief nach super.renderScaled(), das die Widgets
        // inkl. HueBarWidget rendert) - der ~88%-deckende Kasten (OVERLAY_BG_COLOR) legte sich
        // dadurch JEDES Mal über die schon gezeichneten Widgets und dunkelte sie sichtbar ab. Am
        // auffälligsten beim Regenbogen-Farbwähler (satte Farben reagieren am empfindlichsten auf
        // eine Abdunkelung), betraf strukturell aber JEDES Popup-Widget. Fix: der Hintergrund-Kasten
        // wird jetzt VOR den Widgets gezeichnet, Titel/Labels bleiben weiterhin danach (reiner Text,
        // den nichts mehr verdeckt).
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderActivePopupBackground(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, TEXT_COLOR);

        PriceConfirmSnapshot confirm = ClientPriceConfirmCache.get();
        if (confirm != null) {
            renderPriceConfirmOverlay(graphics, confirm);
            return;
        }
        if (nameEditClaimId != null) {
            renderNameEditPopup(graphics);
            return;
        }
        if (welcomeEditClaimId != null) {
            renderWelcomeEditPopup(graphics);
            return;
        }
        if (settingsPopupOpen) {
            renderSettingsPopup(graphics);
            return;
        }
        if (buyoutConfirmClaimId != null) {
            renderBuyoutConfirmPopup(graphics);
            return;
        }

        List<ClaimEditorSnapshot.ClaimEntry> mains = mainClaims();
        if (mains.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("areaclaims.editor.no_claims"), MARGIN, COL1_Y, MUTED_TEXT_COLOR, false);
            return;
        }

        renderMainColumnInfo(graphics, mains);
        renderSubColumnInfo(graphics);
        int lineX = COL3_X - SEPARATOR_X_OFFSET;
        graphics.fill(lineX, COL1_Y, lineX + 1, this.height - BOTTOM_MARGIN, SEPARATOR_COLOR);

        ClaimEditorSnapshot.ClaimEntry focused = focusedClaim();
        if (focused == null) return;

        renderDetailPanelText(graphics, focused);
        renderMemberList(graphics, focused);
        renderRulePanelText(graphics, focused);
    }

    private void renderDetailPanelText(GuiGraphics graphics, ClaimEditorSnapshot.ClaimEntry claim) {
        int y = DETAIL_HEADING_Y;

        if (claim.main) {
            graphics.drawString(this.font, Component.literal(claim.name), COL3_X, y, claim.titleColor, HEADER_BOLD);
            y += DETAIL_ROW_HEIGHT_LARGE;

            graphics.drawString(this.font, Component.translatable("areaclaims.editor.subclaims_header"), COL3_X, y, TEXT_COLOR, HEADER_BOLD);
            y += DETAIL_ROW_HEIGHT;

            List<ClaimEditorSnapshot.ClaimEntry> subs = subClaimsOf(claim.id);
            if (subs.isEmpty()) {
                graphics.drawString(this.font, Component.translatable("areaclaims.editor.no_subclaims"), COL3_X, y, MUTED_TEXT_COLOR, false);
                y += DETAIL_ROW_HEIGHT;
            } else {
                int shown = 0;
                for (ClaimEditorSnapshot.ClaimEntry sub : subs) {
                    if (shown >= MAX_SUBCLAIM_NAME_ROWS) break;
                    graphics.drawString(this.font, Component.literal(sub.name), COL3_X, y, sub.titleColor, false);
                    y += DETAIL_ROW_HEIGHT;
                    shown++;
                }
            }

            graphics.drawString(this.font, welcomePreviewLabel(claim), COL3_X, y, MUTED_TEXT_COLOR, false);
            y += DETAIL_ROW_HEIGHT_LARGE;
        } else {
            ClaimEditorSnapshot.ClaimEntry parent = byId(claim.parentId);
            String parentName = parent != null ? parent.name : "?";
            graphics.drawString(this.font, Component.translatable("areaclaims.editor.parent_prefix", parentName), COL3_X, y, MUTED_TEXT_COLOR, false);
            y += DETAIL_ROW_HEIGHT_LARGE;

            graphics.drawString(this.font, Component.literal(claim.name), COL3_X, y, claim.titleColor, HEADER_BOLD);
            y += DETAIL_ROW_HEIGHT_LARGE;

            graphics.drawString(this.font, welcomePreviewLabel(claim), COL3_X, y, MUTED_TEXT_COLOR, false);
            y += DETAIL_ROW_HEIGHT_LARGE;
        }

        y += 6;
        graphics.drawString(this.font, Component.translatable("areaclaims.editor.members_header"), MEMBERS_PANEL_X, y, TEXT_COLOR, HEADER_BOLD);
    }

    private void renderMemberList(GuiGraphics graphics, ClaimEditorSnapshot.ClaimEntry claim) {
        int my = memberListStartY(claim);
        int shown = 0;
        for (ClaimEditorSnapshot.MemberEntry member : claim.members) {
            if (shown >= MAX_MEMBER_ROWS_SHOWN) break;
            Component line = Component.literal(member.name + " - ").append(parseRoleOrNone(member.role).translatable());
            graphics.drawString(this.font, line, MEMBERS_PANEL_X, my, TEXT_COLOR, false);
            my += MEMBER_LINE_HEIGHT;
            shown++;
        }
        if (claim.members.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("areaclaims.editor.no_members"), MEMBERS_PANEL_X, my + NO_MEMBERS_TEXT_Y_NUDGE, MUTED_TEXT_COLOR, false);
        }
    }

    /** Muss exakt dieselbe Y-Rechnung wie {@link #buildDetailPanel} nutzen, sonst laufen Stift-Buttons und Text auseinander. */
    private int memberListStartY(ClaimEditorSnapshot.ClaimEntry claim) {
        int y = DETAIL_HEADING_Y;
        if (claim.main) {
            y += DETAIL_ROW_HEIGHT_LARGE;
            y += DETAIL_ROW_HEIGHT;
            int shown = Math.min(subClaimsOf(claim.id).size(), MAX_SUBCLAIM_NAME_ROWS);
            y += Math.max(shown, 1) * DETAIL_ROW_HEIGHT;
            y += DETAIL_ROW_HEIGHT_LARGE;
        } else {
            y += DETAIL_ROW_HEIGHT_LARGE;
            y += DETAIL_ROW_HEIGHT_LARGE;
            y += DETAIL_ROW_HEIGHT_LARGE;
        }
        y += 6;
        y += DETAIL_ROW_HEIGHT + 2;
        return y;
    }

    private void renderRulePanelText(GuiGraphics graphics, ClaimEditorSnapshot.ClaimEntry claim) {
        int panelX = rulesPanelX();
        int roleColumnX = panelX + RULE_CHECK_WIDTH + RULE_BUTTON_GAP + RULE_NAME_WIDTH + RULE_BUTTON_GAP;

        graphics.drawString(this.font, Component.translatable("areaclaims.editor.rules_header"), panelX, DETAIL_HEADING_Y, TEXT_COLOR, HEADER_BOLD);
        graphics.drawString(this.font, Component.translatable("areaclaims.serverconfig.ignored_from"), roleColumnX, DETAIL_HEADING_Y, MUTED_TEXT_COLOR, false);

        int y = DETAIL_HEADING_Y + 24;
        for (ClaimEditorSnapshot.RuleEntry rule : claim.rules) {
            RuleType type = parseRuleOrNull(rule.rule);
            Component label = type != null ? type.translatable() : Component.literal(rule.rule);
            graphics.drawString(this.font, label, panelX + RULE_CHECK_WIDTH + RULE_BUTTON_GAP, y + 5, TEXT_COLOR, false);
            y += RULE_ROW_HEIGHT;
        }
    }

    private void renderNameEditPopup(GuiGraphics graphics) {
        int y = (this.height - nameEditOverlayHeight()) / 2;
        int boxLeft = overlayPopupBoxLeft();
        int boxRight = boxLeft + overlayWidth();
        graphics.drawCenteredString(this.font, Component.translatable("areaclaims.nameedit.title"), (boxLeft + boxRight) / 2, y + 8, TEXT_COLOR);
        int row = y + 26;
        int labelX = boxLeft + 10;
        drawOverlayLabel(graphics, "areaclaims.nameedit.name", labelX, row);
        drawOverlayLabel(graphics, "areaclaims.entrymsg.title_color", labelX, row + OVERLAY_ROW_HEIGHT);
        drawOverlayLabel(graphics, "areaclaims.entrymsg.boundary_color", labelX, row + OVERLAY_ROW_HEIGHT * 2);
    }

    private void renderWelcomeEditPopup(GuiGraphics graphics) {
        int y = (this.height - welcomeEditOverlayHeight()) / 2;
        int boxLeft = overlayPopupBoxLeft();
        int boxRight = boxLeft + overlayWidth();
        graphics.drawCenteredString(this.font, Component.translatable("areaclaims.entrymsg.title"), (boxLeft + boxRight) / 2, y + 8, TEXT_COLOR);
        int row = y + 26;
        int labelX = boxLeft + 10;
        drawOverlayLabel(graphics, "areaclaims.entrymsg.welcome_text", labelX, row);
        drawOverlayLabel(graphics, "areaclaims.entrymsg.welcome_color", labelX, row + OVERLAY_ROW_HEIGHT);
    }

    private void renderSettingsPopup(GuiGraphics graphics) {
        int x = (this.width - overlayWidth()) / 2;
        int y = (this.height - settingsOverlayHeight()) / 2;
        graphics.drawCenteredString(this.font, Component.translatable("areaclaims.editor.settings"), x + overlayWidth() / 2, y + 8, TEXT_COLOR);
    }

    private void renderBuyoutConfirmPopup(GuiGraphics graphics) {
        ClaimEditorSnapshot.ClaimEntry claim = byId(buyoutConfirmClaimId);
        ClaimEditorSnapshot.RuleEntry rule = claim == null ? null : claim.rules.stream()
            .filter(r -> r.rule.equals(buyoutConfirmRule)).findFirst().orElse(null);
        int x = (this.width - overlayWidth()) / 2;
        int y = (this.height - buyoutOverlayHeight()) / 2;
        graphics.drawCenteredString(this.font, Component.translatable("areaclaims.editor.buyout_title"), x + overlayWidth() / 2, y + 10, TEXT_COLOR);
        if (rule != null && rule.buyoutPrice != null) {
            graphics.drawCenteredString(this.font, describePrice(rule.buyoutPrice), x + overlayWidth() / 2, y + 26, TEXT_COLOR);
        }
    }

    private void drawOverlayLabel(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(this.font, Component.translatable(key), x, y + 6, TEXT_COLOR, false);
    }

    private void renderPriceConfirmOverlay(GuiGraphics graphics, PriceConfirmSnapshot confirm) {
        int width = priceConfirmWidth();
        int height = 120;
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;

        boolean isAdjust = "ADJUST_MAIN".equals(confirm.action) || "ADJUST_SUB".equals(confirm.action);
        if (isAdjust) {
            renderAdjustConfirmOverlay(graphics, confirm, x, y, width);
            return;
        }

        graphics.drawCenteredString(this.font, Component.translatable("areaclaims.confirm.title"), x + width / 2, y + 10, TEXT_COLOR);
        graphics.drawCenteredString(this.font, Component.translatable("areaclaims.confirm.blocks", confirm.blocks), x + width / 2, y + 26, MUTED_TEXT_COLOR);
        graphics.drawCenteredString(this.font, describePrice(confirm.price), x + width / 2, y + 42, TEXT_COLOR);
        if (!confirm.affordable) {
            graphics.drawCenteredString(this.font, Component.translatable("areaclaims.confirm.cannot_afford"), x + width / 2, y + 58, RED);
        }
    }

    /**
     * Nutzer-Vorgabe (2026-08-18, "Anpassen"-Button): eine Anpassen-Sitzung kann GLEICHZEITIG
     * Blöcke hinzufügen (Preis) UND entfernen (Rückerstattung) - zeigt darum bis zu zwei Zeilen
     * statt der einzelnen Preis-Zeile des normalen Preisbestätigung-Popups.
     */
    private void renderAdjustConfirmOverlay(GuiGraphics graphics, PriceConfirmSnapshot confirm, int x, int y, int width) {
        graphics.drawCenteredString(this.font, Component.translatable("areaclaims.confirm.adjust_title"), x + width / 2, y + 10, TEXT_COLOR);
        int line = y + 26;
        if (confirm.addedBlocks > 0) {
            graphics.drawCenteredString(this.font, Component.translatable("areaclaims.confirm.adjust_added", confirm.addedBlocks), x + width / 2, line, MUTED_TEXT_COLOR);
            line += 10;
            graphics.drawCenteredString(this.font, describePrice(confirm.addedPrice), x + width / 2, line, TEXT_COLOR);
            line += 12;
        }
        if (confirm.removedBlocks > 0) {
            graphics.drawCenteredString(this.font, Component.translatable("areaclaims.confirm.adjust_removed", confirm.removedBlocks), x + width / 2, line, MUTED_TEXT_COLOR);
            line += 10;
            graphics.drawCenteredString(this.font, describePrice(confirm.removedPrice), x + width / 2, line, TEXT_COLOR);
            line += 12;
        }
        if (!confirm.affordable) {
            graphics.drawCenteredString(this.font, Component.translatable("areaclaims.confirm.cannot_afford"), x + width / 2, line, RED);
        }
    }

    /** Nachtrag 7: jetzt ZWEI Zeilen statt einer kombinierten - siehe {@link #MAIN_ROW_INFO_HEIGHT}-Kommentar für die Begründung. */
    private void renderMainColumnInfo(GuiGraphics graphics, List<ClaimEditorSnapshot.ClaimEntry> mains) {
        int y = COL1_Y;
        for (ClaimEditorSnapshot.ClaimEntry claim : mains) {
            Component owner = Component.translatable("areaclaims.editor.claim_info", claim.ownerName);
            Component blocks = Component.translatable("areaclaims.editor.claim_area", String.format("%.1f", claim.area));
            graphics.drawString(this.font, owner, COL1_X, y + MAIN_ROW_BUTTON_HEIGHT + 1, MUTED_TEXT_COLOR, false);
            graphics.drawString(this.font, blocks, COL1_X, y + MAIN_ROW_BUTTON_HEIGHT + 1 + 10, MUTED_TEXT_COLOR, false);
            y += MAIN_ROW_ADVANCE;
        }
    }

    private void renderSubColumnInfo(GuiGraphics graphics) {
        if (selectedMainId == null) return;
        List<ClaimEditorSnapshot.ClaimEntry> subs = subClaimsOf(selectedMainId);
        if (subs.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("areaclaims.editor.no_subclaims"), COL2_X, col2Y(), MUTED_TEXT_COLOR, false);
            return;
        }
        int y = col2Y();
        for (ClaimEditorSnapshot.ClaimEntry sub : subs) {
            Component info = Component.translatable("areaclaims.editor.claim_area", String.format("%.1f", sub.area));
            graphics.drawString(this.font, info, COL2_X, y + SUB_ROW_BUTTON_HEIGHT + 1, MUTED_TEXT_COLOR, false);
            y += SUB_ROW_ADVANCE;
        }
    }

    private RuleType parseRuleOrNull(String name) {
        try {
            return RuleType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
