package com.areaclaims.claim;

import com.areaclaims.data.FeatureConfigManager;
import com.areaclaims.geometry.PolygonUtil;
import com.areaclaims.geometry.Vertex;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hält alle Claims im Speicher, persistiert sie als Gson-JSON im Weltordner (gleicher Stil wie
 * {@link com.areaclaims.data.ToolStateManager}) und pflegt einen groben Chunk-Index, damit
 * Punkt-Abfragen (z. B. bei jedem Block-Event) nicht jedes Mal alle Claims der Welt durchgehen
 * müssen - siehe ROADMAP.md Phase 2.
 *
 * <p><b>Mehrteilige Claims (Nutzer-Vorgabe, ROADMAP.md Phase 5-Nachtrag "Erweitern v2"):</b> ein
 * Claim besteht aus einer Liste unabhängiger Polygon-{@code parts} (siehe {@link Claim}), nicht
 * mehr aus einem einzelnen Polygon. Der Chunk-Index indiziert JEDEN Teil einzeln; Punktabfragen
 * prüfen alle indizierten Kandidaten-Teile.
 */
public class ClaimManager {

    // --- Persistenz-DTOs: bewusst mit String-Schlüsseln statt UUID/Enum-Maps, um Gsons
    // Standard-Map-Serialisierung nicht zu strapazieren (siehe Klassenkommentar-Erwägung in
    // ROADMAP.md-Arbeitsnotizen) ---
    private static class RuleSettingDto {
        boolean enabled;
        String minRole;
    }

    private static class ClaimDto {
        String id;
        String name;
        String owner;
        String dimension;
        String parentId;
        long createdAt;
        /** Liste unabhängiger Teile, jeder Teil eine Liste von [x,z]-Punkten - siehe Klassenkommentar. */
        List<List<int[]>> parts;
        Map<String, String> members = new HashMap<>();
        Map<String, RuleSettingDto> rules = new HashMap<>();
        List<String> boughtOutRules = new ArrayList<>();
        int titleColor = 0xFFFFFF;
        int titleDurationTicks = 70;
        String welcomeMessage = "";
        int welcomeColor = 0xFFFFFF;
        int welcomeDurationTicks = 60;
        int boundaryColor = 0xFFFFFF;
        boolean linkBoundaryColorToTitle = true;
        String imageHash = "";
    }

    private static class Data {
        List<ClaimDto> claims = new ArrayList<>();
    }

    /** Grober Chunk-Index-Schlüssel (Dimension + Chunk-Koordinate). */
    private record ChunkKey(String dimension, int chunkX, int chunkZ) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path dataFile;
    private static final Map<UUID, Claim> claimsById = new HashMap<>();
    private static final Map<ChunkKey, List<UUID>> chunkIndex = new HashMap<>();

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("areaclaims_claims.json");
        load();
    }

    // ---------------------------------------------------------------- Abfragen

    /** Alle Claims (Haupt- UND Unterbereiche), von denen IRGENDEIN Teil den gegebenen XZ-Punkt enthält. */
    public static List<Claim> findClaimsAt(String dimension, int x, int z) {
        ChunkKey key = new ChunkKey(dimension, x >> 4, z >> 4);
        List<UUID> candidates = chunkIndex.get(key);
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<Claim> result = new ArrayList<>();
        for (UUID id : candidates) {
            Claim claim = claimsById.get(id);
            if (claim != null && claim.containsPoint(x, z)) {
                result.add(claim);
            }
        }
        return result;
    }

    /**
     * Alle Claims, deren Chunk-Index Teile im gegebenen Block-Rechteck [minX,maxX]x[minZ,maxZ]
     * berührt (Chunk-Granularität, kein exaktes Polygon-Clipping - ausreichend für eine
     * Nachbarschafts-Voranzeige, siehe {@code StakingService#sendAdjustPreviewParts}, Nutzer-
     * Vorgabe 2026-08-19: fremde Claims während "Anpassen" farbig am Boden mit anzeigen).
     */
    public static List<Claim> findClaimsNear(String dimension, int minX, int minZ, int maxX, int maxZ) {
        java.util.Set<UUID> seen = new java.util.LinkedHashSet<>();
        List<Claim> result = new ArrayList<>();
        int minChunkX = minX >> 4, maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4, maxChunkZ = maxZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                List<UUID> candidates = chunkIndex.get(new ChunkKey(dimension, cx, cz));
                if (candidates == null) continue;
                for (UUID id : candidates) {
                    if (!seen.add(id)) continue;
                    Claim claim = claimsById.get(id);
                    if (claim != null) result.add(claim);
                }
            }
        }
        return result;
    }

    public static Claim findMainClaimAt(String dimension, int x, int z) {
        for (Claim c : findClaimsAt(dimension, x, z)) {
            if (c.isMain()) return c;
        }
        return null;
    }

    /** Unterbereich am Punkt, der Kind von {@code main} ist (falls vorhanden). */
    public static Claim findSubClaimAt(Claim main, String dimension, int x, int z) {
        for (Claim c : findClaimsAt(dimension, x, z)) {
            if (!c.isMain() && main.id().equals(c.parentId())) return c;
        }
        return null;
    }

    public static Claim getById(UUID id) {
        return claimsById.get(id);
    }

    /**
     * Für den GUI-Editor (Phase 5-Nachtrag "3-Spalten-Layout"): alle Hauptbereiche, die der
     * Spieler entweder BESITZT oder in deren Mitgliederliste er steht (irgendeine Rolle) - PLUS
     * alle Unterbereiche dieser Hauptbereiche (unabhängig davon, ob der Spieler auf dem
     * Unterbereich SELBST eingetragen ist - wer den Hauptbereich sehen darf, darf auch dessen
     * volle Unterbereichs-Liste sehen). Bewusst NICHT "alle Claims des Servers" - das wäre eine
     * eigene Admin-Übersichtsfunktion, die hier nicht gefordert wurde.
     */
    public static List<Claim> findAccessibleClaims(UUID player) {
        List<Claim> result = new ArrayList<>();
        java.util.Set<UUID> accessibleMainIds = new java.util.HashSet<>();
        for (Claim c : claimsById.values()) {
            if (!c.isMain()) continue;
            if (c.owner().equals(player) || c.members().containsKey(player)) {
                result.add(c);
                accessibleMainIds.add(c.id());
            }
        }
        for (Claim c : claimsById.values()) {
            if (!c.isMain() && c.parentId() != null && accessibleMainIds.contains(c.parentId())) {
                result.add(c);
            }
        }
        return result;
    }

    /** Für Befehle: eigene Claims nach Name (Admins dürfen alle Claims nach Name finden). */
    public static Claim findMainClaimByNameForEditor(UUID caller, boolean callerIsAdmin, String name) {
        for (Claim c : claimsById.values()) {
            if (!c.isMain() || !c.name().equalsIgnoreCase(name)) continue;
            if (callerIsAdmin || c.owner().equals(caller)) return c;
        }
        return null;
    }

    /** Wie {@link #findMainClaimByNameForEditor}, aber für einen Unterbereich unterhalb eines bestimmten Hauptbereichs (ROADMAP.md Phase 6-Nachtrag "Betreten-Nachricht-Feintuning" - Befehle können Unterbereiche nicht direkt nach Name finden, brauchen den Hauptbereich als Kontext). */
    public static Claim findSubClaimByNameForEditor(UUID caller, boolean callerIsAdmin, String mainName, String subName) {
        Claim main = findMainClaimByNameForEditor(caller, callerIsAdmin, mainName);
        if (main == null) return null;
        for (Claim c : claimsById.values()) {
            if (!c.isMain() && main.id().equals(c.parentId()) && c.name().equalsIgnoreCase(subName)) return c;
        }
        return null;
    }

    /** NUR für den OP4-Admin-Modus des GUI-Editors (ROADMAP.md Phase 6-Nachtrag "Admin-GUI") - wirklich JEDER Claim des Servers, unabhängig von Besitz/Mitgliedschaft. */
    public static List<Claim> findAllClaims() {
        return new ArrayList<>(claimsById.values());
    }

    /**
     * Namens-Eindeutigkeit für Umbenennen (ROADMAP.md Phase 6-Nachtrag 2, Punkt 3 "Claim
     * umbenennen") - bewusst NUR beim Umbenennen geprüft, NICHT rückwirkend bei der Erstellung
     * (Nutzer-Vorgabe war nur "Claim umbenennen", kein umfassenderer Namens-Eindeutigkeits-Fix -
     * bereits bestehende Namensdopplungen aus der Erstellung bleiben also möglich, siehe
     * ROADMAP.md). Für Hauptbereiche: kein anderer Hauptbereich DESSELBEN Besitzers mit gleichem
     * Namen (Groß-/Kleinschreibung ignoriert). Für Unterbereiche: kein anderer Unterbereich
     * DESSELBEN Elternbereichs mit gleichem Namen.
     */
    public static boolean isSiblingNameTaken(Claim claim, String newName) {
        for (Claim other : claimsById.values()) {
            if (other.id().equals(claim.id())) continue;
            if (!other.name().equalsIgnoreCase(newName)) continue;
            if (claim.isMain()) {
                if (other.isMain() && other.owner().equals(claim.owner())) return true;
            } else {
                if (!other.isMain() && claim.parentId().equals(other.parentId())) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Erstellung

    public enum CreateResult {
        OK, TOO_FEW_POINTS, TOO_MANY_POINTS, SELF_INTERSECTING, OVERLAPS_EXISTING, NAME_TAKEN, OUTSIDE_PARENT
    }

    public record CreateOutcome(CreateResult result, Claim claim) {}

    public static CreateOutcome createMainClaim(String name, UUID owner, String dimension, List<Vertex> firstPart) {
        CreateResult check = validateNewMainClaim(owner, dimension, firstPart);
        if (check != CreateResult.OK) return new CreateOutcome(check, null);
        Claim claim = Claim.withSinglePart(UUID.randomUUID(), name, owner, dimension, null, firstPart, System.currentTimeMillis());
        register(claim);
        save();
        return new CreateOutcome(CreateResult.OK, claim);
    }

    /**
     * Reine Prüfung OHNE Mutation - "würde createMainClaim mit diesen Punkten erfolgreich sein?"
     * (ROADMAP.md Phase 7-8 "Klick-zum-Abstecken + Preisbestätigung"): der neue Klick-zum-Abstecken-
     * Ablauf muss die Geometrie VOR der Preisbestätigung validieren können, OHNE den Claim schon
     * anzulegen (der Claim soll erst nach bestätigter/kostenloser Bezahlung tatsächlich existieren -
     * sonst würde ein "Auswahl merken"-Claim anderen Spielern schon als belegt erscheinen, obwohl
     * er noch gar nicht bezahlt ist). {@code createMainClaim} ruft intern NUR diese Methode für die
     * Prüfung auf, damit beide Pfade garantiert dieselbe Logik nutzen.
     */
    public static CreateResult validateNewMainClaim(UUID owner, String dimension, List<Vertex> firstPart) {
        if (firstPart.size() < 3) return CreateResult.TOO_FEW_POINTS;
        // Nachtrag 7, Punkt 3: Verteidigung in der Tiefe - siehe PolygonUtil#MAX_POINTS_PER_PART.
        if (firstPart.size() > PolygonUtil.MAX_POINTS_PER_PART) return CreateResult.TOO_MANY_POINTS;
        if (PolygonUtil.isSelfIntersecting(firstPart)) return CreateResult.SELF_INTERSECTING;
        for (Claim other : claimsById.values()) {
            if (!other.isMain() || !other.dimension().equals(dimension) || other.owner().equals(owner)) continue;
            if (overlapsAnyPart(firstPart, other)) return CreateResult.OVERLAPS_EXISTING;
        }
        return CreateResult.OK;
    }

    /**
     * Nächster freier Name der Form "Claim N" für diesen Besitzer (ROADMAP.md Phase 7 "Klick-zum-
     * Abstecken" - Punkt 7: "Neuer Claim" braucht KEINEN Namen-Eingabe-Schritt mehr, wird
     * automatisch benannt und kann danach über die Umbenennen-Funktion (Phase 6-Nachtrag 2, Punkt
     * 1/3) frei umbenannt werden). Startet bei 1, überspringt bereits vergebene Nummern.
     */
    public static String nextAutoMainClaimName(UUID owner) {
        int n = 1;
        while (true) {
            String candidate = "Claim " + n;
            boolean taken = false;
            for (Claim c : claimsById.values()) {
                if (c.isMain() && c.owner().equals(owner) && c.name().equalsIgnoreCase(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return candidate;
            n++;
        }
    }

    /** Wie {@link #nextAutoMainClaimName}, aber für Unterbereiche eines bestimmten Hauptbereichs ("SubClaim N"). */
    public static String nextAutoSubClaimName(Claim parent) {
        int n = 1;
        while (true) {
            String candidate = "SubClaim " + n;
            boolean taken = false;
            for (Claim c : claimsById.values()) {
                if (!c.isMain() && parent.id().equals(c.parentId()) && c.name().equalsIgnoreCase(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return candidate;
            n++;
        }
    }

    /**
     * Unterbereich - muss geometrisch komplett innerhalb EINES EINZELNEN Teils des Hauptbereichs
     * liegen (nicht über mehrere getrennte Teile des Eltern-Claims hinweg - ein Unterbereich, der
     * "innerhalb" sein soll, muss in einem zusammenhängenden Stück Territorium liegen). Für
     * spätere GUI-Nutzung (Phase 5) bereits vorbereitet.
     */
    public static CreateOutcome createSubClaim(Claim parent, String name, List<Vertex> polygon) {
        CreateResult check = validateNewSubClaim(parent, polygon);
        if (check != CreateResult.OK) return new CreateOutcome(check, null);
        Claim claim = Claim.withSinglePart(UUID.randomUUID(), name, parent.owner(), parent.dimension(), parent.id(), polygon, System.currentTimeMillis());
        register(claim);
        save();
        return new CreateOutcome(CreateResult.OK, claim);
    }

    /** Reine Prüfung OHNE Mutation - siehe {@link #validateNewMainClaim}-Klassenkommentar für die Begründung. */
    public static CreateResult validateNewSubClaim(Claim parent, List<Vertex> polygon) {
        if (polygon.size() < 3) return CreateResult.TOO_FEW_POINTS;
        if (polygon.size() > PolygonUtil.MAX_POINTS_PER_PART) return CreateResult.TOO_MANY_POINTS;
        if (PolygonUtil.isSelfIntersecting(polygon)) return CreateResult.SELF_INTERSECTING;
        for (List<Vertex> parentPart : parent.parts()) {
            if (PolygonUtil.polygonContainsPolygon(parentPart, polygon)) return CreateResult.OK;
        }
        return CreateResult.OUTSIDE_PARENT;
    }

    // ---------------------------------------------------------------- Löschen

    /**
     * Löscht einen Claim UND kaskadierend alle seine Unterbereiche (rekursiv über beliebig
     * viele Ebenen, auch wenn {@link #createSubClaim} aktuell nur eine Ebene erzeugt - das
     * Datenmodell selbst schränkt die Tiefe nicht ein, siehe ROADMAP.md Phase 5-Nachtrag).
     * Baut den Chunk-Index danach komplett neu auf statt punktuell zu bereinigen - bei der
     * überschaubaren Claim-Zahl, die hier zu erwarten ist, unproblematisch und deutlich weniger
     * fehleranfällig als eine gezielte Teil-Entfernung.
     *
     * @return true, wenn ein Claim mit dieser ID existierte und entfernt wurde.
     */
    public static boolean deleteClaim(UUID id) {
        if (!claimsById.containsKey(id)) return false;
        java.util.Deque<UUID> queue = new java.util.ArrayDeque<>();
        queue.add(id);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            claimsById.remove(current);
            for (Claim c : claimsById.values()) {
                if (current.equals(c.parentId())) queue.add(c.id());
            }
        }
        rebuildChunkIndex();
        save();
        return true;
    }

    // ---------------------------------------------------------------- Erweitern (mehrteilig)

    public enum AddPartResult {
        OK, TOO_FEW_POINTS, TOO_MANY_POINTS, SELF_INTERSECTING, OVERLAPS_EXISTING, OUTSIDE_PARENT, TOO_MANY_PARTS
    }

    /**
     * Fügt einem BESTEHENDEN Claim ein neues, komplett UNABHÄNGIGES Territoriums-Teil hinzu
     * (Nutzer-Vorgabe, siehe ROADMAP.md Phase 5-Nachtrag "Erweitern v2" - ersetzt die frühere
     * "Polygon ersetzen, muss das alte enthalten"-Logik). Der neue Teil muss weder das bestehende
     * Territorium berühren noch überschneiden - "Dabei ist es unwichtig, ob die Claims einen
     * Schnittpunkt haben oder nicht" (Nutzer-Zitat). Geprüft wird NUR die Überlappung mit fremden
     * Hauptbereichen (wie bei der Erstellung) - NICHT mit den eigenen anderen Teilen desselben
     * Claims, die dürfen sich per Definition beliebig zueinander verhalten.
     *
     * <p>Ausnahme (ROADMAP.md Phase 6-Nachtrag "SubClaim-Erweitern"): wird ein UNTERBEREICH
     * erweitert, muss der neue Teil zusätzlich komplett innerhalb EINES EINZELNEN Teils des
     * ELTERN-Hauptbereichs liegen - exakt dieselbe Containment-Regel wie bei {@link #createSubClaim},
     * sonst könnte ein Unterbereich durch Erweitern aus seinem Hauptbereich "herauswachsen".
     */
    public static AddPartResult addClaimPart(Claim claim, List<Vertex> newPart) {
        AddPartResult check = validateAddPart(claim, newPart);
        if (check != AddPartResult.OK) return check;
        claim.addPart(newPart);
        registerPart(claim.id(), claim.dimension(), newPart);
        save();
        return AddPartResult.OK;
    }

    /** Reine Prüfung OHNE Mutation - siehe {@link #validateNewMainClaim}-Klassenkommentar für die Begründung. */
    public static AddPartResult validateAddPart(Claim claim, List<Vertex> newPart) {
        if (newPart.size() < 3) return AddPartResult.TOO_FEW_POINTS;
        if (newPart.size() > PolygonUtil.MAX_POINTS_PER_PART) return AddPartResult.TOO_MANY_POINTS;
        if (PolygonUtil.isSelfIntersecting(newPart)) return AddPartResult.SELF_INTERSECTING;
        // Nutzer-Vorgabe (ROADMAP.md Phase 6-Nachtrag "Teile-Obergrenze"): gilt gleich für Haupt-
        // UND Unterbereiche, siehe FeatureConfigManager.Data#maxClaimParts-Kommentar.
        //
        // Bugfix (Nutzer-Fund 2026-08-18): zählte bisher schlicht die Länge von claim.parts() - zwei
        // Teile, die sich berühren (mindestens ein gemeinsamer Block/eine gemeinsame Kante), sollen
        // aber als EIN zusammenhängendes Teil zählen. Nur ein Teil OHNE jeden Kontakt zu irgendeinem
        // anderen Teil ist ein "weiteres" Teil im Sinne der Obergrenze. Bildet dafür die zusammen-
        // hängenden Gruppen aller Teile (bisherige + der neue) über PolygonUtil#polygonsOverlap, das
        // laut eigenem Klassenkommentar bewusst auch reine Kantenberührung als "Überlappung" zählt -
        // exakt die hier gewünschte Berührungs-Definition, kein zusätzlicher Test nötig.
        List<List<Vertex>> partsAfterAdd = new java.util.ArrayList<>(claim.parts());
        partsAfterAdd.add(newPart);
        if (countConnectedPartGroups(partsAfterAdd) > FeatureConfigManager.maxClaimParts()) return AddPartResult.TOO_MANY_PARTS;
        if (!claim.isMain()) {
            Claim parent = claimsById.get(claim.parentId());
            if (parent == null) return AddPartResult.OUTSIDE_PARENT;
            boolean containedInAnyParentPart = false;
            for (List<Vertex> parentPart : parent.parts()) {
                if (PolygonUtil.polygonContainsPolygon(parentPart, newPart)) {
                    containedInAnyParentPart = true;
                    break;
                }
            }
            if (!containedInAnyParentPart) return AddPartResult.OUTSIDE_PARENT;
        }
        for (Claim other : claimsById.values()) {
            if (other.id().equals(claim.id())) continue;
            if (!other.isMain() || !other.dimension().equals(claim.dimension()) || other.owner().equals(claim.owner())) continue;
            if (overlapsAnyPart(newPart, other)) return AddPartResult.OVERLAPS_EXISTING;
        }
        return AddPartResult.OK;
    }

    /**
     * Anzahl zusammenhängender Gruppen in {@code parts}, wobei zwei Teile als "verbunden" gelten,
     * wenn sie sich berühren/überschneiden ({@link PolygonUtil#polygonsOverlap}). Einfache
     * Breitensuche über eine O(n²)-Adjazenzprüfung - für die realistische Teile-Anzahl pro Claim
     * (Obergrenze per {@code maxClaimParts}, siehe Aufrufer) unproblematisch, gleiche Größenordnung
     * wie {@link PolygonUtil}s eigene bewusst simple O(n²)-Ansätze.
     */
    private static int countConnectedPartGroups(List<List<Vertex>> parts) {
        int n = parts.size();
        boolean[] visited = new boolean[n];
        int groups = 0;
        for (int i = 0; i < n; i++) {
            if (visited[i]) continue;
            groups++;
            java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
            stack.push(i);
            visited[i] = true;
            while (!stack.isEmpty()) {
                int current = stack.pop();
                for (int j = 0; j < n; j++) {
                    if (!visited[j] && PolygonUtil.polygonsOverlap(parts.get(current), parts.get(j))) {
                        visited[j] = true;
                        stack.push(j);
                    }
                }
            }
        }
        return groups;
    }

    /**
     * Rollback-Hilfe für Preis-Fehlschläge (ROADMAP.md Phase 6): {@code addClaimPart} wird
     * VOR dem eigentlichen Bezahlvorgang aufgerufen (die Fläche des NEUEN Teils muss ja erst
     * feststehen, um den Preis zu berechnen) - reicht die Bezahlung nicht, macht dies den gerade
     * hinzugefügten Teil wieder rückgängig, statt den Claim mit einem unbezahlten Teil zu lassen.
     */
    public static void removeLastPart(Claim claim) {
        List<List<Vertex>> parts = claim.parts();
        if (parts.isEmpty()) return;
        parts.remove(parts.size() - 1);
        rebuildChunkIndex();
        save();
    }

    // ---------------------------------------------------------------- Erweitern/Verkleinern per Ziehen (Grenz-Hitbox)

    public enum ResizeResult {
        OK, NO_CHANGE, TOO_MANY_POINTS, TOO_MANY_PARTS, OVERLAPS_EXISTING, OUTSIDE_PARENT, SUBCLAIM_WOULD_BE_ORPHANED
    }

    /**
     * Nutzer-Vorgabe (2026-08-18, "Anpassen"-Button): reine Prüfung OHNE Mutation, ob eine
     * über {@link com.areaclaims.geometry.PolygonUtil#applyToggleSetToParts} gebaute Kandidaten-
     * Teile-Liste als NEUE Gesamtgeometrie des Claims übernommen werden dürfte - ersetzt bei Erfolg
     * die KOMPLETTEN {@code parts} des Claims (nicht nur ein einzelner neuer Teil wie bei {@link
     * #validateAddPart}), da Ziehen eine bestehende Grenze verschiebt statt ein unabhängiges Stück
     * hinzuzufügen.
     *
     * <p>Zwei Prüfungen, die {@link #validateAddPart} nicht kennt: (1) beim Verkleinern eines
     * HAUPTbereichs müssen alle seine Unterbereiche weiterhin komplett innerhalb der neuen,
     * kleineren Fläche liegen - sonst würde ein Unterbereich "herrenlos" im jetzt fremden Territorium
     * zurückbleiben; (2) die Teile-Zahl der Kandidaten-Liste ist bereits die tatsächliche
     * zusammenhängende Gruppenzahl (aus {@link com.areaclaims.geometry.PolygonUtil#chainEdgesIntoLoops}
     * abgeleitet, jede Schleife = ein zusammenhängendes Stück), kein zusätzlicher {@code
     * countConnectedPartGroups}-Aufruf nötig.
     */
    public static ResizeResult validateResize(Claim claim, List<List<Vertex>> candidateParts) {
        if (candidateParts.isEmpty()) return ResizeResult.NO_CHANGE;
        for (List<Vertex> part : candidateParts) {
            if (part.size() > PolygonUtil.MAX_POINTS_PER_PART) return ResizeResult.TOO_MANY_POINTS;
        }
        if (candidateParts.size() > FeatureConfigManager.maxClaimParts()) return ResizeResult.TOO_MANY_PARTS;

        if (!claim.isMain()) {
            Claim parent = claimsById.get(claim.parentId());
            if (parent == null) return ResizeResult.OUTSIDE_PARENT;
            for (List<Vertex> part : candidateParts) {
                boolean containedInAnyParentPart = false;
                for (List<Vertex> parentPart : parent.parts()) {
                    if (PolygonUtil.polygonContainsPolygon(parentPart, part)) {
                        containedInAnyParentPart = true;
                        break;
                    }
                }
                if (!containedInAnyParentPart) return ResizeResult.OUTSIDE_PARENT;
            }
        } else {
            for (Claim sub : claimsById.values()) {
                if (sub.isMain() || !claim.id().equals(sub.parentId())) continue;
                for (List<Vertex> subPart : sub.parts()) {
                    boolean stillContained = false;
                    for (List<Vertex> part : candidateParts) {
                        if (PolygonUtil.polygonContainsPolygon(part, subPart)) {
                            stillContained = true;
                            break;
                        }
                    }
                    if (!stillContained) return ResizeResult.SUBCLAIM_WOULD_BE_ORPHANED;
                }
            }
        }

        for (List<Vertex> part : candidateParts) {
            for (Claim other : claimsById.values()) {
                if (other.id().equals(claim.id())) continue;
                if (!other.isMain() || !other.dimension().equals(claim.dimension()) || other.owner().equals(claim.owner())) continue;
                if (overlapsAnyPart(part, other)) return ResizeResult.OVERLAPS_EXISTING;
            }
        }
        return ResizeResult.OK;
    }

    /** Ersetzt die KOMPLETTEN Teile des Claims durch {@code newParts} (siehe {@link #validateResize}-Klassenkommentar) - Aufrufer MUSS vorher validiert haben. */
    public static void applyResize(Claim claim, List<List<Vertex>> newParts) {
        claim.parts().clear();
        claim.parts().addAll(newParts);
        rebuildChunkIndex();
        save();
    }

    private static boolean overlapsAnyPart(List<Vertex> newPart, Claim other) {
        for (List<Vertex> otherPart : other.parts()) {
            if (PolygonUtil.polygonsOverlap(otherPart, newPart)) return true;
        }
        return false;
    }

    private static void register(Claim claim) {
        claimsById.put(claim.id(), claim);
        for (List<Vertex> part : claim.parts()) {
            registerPart(claim.id(), claim.dimension(), part);
        }
    }

    private static void registerPart(UUID claimId, String dimension, List<Vertex> part) {
        int[] box = PolygonUtil.boundingBox(part);
        for (int cx = box[0] >> 4; cx <= box[2] >> 4; cx++) {
            for (int cz = box[1] >> 4; cz <= box[3] >> 4; cz++) {
                chunkIndex.computeIfAbsent(new ChunkKey(dimension, cx, cz), k -> new ArrayList<>()).add(claimId);
            }
        }
    }

    // ---------------------------------------------------------------- Persistenz

    private static void rebuildChunkIndex() {
        chunkIndex.clear();
        for (Claim claim : claimsById.values()) {
            for (List<Vertex> part : claim.parts()) {
                registerPart(claim.id(), claim.dimension(), part);
            }
        }
    }

    private static void load() {
        claimsById.clear();
        chunkIndex.clear();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null || data.claims == null) return;
            for (ClaimDto dto : data.claims) {
                List<List<Vertex>> parts = new ArrayList<>();
                if (dto.parts != null) {
                    for (List<int[]> part : dto.parts) {
                        parts.add(part.stream().map(p -> new Vertex(p[0], p[1])).collect(Collectors.toList()));
                    }
                }
                Claim claim = new Claim(
                    UUID.fromString(dto.id), dto.name, UUID.fromString(dto.owner), dto.dimension,
                    dto.parentId == null ? null : UUID.fromString(dto.parentId), parts, dto.createdAt);
                if (dto.members != null) {
                    dto.members.forEach((uuid, role) -> claim.members().put(UUID.fromString(uuid), ClaimRole.valueOf(role)));
                }
                if (dto.rules != null) {
                    dto.rules.forEach((rule, setting) -> claim.rules().put(RuleType.valueOf(rule),
                        new RuleSetting(setting.enabled, ClaimRole.valueOf(setting.minRole))));
                }
                if (dto.boughtOutRules != null) {
                    dto.boughtOutRules.forEach(rule -> {
                        try {
                            claim.boughtOutRules().add(RuleType.valueOf(rule));
                        } catch (IllegalArgumentException ignored) {}
                    });
                }
                claim.setTitleColor(dto.titleColor);
                claim.setTitleDurationTicks(dto.titleDurationTicks);
                claim.setWelcomeMessage(dto.welcomeMessage);
                claim.setWelcomeColor(dto.welcomeColor);
                claim.setWelcomeDurationTicks(dto.welcomeDurationTicks);
                claim.setBoundaryColor(dto.boundaryColor);
                claim.setLinkBoundaryColorToTitle(dto.linkBoundaryColorToTitle);
                claim.setImageHash(dto.imageHash);
                claimsById.put(claim.id(), claim);
            }
        } catch (IOException | RuntimeException ignored) {}
        rebuildChunkIndex();
    }

    public static void save() {
        if (dataFile == null) return;
        Data data = new Data();
        for (Claim claim : claimsById.values()) {
            ClaimDto dto = new ClaimDto();
            dto.id = claim.id().toString();
            dto.name = claim.name();
            dto.owner = claim.owner().toString();
            dto.dimension = claim.dimension();
            dto.parentId = claim.parentId() == null ? null : claim.parentId().toString();
            dto.createdAt = claim.createdAt();
            dto.parts = claim.parts().stream()
                .map(part -> part.stream().map(v -> new int[] {v.x(), v.z()}).collect(Collectors.toList()))
                .collect(Collectors.toList());
            claim.members().forEach((uuid, role) -> dto.members.put(uuid.toString(), role.name()));
            claim.rules().forEach((rule, setting) -> {
                RuleSettingDto rs = new RuleSettingDto();
                rs.enabled = setting.enabled();
                rs.minRole = setting.minRoleToIgnore().name();
                dto.rules.put(rule.name(), rs);
            });
            claim.boughtOutRules().forEach(rule -> dto.boughtOutRules.add(rule.name()));
            dto.titleColor = claim.titleColor();
            dto.titleDurationTicks = claim.titleDurationTicks();
            dto.welcomeMessage = claim.welcomeMessage();
            dto.welcomeColor = claim.welcomeColor();
            dto.welcomeDurationTicks = claim.welcomeDurationTicks();
            dto.boundaryColor = claim.boundaryColor();
            dto.linkBoundaryColorToTitle = claim.linkBoundaryColorToTitle();
            dto.imageHash = claim.imageHash();
            data.claims.add(dto);
        }
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}

        // JourneyMap-Integration (2026-08-16, Architektur 2026-08-17 umgebaut - siehe
        // com.areaclaims.network.ClaimMapSnapshot-Klassenkommentar): save() ist der EINE gemeinsame
        // Aufrufpunkt, den ALLE claim-verändernden Operationen durchlaufen (erstellen, erweitern,
        // löschen, umbenennen, Farbe/Regeln/Freikauf ändern - siehe die 5 internen + 5 externen
        // save()-Aufrufstellen in dieser Klasse/ClaimEditService/StakingService/BuyoutService), damit
        // hier genau EIN Hook statt vieler Einzelstellen ausreicht. Kein ModAvailability-Guard mehr
        // nötig - der Broadcast ist reine Eigennetzwerk-Kommunikation, für Spieler ohne JourneyMap
        // einfach folgenlos (siehe ClaimMapSnapshotBuilder-Klassenkommentar).
        com.areaclaims.network.ClaimMapSnapshotBuilder.sendToAll();
    }
}
