package com.areaclaims.client.data;

import com.areaclaims.network.PriceConfirmSnapshot;
import com.google.gson.Gson;

/**
 * Hält die zuletzt vom Server empfangene {@link PriceConfirmSnapshot} für das Preisbestätigung-
 * Popup (ROADMAP.md Phase 7-8 "Klick-zum-Abstecken + Preisbestätigung") - gelesen von
 * {@code AreaClaimsEditorScreen} über denselben generation-basierten Neuaufbau-Mechanismus wie
 * {@link ClientClaimCache}. {@link #clear()} wird von {@code OpenEditorPacket#handle} bei JEDEM
 * Öffnen des Editors zuerst aufgerufen - ein {@link com.areaclaims.network.ShowPriceConfirmPacket},
 * das kurz danach eintrifft, überschreibt das wieder (siehe dortigen Klassenkommentar) - so bleibt
 * ein Popup nie über eine normale Editor-Öffnung hinweg "hängen".
 */
public final class ClientPriceConfirmCache {

    private static final Gson GSON = new Gson();
    private static PriceConfirmSnapshot current = null;
    private static int generation = 0;

    private ClientPriceConfirmCache() {}

    public static void update(String json) {
        try {
            current = GSON.fromJson(json, PriceConfirmSnapshot.class);
        } catch (RuntimeException e) {
            current = null;
        }
        generation++;
    }

    /** Optimistisch clientseitig geleert (z. B. sofort nach einem Popup-Button-Klick), bevor die Server-Antwort eintrifft. */
    public static void clear() {
        current = null;
        generation++;
    }

    public static PriceConfirmSnapshot get() {
        return current;
    }

    public static int generation() {
        return generation;
    }
}
