package com.areaclaims.client.data;

import com.areaclaims.geometry.Vertex;
import com.areaclaims.network.AdjustPreviewSnapshot;
import com.areaclaims.network.ShowcaseGeometrySnapshot;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hält die zuletzt vom Server empfangene {@link AdjustPreviewSnapshot} für die "Anpassen"-Block-
 * Einfärbung (siehe {@code com.areaclaims.client.AdjustPreviewTintRenderer}) - gleiches Muster wie
 * {@link ClientBoundaryGeometryCache}, aber bewusst ein EIGENER, kleinerer Cache (höchstens ein
 * eigener Claim statt beliebig vieler), siehe {@link AdjustPreviewSnapshot}-Klassenkommentar.
 *
 * <p>{@link #nearbyClaims()} (Nutzer-Vorgabe 2026-08-19) - Claims ANDERER Spieler in der Nähe,
 * wiederverwendet {@link ClientBoundaryGeometryCache.Entry} (gleiches id/color/parts-Format wie die
 * allgemeine Grenzanzeige).
 */
public final class ClientAdjustPreviewCache {

    private static final Gson GSON = new Gson();
    private static List<List<Vertex>> parts = List.of();
    private static int color = 0xFFFFFF;
    private static List<ClientBoundaryGeometryCache.Entry> nearbyClaims = List.of();
    private static int generation = 0;

    private ClientAdjustPreviewCache() {}

    public static void update(String json) {
        try {
            AdjustPreviewSnapshot parsed = GSON.fromJson(json, AdjustPreviewSnapshot.class);
            if (parsed == null || parsed.parts == null) {
                parts = List.of();
                nearbyClaims = List.of();
            } else {
                color = parsed.color;
                parts = toVertexParts(parsed.parts);
                nearbyClaims = toNearbyEntries(parsed.nearbyClaims);
            }
        } catch (RuntimeException e) {
            parts = List.of();
            nearbyClaims = List.of();
        }
        generation++;
    }

    private static List<List<Vertex>> toVertexParts(List<List<int[]>> raw) {
        List<List<Vertex>> result = new ArrayList<>();
        for (List<int[]> part : raw) {
            result.add(part.stream().map(p -> new Vertex(p[0], p[1])).collect(Collectors.toList()));
        }
        return result;
    }

    private static List<ClientBoundaryGeometryCache.Entry> toNearbyEntries(List<ShowcaseGeometrySnapshot.ClaimGeometry> raw) {
        if (raw == null) return List.of();
        List<ClientBoundaryGeometryCache.Entry> result = new ArrayList<>();
        for (ShowcaseGeometrySnapshot.ClaimGeometry g : raw) {
            if (g.parts == null) continue;
            List<List<Vertex>> entryParts = g.parts.stream()
                .map(part -> part.stream().map(p -> new Vertex(p[0], p[1])).collect(Collectors.toList()))
                .collect(Collectors.toList());
            result.add(new ClientBoundaryGeometryCache.Entry(g.id, g.color, entryParts));
        }
        return result;
    }

    public static boolean isActive() {
        return !parts.isEmpty();
    }

    public static boolean containsPoint(double x, double z) {
        for (List<Vertex> part : parts) {
            if (com.areaclaims.geometry.PolygonUtil.pointInPolygon(part, x, z)) return true;
        }
        return false;
    }

    public static int color() {
        return color;
    }

    public static List<ClientBoundaryGeometryCache.Entry> nearbyClaims() {
        return nearbyClaims;
    }

    public static int generation() {
        return generation;
    }
}
