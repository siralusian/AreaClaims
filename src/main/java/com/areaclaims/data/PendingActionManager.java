package com.areaclaims.data;

import com.areaclaims.geometry.Vertex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Auswahl merken" (ROADMAP.md Phase 7-8 "Klick-zum-Abstecken + Preisbestätigung") - hält GENAU
 * EINE zu Ende abgesteckte, aber noch nicht bezahlte/bestätigte Aktion pro Spieler, damit ein
 * Spieler die Preisbestätigung verlassen kann (z. B. um erst die nötigen Items/CobbleDollars zu
 * besorgen) und später ohne erneutes Abstecken zur selben Bestätigung zurückkehren kann - klickt
 * er denselben Auslöse-Button erneut ({@code BeginSelectionPacket}), wird diese gespeicherte
 * Auswahl erkannt und direkt die Preisbestätigung gezeigt, statt erneut in den Punkte-Platzier-
 * Modus zu wechseln.
 *
 * <p><b>Bewusste Vereinfachung (Judgment call):</b> NUR EIN Slot pro Spieler, nicht mehrere
 * gleichzeitig für unterschiedliche Aktionen - ein Spieler steckt in der Praxis normalerweise
 * eine Sache nach der anderen ab. Wird eine ANDERE Aktion begonnen/gemerkt, während schon eine
 * gemerkt ist, überschreibt die neue die alte (die alte geht verloren) - ein selteneres
 * Nutzungsmuster (mehrere parallele "auf später verschobene" Absteckungen), das hier bewusst
 * nicht unterstützt wird, um die Zustandsverwaltung einfach zu halten.
 */
public final class PendingActionManager {

    public record PendingAction(
        ActiveSelectionManager.ActionType action,
        UUID targetClaimId,
        List<Vertex> points
    ) {}

    private static final Map<UUID, PendingAction> PENDING = new HashMap<>();

    private PendingActionManager() {}

    public static void remember(UUID player, ActiveSelectionManager.ActionType action, UUID targetClaimId, List<Vertex> points) {
        PENDING.put(player, new PendingAction(action, targetClaimId, points));
    }

    /** @return die gemerkte Aktion NUR, wenn sie zu genau dieser Aktion+Ziel passt - sonst {@code null}. */
    public static PendingAction find(UUID player, ActiveSelectionManager.ActionType action, UUID targetClaimId) {
        PendingAction pending = PENDING.get(player);
        if (pending == null) return null;
        if (pending.action() != action) return null;
        boolean sameTarget = pending.targetClaimId() == null ? targetClaimId == null : pending.targetClaimId().equals(targetClaimId);
        return sameTarget ? pending : null;
    }

    public static PendingAction get(UUID player) {
        return PENDING.get(player);
    }

    public static void clear(UUID player) {
        PENDING.remove(player);
    }
}
