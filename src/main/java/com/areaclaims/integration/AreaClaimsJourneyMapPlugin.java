package com.areaclaims.integration;

import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;

/**
 * Einstiegspunkt für JourneyMaps Server-API (siehe {@link JourneyMapBridge}-Klassenkommentar für den
 * Gesamtüberblick). JourneyMap durchsucht selbst (per Annotation-Scan) ALLE geladenen Mod-Jars nach
 * {@code @JourneyMapPlugin}-annotierten Klassen wie dieser hier und ruft {@link #initialize} genau
 * EINMAL beim Serverstart auf - unser eigener Code berührt diese Klasse NIE direkt (anders als
 * {@link JourneyMapBridge}, die von {@link com.areaclaims.claim.ClaimManager}/
 * {@code ClaimEntryListener} aus aufgerufen wird, aber selbst immer erst hinter einem
 * {@link ModAvailability#isJourneyMapAvailable()}-Check). Läuft dieser Server OHNE JourneyMap, scannt
 * niemand danach - diese Klasse wird dann schlicht nie geladen, kein {@code NoClassDefFoundError}
 * möglich (JourneyMap-Typen tauchen nur in Methodensignaturen auf, die JVM lädt/verifiziert die
 * Klasse nicht, solange sie niemand referenziert).
 *
 * <p>{@code require = false}: AreaClaims funktioniert vollständig ohne JourneyMap - ein fehlendes
 * JourneyMap darf den Server nicht am Start hindern.
 */
@JourneyMapPlugin(apiVersion = "2.0.0", require = false)
public class AreaClaimsJourneyMapPlugin implements IServerPlugin {

    @Override
    public String getModId() {
        return "areaclaims";
    }

    @Override
    public void initialize(IServerAPI api) {
        JourneyMapBridge.onInitialized(api);
    }
}
