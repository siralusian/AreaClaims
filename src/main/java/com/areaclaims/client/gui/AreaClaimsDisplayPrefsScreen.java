package com.areaclaims.client.gui;

import com.areaclaims.client.data.ClientDisplayPrefsCache;
import com.areaclaims.client.data.ClientNetworkUtil;
import com.areaclaims.display.PlayerDisplayPreferences;
import com.areaclaims.network.SetDisplayPrefsPacket;
import com.google.gson.Gson;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Persönlicher Anzeige-Einstellungen-Screen (ROADMAP.md Phase 7-Nachtrag 3, Punkte 13-15) - JEDER
 * Spieler (nicht nur Claim-Besitzer/Admins) legt hier fest, WIE ER SELBST Claim-/Unterbereichsnamen
 * und Willkommensnachrichten beim Betreten sieht (Dauer, Bildschirm-Position, "dauerhaft anzeigen").
 * Ersetzt die früher vom Claim-BESITZER festgelegte Anzeigedauer (siehe
 * {@link AreaClaimsEditorScreen#buildNameEditPopup}/{@code buildWelcomeEditPopup}, wo die Dauer-
 * Felder entfernt wurden) durch eine rein persönliche, pro-Betrachter geltende Einstellung.
 *
 * <p>Geöffnet über den "Anzeige-Einstellungen"-Button im Editor (siehe {@code AreaClaimsEditorScreen}),
 * synchronisiert über {@link com.areaclaims.network.DisplayPrefsSyncPacket}/{@link ClientDisplayPrefsCache}
 * - gleiches "bei jedem Öffnen frisch synchronisieren"-Muster wie der Server-Konfigurations-Screen.
 * "Willkommensnachricht" hat bewusst KEINEN Dauerhaft-Umschalter (Nutzer-Vorgabe: nicht ungefragt
 * hinzufügen, siehe {@link PlayerDisplayPreferences}-Klassenkommentar).
 */
public class AreaClaimsDisplayPrefsScreen extends FixedScaleScreen {

    private static final Gson GSON = new Gson();

    private static final int MARGIN = 10;
    private static final int TITLE_Y = 8;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final boolean HEADER_BOLD = true;
    private static final int BUTTON_TEXT_PADDING = 12;

    private static final int LABEL_X = MARGIN;
    private static final int ROW_Y_START = 26;
    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_GAP = 8;

    private static final int DURATION_FIELD_X = 120;
    private static final int DURATION_FIELD_WIDTH = 50;
    private static final int UNIT_BUTTON_GAP = 4;

    private static final int POS_LABEL_X_GAP = 14;
    private static final int POS_FIELD_WIDTH = 45;
    /** Nachtrag 5, Punkt 6: "X"/"Y" jetzt EIGENE kleine Labels statt in den Zeilentext eingebacken - Breite + Abstand vor dem jeweiligen Feld. */
    private static final int XY_LABEL_WIDTH = 8;
    private static final int XY_LABEL_GAP = 4;

    private static final int TOGGLE_BUTTON_X = 120;
    private static final int TOGGLE_BUTTON_WIDTH = 50;

    private static final int CLOSE_BUTTON_WIDTH = 80;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 10;
    private static final int BACK_BUTTON_WIDTH = 80;
    private static final int BACK_BUTTON_GAP = 4;
    private static final int SAVE_BUTTON_WIDTH = 100;
    private static final int LARGE_PREVIEW_BUTTON_WIDTH = 160;
    private static final int LARGE_PREVIEW_BUTTON_HEIGHT = 20;
    private static final int LARGE_PREVIEW_BUTTON_GAP = 4;

    private static final int ZOOM_FIELD_WIDTH = 34;
    private static final int ZOOM_PERCENT_SIGN_GAP = 2;

    // ---------------------------------------------------------------- Punkt 5 (Nachtrag 4): Live-Positionsvorschau
    private static final int PREVIEW_WIDTH = 160;
    private static final int PREVIEW_HEIGHT = 90;
    private static final int PREVIEW_MARGIN = 10;
    private static final int PREVIEW_BG_COLOR = 0xFF1E1E1E;
    private static final int PREVIEW_BORDER_COLOR = 0xFFFFFFFF;
    private static final int PREVIEW_DOT_SIZE = 3;
    private static final int MUTED_PREVIEW_LABEL_COLOR = 0xFFAAAAAA;
    /** Rein für die Vorschau angenommene Referenz-Bildschirmgröße (siehe Klassenkommentar {@link #renderPositionPreview}). */
    private static final int PREVIEW_REFERENCE_WIDTH = 640;
    private static final int PREVIEW_REFERENCE_HEIGHT = 360;
    /** Nachtrag 5, Punkt 1: Ziehradius in Vorschau-Pixeln, innerhalb dessen ein Klick einen Punkt "trifft". */
    private static final int PREVIEW_DRAG_HIT_RADIUS = 6;

    /** Nachtrag 5, Punkt 1 (Ziehbare Vorschau): welches Positions-Feldpaar (falls eines) gerade per Maus gezogen wird. */
    private EditBox draggingXField;
    private EditBox draggingYField;

    private static final int STYLE_TOGGLE_WIDTH = 18;
    private static final int STYLE_TOGGLE_GAP = 2;
    private static final int STYLE_SCALE_WIDTH = 36;
    private static final int STYLE_FONT_BUTTON_X_GAP = 6;

    /** Punkt 3 (Nachtrag 4): bekannte eingebaute Vanilla-Schriftarten, siehe Klassenkommentar unten bei {@link #cycleFont}. */
    private static final String[] KNOWN_FONTS = {"minecraft:default", "minecraft:uniform", "minecraft:alt"};

    private record LabelJob(Component text, int x, int y, boolean bold) {}

    /** Punkt 3 (Nachtrag 4): Text-Stil-Entwurf je Sektion - eigene Klasse statt eines immer länger werdenden Parameter-Rattenschwanzes an {@link #buildNameSection}/{@link #buildWelcomeSection}. */
    private static class StyleDraft {
        boolean bold, italic, underline, shadow = true, outline;
        String font = "minecraft:default";
    }

    private final java.util.List<LabelJob> labelJobs = new java.util.ArrayList<>();

    // -- Hauptbereichsname --
    private EditBox mainDurationField;
    private PlayerDisplayPreferences.DurationUnit mainUnit;
    private EditBox mainPosXField;
    private EditBox mainPosYField;
    private boolean mainPermanent;
    private EditBox mainPermXField;
    private EditBox mainPermYField;
    private EditBox mainScaleField;
    private final StyleDraft mainStyle = new StyleDraft();
    private boolean mainShowImageTimed = true;
    private boolean mainShowImagePermanent = true;
    private EditBox mainZoomField;
    private boolean mainPixelArt = false;

    // -- Unterbereichsname --
    private EditBox subDurationField;
    private PlayerDisplayPreferences.DurationUnit subUnit;
    private EditBox subPosXField;
    private EditBox subPosYField;
    private boolean subPermanent;
    private EditBox subPermXField;
    private EditBox subPermYField;
    private EditBox subScaleField;
    private final StyleDraft subStyle = new StyleDraft();
    private boolean subShowImageTimed = true;
    private boolean subShowImagePermanent = true;
    private EditBox subZoomField;
    private boolean subPixelArt = false;

    // -- Willkommensnachricht --
    private EditBox welcomeDurationField;
    private PlayerDisplayPreferences.DurationUnit welcomeUnit;
    private EditBox welcomePosXField;
    private EditBox welcomePosYField;
    private EditBox welcomeScaleField;
    private final StyleDraft welcomeStyle = new StyleDraft();

    private boolean initializedFromServer = false;

    /** Punkt 3 (Nachtrag 6, "Zurück"-Button): siehe {@code AreaClaimsServerConfigScreen#parentScreen}-Kommentar für die volle Begründung. */
    private final net.minecraft.client.gui.screens.Screen parentScreen;

    public AreaClaimsDisplayPrefsScreen(net.minecraft.client.gui.screens.Screen parentScreen) {
        super(Component.translatable("areaclaims.displayprefs.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void initScaled() {
        buildWidgets();
    }

    private void buildWidgets() {
        labelJobs.clear();
        PlayerDisplayPreferences prefs = ClientDisplayPrefsCache.get();
        if (!initializedFromServer) {
            seedFromServer(prefs);
            initializedFromServer = true;
        }

        int y = ROW_Y_START;
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.displayprefs.section_main"), LABEL_X, y, HEADER_BOLD));
        y += ROW_HEIGHT;
        y = buildNameSection(y, prefs.mainClaim, mainDurationField, mainUnit, mainPosXField, mainPosYField,
            mainPermanent, mainPermXField, mainPermYField, true);

        y += SECTION_GAP;
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.displayprefs.section_sub"), LABEL_X, y, HEADER_BOLD));
        y += ROW_HEIGHT;
        y = buildNameSection(y, prefs.subClaim, subDurationField, subUnit, subPosXField, subPosYField,
            subPermanent, subPermXField, subPermYField, false);

        y += SECTION_GAP;
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.displayprefs.section_welcome"), LABEL_X, y, HEADER_BOLD));
        y += ROW_HEIGHT;
        y = buildWelcomeSection(y);

        y += SECTION_GAP;

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.serverconfig.apply"), b -> save())
            .bounds(MARGIN, y, SAVE_BUTTON_WIDTH, ROW_HEIGHT - 2)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.close"), b -> onClose())
            .bounds(this.width - CLOSE_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.back"),
                b -> this.minecraft.setScreen(parentScreen))
            .bounds(this.width - CLOSE_BUTTON_WIDTH - BACK_BUTTON_GAP - BACK_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                BACK_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());

        // Punkt 2 (Nachtrag 5): öffnet die neue Vollbild-Vorschau - direkt unter dem kleinen
        // Vorschau-Feld platziert (siehe renderPositionPreview für dessen Position).
        int previewBoxX = this.width - PREVIEW_WIDTH - PREVIEW_MARGIN;
        int previewBoxY = PREVIEW_MARGIN + 14;
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.displayprefs.large_preview"),
                b -> this.minecraft.setScreen(new AreaClaimsDisplayPreviewScreen(this)))
            .bounds(previewBoxX + (PREVIEW_WIDTH - LARGE_PREVIEW_BUTTON_WIDTH) / 2, previewBoxY + PREVIEW_HEIGHT + LARGE_PREVIEW_BUTTON_GAP,
                LARGE_PREVIEW_BUTTON_WIDTH, LARGE_PREVIEW_BUTTON_HEIGHT)
            .build());
    }

    private void seedFromServer(PlayerDisplayPreferences prefs) {
        mainUnit = parseUnit(prefs.mainClaim.durationUnit);
        mainPermanent = prefs.mainClaim.permanent;
        subUnit = parseUnit(prefs.subClaim.durationUnit);
        subPermanent = prefs.subClaim.permanent;
        welcomeUnit = parseUnit(prefs.welcome.durationUnit);
        mainShowImageTimed = prefs.mainClaim.showImageTimed;
        mainShowImagePermanent = prefs.mainClaim.showImagePermanent;
        mainPixelArt = prefs.mainClaim.pixelArtFiltering;
        subShowImageTimed = prefs.subClaim.showImageTimed;
        subShowImagePermanent = prefs.subClaim.showImagePermanent;
        subPixelArt = prefs.subClaim.pixelArtFiltering;
        seedStyle(mainStyle, prefs.mainClaim.style);
        seedStyle(subStyle, prefs.subClaim.style);
        seedStyle(welcomeStyle, prefs.welcome.style);
    }

    private void seedStyle(StyleDraft draft, PlayerDisplayPreferences.TextStyle style) {
        draft.bold = style.bold;
        draft.italic = style.italic;
        draft.underline = style.underline;
        draft.shadow = style.shadow;
        draft.outline = style.outline;
        draft.font = style.font;
    }

    private PlayerDisplayPreferences.DurationUnit parseUnit(String name) {
        try {
            return PlayerDisplayPreferences.DurationUnit.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return PlayerDisplayPreferences.DurationUnit.TICKS;
        }
    }

    /**
     * Baut EINE Namens-Sektion (Hauptbereich ODER Unterbereich - identischer Aufbau, Nutzer-Vorgabe
     * "Sub Claim Name: identische Struktur"). Die als Parameter übergebenen Feld-Referenzen sind zum
     * Zeitpunkt des Aufrufs entweder {@code null} (allererster Aufbau) oder die VORHERIGEN Widgets
     * (Tipptext-Erhalt über Struktur-Änderungen hinweg, gleiches Prinzip wie im Server-Konfigurations-
     * Screen) - die tatsächlich NEUEN Referenzen werden über die {@code isMain}-Fallunterscheidung
     * in die jeweiligen Instanzfelder zurückgeschrieben.
     */
    private int buildNameSection(int y, PlayerDisplayPreferences.NameDisplayPrefs serverPrefs,
                                  EditBox prevDuration, PlayerDisplayPreferences.DurationUnit unit,
                                  EditBox prevPosX, EditBox prevPosY,
                                  boolean permanent, EditBox prevPermX, EditBox prevPermY, boolean isMain) {
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.displayprefs.duration_label"), LABEL_X, y, false));
        EditBox durationField = new EditBox(this.font, DURATION_FIELD_X, y, DURATION_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
        durationField.setValue(prevDuration != null ? prevDuration.getValue() : String.valueOf(displayValue(serverPrefs.durationTicks, unit)));
        addRenderableWidget(durationField);
        int unitButtonX = DURATION_FIELD_X + DURATION_FIELD_WIDTH + UNIT_BUTTON_GAP;
        addRenderableWidget(Button.builder(unitLabel(unit), b -> {
                PlayerDisplayPreferences.DurationUnit oldUnit = unit;
                PlayerDisplayPreferences.DurationUnit newUnit = oldUnit.next();
                int currentTicks = ticksFromDisplay(parseIntOr(durationField.getValue(), displayValue(serverPrefs.durationTicks, oldUnit)), oldUnit);
                if (isMain) mainUnit = newUnit; else subUnit = newUnit;
                durationField.setValue(String.valueOf(displayValue(currentTicks, newUnit)));
                clearWidgets();
                buildWidgets();
            })
            .bounds(unitButtonX, y, unitButtonWidth(), ROW_HEIGHT - 2)
            .build());
        y += ROW_HEIGHT;

        PosFields pos = buildPosRow(y, "areaclaims.displayprefs.position_label", prevPosX, prevPosY, serverPrefs.posX, serverPrefs.posY);
        y += ROW_HEIGHT;

        // Nachtrag 5, Punkt 4 (Layout-Überarbeitung): "Bild Anzeigen" für die ZEITGESTEUERTE Phase -
        // eigene, kurze Zeile (Klartext-Label + separater Ein/Aus-Button), nicht mehr in einen
        // Button-Text eingebacken.
        boolean showImageTimed = isMain ? mainShowImageTimed : subShowImageTimed;
        buildToggleRow(y, "areaclaims.displayprefs.show_image_label", showImageTimed, () -> {
            if (isMain) mainShowImageTimed = !mainShowImageTimed; else subShowImageTimed = !subShowImageTimed;
        });
        // Zoom lebt auf DERSELBEN Zeile wie der erste "Bild Anzeigen"-Umschalter (rechts daneben) -
        // Zoom gilt fürs Bild insgesamt, unabhängig davon, in welcher Phase es gerade sichtbar ist.
        int zoomLabelX = TOGGLE_BUTTON_X + TOGGLE_BUTTON_WIDTH + POS_LABEL_X_GAP + 20;
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.displayprefs.zoom_label"), zoomLabelX, y, false));
        EditBox prevZoom = isMain ? mainZoomField : subZoomField;
        EditBox zoomField = new EditBox(this.font, zoomLabelX + 46, y, ZOOM_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
        zoomField.setValue(prevZoom != null ? prevZoom.getValue() : String.valueOf(serverPrefs.imageZoomPercent));
        addRenderableWidget(zoomField);
        labelJobs.add(new LabelJob(Component.literal("%"), zoomLabelX + 46 + ZOOM_FIELD_WIDTH + ZOOM_PERCENT_SIGN_GAP, y, false));
        y += ROW_HEIGHT;

        // Nachtrag 6 (Bild-Qualität-Fund): "Pixelkunst"-Opt-in - Default AUS = glatte Filterung
        // (siehe ClientImageManager-Klassenkommentar), gilt für BEIDE Phasen gleichermaßen (anders
        // als die "Bild Anzeigen"-Umschalter braucht Filterung keine Phasen-Unterscheidung).
        boolean pixelArt = isMain ? mainPixelArt : subPixelArt;
        buildToggleRow(y, "areaclaims.displayprefs.pixel_art_label", pixelArt, () -> {
            if (isMain) mainPixelArt = !mainPixelArt; else subPixelArt = !subPixelArt;
        });
        y += ROW_HEIGHT;

        // Punkt 4 (Nachtrag 5): "Dauerhaft" (ob die Dauerhaft-Phase überhaupt aktiv ist) bekommt jetzt
        // eine EIGENE, saubere Zeile statt in denselben Button-Text wie "Bild Anzeigen" gequetscht zu sein.
        buildToggleRow(y, "areaclaims.displayprefs.permanent_label", permanent, () -> {
            if (isMain) mainPermanent = !mainPermanent; else subPermanent = !subPermanent;
        });
        y += ROW_HEIGHT;

        PosFields permPos = buildPosRow(y, "areaclaims.displayprefs.permanent_position_label", prevPermX, prevPermY, serverPrefs.permanentPosX, serverPrefs.permanentPosY);
        y += ROW_HEIGHT;

        // Gegenstück zur ersten "Bild Anzeigen"-Zeile, aber für die DAUERHAFT-Phase (ersetzt den
        // vorherigen globalen "permanentShowImage"-Umschalter aus Nachtrag 4, siehe
        // PlayerDisplayPreferences.NameDisplayPrefs#showImagePermanent-Klassenkommentar).
        boolean showImagePermanent = isMain ? mainShowImagePermanent : subShowImagePermanent;
        buildToggleRow(y, "areaclaims.displayprefs.show_image_label", showImagePermanent, () -> {
            if (isMain) mainShowImagePermanent = !mainShowImagePermanent; else subShowImagePermanent = !subShowImagePermanent;
        });
        y += ROW_HEIGHT;

        StyleDraft draft = isMain ? mainStyle : subStyle;
        EditBox prevScale = isMain ? mainScaleField : subScaleField;
        EditBox scaleField = buildStyleRow(y, draft, prevScale, serverPrefs.style.scale);
        y += ROW_HEIGHT;

        if (isMain) {
            mainDurationField = durationField;
            mainPosXField = pos.x();
            mainPosYField = pos.y();
            mainPermXField = permPos.x();
            mainPermYField = permPos.y();
            mainScaleField = scaleField;
            mainZoomField = zoomField;
        } else {
            subDurationField = durationField;
            subPosXField = pos.x();
            subPosYField = pos.y();
            subPermXField = permPos.x();
            subPermYField = permPos.y();
            subScaleField = scaleField;
            subZoomField = zoomField;
        }

        return y;
    }

    /** Feldpaar-Rückgabe für {@link #buildPosRow} - Java hat keine Mehrfach-Rückgabewerte, ein winziger Record ist hier klarer als zwei Ausgabe-Parameter. */
    private record PosFields(EditBox x, EditBox y) {}

    /**
     * Punkt 6 (Nachtrag 5): "X"/"Y" waren bisher in den Zeilentext eingebacken ("Position X:") -
     * jetzt EIGENE, kleine Labels direkt vor ihrem jeweiligen Feld, das X-Label selbst beginnt an
     * {@link #DURATION_FIELD_X} (fluchtet dadurch mit der linken Kante des "Dauer"-Feldes darüber),
     * das Feld rückt entsprechend nach rechts.
     */
    private PosFields buildPosRow(int y, String labelKey, EditBox prevX, EditBox prevY, int defaultX, int defaultY) {
        labelJobs.add(new LabelJob(Component.translatable(labelKey), LABEL_X, y, false));
        int xLabelX = DURATION_FIELD_X;
        labelJobs.add(new LabelJob(Component.literal("X"), xLabelX, y, false));
        int xFieldX = xLabelX + XY_LABEL_WIDTH + XY_LABEL_GAP;
        EditBox xField = new EditBox(this.font, xFieldX, y, POS_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
        xField.setValue(prevX != null ? prevX.getValue() : String.valueOf(defaultX));
        addRenderableWidget(xField);

        int yLabelX = xFieldX + POS_FIELD_WIDTH + POS_LABEL_X_GAP;
        labelJobs.add(new LabelJob(Component.literal("Y"), yLabelX, y, false));
        int yFieldX = yLabelX + XY_LABEL_WIDTH + XY_LABEL_GAP;
        EditBox yField = new EditBox(this.font, yFieldX, y, POS_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
        yField.setValue(prevY != null ? prevY.getValue() : String.valueOf(defaultY));
        addRenderableWidget(yField);

        return new PosFields(xField, yField);
    }

    /** Punkt 4 (Nachtrag 5): gemeinsamer Zeilen-Baustein für ALLE "kurzes Klartext-Label + separater Ein/Aus-Button"-Umschalter dieses Screens (Bild-Anzeigen ×2, Dauerhaft) - ersetzt die vorherigen, überladenen Einzel-Button-Texte. */
    private void buildToggleRow(int y, String labelKey, boolean value, Runnable toggle) {
        labelJobs.add(new LabelJob(Component.translatable(labelKey), LABEL_X, y, false));
        addRenderableWidget(Button.builder(onOffLabel(value), b -> {
                toggle.run();
                clearWidgets();
                buildWidgets();
            })
            .bounds(TOGGLE_BUTTON_X, y, TOGGLE_BUTTON_WIDTH, ROW_HEIGHT - 2)
            .build());
    }

    private Component onOffLabel(boolean on) {
        return Component.translatable(on ? "areaclaims.displayprefs.on" : "areaclaims.displayprefs.off");
    }

    /**
     * Punkt 3 (Nachtrag 4): eine Stil-Zeile - Skalierung, Fett/Kursiv/Unterstrichen/Schatten/Umriss-
     * Umschalter (kompakte Einzelbuchstaben-Buttons B/I/U/S/O, wie in vielen Text-Editoren üblich)
     * und ein Schriftart-Zyklus-Button. Wird sowohl von {@link #buildNameSection} als auch
     * {@link #buildWelcomeSection} genutzt (Willkommensnachricht bekommt exakt dieselbe Stil-Zeile,
     * war explizit gefordert: "applies per text-type... consistent with everything else").
     */
    private EditBox buildStyleRow(int y, StyleDraft draft, EditBox prevScale, float serverScale) {
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.displayprefs.style_label"), LABEL_X, y, false));
        EditBox scaleField = new EditBox(this.font, DURATION_FIELD_X, y, STYLE_SCALE_WIDTH, ROW_HEIGHT - 2, Component.empty());
        scaleField.setValue(prevScale != null ? prevScale.getValue() : trimScale(serverScale));
        addRenderableWidget(scaleField);

        int x = DURATION_FIELD_X + STYLE_SCALE_WIDTH + STYLE_FONT_BUTTON_X_GAP;
        x = addStyleToggle(x, y, "B", draft.bold, v -> draft.bold = v);
        x = addStyleToggle(x, y, "I", draft.italic, v -> draft.italic = v);
        x = addStyleToggle(x, y, "U", draft.underline, v -> draft.underline = v);
        x = addStyleToggle(x, y, "S", draft.shadow, v -> draft.shadow = v);
        x = addStyleToggle(x, y, "O", draft.outline, v -> draft.outline = v);

        x += STYLE_FONT_BUTTON_X_GAP;
        addRenderableWidget(Button.builder(fontLabel(draft.font), b -> {
                draft.font = cycleFont(draft.font);
                clearWidgets();
                buildWidgets();
            })
            .bounds(x, y, fontButtonWidth(), ROW_HEIGHT - 2)
            .build());

        return scaleField;
    }

    /** @param onChange schreibt den neuen Umschalt-Zustand in den {@link StyleDraft} zurück. */
    private int addStyleToggle(int x, int y, String glyph, boolean active, java.util.function.Consumer<Boolean> onChange) {
        addRenderableWidget(new GlyphButton(x, y, STYLE_TOGGLE_WIDTH, ROW_HEIGHT - 2, Component.literal(glyph),
            active ? 0xFF55FF55 : 0xFF888888, () -> {
                onChange.accept(!active);
                clearWidgets();
                buildWidgets();
            }));
        return x + STYLE_TOGGLE_WIDTH + STYLE_TOGGLE_GAP;
    }

    /** Zyklus über die bekannten eingebauten Vanilla-Schriftarten (siehe {@link #KNOWN_FONTS}) - bewusst NUR diese drei, kein Versuch, Ressourcenpaket-Schriften zu erraten/aufzulisten (Nutzer-Vorgabe, es gibt keine saubere Laufzeit-Registry dafür). */
    private static String cycleFont(String current) {
        for (int i = 0; i < KNOWN_FONTS.length; i++) {
            if (KNOWN_FONTS[i].equals(current)) return KNOWN_FONTS[(i + 1) % KNOWN_FONTS.length];
        }
        return KNOWN_FONTS[0];
    }

    private Component fontLabel(String font) {
        int idx = font.startsWith("minecraft:") ? font.indexOf(':') + 1 : 0;
        String shortName = idx > 0 && idx < font.length() ? font.substring(idx) : font;
        return Component.literal(shortName.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + shortName.substring(1));
    }

    private int fontButtonWidth() {
        int max = 0;
        for (String f : KNOWN_FONTS) max = Math.max(max, this.font.width(fontLabel(f)));
        return max + BUTTON_TEXT_PADDING;
    }

    private static String trimScale(float scale) {
        String s = String.valueOf(scale);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static float parseFloatOr(String text, float fallback) {
        try {
            return Float.parseFloat(text.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private int buildWelcomeSection(int y) {
        PlayerDisplayPreferences.WelcomeDisplayPrefs serverPrefs = ClientDisplayPrefsCache.get().welcome;
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.displayprefs.duration_label"), LABEL_X, y, false));
        EditBox durationField = new EditBox(this.font, DURATION_FIELD_X, y, DURATION_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
        durationField.setValue(welcomeDurationField != null ? welcomeDurationField.getValue() : String.valueOf(displayValue(serverPrefs.durationTicks, welcomeUnit)));
        addRenderableWidget(durationField);
        int unitButtonX = DURATION_FIELD_X + DURATION_FIELD_WIDTH + UNIT_BUTTON_GAP;
        addRenderableWidget(Button.builder(unitLabel(welcomeUnit), b -> {
                PlayerDisplayPreferences.DurationUnit oldUnit = welcomeUnit;
                PlayerDisplayPreferences.DurationUnit newUnit = oldUnit.next();
                int currentTicks = ticksFromDisplay(parseIntOr(durationField.getValue(), displayValue(serverPrefs.durationTicks, oldUnit)), oldUnit);
                welcomeUnit = newUnit;
                durationField.setValue(String.valueOf(displayValue(currentTicks, newUnit)));
                clearWidgets();
                buildWidgets();
            })
            .bounds(unitButtonX, y, unitButtonWidth(), ROW_HEIGHT - 2)
            .build());
        y += ROW_HEIGHT;

        PosFields pos = buildPosRow(y, "areaclaims.displayprefs.position_label", welcomePosXField, welcomePosYField, serverPrefs.posX, serverPrefs.posY);
        y += ROW_HEIGHT;

        EditBox scaleField = buildStyleRow(y, welcomeStyle, welcomeScaleField, serverPrefs.style.scale);
        y += ROW_HEIGHT;

        welcomeDurationField = durationField;
        welcomePosXField = pos.x();
        welcomePosYField = pos.y();
        welcomeScaleField = scaleField;
        return y;
    }

    private Component unitLabel(PlayerDisplayPreferences.DurationUnit unit) {
        return switch (unit) {
            case TICKS -> Component.translatable("areaclaims.displayprefs.unit.ticks");
            case SECONDS -> Component.translatable("areaclaims.displayprefs.unit.seconds");
            case MINUTES -> Component.translatable("areaclaims.displayprefs.unit.minutes");
        };
    }

    private int unitButtonWidth() {
        int max = 0;
        for (PlayerDisplayPreferences.DurationUnit unit : PlayerDisplayPreferences.DurationUnit.values()) {
            max = Math.max(max, this.font.width(unitLabel(unit)));
        }
        return max + BUTTON_TEXT_PADDING;
    }

    private static int displayValue(int ticks, PlayerDisplayPreferences.DurationUnit unit) {
        return switch (unit) {
            case TICKS -> ticks;
            case SECONDS -> Math.max(0, Math.round(ticks / 20f));
            case MINUTES -> Math.max(0, Math.round(ticks / 1200f));
        };
    }

    private static int ticksFromDisplay(int value, PlayerDisplayPreferences.DurationUnit unit) {
        return switch (unit) {
            case TICKS -> value;
            case SECONDS -> value * 20;
            case MINUTES -> value * 1200;
        };
    }

    private static int parseIntOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private void applyStyle(PlayerDisplayPreferences.TextStyle target, StyleDraft draft, EditBox scaleField) {
        target.scale = Math.max(0.1f, parseFloatOr(scaleField.getValue(), 1.0f));
        target.bold = draft.bold;
        target.italic = draft.italic;
        target.underline = draft.underline;
        target.shadow = draft.shadow;
        target.outline = draft.outline;
        target.font = draft.font;
    }

    /**
     * Ein Eintrag für die große Vorschau (Punkt 2, Nachtrag 5) - Platzhaltertext statt echtem
     * Claim-Namen (dieser Screen kennt keinen echten Claim), an der TATSÄCHLICH konfigurierten
     * Position/Größe im REALEN Bildschirmkoordinatensystem. {@code xField}/{@code yField} sind ab
     * Nachtrag 6 (Punkt 4, "Ziehen auch in der großen Vorschau") mit dabei - DIESELBEN lebenden
     * {@link EditBox}-Objekte dieses Screens, damit {@code AreaClaimsDisplayPreviewScreen} beim
     * Ziehen direkt hineinschreiben kann (identisches "live in dieselben Felder zurückschreiben"-
     * Prinzip wie die kleine Vorschau, siehe {@link #collectPreviewDots}).
     */
    record LargePreviewEntry(String text, PlayerDisplayPreferences.TextStyle style, int screenX, int screenY, EditBox xField, EditBox yField) {}

    /**
     * Punkt 2 (Nachtrag 5, "Große Vorschau"): sammelt alle aktuell konfigurierten Text-Einträge -
     * package-private statt public, {@link AreaClaimsDisplayPreviewScreen} liegt im selben Paket.
     * Nutzt die REALEN Bildschirmmaße ({@code realWidth}/{@code realHeight}, vom Aufrufer über
     * {@code Minecraft#getWindow()} ermittelt - siehe dortigen Klassenkommentar für die Begründung,
     * warum NICHT diese Screens eigene {@link FixedScaleScreen}-virtuelle Auflösung genutzt wird).
     */
    java.util.List<LargePreviewEntry> collectLargePreviewEntries(int realWidth, int realHeight) {
        int centerX = realWidth / 2;
        int centerY = realHeight / 2;
        java.util.List<LargePreviewEntry> entries = new java.util.ArrayList<>();
        if (mainPosXField != null) {
            entries.add(new LargePreviewEntry(Component.translatable("areaclaims.displayprefs.preview_sample_main").getString(),
                currentStyle(mainStyle, mainScaleField), centerX + parseIntOr(mainPosXField.getValue(), 0), centerY + parseIntOr(mainPosYField.getValue(), 0),
                mainPosXField, mainPosYField));
            if (mainPermanent && mainPermXField != null) {
                entries.add(new LargePreviewEntry(Component.translatable("areaclaims.displayprefs.preview_sample_main_permanent").getString(),
                    currentStyle(mainStyle, mainScaleField), centerX + parseIntOr(mainPermXField.getValue(), 0), centerY + parseIntOr(mainPermYField.getValue(), 0),
                    mainPermXField, mainPermYField));
            }
        }
        if (subPosXField != null) {
            entries.add(new LargePreviewEntry(Component.translatable("areaclaims.displayprefs.preview_sample_sub").getString(),
                currentStyle(subStyle, subScaleField), centerX + parseIntOr(subPosXField.getValue(), 0), centerY + parseIntOr(subPosYField.getValue(), 0),
                subPosXField, subPosYField));
            if (subPermanent && subPermXField != null) {
                entries.add(new LargePreviewEntry(Component.translatable("areaclaims.displayprefs.preview_sample_sub_permanent").getString(),
                    currentStyle(subStyle, subScaleField), centerX + parseIntOr(subPermXField.getValue(), 0), centerY + parseIntOr(subPermYField.getValue(), 0),
                    subPermXField, subPermYField));
            }
        }
        if (welcomePosXField != null) {
            entries.add(new LargePreviewEntry(Component.translatable("areaclaims.displayprefs.preview_sample_welcome").getString(),
                currentStyle(welcomeStyle, welcomeScaleField), centerX + parseIntOr(welcomePosXField.getValue(), 0), centerY + parseIntOr(welcomePosYField.getValue(), 0),
                welcomePosXField, welcomePosYField));
        }
        return entries;
    }

    /** Package-private statt private - {@link AreaClaimsDisplayPreviewScreen} rechnet beim Ziehen (Punkt 4, Nachtrag 6) dieselbe Bildschirmmitte gegen, um aus einer Mausposition wieder einen rohen X/Y-Versatz zu machen. */
    int centerXFor(int realWidth) {
        return realWidth / 2;
    }

    int centerYFor(int realHeight) {
        return realHeight / 2;
    }

    private PlayerDisplayPreferences.TextStyle currentStyle(StyleDraft draft, EditBox scaleField) {
        PlayerDisplayPreferences.TextStyle style = new PlayerDisplayPreferences.TextStyle();
        applyStyle(style, draft, scaleField);
        return style;
    }

    private void save() {
        PlayerDisplayPreferences prefs = new PlayerDisplayPreferences();
        prefs.mainClaim.durationTicks = Math.max(1, ticksFromDisplay(parseIntOr(mainDurationField.getValue(), 70), mainUnit));
        prefs.mainClaim.durationUnit = mainUnit.name();
        prefs.mainClaim.posX = parseIntOr(mainPosXField.getValue(), 0);
        prefs.mainClaim.posY = parseIntOr(mainPosYField.getValue(), 0);
        prefs.mainClaim.permanent = mainPermanent;
        prefs.mainClaim.permanentPosX = parseIntOr(mainPermXField.getValue(), 0);
        prefs.mainClaim.permanentPosY = parseIntOr(mainPermYField.getValue(), 0);
        prefs.mainClaim.showImageTimed = mainShowImageTimed;
        prefs.mainClaim.showImagePermanent = mainShowImagePermanent;
        prefs.mainClaim.imageZoomPercent = Math.max(1, Math.min(500, parseIntOr(mainZoomField.getValue(), 100)));
        prefs.mainClaim.pixelArtFiltering = mainPixelArt;
        applyStyle(prefs.mainClaim.style, mainStyle, mainScaleField);

        prefs.subClaim.durationTicks = Math.max(1, ticksFromDisplay(parseIntOr(subDurationField.getValue(), 70), subUnit));
        prefs.subClaim.durationUnit = subUnit.name();
        prefs.subClaim.posX = parseIntOr(subPosXField.getValue(), 0);
        prefs.subClaim.posY = parseIntOr(subPosYField.getValue(), 0);
        prefs.subClaim.permanent = subPermanent;
        prefs.subClaim.permanentPosX = parseIntOr(subPermXField.getValue(), 0);
        prefs.subClaim.permanentPosY = parseIntOr(subPermYField.getValue(), 0);
        prefs.subClaim.showImageTimed = subShowImageTimed;
        prefs.subClaim.showImagePermanent = subShowImagePermanent;
        prefs.subClaim.imageZoomPercent = Math.max(1, Math.min(500, parseIntOr(subZoomField.getValue(), 100)));
        prefs.subClaim.pixelArtFiltering = subPixelArt;
        applyStyle(prefs.subClaim.style, subStyle, subScaleField);

        prefs.welcome.durationTicks = Math.max(1, ticksFromDisplay(parseIntOr(welcomeDurationField.getValue(), 60), welcomeUnit));
        prefs.welcome.durationUnit = welcomeUnit.name();
        prefs.welcome.posX = parseIntOr(welcomePosXField.getValue(), 0);
        prefs.welcome.posY = parseIntOr(welcomePosYField.getValue(), 0);
        applyStyle(prefs.welcome.style, welcomeStyle, welcomeScaleField);

        SetDisplayPrefsPacket packet = new SetDisplayPrefsPacket(GSON.toJson(prefs));
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
        onClose();
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderScaled(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, TEXT_COLOR);
        for (LabelJob job : labelJobs) {
            graphics.drawString(this.font, job.text(), job.x(), job.y() + 6, TEXT_COLOR, job.bold());
        }
        renderPositionPreview(graphics);
    }

    /**
     * Punkt 5 (Nachtrag 4): grobe Live-Vorschau, WO die konfigurierten Positionen ungefähr auf dem
     * Bildschirm landen würden - aktualisiert sich JEDEN Frame direkt aus den aktuell in den
     * Eingabefeldern stehenden Werten (auch VOR "Speichern"), nicht erst nach dem Übernehmen.
     * **Judgment call:** "grob" wörtlich genommen - die Vorschau nimmt eine FESTE Referenz-
     * Bildschirmgröße an ({@link #PREVIEW_REFERENCE_WIDTH}x{@link #PREVIEW_REFERENCE_HEIGHT}
     * virtuelle GUI-Pixel), nicht die TATSÄCHLICHE Auflösung des Spielers zum Zeitpunkt des
     * eigentlichen Anzeigens (die kennt dieser Screen nicht, und posX/posY sind ohnehin nur ein
     * Versatz zur Mitte, kein auflösungsabhängiger Wert) - für eine ROHE Lageeinschätzung ("oben
     * links" vs. "Mitte" vs. "unten rechts") reicht das, exakte Pixel-Deckungsgleichheit war nicht
     * gefordert.
     */
    /** Ein einzelner Punkt der kleinen Vorschau - Feldpaar + Anzeige (siehe {@link #collectPreviewDots}). */
    private record PreviewDot(EditBox xField, EditBox yField, String label, int color) {}

    private int previewBoxX() {
        return this.width - PREVIEW_WIDTH - PREVIEW_MARGIN;
    }

    private int previewBoxY() {
        return PREVIEW_MARGIN + 14;
    }

    /** Punkt 1 (Nachtrag 5): dieselbe Liste wird sowohl zum ZEICHNEN als auch zum TREFFERTEST beim Ziehen genutzt (siehe {@link #mouseClickedScaled}) - kann nicht auseinanderlaufen. */
    private java.util.List<PreviewDot> collectPreviewDots() {
        java.util.List<PreviewDot> dots = new java.util.ArrayList<>();
        if (mainPosXField != null) dots.add(new PreviewDot(mainPosXField, mainPosYField, "H", 0xFF55FF55));
        if (mainPermXField != null && mainPermanent) dots.add(new PreviewDot(mainPermXField, mainPermYField, "H*", 0xFF55FF55));
        if (subPosXField != null) dots.add(new PreviewDot(subPosXField, subPosYField, "U", 0xFF5599FF));
        if (subPermXField != null && subPermanent) dots.add(new PreviewDot(subPermXField, subPermYField, "U*", 0xFF5599FF));
        if (welcomePosXField != null) dots.add(new PreviewDot(welcomePosXField, welcomePosYField, "W", 0xFFFFDD55));
        return dots;
    }

    private void renderPositionPreview(GuiGraphics graphics) {
        int x = previewBoxX();
        int y = previewBoxY();
        graphics.drawString(this.font, Component.translatable("areaclaims.displayprefs.preview_label"), x, y - 12, MUTED_PREVIEW_LABEL_COLOR, false);
        graphics.fill(x, y, x + PREVIEW_WIDTH, y + PREVIEW_HEIGHT, PREVIEW_BG_COLOR);
        graphics.renderOutline(x, y, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_BORDER_COLOR);
        int centerX = x + PREVIEW_WIDTH / 2;
        int centerY = y + PREVIEW_HEIGHT / 2;

        for (PreviewDot dot : collectPreviewDots()) {
            previewDot(graphics, centerX, centerY, dot.xField(), dot.yField(), dot.label(), dot.color());
        }
    }

    private void previewDot(GuiGraphics graphics, int centerX, int centerY, EditBox xField, EditBox yField, String label, int color) {
        int[] screenPos = previewScreenPos(centerX, centerY, xField, yField);
        int px = screenPos[0];
        int py = screenPos[1];
        boolean dragging = xField == draggingXField && yField == draggingYField;
        int size = dragging ? PREVIEW_DOT_SIZE + 2 : PREVIEW_DOT_SIZE;
        graphics.fill(px - size / 2, py - size / 2, px + size / 2 + 1, py + size / 2 + 1, color);
        graphics.drawString(this.font, label, px + size, py - 4, color, true);
    }

    /** Rechnet die aktuellen Feld-Rohwerte in eine geklemmte Bildschirm-Position INNERHALB des Vorschau-Kastens um - gemeinsam von {@link #previewDot} (Zeichnen) und {@link #mouseClickedScaled} (Trefftest) genutzt. */
    private int[] previewScreenPos(int centerX, int centerY, EditBox xField, EditBox yField) {
        int rawX = parseIntOr(xField.getValue(), 0);
        int rawY = parseIntOr(yField.getValue(), 0);
        int px = centerX + rawX * PREVIEW_WIDTH / PREVIEW_REFERENCE_WIDTH;
        int py = centerY + rawY * PREVIEW_HEIGHT / PREVIEW_REFERENCE_HEIGHT;
        px = Math.max(centerX - PREVIEW_WIDTH / 2, Math.min(centerX + PREVIEW_WIDTH / 2, px));
        py = Math.max(centerY - PREVIEW_HEIGHT / 2, Math.min(centerY + PREVIEW_HEIGHT / 2, py));
        return new int[] {px, py};
    }

    /**
     * Punkt 1 (Nachtrag 5): die Vorschau ist jetzt ZIEHBAR - ein Klick nahe genug an einem der
     * gezeichneten Punkte (siehe {@link #collectPreviewDots}) startet das Ziehen dieses Punktes;
     * {@link #mouseDraggedScaled} schreibt die neue Position anschließend LIVE in die zugehörigen
     * X/Y-Eingabefelder zurück (exakt dieselben Felder, die auch per Tastatur editierbar bleiben).
     */
    @Override
    protected boolean mouseClickedScaled(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = previewBoxX();
            int y = previewBoxY();
            int centerX = x + PREVIEW_WIDTH / 2;
            int centerY = y + PREVIEW_HEIGHT / 2;
            PreviewDot closest = null;
            double closestDist = PREVIEW_DRAG_HIT_RADIUS + 1;
            for (PreviewDot dot : collectPreviewDots()) {
                int[] pos = previewScreenPos(centerX, centerY, dot.xField(), dot.yField());
                double dist = Math.hypot(mouseX - pos[0], mouseY - pos[1]);
                if (dist <= PREVIEW_DRAG_HIT_RADIUS && dist < closestDist) {
                    closest = dot;
                    closestDist = dist;
                }
            }
            if (closest != null) {
                draggingXField = closest.xField();
                draggingYField = closest.yField();
                return true;
            }
        }
        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Override
    protected boolean mouseDraggedScaled(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingXField != null) {
            int x = previewBoxX();
            int y = previewBoxY();
            int centerX = x + PREVIEW_WIDTH / 2;
            int centerY = y + PREVIEW_HEIGHT / 2;
            double clampedX = Math.max(x, Math.min(x + PREVIEW_WIDTH, mouseX));
            double clampedY = Math.max(y, Math.min(y + PREVIEW_HEIGHT, mouseY));
            int rawX = (int) Math.round((clampedX - centerX) * PREVIEW_REFERENCE_WIDTH / (double) PREVIEW_WIDTH);
            int rawY = (int) Math.round((clampedY - centerY) * PREVIEW_REFERENCE_HEIGHT / (double) PREVIEW_HEIGHT);
            draggingXField.setValue(String.valueOf(rawX));
            draggingYField.setValue(String.valueOf(rawY));
            return true;
        }
        return super.mouseDraggedScaled(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean mouseReleasedScaled(double mouseX, double mouseY, int button) {
        if (draggingXField != null) {
            draggingXField = null;
            draggingYField = null;
            return true;
        }
        return super.mouseReleasedScaled(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
