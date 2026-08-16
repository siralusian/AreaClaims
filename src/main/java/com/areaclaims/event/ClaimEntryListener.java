package com.areaclaims.event;

import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.data.AdminViewManager;
import com.areaclaims.data.ClaimShowcaseManager;
import com.areaclaims.data.FeatureConfigManager;
import com.areaclaims.data.SelectionManager;
import com.areaclaims.display.PlayerDisplayPreferences;
import com.areaclaims.display.PlayerDisplayPreferencesManager;
import com.areaclaims.network.EntryDisplaySlotPacket;
import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Betreten-Nachricht (Ortsschild-Stil, siehe ROADMAP.md Phase 3): prüft alle 10 Ticks pro
 * Online-Spieler, in welchem Haupt-/Unterbereich er gerade steht, und meldet bei einem Wechsel den
 * Haupt-/Unterbereichsnamen + Willkommensnachricht. Hysterese: ein neuer Zustand muss zweimal
 * hintereinander (über 2 Prüfintervalle = 20 Ticks) beobachtet werden, bevor er gemeldet wird -
 * verhindert Flackern bei Grenz-Zittern (unverändert seit Phase 3).
 *
 * <p><b>Komplett überarbeitet für ROADMAP.md Phase 7-Nachtrag 3, Punkte 13-15 ("Betreten-Nachricht-
 * Overhaul"):</b> Anzeigedauer/-Position/"dauerhaft anzeigen" sind keine claim-eigenen Einstellungen
 * des BESITZERS mehr, sondern rein persönliche, pro-BETRACHTER geltende Einstellungen (siehe
 * {@link PlayerDisplayPreferences}). Diese Klasse schaut deshalb für JEDEN Spieler in dessen EIGENE
 * {@link PlayerDisplayPreferencesManager}-Einstellungen, um personalisierte
 * {@link EntryDisplaySlotPacket}e zu bauen - Vanillas Titel-/Untertitel-/Actionbar-Pakete werden
 * dafür NICHT MEHR verwendet (siehe {@link EntryDisplaySlotPacket}-Klassenkommentar für die
 * Begründung: Vanilla-Pakete unterstützen keine Positionierung, ein eigenes Overlay übernimmt jetzt
 * BEIDE Fälle - "dauerhaft" UND "zeitgesteuert" - einheitlich).
 *
 * <p>Der Claim-BESITZER behält weiterhin Namenstext, Titelfarbe, Grenzfarbe sowie
 * Willkommenstext+-farbe (unverändert in {@link Claim}); nur die Dauer/Position/"dauerhaft" wurden
 * aus dem Besitzer-Bearbeiten-Popup entfernt (siehe {@code AreaClaimsEditorScreen#buildNameEditPopup}).
 */
public class ClaimEntryListener {

    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final Gson GSON = new Gson();

    private record LocationState(UUID mainId, UUID subId) {
        static final LocationState WILDERNESS = new LocationState(null, null);
    }

    // Nachtrag 6, Punkt 1 (Bug-Fund während der erneuten Resize-Untersuchung, siehe
    // SetClaimImagePacket-Klassenkommentar): STATIC statt Instanzfelder, damit
    // invalidateAll() von außerhalb dieser Klasse erreichbar ist, ohne die einzige registrierte
    // Instanz extra durchreichen zu müssen (es gibt ohnehin nur genau eine, siehe AreaClaims.java).
    private static final Map<UUID, LocationState> lastFired = new HashMap<>();
    private static final Map<UUID, LocationState> pending = new HashMap<>();
    private static final Map<UUID, Integer> pendingStreak = new HashMap<>();

    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % CHECK_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                checkPlayer(player);
            }
        }
    }

    /**
     * Räumt beim Abmelden auch die anderen rein-clientbezogenen In-Memory-Zustände auf (Nutzer-
     * Vorgabe beim "Erweitern v2"-Nachtrag: die persistente Grenzen-Vorschau ({@link
     * ClaimShowcaseManager}) darf für abgemeldete Spieler nicht "hängen bleiben") - alles hier ist
     * bewusst nur im Speicher, geht bei Serverneustart ohnehin verloren, siehe die jeweiligen
     * Klassenkommentare.
     */
    /**
     * JourneyMap-Integration (2026-08-16): ein frisch verbundener Client hat noch KEINE Overlays -
     * die laufenden {@link com.areaclaims.claim.ClaimManager#save}-Pushes erreichen nur bereits
     * verbundene Spieler. Push hier einmalig alle aktuell existierenden Claims gezielt an DIESEN
     * einen Spieler nach (siehe {@code JourneyMapBridge#pushAllTo}).
     */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (com.areaclaims.integration.ModAvailability.isJourneyMapAvailable()
            && event.getEntity() instanceof ServerPlayer player) {
            com.areaclaims.integration.JourneyMapBridge.pushAllTo(player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        lastFired.remove(id);
        pending.remove(id);
        pendingStreak.remove(id);
        ClaimShowcaseManager.stop(id);
        SelectionManager.clear(id);
        AdminViewManager.stop(id);
    }

    private void checkPlayer(ServerPlayer player) {
        String dimension = player.level().dimension().location().toString();
        var pos = player.blockPosition();
        Claim main = ClaimManager.findMainClaimAt(dimension, pos.getX(), pos.getZ());
        LocationState observed = main == null
            ? LocationState.WILDERNESS
            : new LocationState(main.id(), sub(main, dimension, pos.getX(), pos.getZ()));

        UUID uuid = player.getUUID();
        LocationState prevPending = pending.get(uuid);
        if (observed.equals(prevPending)) {
            pendingStreak.merge(uuid, 1, Integer::sum);
        } else {
            pending.put(uuid, observed);
            pendingStreak.put(uuid, 1);
        }

        if (pendingStreak.getOrDefault(uuid, 0) >= 2 && !observed.equals(lastFired.get(uuid))) {
            lastFired.put(uuid, observed);
            fireEntryDisplay(player, main, observed.subId());
        }
    }

    /**
     * Nachtrag 6, Punkt 1: reale Root-Cause-Untersuchung ("Bild-Größenanpassung funktioniert
     * angeblich nicht") ergab, dass der Skalierungs-Algorithmus selbst UND die komplette
     * Speicher-Kette (unabhängig per Byte-Ebene-PNG-IHDR-Parse gegen die tatsächlich geschriebene
     * Datei nachgewiesen, siehe Abschlussbericht) korrekt funktionieren - der eigentliche Bug lag
     * hier: {@link #checkPlayer} feuert {@link #fireEntryDisplay} NUR bei einem tatsächlichen
     * Standort-WECHSEL. Weist ein Besitzer einem Claim ein NEUES Bild zu, während ein Betrachter
     * BEREITS darin steht, ändert sich dessen Standort NICHT - er bekommt also nie ein aktualisiertes
     * {@link EntryDisplaySlotPacket} und sieht weiterhin das alte (oder gar kein) Bild, bis er den
     * Claim verlässt und erneut betritt. Von {@link com.areaclaims.network.SetClaimImagePacket}
     * nach jeder erfolgreichen Zuweisung aufgerufen - löscht bewusst {@link #lastFired} für ALLE
     * Online-Spieler (nicht nur die im betroffenen Claim), damit der nächste reguläre 10-Tick-Check
     * für jeden sofort neu feuert. Einfacher/sicherer als eine präzise "wer steht gerade in diesem
     * einen Claim"-Neuberechnung nachzubauen - kostet im schlimmsten Fall ein paar harmlose,
     * inhaltlich unveränderte Neuversendungen an unbeteiligte Spieler, was bei diesem seltenen,
     * nur admin-/besitzer-ausgelösten Ereignis unproblematisch ist.
     */
    public static void invalidateAll() {
        lastFired.clear();
    }

    private UUID sub(Claim main, String dimension, int x, int z) {
        Claim sub = ClaimManager.findSubClaimAt(main, dimension, x, z);
        return sub == null ? null : sub.id();
    }

    private void fireEntryDisplay(ServerPlayer player, Claim main, UUID subId) {
        PlayerDisplayPreferences prefs = PlayerDisplayPreferencesManager.get(player.getUUID());
        Claim sub = subId != null ? ClaimManager.getById(subId) : null;

        // Unterbereichs-Willkommensnachricht ist spezifischer, hat also Vorrang, falls gesetzt
        // (unverändert übernommene Regel aus der alten fireTitle()-Logik).
        String welcomeText = "";
        int welcomeColor = 0xFFFFFF;
        if (main != null) {
            welcomeText = main.welcomeMessage();
            welcomeColor = main.welcomeColor();
            if (sub != null && !sub.welcomeMessage().isBlank()) {
                welcomeText = sub.welcomeMessage();
                welcomeColor = sub.welcomeColor();
            }
        }

        // Punkt 1 (Nachtrag 4): die in Nachtrag 3 ersatzlos gestrichene "Wildnis"-Austritts-Meldung
        // ist zurück, jetzt admin-konfigurierbar (an/aus + freier Text, siehe FeatureConfigManager).
        // Löst nebenbei den früheren Judgment-Call: der Text kommt jetzt direkt vom Admin als roher
        // String (kein Übersetzungsschlüssel mehr nötig), also kein Auflösungsproblem mehr.
        boolean showWilderness = main == null && FeatureConfigManager.wildernessMessageEnabled();
        String mainText = main != null ? main.name() : (showWilderness ? FeatureConfigManager.wildernessMessageText() : "");
        sendSlot(player, "MAIN", main != null || showWilderness, mainText, main != null ? main.titleColor() : 0xFFFFFF,
            main != null ? main.imageHash() : "", prefs.mainClaim);
        sendSlot(player, "SUB", sub != null, sub != null ? sub.name() : "", sub != null ? sub.titleColor() : 0xFFFFFF,
            sub != null ? sub.imageHash() : "", prefs.subClaim);
        sendWelcomeSlot(player, !welcomeText.isBlank(), welcomeText, welcomeColor, prefs.welcome);
    }

    /**
     * Punkt 2 (Nachtrag 4): schickt IMMER die zeitgesteuerte Phase (normale Position/Dauer) -
     * {@code permanentAfter} sagt dem Client lediglich, ob er NACH Ablauf dieser Dauer selbstständig
     * auf die Dauerhaft-Position umschalten soll, statt auszublenden (siehe EntryDisplaySlotPacket-
     * Klassenkommentar "Punkt 2" für den vollen Ablauf).
     */
    private void sendSlot(ServerPlayer player, String slot, boolean visible, String text, int color, String imageHash, PlayerDisplayPreferences.NameDisplayPrefs prefs) {
        EntryDisplaySlotPacket.Dto dto = new EntryDisplaySlotPacket.Dto();
        dto.slot = slot;
        dto.visible = visible;
        if (visible) {
            dto.text = text;
            dto.color = color;
            dto.posX = prefs.posX;
            dto.posY = prefs.posY;
            dto.durationTicks = Math.max(1, prefs.durationTicks);
            dto.permanentAfter = prefs.permanent;
            dto.permanentPosX = prefs.permanentPosX;
            dto.permanentPosY = prefs.permanentPosY;
            dto.imageHash = imageHash == null ? "" : imageHash;
        }
        send(player, dto);
    }

    private void sendWelcomeSlot(ServerPlayer player, boolean visible, String text, int color, PlayerDisplayPreferences.WelcomeDisplayPrefs prefs) {
        EntryDisplaySlotPacket.Dto dto = new EntryDisplaySlotPacket.Dto();
        dto.slot = "WELCOME";
        dto.visible = visible;
        if (visible) {
            dto.text = text;
            dto.color = color;
            dto.posX = prefs.posX;
            dto.posY = prefs.posY;
            dto.durationTicks = Math.max(1, prefs.durationTicks);
            // Willkommensnachricht hat bewusst KEINEN Dauerhaft-Modus (Nutzer-Vorgabe, siehe
            // PlayerDisplayPreferences-Klassenkommentar) - permanentAfter bleibt false.
        }
        send(player, dto);
    }

    private void send(ServerPlayer player, EntryDisplaySlotPacket.Dto dto) {
        PacketDistributor.sendToPlayer(player, new EntryDisplaySlotPacket(GSON.toJson(dto)));
    }
}
