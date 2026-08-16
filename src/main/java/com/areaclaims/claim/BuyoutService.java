package com.areaclaims.claim;

import com.areaclaims.data.FeatureConfigManager;
import com.areaclaims.economy.PriceCharger;
import com.areaclaims.economy.PriceConfigManager;
import com.areaclaims.integration.CobbleCompanionBridge;
import net.minecraft.server.level.ServerPlayer;

/**
 * Einzige Stelle, an der ein Regel-Freikauf (siehe {@link Claim#boughtOutRules()}) tatsächlich
 * validiert/bezahlt/angewendet wird - extrahiert aus {@code AreaClaimsCommands#buyoutRule}
 * (ROADMAP.md Phase 7-Nachtrag, Punkt 4 "Kaufen-Button") - sowohl der Chat-Befehl
 * {@code /areaclaims buyout} als auch das neue {@code BuyoutRulePacket} (GUI-"Kaufen"-Button)
 * rufen NUR {@link #buyout} auf, gleiches "einzige Quelle der Wahrheit"-Prinzip wie
 * {@code ClaimEditService}/{@code AdminConfigService}/{@code StakingService}.
 */
public final class BuyoutService {

    private BuyoutService() {}

    public enum Result { OK, NOT_AUTHORIZED, ALREADY_BOUGHT_OUT, PRICE_FAILURE }

    public record Outcome(Result result, PriceCharger.Result priceResult) {
        public static Outcome of(Result result) {
            return new Outcome(result, PriceCharger.Result.OK);
        }
    }

    public static Outcome buyout(ServerPlayer player, Claim claim, RuleType rule) {
        if (!FeatureConfigManager.canBuyoutRule(player)) return Outcome.of(Result.NOT_AUTHORIZED);
        if (!ClaimEditService.canEdit(player, claim)) return Outcome.of(Result.NOT_AUTHORIZED);
        if (claim.boughtOutRules().contains(rule)) return Outcome.of(Result.ALREADY_BOUGHT_OUT);

        PriceCharger.ChargeOutcome chargeOutcome = PriceCharger.chargeDetailed(player, PriceConfigManager.ruleBuyoutPrice(rule), 1);
        if (chargeOutcome.result() != PriceCharger.Result.OK) {
            return new Outcome(Result.PRICE_FAILURE, chargeOutcome.result());
        }
        if (chargeOutcome.dollarsCharged().signum() > 0) {
            CobbleCompanionBridge.logCharge(player, chargeOutcome.dollarsCharged(), "AreaClaims: Regel freigekauft (" + rule.name() + ")");
        }
        claim.boughtOutRules().add(rule);
        ClaimManager.save();
        return Outcome.of(Result.OK);
    }
}
