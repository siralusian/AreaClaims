package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimEditService;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.claim.ClaimRole;
import com.areaclaims.claim.RuleType;
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
 * Client -> Server: GUI-Editor-Äquivalent von
 * {@code /areaclaims rule <Claim> <Regel> enable <MindestRolle>|disable}. Ruft exakt dieselbe
 * {@link ClaimEditService#applyRule} auf wie der Chat-Befehl (siehe ClaimEditService-
 * Klassenkommentar). {@code minRole} wird bei {@code enabled=false} ignoriert (Server setzt dann
 * ohnehin COOWNER als neutralen Wert, siehe ClaimEditService#applyRule).
 */
public record SetRulePacket(String claimId, String rule, boolean enabled, String minRole) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetRulePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "set_rule"));

    public static final StreamCodec<ByteBuf, SetRulePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetRulePacket::claimId,
        ByteBufCodecs.STRING_UTF8, SetRulePacket::rule,
        ByteBufCodecs.BOOL, SetRulePacket::enabled,
        ByteBufCodecs.STRING_UTF8, SetRulePacket::minRole,
        SetRulePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetRulePacket packet, IPayloadContext context) {
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
            ClaimRole minRole = ClaimEditService.parseRole(packet.minRole());
            ClaimEditService.Result result = ClaimEditService.applyRule(caller, claim, rule, packet.enabled(), minRole);
            switch (result) {
                case OK -> {
                    caller.displayClientMessage(Component.translatable(
                        packet.enabled() ? "areaclaims.command.rule.set_enabled" : "areaclaims.command.rule.set_disabled",
                        packet.rule(), claim.name(), packet.minRole()), true);
                    ClaimSnapshotBuilder.sendTo(caller);
                }
                case INVALID_RULE -> caller.displayClientMessage(Component.translatable("areaclaims.command.rule.invalid", packet.rule()), true);
                case NOT_AUTHORIZED -> caller.displayClientMessage(Component.translatable("areaclaims.command.no_permission"), true);
                case RULE_NOT_BOUGHT_OUT -> caller.displayClientMessage(Component.translatable("areaclaims.command.rule.not_bought_out"), true);
                default -> {}
            }
        });
    }
}
