package com.areaclaims.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-seitiger "gerade aktiv am Punkte-Setzen"-Zustand pro Spieler (ROADMAP.md Phase 7
 * "Klick-zum-Abstecken" - ersetzt den früheren goldene-Hacke-Werkzeug-Modus komplett). Ausgelöst
 * NUR durch Klick auf einen der GUI-Buttons "Erweitern"/"Neuer SubClaim"/"Neuer Claim" (siehe
 * {@code BeginSelectionPacket}) - solange ein Spieler hier aktiv ist, fängt
 * {@code ToolInteractionListener} JEDEN Linksklick (Punkt hinzufügen) und JEDEN Rechtsklick
 * (Auswahl beenden) ab, unabhängig vom gehaltenen Item.
 *
 * <p>Rein im Speicher (wie {@link SelectionManager}, dessen bereits bestehende Punkt-Liste
 * weiterhin als Ablage für die tatsächlich gesetzten Punkte dient - hier wird nur der "WAS wird
 * gerade abgesteckt"-Kontext gehalten).
 */
public final class ActiveSelectionManager {

    /**
     * {@code ADJUST_MAIN}/{@code ADJUST_SUB} (2026-08-18, "Anpassen"-Button): eigener Modus NEBEN
     * {@code EXPAND_MAIN}/{@code EXPAND_SUB} - dort bleibt reines Klick-für-Klick (unabhängige neue
     * Teile), hier wird stattdessen block-weise "gehört zum Claim"/"gehört nicht dazu" umgeschaltet
     * (siehe {@link com.areaclaims.data.ClaimAdjustManager}) - ersetzt den vorherigen, per Nutzer-
     * Feedback verworfenen Gedrückt-Halten-Ziehversuch an der Grenze.
     */
    public enum ActionType { NEW_CLAIM, EXPAND_MAIN, EXPAND_SUB, NEW_SUBCLAIM, ADJUST_MAIN, ADJUST_SUB }

    /**
     * @param targetClaimId bei EXPAND_MAIN/EXPAND_SUB der zu erweiternde Claim; bei NEW_SUBCLAIM
     *                       der Hauptbereich, unter dem der neue Unterbereich entstehen soll; bei
     *                       NEW_CLAIM {@code null}.
     */
    public record ActiveState(ActionType action, UUID targetClaimId) {}

    private static final Map<UUID, ActiveState> ACTIVE = new HashMap<>();

    private ActiveSelectionManager() {}

    public static void start(UUID player, ActionType action, UUID targetClaimId) {
        ACTIVE.put(player, new ActiveState(action, targetClaimId));
    }

    public static boolean isActive(UUID player) {
        return ACTIVE.containsKey(player);
    }

    public static ActiveState get(UUID player) {
        return ACTIVE.get(player);
    }

    public static void stop(UUID player) {
        ACTIVE.remove(player);
    }
}
