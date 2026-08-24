package com.areaclaims.integration;

import com.areaclaims.client.data.ClientClaimMapCache;
import com.areaclaims.client.data.ClientMapOverlayPrefs;
import com.areaclaims.geometry.PolygonUtil;
import com.areaclaims.geometry.Vertex;
import com.areaclaims.network.ClaimMapSnapshot;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-seitiges Gegenstück zu {@link AreaClaimsJourneyMapClientPlugin} - baut aus dem zuletzt
 * empfangenen {@link ClientClaimMapCache} echte JourneyMap-{@link PolygonOverlay}-Objekte und
 * zeigt sie direkt über {@link IClientAPI#show} an. Nutzer-Vorgabe (2026-08-17, "Create schafft
 * das doch auch"): nach dem Vorbild von Create's {@code JourneyTrainMap} (eigene Datenübertragung
 * + rein lokaler Aufruf der Client-API) statt der als kaputt bestätigten
 * {@code IServerOverlayAPI}-Push-Route - siehe {@link com.areaclaims.network.ClaimMapSnapshot}-
 * Klassenkommentar für den vollen Hintergrund der Untersuchung.
 *
 * <p><b>Isolations-Muster</b> (wie das frühere, inzwischen entfernte {@code JourneyMapBridge}):
 * referenziert echte JourneyMap-Client-API-Typen - wird aber ausschließlich von
 * {@link AreaClaimsJourneyMapClientPlugin}
 * berührt, die JourneyMap selbst nur dann per Reflection instanziiert, wenn JourneyMap tatsächlich
 * (client-seitig) geladen ist. Kein manueller Verfügbarkeits-Check nötig, siehe dortigen
 * Klassenkommentar.
 */
public final class JourneyMapClientBridge {

    private static final String MOD_ID = "areaclaims";

    private static volatile IClientAPI clientApi;
    private static int lastAppliedGeneration = -1;

    private JourneyMapClientBridge() {}

    /** Von {@link AreaClaimsJourneyMapClientPlugin#initialize} aufgerufen, sobald JourneyMap selbst bereit ist. */
    public static void onInitialized(IClientAPI api) {
        clientApi = api;
        lastAppliedGeneration = -1; // erzwingt einen frischen Aufbau, sobald Daten vorliegen
    }

    /**
     * Von einem Client-Tick-Hook aufgerufen (siehe {@code AreaClaimsClient}) - baut die Overlays
     * NUR neu, wenn sich {@link ClientClaimMapCache#generation()} seit dem letzten Aufruf
     * geändert hat (billiger Vergleich statt teurem Neuaufbau bei jedem Tick).
     */
    public static void tick() {
        if (clientApi == null) return;
        int generation = ClientClaimMapCache.generation();
        if (generation == lastAppliedGeneration) return;
        lastAppliedGeneration = generation;
        refresh();
    }

    /**
     * Von {@code MapOverlayToggleWidget} aufgerufen, sobald der Spieler seinen lokalen Ein/Aus-
     * Schalter betätigt hat - erzwingt einen sofortigen Neuaufbau, unabhängig vom
     * {@link ClientClaimMapCache#generation()}-Stand (der sich durch das reine Umschalten ja nicht
     * ändert).
     */
    public static void onOverlayPrefChanged() {
        if (clientApi == null) return;
        refresh();
    }

    /** "Alles neu" statt Einzel-Diffing - gleiche Begründung wie im früheren {@code JourneyMapBridge#refreshAll}. */
    private static void refresh() {
        try {
            clientApi.removeAll(MOD_ID);
        } catch (Exception ignored) {}
        if (!ClientMapOverlayPrefs.showOnMap()) return;
        for (ClaimMapSnapshot.ClaimEntry claim : ClientClaimMapCache.get()) {
            for (PolygonOverlay overlay : toOverlays(claim)) {
                try {
                    clientApi.show(overlay);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Nutzer-Vorgabe (2026-08-18, "Grenzanzeige der Teile ist nicht sauber wenn sie sich
     * berühren"): statt naiv EIN Polygon-Overlay je rohem Claim-Teil zu bauen (führte bei
     * sich berührenden Teilen zu zwei optisch getrennten Kartenflächen, obwohl sie für Preis/
     * Teile-Zählung längst als EINE zusammenhängende Fläche behandelt werden, siehe
     * {@link com.areaclaims.claim.ClaimManager}), wird hier - exakt wie die Partikel-Grenzanzeige
     * in {@code ToolInteractionListener} - zuerst {@link PolygonUtil#extractBoundaryEdges} auf ALLE
     * Teile zusammen angewendet und das Ergebnis über {@link PolygonUtil#chainEdgesIntoLoops} zu
     * geschlossenen Konturen verkettet. Sich berührende/überlappende Teile ergeben so automatisch
     * EINE gemeinsame Kontur (= ein Overlay), komplett getrennte Teile weiterhin je eine eigene.
     */
    private static List<PolygonOverlay> toOverlays(ClaimMapSnapshot.ClaimEntry claim) {
        List<PolygonOverlay> overlays = new ArrayList<>();
        ResourceLocation dimId = ResourceLocation.tryParse(claim.dimension);
        if (dimId == null) return overlays;
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);

        int color = claim.color & 0xFFFFFF;
        ShapeProperties shape = new ShapeProperties()
            .setFillColor(color)
            .setFillOpacity(claim.isMain ? 0.15f : 0.30f)
            .setStrokeColor(color)
            .setStrokeWidth(claim.isMain ? 2.0f : 1.5f)
            .setStrokeOpacity(0.8f);

        List<List<Vertex>> parts = new ArrayList<>();
        for (List<int[]> part : claim.parts) {
            if (part.size() < 3) continue;
            List<Vertex> vertices = new ArrayList<>(part.size());
            for (int[] p : part) vertices.add(new Vertex(p[0], p[1]));
            parts.add(vertices);
        }
        if (parts.isEmpty()) return overlays;

        List<List<Vertex>> loops = PolygonUtil.chainEdgesIntoLoops(PolygonUtil.extractBoundaryEdges(parts));
        int loopIndex = 0;
        for (List<Vertex> loop : loops) {
            // Y ist für ein Top-Down-Kartenoverlay irrelevant (Claims sind reine XZ-Grundrisse) -
            // 64 nur als neutraler, sicher innerhalb der Weltbauhöhe liegender Wert.
            List<BlockPos> points = new ArrayList<>(loop.size());
            for (Vertex v : loop) points.add(new BlockPos(v.x(), 64, v.z()));

            PolygonOverlay overlay = new PolygonOverlay(MOD_ID, dimKey, shape, new MapPolygon(points));
            overlay.setOverlayGroupName(claim.id + "_" + loopIndex);
            overlay.setDisplayOrder(claim.isMain ? 0 : 1);
            overlay.setLabel(claim.name);
            overlay.setTitle(claim.name);
            overlays.add(overlay);
            loopIndex++;
        }
        return overlays;
    }
}
