package com.areaclaims.geometry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Geometrie-Hilfsfunktionen für die reinen XZ-Grundriss-Polygone der Claims (kein Y-Anteil,
 * siehe ROADMAP.md Phase 1). Alle Methoden sind bewusst einfach gehalten (Ray-Casting,
 * Shoelace-Formel, paarweise Kanten-Schnitttests) - für die Punktzahlen, die Spieler per Hand
 * mit der Hacke abstecken (typischerweise << 100 Punkte), ist die O(n^2)-Kantenprüfung
 * unproblematisch.
 */
public final class PolygonUtil {

    private PolygonUtil() {}

    /**
     * Nachtrag 7, Punkt 3 ("zu viele Punkte -> stiller Abbruch beim Rechtsklick"): harte Obergrenze
     * für die Punktzahl EINES Polygon-Teils - vorher gab es GAR KEINE Grenze (siehe Klassenkommentar
     * "typischerweise &lt;&lt; 100 Punkte" - eine reine Annahme, nie tatsächlich erzwungen). Root-
     * Cause-Recherche: kein einzelner "Rauchender Colt" gefunden, sondern mehrere Risiken, die alle
     * bei sehr hohen Punktzahlen zusammenkommen - (a) die O(n^2)-Kanten-Prüfung hier wird spürbar
     * langsam, (b) {@code ShowcaseGeometrySyncPacket}/{@code ClaimSyncPacket} kodieren ihre Nutzlast
     * über Vanillas {@code ByteBufCodecs#STRING_UTF8}, dessen Standard-Obergrenze (32767 Zeichen,
     * dekompiliert verifiziert) bei genug Punkten in der JSON-Geometrie überschritten würde - ein
     * Netty-{@code EncoderException} beim Kodieren wird NICHT an den Spieler gemeldet, das Paket
     * wird einfach verworfen ("stiller Abbruch" exakt wie gemeldet). Statt eine einzelne Ursache zu
     * erraten, wird hier die Punktzahl selbst großzügig, aber verbindlich gedeckelt (500 - deutlich
     * über dem "typischen" Gebrauch, aber weit unterhalb dessen, was die O(n^2)-Prüfung oder die
     * Paketgröße gefährden würde) UND zusätzlich (siehe {@code ShowcaseGeometrySyncPacket}/
     * {@code ClaimSyncPacket}) die Paket-Obergrenze selbst großzügig angehoben - doppelte
     * Absicherung statt Vermutung. Durchgesetzt SOWOHL beim Klicken selbst (sofortiges Feedback,
     * siehe {@code ToolInteractionListener}) ALS AUCH nochmal bei der Commit-Validierung (siehe
     * {@code ClaimManager#validateNewMainClaim}/{@code #validateNewSubClaim}/{@code #validateAddPart}) -
     * Verteidigung in der Tiefe, falls Punkte je auf einem anderen Weg als dem Live-Klicken
     * hereinkommen (z. B. {@code StakingService#resumePending}).
     */
    public static final int MAX_POINTS_PER_PART = 500;

    /** Ray-Casting-Punkt-in-Polygon-Test. Punkte exakt auf der Kante gelten als "innen". */
    public static boolean pointInPolygon(List<Vertex> polygon, double x, double z) {
        if (polygon.size() < 3) return false;
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vertex a = polygon.get(i);
            Vertex b = polygon.get(j);
            if (onSegment(a, b, x, z)) return true;
            boolean crosses = ((a.z() > z) != (b.z() > z))
                && (x < (double) (b.x() - a.x()) * (z - a.z()) / (double) (b.z() - a.z()) + a.x());
            if (crosses) inside = !inside;
        }
        return inside;
    }

    private static boolean onSegment(Vertex a, Vertex b, double x, double z) {
        double cross = (b.x() - a.x()) * (z - a.z()) - (b.z() - a.z()) * (x - a.x());
        if (Math.abs(cross) > 1e-6) return false;
        double dot = (x - a.x()) * (x - b.x()) + (z - a.z()) * (z - b.z());
        return dot <= 1e-6;
    }

    /** Shoelace-Formel, Ergebnis in Blocks^2 (immer positiv, unabhängig von Wicklungsrichtung). */
    public static double area(List<Vertex> polygon) {
        if (polygon.size() < 3) return 0.0;
        double sum = 0.0;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            Vertex a = polygon.get(i);
            Vertex b = polygon.get((i + 1) % n);
            sum += (double) a.x() * b.z() - (double) b.x() * a.z();
        }
        return Math.abs(sum) / 2.0;
    }

    /**
     * Nutzer-Fund (2026-08-19): "Erweitern" berechnete den Preis bisher aus der GESAMTEN neu
     * gezogenen Fläche ({@link #area}) - zog ein Spieler ein größeres Rechteck, das seinen
     * bestehenden Claim mit umschließt (statt exakt nur den neuen Rand), zahlte er für Blöcke, die
     * er bereits besaß, ein zweites Mal. Zählt stattdessen NUR die Block-Spalten (Mittelpunkte) der
     * neuen Fläche, die in KEINEM der {@code existingParts} liegen - Rasterung über die Bounding-Box
     * analog zu {@link #applyToggleSetToParts}, liefert direkt eine Block-Anzahl (kein
     * {@code Math.ceil} auf einer Kontinuum-Fläche nötig).
     */
    public static int netNewBlockCount(List<Vertex> newPolygon, List<List<Vertex>> existingParts) {
        if (newPolygon.size() < 3) return 0;
        int[] box = boundingBox(newPolygon);
        int count = 0;
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                double cx = x + 0.5, cz = z + 0.5;
                if (!pointInPolygon(newPolygon, cx, cz)) continue;
                if (isInAnyPart(existingParts, cx, cz)) continue;
                count++;
            }
        }
        return count;
    }

    private static boolean isInAnyPart(List<List<Vertex>> parts, double x, double z) {
        for (List<Vertex> part : parts) {
            if (pointInPolygon(part, x, z)) return true;
        }
        return false;
    }

    /** {minX, minZ, maxX, maxZ}. */
    public static int[] boundingBox(List<Vertex> polygon) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Vertex v : polygon) {
            minX = Math.min(minX, v.x());
            minZ = Math.min(minZ, v.z());
            maxX = Math.max(maxX, v.x());
            maxZ = Math.max(maxZ, v.z());
        }
        return new int[] {minX, minZ, maxX, maxZ};
    }

    /** Klassischer Orientierungstest für die Segment-Schnitt-Prüfung. */
    private static int orientation(Vertex p, Vertex q, Vertex r) {
        long val = (long) (q.z() - p.z()) * (r.x() - q.x()) - (long) (q.x() - p.x()) * (r.z() - q.z());
        if (val == 0) return 0;
        return val > 0 ? 1 : 2;
    }

    private static boolean onSegmentStrict(Vertex p, Vertex q, Vertex r) {
        return q.x() <= Math.max(p.x(), r.x()) && q.x() >= Math.min(p.x(), r.x())
            && q.z() <= Math.max(p.z(), r.z()) && q.z() >= Math.min(p.z(), r.z());
    }

    /** Segment-Segment-Schnittprüfung (inklusive kollinearer Überlappung). */
    public static boolean segmentsIntersect(Vertex p1, Vertex q1, Vertex p2, Vertex q2) {
        int o1 = orientation(p1, q1, p2);
        int o2 = orientation(p1, q1, q2);
        int o3 = orientation(p2, q2, p1);
        int o4 = orientation(p2, q2, q1);

        if (o1 != o2 && o3 != o4) return true;

        if (o1 == 0 && onSegmentStrict(p1, p2, q1)) return true;
        if (o2 == 0 && onSegmentStrict(p1, q2, q1)) return true;
        if (o3 == 0 && onSegmentStrict(p2, p1, q2)) return true;
        if (o4 == 0 && onSegmentStrict(p2, q1, q2)) return true;

        return false;
    }

    /**
     * Grobe Selbstüberschneidungsprüfung: true, wenn sich zwei nicht direkt benachbarte Kanten
     * schneiden. Bewusst einfach (siehe Klassenkommentar) - reicht für die vom Spieler per Hand
     * gesetzten Polygone locker aus.
     */
    public static boolean isSelfIntersecting(List<Vertex> polygon) {
        int n = polygon.size();
        if (n < 4) return false;
        for (int i = 0; i < n; i++) {
            Vertex a1 = polygon.get(i);
            Vertex a2 = polygon.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                if (j == i) continue;
                // Direkt benachbarte Kanten (teilen sich einen Eckpunkt) werden übersprungen.
                if (j == i + 1 || (i == 0 && j == n - 1)) continue;
                Vertex b1 = polygon.get(j);
                Vertex b2 = polygon.get((j + 1) % n);
                if (segmentsIntersect(a1, a2, b1, b2)) return true;
            }
        }
        return false;
    }

    /**
     * true, wenn sich die beiden Polygone (Bounding-Box-Vorprüfung + Vertex-in-Polygon in beide
     * Richtungen + Kanten-Kreuzungstest) irgendwie überlappen. Bewusst konservativ/grob für
     * Randfälle (z. B. reine Kantenberührung ohne echte Flächenüberlappung zählt hier auch als
     * Überlappung) - siehe ROADMAP.md Phase 2.
     */
    public static boolean polygonsOverlap(List<Vertex> a, List<Vertex> b) {
        int[] boxA = boundingBox(a);
        int[] boxB = boundingBox(b);
        if (boxA[2] < boxB[0] || boxB[2] < boxA[0] || boxA[3] < boxB[1] || boxB[3] < boxA[1]) {
            return false;
        }
        for (Vertex v : a) {
            if (pointInPolygon(b, v.x(), v.z())) return true;
        }
        for (Vertex v : b) {
            if (pointInPolygon(a, v.x(), v.z())) return true;
        }
        int na = a.size(), nb = b.size();
        for (int i = 0; i < na; i++) {
            Vertex a1 = a.get(i);
            Vertex a2 = a.get((i + 1) % na);
            for (int j = 0; j < nb; j++) {
                Vertex b1 = b.get(j);
                Vertex b2 = b.get((j + 1) % nb);
                if (segmentsIntersect(a1, a2, b1, b2)) return true;
            }
        }
        return false;
    }

    /**
     * true, wenn das "inner"-Polygon komplett innerhalb von "outer" liegt: alle Eckpunkte von
     * inner liegen in outer UND keine Kante von inner kreuzt eine Kante von outer. Bekannter
     * grober Randfall (siehe ROADMAP.md Phase 2): eine Kantenberührung ohne echtes Kreuzen wird
     * nicht gesondert behandelt - für den Start bewusst nicht überentwickelt.
     */
    public static boolean polygonContainsPolygon(List<Vertex> outer, List<Vertex> inner) {
        for (Vertex v : inner) {
            if (!pointInPolygon(outer, v.x(), v.z())) return false;
        }
        int no = outer.size(), ni = inner.size();
        for (int i = 0; i < no; i++) {
            Vertex o1 = outer.get(i);
            Vertex o2 = outer.get((i + 1) % no);
            for (int j = 0; j < ni; j++) {
                Vertex i1 = inner.get(j);
                Vertex i2 = inner.get((j + 1) % ni);
                if (segmentsIntersect(o1, o2, i1, i2)) return false;
            }
        }
        return true;
    }

    /**
     * Nutzer-Vorschlag (2026-08-18, "Grenzanzeige der Teile ist nicht sauber wenn sie sich
     * berühren"): statt Kanten der EINZELNEN Polygon-Teile gegeneinander zu prüfen (fehleranfällig
     * bei Teil-Überschneidungen, siehe frühere {@code isEdgeCoveredByOtherPart}-Heuristik in
     * {@code ToolInteractionListener}, die nur EINEN Punkt pro Kante abtastete), wird hier - exakt
     * wie {@code BoundaryTintRenderer}s Block-Einfärbung - pro Block-SPALTE geprüft, ob sie zu
     * IRGENDEINEM Teil gehört ({@link #pointInPolygon} auf den jeweiligen Spalten-Mittelpunkt), und
     * die Grenze exakt dort gezogen, wo sich dieser Zugehörigkeits-Status zum direkten Nachbarn
     * (X- oder Z-Richtung) ändert. Liefert die AUSSENGRENZE(N) der Vereinigung aller Teile als
     * ungeordnete Menge von Gitter-Kanten (Länge 1, achsenparallel) - funktioniert automatisch
     * korrekt für beliebig viele sich berührende/überlappende/getrennte Teile, ganz ohne
     * Sonderfall-Behandlung pro Konfiguration.
     *
     * <p>Bewusst auf die gemeinsame Bounding-Box aller Teile begrenzt (nicht die ganze Welt) - für
     * die realistische Claim-Größenordnung (Punktzahl pro Teil ohnehin auf {@link
     * #MAX_POINTS_PER_PART} gedeckelt) unproblematisch, gleiche Größenordnungs-Annahme wie der Rest
     * dieser Klasse (siehe Klassenkommentar).
     *
     * @return Liste von {@code {x1, z1, x2, z2}}-Gitterpunktpaaren, je ein Segment der Länge 1.
     */
    public static List<int[]> extractBoundaryEdges(List<List<Vertex>> parts) {
        List<int[]> edges = new ArrayList<>();
        List<List<Vertex>> real = parts.stream().filter(p -> p.size() >= 3).toList();
        if (real.isEmpty()) return edges;

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (List<Vertex> part : real) {
            int[] box = boundingBox(part);
            minX = Math.min(minX, box[0]);
            minZ = Math.min(minZ, box[1]);
            maxX = Math.max(maxX, box[2]);
            maxZ = Math.max(maxZ, box[3]);
        }
        int width = maxX - minX;
        int height = maxZ - minZ;
        if (width <= 0 || height <= 0) return edges;

        boolean[][] inside = rasterize(real, minX, minZ, width, height);
        return boundaryEdgesOfGrid(inside, minX, minZ, width, height);
    }

    private static boolean[][] rasterize(List<List<Vertex>> real, int minX, int minZ, int width, int height) {
        boolean[][] inside = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                double cx = minX + x + 0.5;
                double cz = minZ + z + 0.5;
                for (List<Vertex> part : real) {
                    if (pointInPolygon(part, cx, cz)) {
                        inside[x][z] = true;
                        break;
                    }
                }
            }
        }
        return inside;
    }

    /** Reine Gitter->Grenzkanten-Extraktion (ohne Rasterisierung) - herausgelöst aus {@link #extractBoundaryEdges}, damit {@link #applyRectToParts} dieselbe Logik auf einem NACH der Rasterisierung noch veränderten Gitter (Rechteck hinzugefügt/entfernt) wiederverwenden kann. */
    private static List<int[]> boundaryEdgesOfGrid(boolean[][] inside, int minX, int minZ, int width, int height) {
        List<int[]> edges = new ArrayList<>();
        // Vertikale Grenzsegmente: zwischen Spalte x und x+1 (Segment liegt bei Gitter-X = minX+x+1).
        for (int x = -1; x < width; x++) {
            boolean left, right;
            for (int z = 0; z < height; z++) {
                left = x >= 0 && inside[x][z];
                right = x + 1 < width && inside[x + 1][z];
                if (left != right) {
                    int gx = minX + x + 1;
                    edges.add(new int[] {gx, minZ + z, gx, minZ + z + 1});
                }
            }
        }
        // Horizontale Grenzsegmente: zwischen Spalte z und z+1 (Segment liegt bei Gitter-Z = minZ+z+1).
        for (int x = 0; x < width; x++) {
            boolean below, above;
            for (int z = -1; z < height; z++) {
                below = z >= 0 && inside[x][z];
                above = z + 1 < height && inside[x][z + 1];
                if (below != above) {
                    int gz = minZ + z + 1;
                    edges.add(new int[] {minX + x, gz, minX + x + 1, gz});
                }
            }
        }
        return edges;
    }

    /**
     * Nutzer-Vorgabe (2026-08-18, "Anpassen"-Button - ersetzt den zuvor per Nutzer-Feedback
     * verworfenen Gedrückt-Halten-Ziehversuch an der Grenze): baut aus den bestehenden Teilen die
     * neue(n) Kontur(en), nachdem für JEDE Block-Spalte in {@code toggledColumns} ({@code {x,z}}-
     * Paare) ihr Zugehörigkeits-Status UMGEDREHT wurde (gehörte sie dazu, gehört sie danach nicht
     * mehr, und umgekehrt) - beliebig verstreute Einzel-Spalten, exakt das block-weise Umschalten,
     * das der Spieler per Linksklick auslöst (siehe {@code ClaimAdjustManager}). Nutzt dieselbe
     * Rasterisierung wie {@link #extractBoundaryEdges}, nur mit den umgeschalteten Spalten als
     * zusätzlicher Mutation auf demselben Gitter, plus {@link #chainEdgesIntoLoops} für eine ECHTE
     * geschlossene Kontur (nötig, da das Ergebnis als neue {@code parts}-Liste des Claims
     * gespeichert wird, nicht nur zur Anzeige dient). Kann mehrere Schleifen liefern (z. B. wenn
     * das Entfernen eine bisher zusammenhängende Fläche in zwei trennt) - das ist im mehrteiligen
     * Claim-Modell (siehe {@code Claim}-Klassenkommentar) ausdrücklich zulässig. Leere {@code
     * toggledColumns} liefert die Original-Teile UNVERÄNDERT zurück (kein unnötiges Gitter-Redraw,
     * das die Original-Eckpunkte durch eine Stufen-Kontur ersetzen würde).
     */
    public static List<List<Vertex>> applyToggleSetToParts(List<List<Vertex>> parts, List<int[]> toggledColumns) {
        List<List<Vertex>> real = parts.stream().filter(p -> p.size() >= 3).toList();
        if (toggledColumns.isEmpty()) return real;

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (List<Vertex> part : real) {
            int[] box = boundingBox(part);
            minX = Math.min(minX, box[0]);
            minZ = Math.min(minZ, box[1]);
            maxX = Math.max(maxX, box[2]);
            maxZ = Math.max(maxZ, box[3]);
        }
        for (int[] c : toggledColumns) {
            minX = Math.min(minX, c[0]);
            minZ = Math.min(minZ, c[1]);
            maxX = Math.max(maxX, c[0] + 1);
            maxZ = Math.max(maxZ, c[1] + 1);
        }
        int width = maxX - minX;
        int height = maxZ - minZ;
        if (width <= 0 || height <= 0) return List.of();

        boolean[][] inside = rasterize(real, minX, minZ, width, height);
        for (int[] c : toggledColumns) {
            int gx = c[0] - minX, gz = c[1] - minZ;
            if (gx >= 0 && gx < width && gz >= 0 && gz < height) inside[gx][gz] = !inside[gx][gz];
        }

        List<int[]> edges = boundaryEdgesOfGrid(inside, minX, minZ, width, height);
        return chainEdgesIntoLoops(edges);
    }

    /**
     * Verkettet die ungeordneten Gitter-Kanten aus {@link #extractBoundaryEdges} zu geschlossenen
     * Schleifen (für Systeme, die - anders als eine reine Partikel-Linie - eine ECHTE geschlossene
     * Polygon-Kontur brauchen, z. B. ein gefülltes Kartenoverlay). Läuft die Adjazenz jedes
     * Gitterpunkts ab (Breitensuche-artiges Verfolgen, kein Backtracking nötig, da jede Kante genau
     * einmal "verbraucht" wird) - liefert typischerweise EINE Schleife pro zusammenhängender
     * Teile-Gruppe (plus ggf. weitere Schleifen für Löcher, falls die Teile-Anordnung welche
     * einschließt).
     *
     * <p>Bekannter Grenzfall (bewusst nicht weiter behandelt, siehe Klassenkommentar "bewusst
     * einfach gehalten"): berühren sich zwei Teile NUR an einer einzelnen Diagonale (ein
     * Gitterpunkt mit vier anliegenden Kanten statt der üblichen zwei), wählt die Verfolgung an
     * dieser Stelle einfach die nächste verfügbare Kante - bei handgezeichneten, meist
     * kanten-berührenden Claim-Formen ein seltener Fall.
     */
    public static List<List<Vertex>> chainEdgesIntoLoops(List<int[]> edges) {
        List<List<Vertex>> loops = new ArrayList<>();
        if (edges.isEmpty()) return loops;

        Map<Long, Deque<Long>> adjacency = new HashMap<>();
        for (int[] edge : edges) {
            long a = pack(edge[0], edge[1]);
            long b = pack(edge[2], edge[3]);
            adjacency.computeIfAbsent(a, k -> new ArrayDeque<>()).add(b);
            adjacency.computeIfAbsent(b, k -> new ArrayDeque<>()).add(a);
        }

        Set<Long> startCandidates = new LinkedHashSet<>(adjacency.keySet());
        for (Long start : startCandidates) {
            Deque<Long> startOptions = adjacency.get(start);
            while (startOptions != null && !startOptions.isEmpty()) {
                List<Vertex> loop = new ArrayList<>();
                long current = start;
                long previous = -1;
                loop.add(unpack(current));
                boolean closed = false;
                while (true) {
                    Deque<Long> options = adjacency.get(current);
                    if (options == null || options.isEmpty()) break;
                    Long next = null;
                    for (Long candidate : options) {
                        if (options.size() == 1 || candidate != previous) {
                            next = candidate;
                            break;
                        }
                    }
                    if (next == null) next = options.peekFirst();
                    options.remove(next);
                    Deque<Long> reverse = adjacency.get(next);
                    if (reverse != null) reverse.remove(current);
                    previous = current;
                    current = next;
                    loop.add(unpack(current));
                    if (current == start) {
                        closed = true;
                        break;
                    }
                }
                if (closed && loop.size() >= 4) loops.add(loop);
                startOptions = adjacency.get(start);
            }
        }
        return loops;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static Vertex unpack(long packed) {
        return new Vertex((int) (packed >> 32), (int) packed);
    }
}
