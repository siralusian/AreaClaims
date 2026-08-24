package com.areaclaims.claim;

import java.util.UUID;

/**
 * Zentraler "darf Spieler X Aktion Y an Position Z tun"-Check, den die verdrahteten
 * Event-Handler in {@link com.areaclaims.event.ClaimProtectionListener} UND spätere,
 * noch nicht verdrahtete Regeln (siehe {@link RuleType}-Klassenkommentar) einheitlich nutzen
 * können.
 *
 * Semantik (siehe ROADMAP.md Phase 4): Hauptbereich UND (falls der Punkt zusätzlich in einem
 * Unterbereich liegt) Unterbereich müssen BEIDE die Aktion erlauben (UND-Verknüpfung) - ein
 * Unterbereich kann eine Regel also nur verschärfen, nie den Schutz des Hauptbereichs aufheben.
 * Der Besitzer eines Claims ignoriert auf diesem Claim immer alle Regeln.
 *
 * <p>Freigekaufte Regeln (ROADMAP.md Phase 6, {@code /areaclaims buyout}) SIND NICHT selbst ein
 * Freigabe-Mechanismus (Bugfix 2026-08-18, Nutzer-Klarstellung - vorherige Fehlannahme hier war
 * FALSCH und hat auf dem Live-Server jede Regel mit konfiguriertem Freikauf-Preis wirkungslos
 * gemacht, sobald sie bezahlt wurde): {@link Claim#boughtOutRules()} schaltet AUSSCHLIESSLICH das
 * Recht des Besitzers frei, die Regel überhaupt scharf/unscharf zu stellen (siehe
 * {@code ClaimEditService#applyRule}, {@code RULE_NOT_BOUGHT_OUT}-Sperre) - hat nach dem Kauf
 * KEINEN eigenen Effekt mehr auf die Durchsetzung hier. Ob die Regel nach dem Freikauf tatsächlich
 * greift, hängt ganz normal wie bei jeder anderen Regel nur von {@code enabled} + Rolle ab.
 */
public final class ClaimProtectionManager {

    private ClaimProtectionManager() {}

    public static boolean isAllowed(UUID player, String dimension, int x, int z, RuleType rule) {
        Claim main = ClaimManager.findMainClaimAt(dimension, x, z);
        if (main == null) return true; // Wildnis - keine Einschränkung
        if (!checkClaimRule(player, main, rule)) return false;
        Claim sub = ClaimManager.findSubClaimAt(main, dimension, x, z);
        if (sub != null) {
            return checkClaimRule(player, sub, rule);
        }
        return true;
    }

    /** Variante ohne Spielerbezug (z. B. Mob-Spawning, wo kein handelnder Spieler existiert). */
    public static boolean isRuleActive(String dimension, int x, int z, RuleType rule) {
        return resolveActiveRuleClaim(dimension, x, z, rule) != null;
    }

    /**
     * Wie {@link #isRuleActive}, liefert aber zusätzlich den Claim (Haupt- ODER Unterbereich),
     * der die Regel an dieser Position tatsächlich durchsetzt - {@code null}, wenn keiner. Für
     * Fälle, die zusätzlich zur ja/nein-Antwort die konkrete Claim-Geometrie brauchen (siehe
     * {@code ClaimProtectionListener}s feindliche-Mobs-Zurückdrängen-Logik für {@link
     * RuleType#MOB_SPAWNING}).
     */
    public static Claim resolveActiveRuleClaim(String dimension, int x, int z, RuleType rule) {
        Claim main = ClaimManager.findMainClaimAt(dimension, x, z);
        if (main == null) return null;
        // Bugfix 2026-08-18: boughtOutRules() KEIN Freigabe-Check hier (siehe Klassenkommentar) -
        // Freikauf schaltet nur das Recht des Besitzers frei, enabled/minRole zu setzen.
        RuleSetting mainSetting = main.rules().get(rule);
        if (mainSetting != null && mainSetting.enabled()) return main;
        Claim sub = ClaimManager.findSubClaimAt(main, dimension, x, z);
        if (sub != null) {
            RuleSetting subSetting = sub.rules().get(rule);
            if (subSetting != null && subSetting.enabled()) return sub;
        }
        return null;
    }

    private static boolean checkClaimRule(UUID player, Claim claim, RuleType rule) {
        // Bugfix 2026-08-18: boughtOutRules() KEIN Freigabe-Check hier (siehe Klassenkommentar) -
        // Freikauf schaltet nur das Recht des Besitzers frei, enabled/minRole zu setzen, hat aber
        // selbst KEINEN Effekt auf die Durchsetzung.
        if (claim.owner().equals(player)) return true;
        RuleSetting setting = claim.rules().get(rule);
        if (setting == null || !setting.enabled()) return true;
        ClaimRole role = claim.roleOf(player);
        return role.ordinal() >= setting.minRoleToIgnore().ordinal();
    }
}
