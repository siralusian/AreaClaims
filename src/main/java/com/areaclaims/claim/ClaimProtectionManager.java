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
 * <p>Freigekaufte Regeln (ROADMAP.md Phase 6, {@code /areaclaims buyout}) sind ein SEPARATER,
 * geld-/itembasierter Mechanismus zusätzlich zum rollenbasierten Ignorieren oben - einmal
 * freigekauft, gilt eine Regel auf diesem Claim für ALLE als deaktiviert, unabhängig von Rolle
 * oder {@code enabled}-Flag (siehe {@link Claim#boughtOutRules()}).
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
        Claim main = ClaimManager.findMainClaimAt(dimension, x, z);
        if (main == null) return false;
        if (main.boughtOutRules().contains(rule)) return false;
        RuleSetting mainSetting = main.rules().get(rule);
        if (mainSetting != null && mainSetting.enabled()) return true;
        Claim sub = ClaimManager.findSubClaimAt(main, dimension, x, z);
        if (sub != null) {
            if (sub.boughtOutRules().contains(rule)) return false;
            RuleSetting subSetting = sub.rules().get(rule);
            return subSetting != null && subSetting.enabled();
        }
        return false;
    }

    private static boolean checkClaimRule(UUID player, Claim claim, RuleType rule) {
        if (claim.owner().equals(player)) return true;
        if (claim.boughtOutRules().contains(rule)) return true;
        RuleSetting setting = claim.rules().get(rule);
        if (setting == null || !setting.enabled()) return true;
        ClaimRole role = claim.roleOf(player);
        return role.ordinal() >= setting.minRoleToIgnore().ordinal();
    }
}
