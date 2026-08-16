package com.areaclaims.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Reine Gson-Transport-DTO (gleicher Stil wie {@code ClaimManager}s Persistenz-DTOs - plain
 * Klassen statt Records, um Gson keine Steine in den Weg zu legen) für den Editor-Screen:
 * die Claims, die der öffnende Spieler besitzt, jeweils mit aufgelöster Mitgliederliste
 * (Name statt nur UUID, siehe {@link ClaimSnapshotBuilder}) und dem vollständigen Regelsatz.
 * Wird als JSON-String über {@link ClaimSyncPacket} verschickt, statt einer komplexen
 * StreamCodec-Struktur - deutlich weniger Fehleranfällig für ein Datenpaket, das ohnehin nur der
 * GUI-Anzeige dient.
 */
public class ClaimEditorSnapshot {

    public static class MemberEntry {
        public String uuid;
        public String name;
        public String role;
    }

    public static class RuleEntry {
        public String rule;
        public boolean enabled;
        public String minRole;
        /** ROADMAP.md Phase 7-Nachtrag, Punkt 4 "Kaufen-Button" - true, wenn diese Regel auf diesem Claim bereits dauerhaft freigekauft wurde (siehe {@code Claim#boughtOutRules()}). */
        public boolean boughtOut;
        /** Nur gesetzt (nicht {@code null}/nicht kostenlos), wenn ein Admin für diese Regel einen Freikauf-Preis konfiguriert hat - steuert, ob der Editor statt des Rollen-Buttons einen "Kaufen"-Button zeigt. */
        public ServerConfigSnapshot.PriceEntry buyoutPrice;
    }

    public static class ClaimEntry {
        public String id;
        public String name;
        public boolean main;
        public String parentId;
        /** Name des Besitzers (aufgelöst, nicht nur UUID) - siehe ClaimSnapshotBuilder#resolveName. */
        public String ownerName;
        /** Gesamtfläche über alle Territoriums-Teile ({@link com.areaclaims.claim.Claim#totalArea}). */
        public double area;
        public List<MemberEntry> members = new ArrayList<>();
        public List<RuleEntry> rules = new ArrayList<>();

        // Betreten-Nachricht-Einstellungen (ROADMAP.md Phase 6-Nachtrag 2, Punkt 2 "Betreten-
        // Nachricht-GUI") - 1:1 gespiegelt von com.areaclaims.claim.Claim, damit die Name-/
        // Willkommensnachricht-Bearbeiten-Popups in AreaClaimsEditorScreen (ROADMAP.md Phase
        // 7-Nachtrag 2, Punkt 1) sie vorausfüllen können, statt bei jedem Öffnen mit leeren
        // Feldern zu starten.
        public int titleColor;
        public int titleDurationTicks;
        public String welcomeMessage;
        public int welcomeColor;
        public int welcomeDurationTicks;
        public int boundaryColor;
        public boolean linkBoundaryColorToTitle;
        /** Punkt 6 (Nachtrag 4): Inhalts-Hash eines zugewiesenen Claim-Bilds, leer = keins (siehe {@code Claim#imageHash}). */
        public String imageHash = "";
    }

    public List<ClaimEntry> claims = new ArrayList<>();
    /**
     * Aktueller Anzeigestil (Partikel/Einfärbung, siehe {@code ClaimShowcaseManager.DisplayMode})
     * DIESES Spielers - "PARTICLES" oder "TINT" (ROADMAP.md Phase 6-Nachtrag 2, Punkt 7). Zählt
     * nicht pro Claim, sondern global pro Spieler - trotzdem hier im Snapshot statt einem eigenen
     * Paket, da der Editor-Screen ohnehin schon dessen Generation-Zähler für Neuaufbauten nutzt.
     */
    public String showcaseMode = "PARTICLES";
    /**
     * Persönliche Grenzfarbe-Überschreibung DIESES Spielers als Hex-String, oder leer, wenn keine
     * gesetzt ist (ROADMAP.md Phase 7-Nachtrag, Punkt 5 "Einstellungen"-Popup) - siehe
     * {@code ClaimShowcaseManager#colorOverride}.
     */
    public String boundaryColorOverride = "";
}
