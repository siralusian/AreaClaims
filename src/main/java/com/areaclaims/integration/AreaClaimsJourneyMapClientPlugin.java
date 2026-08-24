package com.areaclaims.integration;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;

/**
 * Ersetzt das frühere, als kaputt bestätigte {@code IServerOverlayAPI}-Server-Plugin (siehe
 * {@link com.areaclaims.network.ClaimMapSnapshot} für den vollen Untersuchungs-Hintergrund und
 * die neue Architektur). JourneyMap durchsucht auch client-seitig selbst (per Annotation-Scan) alle
 * geladenen Mod-Jars nach {@code @JourneyMapPlugin}-annotierten {@link IClientPlugin}s und ruft
 * {@link #initialize} genau EINMAL beim eigenen Start auf - komplett unabhängig vom
 * Server-Plugin-Scan. Löst intern direkt an {@link JourneyMapClientBridge} weiter, die von
 * {@link com.areaclaims.claim.ClaimManager}/{@code AreaClaimsClient} über den Client-Tick
 * gefütterten {@code ClientClaimMapCache}-Stand tatsächlich als Overlays anzeigt.
 *
 * <p>{@code require = false}: AreaClaims funktioniert vollständig ohne JourneyMap - ein fehlendes
 * JourneyMap darf den Client nicht am Start hindern.
 */
@JourneyMapPlugin(apiVersion = "2.0.0", require = false)
public class AreaClaimsJourneyMapClientPlugin implements IClientPlugin {

    @Override
    public String getModId() {
        return "areaclaims";
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        JourneyMapClientBridge.onInitialized(jmClientApi);
        // Eigener Ein/Aus-Umschalter auf der Vollbildkarte (Nutzer-Vorgabe 2026-08-17, "so wie es
        // Create auch macht") - Registrierung an derselben Stelle wie Create's eigener
        // FULLSCREEN_RENDER_EVENT-Hook (siehe JourneyTrainMap#initialize).
        com.areaclaims.client.MapOverlayToggleWidget.register();
    }
}
