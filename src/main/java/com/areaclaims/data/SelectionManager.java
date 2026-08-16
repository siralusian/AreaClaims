package com.areaclaims.data;

import com.areaclaims.geometry.PolygonUtil;
import com.areaclaims.geometry.Vertex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Die noch nicht committete Polygon-Auswahl pro Spieler (Werkzeug-Modus, siehe ROADMAP.md
 * Phase 1). Bewusst NUR im Speicher - geht bei Abmelden/Neustart verloren, das ist laut
 * Nutzer-Vorgabe okay ("working selection", keine committeten Daten).
 */
public final class SelectionManager {

    private static final Map<UUID, List<Vertex>> SELECTIONS = new HashMap<>();

    private SelectionManager() {}

    public static List<Vertex> get(UUID player) {
        return SELECTIONS.getOrDefault(player, List.of());
    }

    public static void addPoint(UUID player, int x, int z) {
        SELECTIONS.computeIfAbsent(player, k -> new ArrayList<>()).add(new Vertex(x, z));
    }

    /** @return der entfernte Punkt, oder null wenn die Auswahl bereits leer war. */
    public static Vertex removeLast(UUID player) {
        List<Vertex> list = SELECTIONS.get(player);
        if (list == null || list.isEmpty()) return null;
        return list.remove(list.size() - 1);
    }

    public static void clear(UUID player) {
        SELECTIONS.remove(player);
    }

    public static double area(UUID player) {
        return PolygonUtil.area(get(player));
    }
}
