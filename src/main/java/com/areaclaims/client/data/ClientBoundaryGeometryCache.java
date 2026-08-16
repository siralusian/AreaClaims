package com.areaclaims.client.data;

import com.areaclaims.geometry.Vertex;
import com.areaclaims.network.ShowcaseGeometrySnapshot;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hält die zuletzt vom Server empfangene {@link ShowcaseGeometrySnapshot} für den TINT-
 * Anzeigemodus (ROADMAP.md Phase 6-Nachtrag 2, Punkt 7 "Block-Einfärbung") - gelesen von
 * {@code com.areaclaims.client.BoundaryTintRenderer}. Wandelt die rohen [x,z]-Punkt-Arrays direkt
 * in {@link Vertex}-Listen um, damit der Renderer dieselbe {@code PolygonUtil.pointInPolygon}-
 * Logik wie der Server nutzen kann (rein geometrische Klassen, keine Server-/Level-Abhängigkeit -
 * unproblematisch, auch clientseitig zu verwenden).
 */
public final class ClientBoundaryGeometryCache {

    public static class Entry {
        public final String id;
        public final int color;
        public final List<List<Vertex>> parts;

        Entry(String id, int color, List<List<Vertex>> parts) {
            this.id = id;
            this.color = color;
            this.parts = parts;
        }

        public boolean containsPoint(double x, double z) {
            for (List<Vertex> part : parts) {
                if (com.areaclaims.geometry.PolygonUtil.pointInPolygon(part, x, z)) return true;
            }
            return false;
        }
    }

    private static final Gson GSON = new Gson();
    private static List<Entry> claims = List.of();
    private static int rangeBlocks = 32;
    private static int generation = 0;

    private ClientBoundaryGeometryCache() {}

    public static void update(String json) {
        try {
            ShowcaseGeometrySnapshot parsed = GSON.fromJson(json, ShowcaseGeometrySnapshot.class);
            claims = parsed == null ? List.of() : toEntries(parsed);
            rangeBlocks = parsed == null ? 32 : parsed.rangeBlocks;
        } catch (RuntimeException e) {
            claims = List.of();
        }
        generation++;
    }

    public static int rangeBlocks() {
        return rangeBlocks;
    }

    private static List<Entry> toEntries(ShowcaseGeometrySnapshot snapshot) {
        List<Entry> result = new ArrayList<>();
        if (snapshot.claims == null) return result;
        for (ShowcaseGeometrySnapshot.ClaimGeometry g : snapshot.claims) {
            if (g.parts == null) continue;
            List<List<Vertex>> parts = g.parts.stream()
                .map(part -> part.stream().map(p -> new Vertex(p[0], p[1])).collect(Collectors.toList()))
                .collect(Collectors.toList());
            result.add(new Entry(g.id, g.color, parts));
        }
        return result;
    }

    public static List<Entry> get() {
        return claims;
    }

    public static int generation() {
        return generation;
    }
}
