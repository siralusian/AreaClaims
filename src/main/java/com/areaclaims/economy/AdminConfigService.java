package com.areaclaims.economy;

import com.areaclaims.claim.RuleType;
import com.areaclaims.data.FeatureConfigManager;
import com.areaclaims.integration.ModAvailability;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.List;

/**
 * Einzige Stelle, an der die OP4-Admin-Konfiguration (Freischaltungsschwellen, Preise, Teile-
 * Obergrenze) tatsächlich validiert und angewendet wird - sowohl {@code /areaclaims config}
 * (Chat-Befehl) als auch {@link com.areaclaims.network.SetServerConfigPacket}/
 * {@link com.areaclaims.network.SetPriceRowPacket} (Admin-GUI, ROADMAP.md Phase 6-Nachtrag
 * "Admin-GUI" bzw. Phase 6-Nachtrag 2 "Preis-UI-Neugestaltung") rufen NUR diese Methoden auf,
 * damit sich beide Wege identisch verhalten - exakt dasselbe Prinzip wie
 * {@link com.areaclaims.claim.ClaimEditService} für Claim-Mutationen.
 */
public final class AdminConfigService {

    private AdminConfigService() {}

    public enum Result { OK, INVALID_ITEM, COBBLE_DOLLARS_UNAVAILABLE, INVALID_FEATURE }

    public static Result setFeatureLevel(String feature, int level) {
        switch (feature) {
            case "createclaim" -> FeatureConfigManager.setMinOpLevelCreateClaim(level);
            case "expandclaim" -> FeatureConfigManager.setMinOpLevelExpandClaim(level);
            case "buyout" -> FeatureConfigManager.setMinOpLevelBuyout(level);
            default -> {
                return Result.INVALID_FEATURE;
            }
        }
        return Result.OK;
    }

    public static void setMaxClaimParts(int max) {
        FeatureConfigManager.setMaxClaimParts(max);
    }

    public static void setTintRangeChunks(int chunks) {
        FeatureConfigManager.setTintRangeChunks(chunks);
    }

    /** Punkt 1 (Nachtrag 4): "Wildnis"-Meldung an/aus. */
    public static void setWildernessMessageEnabled(boolean enabled) {
        FeatureConfigManager.setWildernessMessageEnabled(enabled);
    }

    /** Punkt 1 (Nachtrag 4): freier Text der "Wildnis"-Meldung. */
    public static void setWildernessMessageText(String text) {
        FeatureConfigManager.setWildernessMessageText(text);
    }

    /**
     * JourneyMap-Integration (2026-08-16, Architektur 2026-08-17 umgebaut - siehe
     * {@link com.areaclaims.network.ClaimMapSnapshot}-Klassenkommentar) an/aus. Löst sofort einen
     * Refresh bei allen Online-Spielern aus, statt auf die nächste Claim-Änderung zu warten -
     * {@link com.areaclaims.network.ClaimMapSnapshotBuilder#build} liefert beim Abschalten
     * automatisch eine leere Claim-Liste, wodurch {@code JourneyMapClientBridge#refresh} auf dem
     * Client vorhandene Overlays sofort entfernt statt bis zum nächsten Neustart hängen zu bleiben.
     * Kein {@code ModAvailability.isJourneyMapAvailable()}-Guard mehr nötig: der Server selbst
     * braucht JourneyMap nicht mehr installiert (nur noch der jeweilige Spieler-Client), der Broadcast
     * an Spieler ohne JourneyMap ist einfach folgenlos.
     */
    public static void setJourneyMapIntegrationEnabled(boolean enabled) {
        FeatureConfigManager.setJourneyMapIntegrationEnabled(enabled);
        com.areaclaims.network.ClaimMapSnapshotBuilder.sendToAll();
    }

    /** Punkt 12 (Nachtrag 3): {@code target} = "block" oder "subclaim", siehe {@link com.areaclaims.network.SetServerConfigPacket}. */
    public static Result setPriceDivisor(String target, int divisor) {
        switch (target) {
            case "block" -> PriceConfigManager.setPerBlockDivisor(divisor);
            case "subclaim" -> PriceConfigManager.setSubClaimDivisor(divisor);
            case "block_refund" -> PriceConfigManager.setPerBlockRefundDivisor(divisor);
            case "subclaim_refund" -> PriceConfigManager.setSubClaimRefundDivisor(divisor);
            default -> {
                return Result.INVALID_FEATURE;
            }
        }
        return Result.OK;
    }

    // ---------------------------------------------------------------- Einzel-Komponenten-Preise (Chat-Befehl, siehe AreaClaimsCommands)
    // Bewusst auf GENAU EINE Komponente beschränkt (kein UND/ODER über den Befehl setzbar) -
    // die volle Mehrkomponenten-Bearbeitung ist GUI-only (siehe setPriceRow unten), da eine
    // Chat-Befehl-Syntax für mehrere Items + Kombinatoren unhandlich wäre (Nutzer-Vorgabe war ein
    // Preis-UI-REDESIGN, primär GUI-getrieben - siehe ROADMAP.md für diese bewusste Scope-Grenze).

    public static Result setPerBlockPriceNone() {
        PriceConfigManager.setPerBlockPrice(PriceConfig.none());
        return Result.OK;
    }

    public static Result setPerBlockPriceItem(String itemId, int amount) {
        if (!isValidItem(itemId)) return Result.INVALID_ITEM;
        PriceConfigManager.setPerBlockPrice(PriceConfig.singleItem(itemId, amount));
        return Result.OK;
    }

    public static Result setPerBlockPriceDollars(long amount) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return Result.COBBLE_DOLLARS_UNAVAILABLE;
        PriceConfigManager.setPerBlockPrice(PriceConfig.singleDollars(BigInteger.valueOf(amount)));
        return Result.OK;
    }

    public static Result setRuleBuyoutPriceNone(RuleType rule) {
        PriceConfigManager.setRuleBuyoutPrice(rule, PriceConfig.none());
        return Result.OK;
    }

    public static Result setRuleBuyoutPriceItem(RuleType rule, String itemId, int amount) {
        if (!isValidItem(itemId)) return Result.INVALID_ITEM;
        PriceConfigManager.setRuleBuyoutPrice(rule, PriceConfig.singleItem(itemId, amount));
        return Result.OK;
    }

    public static Result setRuleBuyoutPriceDollars(RuleType rule, long amount) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return Result.COBBLE_DOLLARS_UNAVAILABLE;
        PriceConfigManager.setRuleBuyoutPrice(rule, PriceConfig.singleDollars(BigInteger.valueOf(amount)));
        return Result.OK;
    }

    // ---------------------------------------------------------------- Volle Mehrkomponenten-Preise (GUI, SetPriceRowPacket)

    /**
     * Setzt eine komplette Preis-Zeile (block ODER ein Regel-Name) auf EINMAL - genutzt vom neuen
     * Server-Konfigurations-Screen (ROADMAP.md Phase 6-Nachtrag 2, Punkt 6). {@code dollars} darf
     * {@code null}/{@code <= 0} sein (kein Dollars-Anteil) - wird aber, falls positiv UND
     * CobbleDollars nicht verfügbar ist, mit {@link Result#COBBLE_DOLLARS_UNAVAILABLE} abgelehnt
     * (fail closed, statt den Dollars-Anteil stillschweigend zu verwerfen). Jede Item-ID wird
     * geprüft - eine einzige ungültige ID lässt die GESAMTE Zeile fehlschlagen (nichts wird
     * teilweise übernommen).
     */
    public static Result setPriceRow(String target, BigInteger dollars, List<PriceConfig.ItemAmount> items, List<PriceConfig.Combinator> combinators) {
        if (dollars != null && dollars.signum() > 0 && !ModAvailability.isCobbleDollarsAvailable()) {
            return Result.COBBLE_DOLLARS_UNAVAILABLE;
        }
        for (PriceConfig.ItemAmount item : items) {
            if (!isValidItem(item.itemId())) return Result.INVALID_ITEM;
        }
        PriceConfig price = PriceConfig.of(dollars, items, combinators);
        if ("block".equals(target)) {
            PriceConfigManager.setPerBlockPrice(price);
        } else if ("subclaim".equals(target)) {
            PriceConfigManager.setSubClaimPrice(price);
        } else if ("block_refund".equals(target)) {
            PriceConfigManager.setPerBlockRefund(price);
        } else if ("subclaim_refund".equals(target)) {
            PriceConfigManager.setSubClaimRefund(price);
        } else {
            RuleType rule = parseRule(target);
            if (rule == null) return Result.INVALID_FEATURE;
            PriceConfigManager.setRuleBuyoutPrice(rule, price);
        }
        return Result.OK;
    }

    private static RuleType parseRule(String name) {
        try {
            return RuleType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static boolean isValidItem(String itemId) {
        ResourceLocation loc = itemId == null ? null : ResourceLocation.tryParse(itemId);
        return loc != null && BuiltInRegistries.ITEM.containsKey(loc);
    }
}
