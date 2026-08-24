package com.areaclaims.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-seitiger "gerade Blöcke am An-/Abwählen"-Zustand pro Spieler (Nutzer-Vorgabe, 2026-08-18,
 * "Anpassen"-Button - ersetzt den zuvor per Nutzer-Feedback verworfenen Gedrückt-Halten-Ziehversuch
 * an der Grenze): "man drückt auf einen Block und dieser wechselt seinen Zustand von 'Gehört nicht
 * zum Claim' zu 'Gehört zum Claim'" (und umgekehrt). Bewusst EINFACHER als der vorherige Versuch -
 * jeder Linksklick ist ein einzelner, sofortiger Zustandswechsel EINER Block-Spalte, kein
 * Gedrückt-Halten/Loslassen-Timing nötig.
 *
 * <p>Ein Block-Spalte-Klick "togglet": ist die Spalte schon im {@code toggled}-Set, wird sie
 * WIEDER ENTFERNT (macht den Klick rückgängig, zurück zum Original-Zustand des Claims) statt
 * ein zweites Mal umzuschalten - klickt man also zweimal auf denselben Block, passiert am Ende
 * nichts. Das Set enthält NUR die Spalten, die sich vom aktuellen, gespeicherten Claim-Zustand
 * unterscheiden sollen - {@link com.areaclaims.geometry.PolygonUtil#applyToggleSetToParts} baut
 * daraus die tatsächliche Vorschau-/Ziel-Geometrie.
 */
public final class ClaimAdjustManager {

    public static final class AdjustState {
        public final UUID claimId;
        private final Set<Long> toggled = new HashSet<>();

        private AdjustState(UUID claimId) {
            this.claimId = claimId;
        }

        public boolean isEmpty() {
            return toggled.isEmpty();
        }

        public List<int[]> toColumnList() {
            List<int[]> columns = new ArrayList<>(toggled.size());
            for (long key : toggled) columns.add(new int[] {unpackX(key), unpackZ(key)});
            return columns;
        }
    }

    private static final Map<UUID, AdjustState> ACTIVE = new HashMap<>();

    private ClaimAdjustManager() {}

    public static boolean isActive(UUID player) {
        return ACTIVE.containsKey(player);
    }

    public static AdjustState get(UUID player) {
        return ACTIVE.get(player);
    }

    public static void start(UUID player, UUID claimId) {
        ACTIVE.put(player, new AdjustState(claimId));
    }

    public static void stop(UUID player) {
        ACTIVE.remove(player);
    }

    /** Schaltet die angeklickte Spalte im übergebenen Zustand um - siehe Klassenkommentar. */
    public static void toggle(AdjustState state, int x, int z) {
        long key = pack(x, z);
        if (!state.toggled.remove(key)) state.toggled.add(key);
    }

    /** Setzt eine Spalte OHNE Umschalt-Logik direkt (für {@code StakingService#resumePending} - stellt einen gemerkten Zustand 1:1 wieder her, statt ihn erneut zu "toggeln"). */
    public static void restore(AdjustState state, int x, int z) {
        state.toggled.add(pack(x, z));
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
