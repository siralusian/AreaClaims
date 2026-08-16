package com.areaclaims.claim;

import com.areaclaims.geometry.PolygonUtil;
import com.areaclaims.geometry.Vertex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Haupt- oder Unterbereich. Hauptbereiche haben {@code parentId == null}; Unterbereiche
 * verweisen über {@code parentId} auf ihren Hauptbereich. Reines XZ-Grundriss-Polygon, kein
 * Y-Anteil (siehe ROADMAP.md Phase 1) - ein Claim erstreckt sich immer über die komplette
 * Weltbauhöhe seiner Dimension.
 *
 * <p><b>Architektur-Nachtrag (Nutzer-Vorgabe, siehe ROADMAP.md Phase 5-Nachtrag "Erweitern v2"):</b>
 * ein Claim ist KEIN einzelnes Polygon mehr, sondern eine Liste unabhängiger Polygon-{@code parts}
 * ("mehrteiliges Territorium" - bewusst KEIN echtes Polygon-Union, jeder Teil bleibt ein eigenes,
 * simples Polygon). Teile müssen sich weder berühren noch überschneiden - ein Claim kann aus
 * mehreren komplett getrennten Grundstücken unter einem Namen bestehen. "Punkt in Claim" bedeutet
 * "Punkt in IRGENDEINEM Teil" (siehe {@link #containsPoint}), "Fläche" ist die Summe aller
 * Teilflächen (siehe {@link #totalArea}). Keine Abwärtskompatibilität zu alten Speicherständen
 * nötig (Nutzer-Vorgabe: noch keine Produktivdaten, altes Test-Save kann verworfen werden).
 */
public class Claim {

    private final UUID id;
    private String name;
    private final UUID owner;
    private final String dimension;
    private final UUID parentId;
    private final List<List<Vertex>> parts;
    private final long createdAt;
    private final Map<UUID, ClaimRole> members = new HashMap<>();
    private final Map<RuleType, RuleSetting> rules = new HashMap<>();
    /**
     * Regeln, die per Geld/Item-Freikauf dauerhaft für ALLE (nicht nur bestimmte Rollen)
     * neutralisiert wurden (ROADMAP.md Phase 6, {@code /areaclaims buyout}) - unabhängig vom
     * rollenbasierten Ignorieren-Mechanismus in {@link #rules}. Siehe {@code ClaimProtectionManager}.
     */
    private final Set<RuleType> boughtOutRules = new HashSet<>();

    /**
     * Betreten-Nachricht (ROADMAP.md Phase 6-Nachtrag "Betreten-Nachricht-Feintuning"): JEDER
     * Claim (Haupt- ODER Unterbereich) hat seine EIGENEN Anzeige-Einstellungen - beim Betreten
     * wird der Claim-Name als Titel (Hauptbereich) bzw. Untertitel (Unterbereich) gezeigt, in
     * DIESER Farbe/Dauer, PLUS optional eine freie Willkommensnachricht über die Actionbar (siehe
     * ClaimEntryListener - vanilla hat nur 2 Titel-Slots, die Actionbar ist der dritte Kanal).
     */
    private int titleColor = 0xFFFFFF;
    private int titleDurationTicks = 70;
    private String welcomeMessage = "";
    private int welcomeColor = 0xFFFFFF;
    private int welcomeDurationTicks = 60;

    /**
     * Grenzen-Partikelfarbe (ROADMAP.md Phase 6-Nachtrag "Mehrere Showcases"), bewusst GETRENNT
     * von {@link #titleColor} - {@link #linkBoundaryColorToTitle} ist die Komfort-Option, um beide
     * zu koppeln (Standard: an, damit ein frischer Claim ohne weitere Konfiguration trotzdem eine
     * sinnvolle Grenzfarbe hat).
     */
    private int boundaryColor = 0xFFFFFF;
    private boolean linkBoundaryColorToTitle = true;

    /**
     * Punkt 6 (Nachtrag 4, "Bild statt Text"): optionaler Inhalts-Hash (siehe
     * {@code com.areaclaims.image.ServerImageStore}) eines vom Besitzer hochgeladenen/aus der
     * Galerie gewählten Bilds, das den Namen dieses Claims (Haupt- ODER Unterbereich) beim
     * Betreten ERSETZEN kann - {@code null}/leer = kein Bild zugewiesen, normaler Textname bleibt.
     * Ob ein Betrachter das Bild tatsächlich statt Text sieht, ist WIEDERUM eine rein persönliche
     * Einstellung (siehe {@code PlayerDisplayPreferences#mainClaim.showImage}), nicht claim-eigen -
     * der Besitzer legt nur fest, WAS es zu sehen gäbe, nicht OB.
     */
    private String imageHash = "";

    public Claim(UUID id, String name, UUID owner, String dimension, UUID parentId, List<List<Vertex>> parts, long createdAt) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.dimension = dimension;
        this.parentId = parentId;
        this.parts = parts;
        this.createdAt = createdAt;
    }

    /** Bequemlichkeits-Konstruktor für den Normalfall "neuer Claim mit genau einem (ersten) Teil". */
    public static Claim withSinglePart(UUID id, String name, UUID owner, String dimension, UUID parentId, List<Vertex> firstPart, long createdAt) {
        List<List<Vertex>> parts = new ArrayList<>();
        parts.add(firstPart);
        return new Claim(id, name, owner, dimension, parentId, parts, createdAt);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID owner() { return owner; }
    public String dimension() { return dimension; }
    public UUID parentId() { return parentId; }
    public boolean isMain() { return parentId == null; }
    public List<List<Vertex>> parts() { return parts; }
    public void addPart(List<Vertex> part) { parts.add(part); }
    public long createdAt() { return createdAt; }
    public Map<UUID, ClaimRole> members() { return members; }
    public Map<RuleType, RuleSetting> rules() { return rules; }
    public Set<RuleType> boughtOutRules() { return boughtOutRules; }

    public int titleColor() { return titleColor; }
    public void setTitleColor(int titleColor) { this.titleColor = titleColor; }
    public int titleDurationTicks() { return titleDurationTicks; }
    public void setTitleDurationTicks(int ticks) { this.titleDurationTicks = ticks; }
    public String welcomeMessage() { return welcomeMessage; }
    public void setWelcomeMessage(String welcomeMessage) { this.welcomeMessage = welcomeMessage == null ? "" : welcomeMessage; }
    public int welcomeColor() { return welcomeColor; }
    public void setWelcomeColor(int welcomeColor) { this.welcomeColor = welcomeColor; }
    public int welcomeDurationTicks() { return welcomeDurationTicks; }
    public void setWelcomeDurationTicks(int ticks) { this.welcomeDurationTicks = ticks; }
    public int boundaryColor() { return boundaryColor; }
    public void setBoundaryColor(int boundaryColor) { this.boundaryColor = boundaryColor; }
    public boolean linkBoundaryColorToTitle() { return linkBoundaryColorToTitle; }
    public void setLinkBoundaryColorToTitle(boolean link) { this.linkBoundaryColorToTitle = link; }
    public String imageHash() { return imageHash; }
    public void setImageHash(String imageHash) { this.imageHash = imageHash == null ? "" : imageHash; }
    /** Tatsächlich zu verwendende Grenzfarbe - siehe {@link #linkBoundaryColorToTitle}. */
    public int effectiveBoundaryColor() { return linkBoundaryColorToTitle ? titleColor : boundaryColor; }

    /** true, wenn der Punkt in IRGENDEINEM der unabhängigen Teile liegt. */
    public boolean containsPoint(double x, double z) {
        for (List<Vertex> part : parts) {
            if (PolygonUtil.pointInPolygon(part, x, z)) return true;
        }
        return false;
    }

    /** Summe der Flächen aller Teile in Blocks^2 (kein Abzug für eventuell überlappende eigene Teile - siehe Klassenkommentar, das ist hier bewusst erlaubt). */
    public double totalArea() {
        double sum = 0.0;
        for (List<Vertex> part : parts) sum += PolygonUtil.area(part);
        return sum;
    }

    /** Rolle eines Spielers auf DIESEM Claim (nicht vererbt von Eltern-Claim) - NONE als Default. */
    public ClaimRole roleOf(UUID player) {
        if (player.equals(owner)) return ClaimRole.COOWNER;
        return members.getOrDefault(player, ClaimRole.NONE);
    }
}
