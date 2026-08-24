package com.areaclaims.client.data;

import com.areaclaims.network.ClaimMapSnapshot;
import com.google.gson.Gson;

import java.util.List;

/**
 * Hält den zuletzt vom Server empfangenen {@link ClaimMapSnapshot} (siehe dortigen
 * Klassenkommentar für den vollen Hintergrund) - reine Datenhalter-Klasse, KEIN
 * {@code net.minecraft.client.*}-Import (gleiches Muster wie {@link ClientBoundaryGeometryCache}
 * bzw. CopycatSigns {@code ClientPictureBridge}), damit sie beim Laden auf einem Dedicated Server
 * unbedenklich ist - {@link com.areaclaims.network.ClaimMapSyncPacket#handle} ruft {@link #update}
 * direkt auf. Die eigentliche JourneyMap-Arbeit (braucht die client-only JourneyMap-API) passiert
 * ausschließlich in {@code AreaClaimsJourneyMapClientPlugin}/{@code JourneyMapClientBridge}, die
 * per Client-Tick den {@link #generation()}-Zähler beobachten.
 */
public final class ClientClaimMapCache {

    private static final Gson GSON = new Gson();
    private static List<ClaimMapSnapshot.ClaimEntry> claims = List.of();
    private static int generation = 0;

    private ClientClaimMapCache() {}

    public static void update(String json) {
        try {
            ClaimMapSnapshot parsed = GSON.fromJson(json, ClaimMapSnapshot.class);
            claims = parsed == null || parsed.claims == null ? List.of() : parsed.claims;
        } catch (RuntimeException e) {
            claims = List.of();
        }
        generation++;
    }

    public static List<ClaimMapSnapshot.ClaimEntry> get() {
        return claims;
    }

    public static int generation() {
        return generation;
    }
}
