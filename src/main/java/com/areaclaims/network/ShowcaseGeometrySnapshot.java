package com.areaclaims.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Reine Gson-Transport-DTO (gleicher Stil wie {@link ClaimEditorSnapshot}/{@link ServerConfigSnapshot})
 * für den Block-Einfärbung-Anzeigemodus (ROADMAP.md Phase 6-Nachtrag 2, Punkt 7
 * "Block-Einfärbung") - im Gegensatz zum reinen Partikel-Modus (der komplett serverseitig
 * gerendert wird, siehe ToolInteractionListener) muss der Client hier die tatsächliche
 * Polygon-Geometrie kennen, um selbst zu entscheiden, welche Blöcke eingefärbt werden. Wird NUR
 * verschickt, wenn der TINT-Anzeigemodus des jeweiligen Spielers aktiv ist (siehe
 * ShowcaseGeometrySnapshotBuilder) - im Partikel-Modus ist die Liste immer leer, das räumt den
 * clientseitigen Cache automatisch, wenn zurückgeschaltet wird.
 */
public class ShowcaseGeometrySnapshot {

    public static class ClaimGeometry {
        public String id;
        /** {@code Claim#effectiveBoundaryColor()} - 0xRRGGBB. */
        public int color;
        /** Liste unabhängiger Teile, jeder Teil eine Liste von [x,z]-Punkten - gleiches Format wie ClaimManager's Persistenz-DTO. */
        public List<List<int[]>> parts;
    }

    public List<ClaimGeometry> claims = new ArrayList<>();
    /** {@code FeatureConfigManager#tintRangeChunks() * 16} - siehe ROADMAP.md Phase 6-Nachtrag 4 (ersetzt den vorher fest verdrahteten 20-Block-Radius im Client-Renderer). */
    public int rangeBlocks = 32;
}
