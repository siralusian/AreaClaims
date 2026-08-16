package com.areaclaims.data;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Ob ein Spieler den GUI-Editor gerade im OP4-Admin-Modus geöffnet hat (ROADMAP.md Phase 6-Nachtrag
 * "Admin-GUI") - dann bekommt er über {@link com.areaclaims.network.ClaimSnapshotBuilder} JEDEN
 * Claim des Servers geschickt statt nur die eigenen/Mitgliedschafts-Claims
 * ({@code ClaimManager.findAllClaims} statt {@code findAccessibleClaims}). Rein im Speicher, wie
 * {@link ClaimShowcaseManager} - kein persistenter Zustand nötig, geht beim Abmelden verloren
 * (siehe {@code ClaimEntryListener#onLogout}).
 */
public final class AdminViewManager {

    private static final Set<UUID> ACTIVE = new HashSet<>();

    private AdminViewManager() {}

    public static boolean isActive(UUID player) {
        return ACTIVE.contains(player);
    }

    public static void setActive(UUID player, boolean active) {
        if (active) ACTIVE.add(player); else ACTIVE.remove(player);
    }

    public static void stop(UUID player) {
        ACTIVE.remove(player);
    }
}
