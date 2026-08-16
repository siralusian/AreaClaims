package com.areaclaims.client.gui;

import com.areaclaims.claim.RuleType;
import com.areaclaims.client.data.ClientNetworkUtil;
import com.areaclaims.client.data.ClientServerConfigCache;
import com.areaclaims.economy.PriceConfig;
import com.areaclaims.network.ServerConfigSnapshot;
import com.areaclaims.network.SetPriceRowPacket;
import com.areaclaims.network.SetServerConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OP4-Admin-Screen für Freischaltungsschwellen, Teile-Obergrenze und Preise (ROADMAP.md Phase 6-
 * Nachtrag "Admin-GUI", grundlegend überarbeitet in Phase 6-Nachtrag 2 "Punkt 5/6"). Erreichbar
 * über den "Server-Konfiguration"-Button im Admin-Modus des {@link AreaClaimsEditorScreen}
 * ({@code /areaclaims admin}).
 *
 * <p><b>Punkt 5 (OP-Stufen-Anzeige):</b> die vier Freischaltungsschwellen zeigen jetzt einen
 * Zyklus-Button "Spieler"/"OP 1"/"OP 2"/"OP 3"/"OP 4" statt einer nackten Zahl, "Mindest-OP-Stufe"
 * als Wortlaut wurde aus den Beschriftungen entfernt (Nutzer-Vorgabe) - NUR auf dieser Bildschirm-
 * Beschriftung, nicht in den {@code /areaclaims config}-Befehlsausgaben (die sind kein "Steuerelement"
 * im Sinne der Vorgabe, siehe ROADMAP.md für diese bewusste Abgrenzung).
 *
 * <p><b>Punkt 6 (Preis-UI-Neugestaltung):</b> das alte NONE/ITEM/COBBLE_DOLLARS-Umschalt-Feld ist
 * komplett verschwunden - ein leeres Item-Feld bedeutet jetzt automatisch "kein Preis über diesen
 * Slot" (siehe {@link PriceConfig}). Reihenfolge: CobbleDollars-Betrag (falls verfügbar) IMMER
 * zuerst, danach bis zu {@link PriceConfig#MAX_ITEM_SLOTS} Item-Preis-Felder, ein "+"-Button fügt
 * weitere hinzu. Zwischen je zwei sichtbaren Feldern steht ein UND/ODER-Umschalt-Button. Bewusst
 * ALS EIGENE ZEILE PRO ITEM-SLOT (vertikal gestapelt) statt alles in eine einzige, sehr breite
 * Zeile zu quetschen - der Normalfall (0-1 Item-Preise, siehe {@code draftFromServer}) bleibt dabei
 * genauso kompakt wie vorher (eine Zeile), nur wer tatsächlich mehrere Item-Preise kombiniert,
 * bekommt eine entsprechend höhere Preis-Gruppe - eine bewusste, hier dokumentierte Vereinfachung,
 * um das Layout ohne Live-Test nicht zu riskieren (siehe ROADMAP.md).
 *
 * <p><b>Ungespeicherter Tipptext bleibt jetzt über Strukturänderungen hinweg erhalten</b> (behebt
 * den in der letzten Runde dokumentierten Bug: das alte Preistyp-Umschalten baute die Zeile neu
 * auf und verwarf dabei unangewendeten Tipptext) - siehe {@link #drafts}: jede Preis-Zeile hat
 * einen eigenen, NUR clientseitigen Entwurfszustand, der bei JEDER Struktur-Änderung (Item
 * hinzufügen/entfernen, UND/ODER umschalten) aus den GERADE AKTUELLEN Feldwerten neu befüllt wird,
 * statt vom letzten Server-Stand - bestätigt: es gibt keinen Typ-Umschalt-Button mehr, der diesen
 * Bug hätte reproduzieren können.
 */
public class AreaClaimsServerConfigScreen extends FixedScaleScreen {

    private static final int TITLE_Y = 8;
    private static final int MARGIN = 10;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final boolean HEADER_BOLD = true;
    private static final int BUTTON_TEXT_PADDING = 12;

    private static final int LABEL_X = MARGIN;
    private static final int LABEL_WIDTH = 190;
    private static final int ROW_Y_START = 28;
    private static final int ROW_HEIGHT = 20;

    private static final int FEATURE_BUTTON_X = LABEL_X + LABEL_WIDTH;

    private static final int MAXPARTS_FIELD_WIDTH = 60;
    private static final int MAXPARTS_APPLY_WIDTH = 70;
    private static final int MAXPARTS_GAP = 4;

    private static final int FIELD_X = LABEL_X + LABEL_WIDTH;
    private static final int DOLLARS_LABEL_WIDTH = 130;
    private static final int DOLLARS_FIELD_WIDTH = 90;
    private static final int COMBINATOR_GAP = 4;
    private static final int ITEM_ID_FIELD_WIDTH = 150;
    private static final int ITEM_AMOUNT_FIELD_WIDTH = 60;
    private static final int SMALL_BUTTON_WIDTH = 20;
    private static final int APPLY_BUTTON_WIDTH = 90;
    private static final int PLUS_BUTTON_WIDTH = 90;

    private static final int SECTION_GAP = 10;

    private static final int CLOSE_BUTTON_WIDTH = 80;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 10;
    /** Punkt 3 (Nachtrag 6): "Zurück" sitzt LINKS neben "Schließen", gleiche Höhe, kleiner Abstand dazwischen. */
    private static final int BACK_BUTTON_WIDTH = 80;
    private static final int BACK_BUTTON_GAP = 4;

    // ---------------------------------------------------------------- Scrollbar (Punkt 10, Nachtrag 3)
    // Nutzer-Fund: der Inhalt (Freischaltungsschwellen + Teile-Obergrenze + Sichtweite + Preiszeilen,
    // besonders bei mehreren Regel-Freikaufpreisen mit mehreren Item-Slots) wächst inzwischen über
    // eine Bildschirmhöhe hinaus. Bewusst KEIN hartes Scissor-Clipping (siehe FixedScaleScreen-
    // Klassenkommentar zu toRealX/toRealY-Umrechnung UND die Tatsache, dass Vanillas Screen.render()
    // ALLE Widgets inkl. des unten fixierten "Schließen"-Buttons in EINEM Durchlauf zeichnet - ein
    // korrektes Scissoring, das den Schließen-Button dabei ausspart, bräuchte einen zweiten,
    // separaten Render-Durchlauf und wurde ohne Live-Test als zu riskant eingestuft, siehe
    // Klassenkommentar zu "Layout ohne Live-Test nicht riskieren"). Stattdessen: einfacher,
    // geklemmter Scroll-Offset + sichtbarer Balken rechts - Inhalt kann beim Herunterscrollen in
    // Extremfällen kurz unter der Titelzeile durchscheinen, ist aber immer erreichbar/klickbar.
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_TRACK_COLOR = 0x40FFFFFF;
    private static final int SCROLLBAR_THUMB_COLOR = 0xFFAAAAAA;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 12;
    private static final double SCROLL_PIXELS_PER_NOTCH = 16.0;

    private static class PriceRowDraft {
        String dollarsText = "";
        final List<String> itemIds = new ArrayList<>();
        final List<String> itemAmounts = new ArrayList<>();
        final List<PriceConfig.Combinator> combinators = new ArrayList<>();
        /** Punkt 12 (Nachtrag 3): "X pro N Blöcke"-Teiler-Entwurf - nur für Zeilen mit Teiler gepflegt (siehe {@link #buildPriceRowComponent}, {@code serverDivisor != null}). */
        String divisorText = "1";
    }

    /** Clientseitiger Entwurfszustand je Preis-Zeile (Schlüssel: "block"/"subclaim" oder ein RuleType-Name) - siehe Klassenkommentar. */
    private final Map<String, PriceRowDraft> drafts = new HashMap<>();
    /** Labels werden während buildWidgets() gesammelt und in renderScaled() 1:1 wiederverwendet, statt die Höhen-Berechnung zweimal zu duplizieren. */
    private final List<LabelJob> labelJobs = new ArrayList<>();

    /**
     * Eigenes {@code x} statt fest {@code LABEL_X} (Fund beim Einbauen von Punkt 11/12: das
     * "CobbleDollars-Betrag:"-Sub-Label einer Preiszeile wurde bisher IMMER bei LABEL_X gezeichnet -
     * exakt dieselbe Spalte wie das Zeilen-Hauptlabel ("Claim Preis" usw.) auf DERSELBEN Zeile,
     * was zu deckungsgleich übereinander gezeichnetem Text führte. Da für die neue Teiler-Zeile
     * (Punkt 12) ohnehin ein zweites Sub-Label pro Zeile hinzukommt, wird das hier gleich mit
     * behoben, statt den Fehler ein drittes Mal zu kopieren - siehe FIELD_X-Aufrufstellen unten.
     */
    private record LabelJob(Component text, int x, int y, boolean bold) {
        LabelJob(Component text, int y, boolean bold) {
            this(text, LABEL_X, y, bold);
        }
    }

    /** Aktueller Scroll-Offset in Pixeln (0 = ganz oben) - siehe Klassenkommentar "Scrollbar". */
    private int scrollOffset = 0;
    /** Aus dem VORHERIGEN buildWidgets()-Durchlauf bekannte Obergrenze - wird zu Beginn des nächsten Durchlaufs zum Klemmen von {@link #scrollOffset} verwendet (ein Durchlauf "Verzögerung" bei Inhalt-Höhenänderungen ist unauffällig, da ohnehin bei jeder Interaktion neu gebaut wird). */
    private int maxScrollOffset = 0;

    /** Punkt 3 (Nachtrag 6, "Zurück"-Button): der Editor, aus dem heraus dieser Screen angefordert wurde (siehe {@code ClientServerConfigCache#openFrom}) - {@code null} nur in Randfällen (z. B. veralteter statischer Zustand nach Trennen/Neuverbinden), dann fällt "Zurück" auf dasselbe Verhalten wie "Schließen" zurück. */
    private final net.minecraft.client.gui.screens.Screen parentScreen;

    public AreaClaimsServerConfigScreen(net.minecraft.client.gui.screens.Screen parentScreen) {
        super(Component.translatable("areaclaims.serverconfig.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void initScaled() {
        buildWidgets();
    }

    private int scrollViewportBottom() {
        return this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN - 6;
    }

    private void buildWidgets() {
        labelJobs.clear();
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        ServerConfigSnapshot snapshot = ClientServerConfigCache.get();
        int y = ROW_Y_START - scrollOffset;

        y = buildFeatureRow("createclaim", "areaclaims.serverconfig.feature_createclaim", snapshot.minOpLevelCreateClaim, y);
        y = buildFeatureRow("expandclaim", "areaclaims.serverconfig.feature_expandclaim", snapshot.minOpLevelExpandClaim, y);
        y = buildFeatureRow("buyout", "areaclaims.serverconfig.feature_buyout", snapshot.minOpLevelBuyout, y);

        y += SECTION_GAP;
        y = buildMaxPartsRow(snapshot, y);
        y = buildTintRangeRow(snapshot, y);
        y = buildWildernessMessageRow(snapshot, y);
        y = buildJourneyMapRow(snapshot, y);

        y += SECTION_GAP;
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.serverconfig.prices_header"), y, HEADER_BOLD));
        y += 12;
        y = buildPriceRowComponent("block", Component.translatable("areaclaims.serverconfig.price_block"), snapshot.perBlockPrice, y, snapshot.perBlockPriceDivisor);
        y += 2;
        // Punkt 11 (Nachtrag 3): eigene, unabhängig konfigurierbare Preiszeile NUR für Unterbereiche -
        // gleiche Zeilenform wie "Claim Preis" (siehe Klassenkommentar "Punkt 6"), inkl. eigenem Teiler.
        y = buildPriceRowComponent("subclaim", Component.translatable("areaclaims.serverconfig.price_subclaim"), snapshot.subClaimPrice, y, snapshot.subClaimPriceDivisor);

        for (ServerConfigSnapshot.RuleBuyoutPriceEntry entry : snapshot.ruleBuyoutPrices) {
            y += 2;
            RuleType rule = parseRuleOrNull(entry.rule);
            Component label = rule != null ? rule.translatable() : Component.literal(entry.rule);
            // Regel-Freikaufpreise bekommen bewusst KEINEN Teiler (null) - Punkt 12 (Nachtrag 3) nennt
            // nur "Claim Preis" und "SubClaim Preis" explizit, ein Freikauf ist ein Einmalpreis pro
            // Regel, keine flächenabhängige Größe, für die "pro N Blöcke" überhaupt Sinn ergäbe.
            y = buildPriceRowComponent(entry.rule, label, entry.price, y, null);
        }

        // y ist an dieser Stelle bereits um scrollOffset nach oben verschoben (siehe Startwert
        // oben) - "+ scrollOffset" rechnet das wieder heraus und liefert die tatsächliche,
        // scroll-UNABHÄNGIGE Gesamthöhe des Inhalts (mathematisch invariant, da scrollOffset nur
        // additiv/linear in die Zeilen-Fortschreibung eingeht).
        int contentBottom = y + scrollOffset;
        maxScrollOffset = Math.max(0, contentBottom - scrollViewportBottom());

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.close"), b -> onClose())
            .bounds(this.width - CLOSE_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.back"),
                b -> this.minecraft.setScreen(parentScreen != null ? parentScreen : null))
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

    // ---------------------------------------------------------------- Freischaltungsschwellen (Punkt 5)

    private int featureLevelButtonWidth() {
        int max = 0;
        for (String key : new String[] {"areaclaims.serverconfig.level.player", "areaclaims.serverconfig.level.op1",
                "areaclaims.serverconfig.level.op2", "areaclaims.serverconfig.level.op3", "areaclaims.serverconfig.level.op4"}) {
            max = Math.max(max, this.font.width(Component.translatable(key)));
        }
        return max + BUTTON_TEXT_PADDING;
    }

    private Component levelLabel(int level) {
        return switch (Math.max(0, Math.min(4, level))) {
            case 0 -> Component.translatable("areaclaims.serverconfig.level.player");
            case 1 -> Component.translatable("areaclaims.serverconfig.level.op1");
            case 2 -> Component.translatable("areaclaims.serverconfig.level.op2");
            case 3 -> Component.translatable("areaclaims.serverconfig.level.op3");
            default -> Component.translatable("areaclaims.serverconfig.level.op4");
        };
    }

    private int buildFeatureRow(String featureKey, String labelKey, int currentLevel, int y) {
        labelJobs.add(new LabelJob(Component.translatable(labelKey), y, false));
        int width = featureLevelButtonWidth();
        addRenderableWidget(Button.builder(levelLabel(currentLevel), b -> {
                int next = (currentLevel + 1) % 5;
                sendConfig("feature", featureKey, "", next);
            })
            .bounds(FEATURE_BUTTON_X, y, width, ROW_HEIGHT - 2)
            .build());
        return y + ROW_HEIGHT;
    }

    private int buildMaxPartsRow(ServerConfigSnapshot snapshot, int y) {
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.serverconfig.maxparts"), y, false));
        EditBox field = new EditBox(this.font, FEATURE_BUTTON_X, y, MAXPARTS_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
        field.setValue(String.valueOf(snapshot.maxClaimParts));
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.serverconfig.apply"), b -> {
                int value;
                try {
                    value = Integer.parseInt(field.getValue().trim());
                } catch (NumberFormatException e) {
                    value = snapshot.maxClaimParts;
                }
                sendConfig("maxparts", "", "", value);
            })
            .bounds(FEATURE_BUTTON_X + MAXPARTS_FIELD_WIDTH + MAXPARTS_GAP, y, MAXPARTS_APPLY_WIDTH, ROW_HEIGHT - 2)
            .build());
        return y + ROW_HEIGHT;
    }

    /** Sichtweite der Block-Einfärbung-Anzeige in Chunks (ROADMAP.md Phase 6-Nachtrag 4 - Nutzer-Feedback nach Live-Test, ersetzt den vorher fest verdrahteten 20-Block-Radius). Gleiches Zeilen-Muster wie {@link #buildMaxPartsRow}. */
    private int buildTintRangeRow(ServerConfigSnapshot snapshot, int y) {
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.serverconfig.tintrange"), y, false));
        EditBox field = new EditBox(this.font, FEATURE_BUTTON_X, y, MAXPARTS_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
        field.setValue(String.valueOf(snapshot.tintRangeChunks));
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.serverconfig.apply"), b -> {
                int value;
                try {
                    value = Integer.parseInt(field.getValue().trim());
                } catch (NumberFormatException e) {
                    value = snapshot.tintRangeChunks;
                }
                sendConfig("tintrange", "", "", value);
            })
            .bounds(FEATURE_BUTTON_X + MAXPARTS_FIELD_WIDTH + MAXPARTS_GAP, y, MAXPARTS_APPLY_WIDTH, ROW_HEIGHT - 2)
            .build());
        return y + ROW_HEIGHT;
    }

    /**
     * Punkt 1 (Nachtrag 4): admin-konfigurierbare "Wildnis"-Austritts-Meldung (an/aus + freier
     * Text) - Rückkehr der in Nachtrag 3 ersatzlos gestrichenen Meldung, siehe ClaimEntryListener-
     * Klassenkommentar. Zwei Zeilen: An/Aus-Umschalter, darunter Text-Feld + Übernehmen - gleiches
     * Muster wie {@link #buildMaxPartsRow}/{@link #buildTintRangeRow}, bewusst OHNE Tipptext-Erhalt
     * über Struktur-Änderungen hinweg (der Text ändert seine Zeilen-Struktur nie, anders als die
     * Preis-Zeilen).
     */
    private int buildWildernessMessageRow(ServerConfigSnapshot snapshot, int y) {
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.serverconfig.wilderness_enabled"), y, false));
        addRenderableWidget(Button.builder(
                Component.translatable(snapshot.wildernessMessageEnabled ? "areaclaims.serverconfig.wilderness_on" : "areaclaims.serverconfig.wilderness_off"),
                b -> sendConfig("wildernessmsgenabled", "", "", snapshot.wildernessMessageEnabled ? 0 : 1))
            .bounds(FEATURE_BUTTON_X, y, wildernessToggleWidth(), ROW_HEIGHT - 2)
            .build());
        y += ROW_HEIGHT;

        labelJobs.add(new LabelJob(Component.translatable("areaclaims.serverconfig.wilderness_text"), y, false));
        EditBox field = new EditBox(this.font, FEATURE_BUTTON_X, y, MAXPARTS_FIELD_WIDTH * 2, ROW_HEIGHT - 2, Component.empty());
        field.setValue(snapshot.wildernessMessageText);
        field.setMaxLength(64);
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.serverconfig.apply"), b -> {
                SetServerConfigPacket packet = new SetServerConfigPacket("wildernessmsgtext", "", field.getValue(), 0);
                if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
                    PacketDistributor.sendToServer(packet);
                }
            })
            .bounds(FEATURE_BUTTON_X + MAXPARTS_FIELD_WIDTH * 2 + MAXPARTS_GAP, y, MAXPARTS_APPLY_WIDTH, ROW_HEIGHT - 2)
            .build());
        return y + ROW_HEIGHT;
    }

    private int wildernessToggleWidth() {
        int max = Math.max(this.font.width(Component.translatable("areaclaims.serverconfig.wilderness_on")),
            this.font.width(Component.translatable("areaclaims.serverconfig.wilderness_off")));
        return max + BUTTON_TEXT_PADDING;
    }

    /**
     * JourneyMap-Integration (2026-08-16, Nutzer-Wunsch "Claims auf der Karte sehen"): an/aus-
     * Umschalter nach exakt demselben Muster wie {@link #buildWildernessMessageRow}. Ist JourneyMap
     * auf dem Server gar nicht installiert (siehe {@link ServerConfigSnapshot#journeyMapAvailable}),
     * wird STATT eines Umschalters nur ein informativer, deaktivierter Hinweistext gezeigt - ein
     * Umschalter ohne jede Wirkung wäre irreführend.
     */
    private int buildJourneyMapRow(ServerConfigSnapshot snapshot, int y) {
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.serverconfig.journeymap_enabled"), y, false));
        if (!snapshot.journeyMapAvailable) {
            addRenderableWidget(Button.builder(Component.translatable("areaclaims.serverconfig.journeymap_not_installed"), b -> {})
                .bounds(FEATURE_BUTTON_X, y, journeyMapToggleWidth(), ROW_HEIGHT - 2)
                .build())
                .active = false;
            return y + ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(
                Component.translatable(snapshot.journeyMapIntegrationEnabled ? "areaclaims.serverconfig.journeymap_on" : "areaclaims.serverconfig.journeymap_off"),
                b -> sendConfig("journeymapenabled", "", "", snapshot.journeyMapIntegrationEnabled ? 0 : 1))
            .bounds(FEATURE_BUTTON_X, y, journeyMapToggleWidth(), ROW_HEIGHT - 2)
            .build());
        return y + ROW_HEIGHT;
    }

    private int journeyMapToggleWidth() {
        int max = Math.max(this.font.width(Component.translatable("areaclaims.serverconfig.journeymap_on")),
            Math.max(this.font.width(Component.translatable("areaclaims.serverconfig.journeymap_off")),
                this.font.width(Component.translatable("areaclaims.serverconfig.journeymap_not_installed"))));
        return max + BUTTON_TEXT_PADDING;
    }

    // ---------------------------------------------------------------- Preise (Punkt 6, Teiler: Punkt 12 Nachtrag 3)

    /**
     * @param serverDivisor {@code null} = keine Teiler-Zeile (Regel-Freikaufpreise, siehe Aufrufstelle
     *                      in {@link #buildWidgets}) - sonst der zuletzt vom Server bekannte Teiler,
     *                      nur beim ALLERERSTEN Aufbau dieser Zeile in den Entwurf übernommen (gleiches
     *                      Prinzip wie {@link #draftFromServer} für Dollars/Items).
     */
    private int buildPriceRowComponent(String rowKey, Component label, ServerConfigSnapshot.PriceEntry serverPrice, int y, Integer serverDivisor) {
        labelJobs.add(new LabelJob(label, y, false));
        boolean isNewDraft = !drafts.containsKey(rowKey);
        PriceRowDraft draft = drafts.computeIfAbsent(rowKey, k -> draftFromServer(serverPrice));
        if (isNewDraft && serverDivisor != null) draft.divisorText = String.valueOf(Math.max(1, serverDivisor));
        boolean dollarsAvailable = ClientServerConfigCache.get().cobbleDollarsAvailable;
        ensureCombinatorSize(draft, dollarsAvailable);

        int rowY = y;
        int x = FIELD_X;

        EditBox dollarsField = null;
        if (dollarsAvailable) {
            labelJobs.add(new LabelJob(Component.translatable("areaclaims.serverconfig.dollars_label"), FIELD_X, rowY, false));
            dollarsField = new EditBox(this.font, x + DOLLARS_LABEL_WIDTH, rowY, DOLLARS_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
            dollarsField.setValue(draft.dollarsText);
            addRenderableWidget(dollarsField);
            rowY += ROW_HEIGHT;
        }
        final EditBox dollarsFieldRef = dollarsField;

        List<EditBox> itemIdFields = new ArrayList<>();
        List<EditBox> itemAmountFields = new ArrayList<>();
        int combinatorOffset = dollarsAvailable ? 1 : 0;

        for (int i = 0; i < draft.itemIds.size(); i++) {
            x = FIELD_X;
            boolean hasPredecessor = i > 0 || dollarsAvailable;
            if (hasPredecessor) {
                int comboIndex = combinatorOffset + i - 1;
                addCombinatorButton(x, rowY, draft, comboIndex);
                x += combinatorButtonWidth() + COMBINATOR_GAP;
            }
            EditBox idField = new EditBox(this.font, x, rowY, ITEM_ID_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
            idField.setHint(Component.translatable("areaclaims.serverconfig.item_hint"));
            idField.setValue(draft.itemIds.get(i));
            addRenderableWidget(idField);
            itemIdFields.add(idField);
            x += ITEM_ID_FIELD_WIDTH + COMBINATOR_GAP;

            EditBox amountField = new EditBox(this.font, x, rowY, ITEM_AMOUNT_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
            amountField.setHint(Component.translatable("areaclaims.serverconfig.amount_hint"));
            amountField.setValue(draft.itemAmounts.get(i));
            addRenderableWidget(amountField);
            itemAmountFields.add(amountField);
            x += ITEM_AMOUNT_FIELD_WIDTH + COMBINATOR_GAP;

            if (draft.itemIds.size() > 1) {
                int removeIndex = i;
                addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                        captureDraft(rowKey, draft, dollarsFieldRef, itemIdFields, itemAmountFields);
                        draft.itemIds.remove(removeIndex);
                        draft.itemAmounts.remove(removeIndex);
                        ensureCombinatorSize(draft, dollarsAvailable);
                        clearWidgets();
                        buildWidgets();
                    })
                    .bounds(x, rowY, SMALL_BUTTON_WIDTH, ROW_HEIGHT - 2)
                    .build());
            }
            rowY += ROW_HEIGHT;
        }

        // Letzte Zeile: "+"-Button (falls unter der Obergrenze) und "Übernehmen" - siehe
        // Klassenkommentar zur bewussten Entscheidung, den Übernehmen-Button IMMER unten
        // (statt inline in der ersten Zeile) zu platzieren, um das Sammeln aller Feld-
        // Referenzen einfach und robust zu halten.
        x = FIELD_X;
        if (draft.itemIds.size() < PriceConfig.MAX_ITEM_SLOTS) {
            addRenderableWidget(Button.builder(Component.translatable("areaclaims.serverconfig.add_item"), b -> {
                    captureDraft(rowKey, draft, dollarsFieldRef, itemIdFields, itemAmountFields);
                    draft.itemIds.add("");
                    draft.itemAmounts.add("");
                    ensureCombinatorSize(draft, dollarsAvailable);
                    clearWidgets();
                    buildWidgets();
                })
                .bounds(x, rowY, PLUS_BUTTON_WIDTH, ROW_HEIGHT - 2)
                .build());
            x += PLUS_BUTTON_WIDTH + COMBINATOR_GAP;
        }
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.serverconfig.apply"), b -> {
                captureDraft(rowKey, draft, dollarsFieldRef, itemIdFields, itemAmountFields);
                applyRow(rowKey, draft, dollarsAvailable);
            })
            .bounds(x, rowY, APPLY_BUTTON_WIDTH, ROW_HEIGHT - 2)
            .build());
        rowY += ROW_HEIGHT;

        if (serverDivisor != null) {
            rowY = buildDivisorRow(rowKey, draft, rowY);
        }

        return rowY;
    }

    /** Punkt 12 (Nachtrag 3): eigene "pro wie vielen Blöcken"-Zeile - z. B. "1 Diamant pro 100 Blöcke". Nur für Zeilen, die {@link #buildPriceRowComponent} mit einem nicht-null {@code serverDivisor} aufrufen (Claim-/SubClaim-Preis, siehe {@link #buildWidgets}). */
    private int buildDivisorRow(String rowKey, PriceRowDraft draft, int y) {
        labelJobs.add(new LabelJob(Component.translatable("areaclaims.serverconfig.divisor_label"), FIELD_X, y, false));
        EditBox divisorField = new EditBox(this.font, FIELD_X + DOLLARS_LABEL_WIDTH, y, MAXPARTS_FIELD_WIDTH, ROW_HEIGHT - 2, Component.empty());
        divisorField.setHint(Component.translatable("areaclaims.serverconfig.divisor_hint"));
        divisorField.setValue(draft.divisorText);
        addRenderableWidget(divisorField);
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.serverconfig.apply"), b -> {
                draft.divisorText = divisorField.getValue();
                int value;
                try {
                    value = Integer.parseInt(draft.divisorText.trim());
                } catch (NumberFormatException e) {
                    value = 1;
                }
                sendConfig("pricedivisor", rowKey, "", Math.max(1, value));
            })
            .bounds(FIELD_X + DOLLARS_LABEL_WIDTH + MAXPARTS_FIELD_WIDTH + MAXPARTS_GAP, y, MAXPARTS_APPLY_WIDTH, ROW_HEIGHT - 2)
            .build());
        return y + ROW_HEIGHT;
    }

    private int combinatorButtonWidth() {
        int max = Math.max(
            this.font.width(Component.translatable("areaclaims.serverconfig.combinator.and")),
            this.font.width(Component.translatable("areaclaims.serverconfig.combinator.or")));
        return max + BUTTON_TEXT_PADDING;
    }

    private void addCombinatorButton(int x, int y, PriceRowDraft draft, int index) {
        PriceConfig.Combinator current = draft.combinators.get(index);
        addRenderableWidget(Button.builder(
                Component.translatable(current == PriceConfig.Combinator.AND ? "areaclaims.serverconfig.combinator.and" : "areaclaims.serverconfig.combinator.or"),
                b -> {
                    draft.combinators.set(index, current == PriceConfig.Combinator.AND ? PriceConfig.Combinator.OR : PriceConfig.Combinator.AND);
                    clearWidgets();
                    buildWidgets();
                })
            .bounds(x, y, combinatorButtonWidth(), ROW_HEIGHT - 2)
            .build());
    }

    /** Sorgt dafür, dass {@code draft.combinators} immer genau {@code sichtbare Slots - 1} Einträge hat, neue Lücken werden mit AND aufgefüllt. */
    private void ensureCombinatorSize(PriceRowDraft draft, boolean dollarsAvailable) {
        int visibleSlots = (dollarsAvailable ? 1 : 0) + draft.itemIds.size();
        int needed = Math.max(0, visibleSlots - 1);
        while (draft.combinators.size() < needed) draft.combinators.add(PriceConfig.Combinator.AND);
        while (draft.combinators.size() > needed) draft.combinators.remove(draft.combinators.size() - 1);
    }

    /** Übernimmt die AKTUELL im Bild stehenden Feldwerte in den Entwurf, BEVOR eine Struktur-Änderung die Widgets neu aufbaut - behebt den früheren "Tipptext geht beim Umschalten verloren"-Bug endgültig. */
    private void captureDraft(String rowKey, PriceRowDraft draft, EditBox dollarsField, List<EditBox> itemIdFields, List<EditBox> itemAmountFields) {
        if (dollarsField != null) draft.dollarsText = dollarsField.getValue();
        for (int i = 0; i < itemIdFields.size() && i < draft.itemIds.size(); i++) {
            draft.itemIds.set(i, itemIdFields.get(i).getValue());
            draft.itemAmounts.set(i, itemAmountFields.get(i).getValue());
        }
    }

    private PriceRowDraft draftFromServer(ServerConfigSnapshot.PriceEntry price) {
        PriceRowDraft draft = new PriceRowDraft();
        draft.dollarsText = price.dollars != null && !"0".equals(price.dollars) ? price.dollars : "";
        if (price.items != null) {
            for (ServerConfigSnapshot.ItemEntry item : price.items) {
                draft.itemIds.add(item.itemId);
                draft.itemAmounts.add(String.valueOf(item.amount));
            }
        }
        if (draft.itemIds.isEmpty()) {
            // Immer mindestens EIN (ggf. leeres) Item-Feld anzeigen, sonst gäbe es keinen sichtbaren Startpunkt zum Ausfüllen.
            draft.itemIds.add("");
            draft.itemAmounts.add("");
        }
        if (price.combinators != null) {
            for (String c : price.combinators) {
                try {
                    draft.combinators.add(PriceConfig.Combinator.valueOf(c));
                } catch (IllegalArgumentException ignored) {
                    draft.combinators.add(PriceConfig.Combinator.AND);
                }
            }
        }
        return draft;
    }

    private void applyRow(String rowKey, PriceRowDraft draft, boolean dollarsAvailable) {
        List<String> itemIds = draft.itemIds;
        List<String> itemAmounts = draft.itemAmounts;
        List<String> combinatorNames = new ArrayList<>();
        for (PriceConfig.Combinator c : draft.combinators) combinatorNames.add(c.name());
        String dollarsText = dollarsAvailable ? draft.dollarsText : "";
        String json = SetPriceRowPacket.buildJson(dollarsText, itemIds, itemAmounts, combinatorNames);
        SetPriceRowPacket packet = new SetPriceRowPacket(rowKey, json);
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void sendConfig(String action, String target, String stringArg, long longArg) {
        SetServerConfigPacket packet = new SetServerConfigPacket(action, target, stringArg, longArg);
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
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
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderScaled(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, TEXT_COLOR);

        for (LabelJob job : labelJobs) {
            graphics.drawString(this.font, job.text(), job.x(), job.y() + 6, TEXT_COLOR, job.bold());
        }

        renderScrollbar(graphics);
    }

    /** Punkt 10 (Nachtrag 3): einfacher Scroll-Balken rechts, nur sichtbar wenn der Inhalt tatsächlich über eine Bildschirmhöhe hinausgeht. */
    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScrollOffset <= 0) return;
        int trackTop = ROW_Y_START - 4;
        int trackBottom = scrollViewportBottom();
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int trackX = this.width - MARGIN - SCROLLBAR_WIDTH;
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackBottom, SCROLLBAR_TRACK_COLOR);

        int contentHeight = trackHeight + maxScrollOffset;
        int thumbHeight = Math.max(SCROLLBAR_MIN_THUMB_HEIGHT, trackHeight * trackHeight / contentHeight);
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = trackTop + (maxScrollOffset > 0 ? thumbTravel * scrollOffset / maxScrollOffset : 0);
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLLBAR_THUMB_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
