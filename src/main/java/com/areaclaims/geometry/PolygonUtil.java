package com.areaclaims.geometry;

import java.util.List;

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
}
