package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.BuyoutService;
import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimEditService;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.claim.RuleType;
import com.areaclaims.economy.PriceCharger;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: GUI-Editor-Äquivalent von {@code /areaclaims buyout <Claim> <Regel>} - der
 * "Kaufen"-Button, der im Regel-Panel statt des normalen Rollen-Buttons erscheint, wenn ein Preis
 * konfiguriert UND die Regel noch nicht freigekauft ist (ROADMAP.md Phase 7-Nachtrag, Punkt 4).
 * Ruft dieselbe {@link BuyoutService#buyout} auf wie der Chat-Befehl.
 */
public record BuyoutRulePacket(String claimId, String rule) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuyoutRulePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "buyout_rule"));

    public static final StreamCodec<ByteBuf, BuyoutRulePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, BuyoutRulePacket::claimId,
        ByteBufCodecs.STRING_UTF8, BuyoutRulePacket::rule,
        BuyoutRulePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BuyoutRulePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller)) return;

            Claim claim;
            try {
                claim = ClaimManager.getById(UUID.fromString(packet.claimId()));
            } catch (IllegalArgumentException e) {
                return;
            }
            if (claim == null) {
                caller.displayClientMessage(Component.translatable("areaclaims.command.claim.not_found", packet.claimId()), true);
                return;
            }
            RuleType rule = ClaimEditService.parseRule(packet.rule());
            if (rule == null) return;

            BuyoutService.Outcome outcome = BuyoutService.buyout(caller, claim, rule);
            switch (outcome.result()) {
                case OK -> {
                    caller.displayClientMessage(Component.translatable("areaclaims.command.buyout.success", rule.name(), claim.name()), true);
                    ClaimSnapshotBuilder.sendTo(caller);
                }
                case ALREADY_BOUGHT_OUT -> caller.displayClientMessage(Component.translatable("areaclaims.command.buyout.already", rule.name(), claim.name()), true);
                case NOT_AUTHORIZED -> caller.displayClientMessage(Component.translatable("areaclaims.command.no_permission"), true);
                case PRICE_FAILURE -> caller.displayClientMessage(priceFailureMessage(outcome.priceResult()), true);
            }
        });
    }

    private static Component priceFailureMessage(PriceCharger.Result result) {
        return switch (result) {
            case INSUFFICIENT_ITEM -> Component.translatable("areaclaims.command.price.insufficient_item");
            case INSUFFICIENT_COBBLE_DOLLARS -> Component.translatable("areaclaims.command.price.insufficient_dollars");
            case INSUFFICIENT_PRICE -> Component.translatable("areaclaims.command.price.insufficient_price");
            case UNAVAILABLE -> Component.translatable("areaclaims.command.price.unavailable");
            case MISCONFIGURED -> Component.translatable("areaclaims.command.price.misconfigured");
            case OK -> Component.empty();
        };
    }
}
