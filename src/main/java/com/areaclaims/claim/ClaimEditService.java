package com.areaclaims.claim;

import com.areaclaims.economy.PriceConfig;
import com.areaclaims.economy.PriceConfigManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;

/**
 * Einzige Stelle, an der Mitglieder-/Regel-Mutationen tatsächlich validiert und angewendet
 * werden - sowohl {@code /areaclaims role}/{@code /areaclaims rule} (Chat-Befehle) als auch
 * {@code SetRolePacket}/{@code SetRulePacket} (GUI-Editor, Phase 5) rufen NUR diese Methoden auf,
 * damit sich beide Zugriffswege garantiert identisch verhalten (siehe ROADMAP.md Phase 5 -
 * Nutzer-Vorgabe "nicht duplizieren/divergieren lassen"). Die beiden Aufrufer unterscheiden sich
 * nur darin, WIE sie den Ziel-{@link Claim} auflösen (Befehl: nach Name, bereits Besitzer-
 * gescoped über {@link ClaimManager#findMainClaimByNameForEditor}; Paket: direkt nach UUID aus
 * dem synchronisierten Snapshot) - die Berechtigungsprüfung hier läuft deshalb bewusst NOCH
 * einmal, damit der UUID-Zugriffsweg nicht auf die Scoping-Prüfung des Namens-Lookups angewiesen
 * ist.
 */
public final class ClaimEditService {

    private ClaimEditService() {}

    public enum Result { OK, NOT_AUTHORIZED, INVALID_ROLE, INVALID_RULE, PLAYER_NOT_FOUND, INVALID_NAME, NAME_TAKEN, CANNOT_TARGET_OWNER, RULE_NOT_BOUGHT_OUT }

    /**
     * Besitzer darf immer, sonst nur Vanilla-OP-Stufe 4 (Nutzer-Vorgabe: die frühere eigenständige
     * Admin-Liste wurde ersatzlos entfernt, siehe ROADMAP.md Phase 0).
     */
    public static boolean canEdit(ServerPlayer caller, Claim claim) {
        return claim.owner().equals(caller.getUUID()) || caller.hasPermissions(4);
    }

    public static ClaimRole parseRole(String name) {
        try {
            return ClaimRole.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    public static RuleType parseRule(String name) {
        try {
            return RuleType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    public static Result applyRole(ServerPlayer caller, Claim claim, ServerPlayer target, ClaimRole role) {
        if (!canEdit(caller, claim)) return Result.NOT_AUTHORIZED;
        if (role == null) return Result.INVALID_ROLE;
        // Bug-Root-Cause (Nachtrag 4, erneute Untersuchung nach Nutzer-Feedback "Kaskadierung
        // funktioniert immer noch nicht" - diesmal per echtem Datei-Abgleich der persistierten
        // Testwelt-Daten bestätigt, nicht nur Code-Lesen): der Claim-BESITZER selbst ist laut
        // Klassenkommentar von {@link Claim#members()} bewusst NIE in der Mitgliederliste enthalten
        // ({@link Claim#roleOf} liefert für ihn immer COOWNER, unabhängig von members()). Bisher
        // erlaubte applyRole trotzdem, den Besitzer explizit als "MEMBER" in members() einzutragen
        // (passiert leicht beim Solo-Testen, wenn der einzige verfügbare Account zum Testen der
        // eigene ist) - cascadeMembershipToParent() erkennt diesen Fall am Ziel-Claim (Hauptbereich)
        // korrekt über roleOf() und trägt NICHTS ein (der Besitzer braucht dort keinen expliziten
        // Eintrag) - das sah dann so aus, als würde die Kaskadierung nicht funktionieren, obwohl der
        // Unterbereich selbst den (redundanten) Eintrag zeigte. Fix: den Besitzer von vornherein NIE
        // in members() zulassen, auf JEDEM Claim (Haupt- oder Unterbereich) - konsistent überall,
        // kein asymmetrischer Sonderfall mehr zwischen Unterbereich und kaskadiertem Hauptbereich.
        if (target.getUUID().equals(claim.owner())) return Result.CANNOT_TARGET_OWNER;
        if (role == ClaimRole.NONE) {
            claim.members().remove(target.getUUID());
        } else {
            claim.members().put(target.getUUID(), role);
            cascadeMembershipToParent(claim, target.getUUID());
        }
        ClaimManager.save();
        return Result.OK;
    }

    /**
     * Nutzer-Vorgabe (siehe ROADMAP.md Phase 5-Nachtrag "3-Spalten-Layout"): wird ein Spieler
     * Mitglied eines UNTERBEREICHS, wird er automatisch auch Mitglied des zugehörigen
     * Hauptbereichs - aber NICHT zwingend mit derselben Rolle. Default: MEMBER (niedrigste
     * Stufe), NUR wenn er dort noch keinen Eintrag hat oder aktuell niedriger eingestuft ist
     * (kein Downgrade eines bestehenden höherrangigen Eintrags). Läuft NUR in diese eine
     * Richtung (Unterbereich -> Hauptbereich) - das Entfernen aus einem Unterbereich kaskadiert
     * NICHT rückwärts (der Spieler könnte weiterhin direkt Mitglied des Hauptbereichs sein, oder
     * noch in einem anderen Unterbereich).
     */
    private static void cascadeMembershipToParent(Claim claim, UUID player) {
        if (claim.isMain()) return;
        Claim parent = ClaimManager.getById(claim.parentId());
        if (parent == null) return;
        if (parent.roleOf(player).ordinal() < ClaimRole.MEMBER.ordinal()) {
            parent.members().put(player, ClaimRole.MEMBER);
        }
    }

    public static Result applyRule(ServerPlayer caller, Claim claim, RuleType rule, boolean enabled, ClaimRole minRole) {
        if (!canEdit(caller, claim)) return Result.NOT_AUTHORIZED;
        if (rule == null) return Result.INVALID_RULE;
        // Nachtrag 7, Punkt 4 (Exploit-Fund): eine Regel mit konfiguriertem, noch nicht bezahltem
        // Freikauf-Preis darf GAR NICHT umgeschaltet werden - vorher gab es hier KEINE Prüfung, der
        // grüne-Haken/rotes-X-Umschalter war rein CLIENTSEITIG hinter dem "Kaufen"-Button versteckt
        // (siehe AreaClaimsEditorScreen), ein direkt gesendetes SetRulePacket (oder der /areaclaims
        // rule-Befehl) konnte die Regel trotzdem umschalten, OHNE bezahlt zu haben. Serverseitig
        // durchgesetzt, damit ein client-seitig umgangenes Paket NICHT ausreicht (siehe
        // Klassenkommentar "beide Zugriffswege" - Befehl UND Paket laufen durch dieselbe Prüfung hier).
        if (!claim.boughtOutRules().contains(rule)) {
            PriceConfig buyoutPrice = PriceConfigManager.ruleBuyoutPrice(rule);
            if (!buyoutPrice.isFree()) return Result.RULE_NOT_BOUGHT_OUT;
        }
        claim.rules().put(rule, new RuleSetting(enabled, minRole == null ? ClaimRole.COOWNER : minRole));
        ClaimManager.save();
        return Result.OK;
    }

    /** Löscht {@code claim} (und kaskadierend seine Unterbereiche, siehe {@link ClaimManager#deleteClaim}). */
    public static Result deleteClaim(ServerPlayer caller, Claim claim) {
        if (!canEdit(caller, claim)) return Result.NOT_AUTHORIZED;
        ClaimManager.deleteClaim(claim.id());
        return Result.OK;
    }

    /**
     * Benennt einen Claim (Haupt- ODER Unterbereich) um (ROADMAP.md Phase 6-Nachtrag 2, Punkt 3
     * "Claim umbenennen") - einzige Stelle für diese Mutation, genutzt von sowohl
     * {@code /areaclaims rename} als auch {@link com.areaclaims.network.RenameClaimPacket}.
     */
    /**
     * Punkt 6 (Nachtrag 4): weist einem Claim (Haupt- ODER Unterbereich) ein hochgeladenes/aus der
     * Galerie gewähltes Bild zu (oder entfernt es, {@code hash} leer/{@code null}) - einzige Stelle,
     * die {@link Claim#setImageHash} aufruft, gleiches "einzige Quelle der Wahrheit"-Prinzip wie die
     * übrigen Mutationen hier. Der Hash selbst wird NICHT gegen {@code ServerImageStore} geprüft -
     * das übernimmt bereits der Client (nur tatsächlich aus der Galerie/einem Upload-Ergebnis
     * bekannte Hashes werden hier je verschickt) - eine serverseitige Prüfung wäre reine
     * Verteidigung in der Tiefe ohne echten Angriffsvektor (ein erfundener Hash zeigt beim Rendern
     * einfach auf kein Bild, siehe {@code ImageRequestPacket}s "kein Bild gespeichert"-Antwort).
     */
    public static Result setClaimImage(ServerPlayer caller, Claim claim, String hash) {
        if (!canEdit(caller, claim)) return Result.NOT_AUTHORIZED;
        claim.setImageHash(hash);
        ClaimManager.save();
        return Result.OK;
    }

    public static Result renameClaim(ServerPlayer caller, Claim claim, String newName) {
        if (!canEdit(caller, claim)) return Result.NOT_AUTHORIZED;
        if (newName == null || newName.isBlank()) return Result.INVALID_NAME;
        String trimmed = newName.trim();
        if (ClaimManager.isSiblingNameTaken(claim, trimmed)) return Result.NAME_TAKEN;
        claim.setName(trimmed);
        ClaimManager.save();
        return Result.OK;
    }
}
