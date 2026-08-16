package com.areaclaims.client.data;

import com.areaclaims.network.ClaimEditorSnapshot;
import com.google.gson.Gson;

/**
 * Hält den zuletzt vom Server empfangenen {@link ClaimEditorSnapshot} (siehe
 * {@code ClaimSyncPacket}) für den Editor-Screen. Rein clientseitig, kein Zustand wird von hier
 * aus zurückgeschickt - Mutationen laufen über {@code SetRolePacket}/{@code SetRulePacket}, die
 * Antwort kommt als frischer Snapshot zurück und ersetzt den Cache komplett.
 */
public final class ClientClaimCache {

    private static final Gson GSON = new Gson();
    private static ClaimEditorSnapshot snapshot = new ClaimEditorSnapshot();

    // Wird bei jedem update() erhöht - der offene AreaClaimsEditorScreen vergleicht das in tick()
    // gegen seinen zuletzt gesehenen Stand, um zu wissen, wann er seine Widgets neu aufbauen muss,
    // statt jeden Tick unbedingt neu zu bauen.
    private static int generation = 0;

    private ClientClaimCache() {}

    public static void update(String json) {
        try {
            ClaimEditorSnapshot parsed = GSON.fromJson(json, ClaimEditorSnapshot.class);
            snapshot = parsed != null ? parsed : new ClaimEditorSnapshot();
        } catch (RuntimeException e) {
            snapshot = new ClaimEditorSnapshot();
        }
        generation++;
    }

    public static ClaimEditorSnapshot get() {
        return snapshot;
    }

    public static int generation() {
        return generation;
    }
}
