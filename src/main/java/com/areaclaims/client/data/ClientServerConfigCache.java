package com.areaclaims.client.data;

import com.areaclaims.client.gui.AreaClaimsServerConfigScreen;
import com.areaclaims.network.ServerConfigSnapshot;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Hält den zuletzt vom Server empfangenen {@link ServerConfigSnapshot} für den Admin-Server-
 * Konfigurations-Screen (ROADMAP.md Phase 6-Nachtrag "Admin-GUI"). Bewusst einfacher als
 * {@link ClientClaimCache}: {@link #updateAndOpen} öffnet den Screen bei JEDEM Empfang frisch neu
 * (sowohl beim ersten Öffnen als auch nach jeder Änderung) statt nur den Cache zu aktualisieren
 * und auf einen bereits offenen Screen zu setzen - dieser Screen wird seltener genutzt als der
 * Claim-Editor, die einfachere Lösung war hier vertretbar.
 *
 * <p><b>Punkt 3 (Nachtrag 6, "Zurück"-Button):</b> {@link #parentScreen} ist "klebrig" - wird NUR
 * von {@link #openFrom} gesetzt (aufgerufen, wenn der Screen frisch aus dem Editor heraus
 * angefordert wird), NICHT von jedem {@link #updateAndOpen}-Aufruf. Ohne diese Trennung würde ein
 * innerhalb dieses Screens ausgelöstes Update (z. B. nach dem Anwenden einer Einstellung, die
 * ebenfalls über {@link #updateAndOpen} neu öffnet) den gerade sichtbaren - und damit VERALTETEN,
 * noch-nicht-aktualisierten - Screen als seinen eigenen "Zurück"-Elternteil festhalten.
 */
public final class ClientServerConfigCache {

    private static final Gson GSON = new Gson();
    private static ServerConfigSnapshot snapshot = new ServerConfigSnapshot();
    private static Screen parentScreen;

    private ClientServerConfigCache() {}

    /** Vom Editor (oder jedem anderen "frischen" Öffnen-Auslöser) aufgerufen - merkt sich den AKTUELL sichtbaren Screen als Rückkehrziel. */
    public static void openFrom(Screen parent) {
        parentScreen = parent;
    }

    public static void updateAndOpen(String json) {
        try {
            ServerConfigSnapshot parsed = GSON.fromJson(json, ServerConfigSnapshot.class);
            snapshot = parsed != null ? parsed : new ServerConfigSnapshot();
        } catch (RuntimeException e) {
            snapshot = new ServerConfigSnapshot();
        }
        Minecraft.getInstance().setScreen(new AreaClaimsServerConfigScreen(parentScreen));
    }

    public static ServerConfigSnapshot get() {
        return snapshot;
    }
}
