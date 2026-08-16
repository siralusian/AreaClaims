package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimEditService;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.claim.ClaimRole;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: GUI-Editor-Äquivalent von {@code /areaclaims role <Claim> <Spieler> <Rolle>}.
 * Ruft exakt dieselbe {@link ClaimEditService#applyRole} auf wie der Chat-Befehl (siehe
 * ClaimEditService-Klassenkommentar) - "role" = "NONE" entfernt die Mitgliedschaft, genau wie
 * beim Befehl. Ziel-Claim wird über die UUID aus dem zuletzt gesendeten {@link ClaimSyncPacket}
 * aufgelöst (der Editor kennt nur Claims, die er selbst gerade angezeigt bekommen hat).
 */
public record SetRolePacket(String claimId, String playerName, String role) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetRolePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "set_role"));

    public static final StreamCodec<ByteBuf, SetRolePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetRolePacket::claimId,
        ByteBufCodecs.STRING_UTF8, SetRolePacket::playerName,
        ByteBufCodecs.STRING_UTF8, SetRolePacket::role,
        SetRolePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetRolePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller)) return;
            MinecraftServer server = caller.getServer();
            if (server == null) return;

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

            ServerPlayer target = server.getPlayerList().getPlayerByName(packet.playerName());
            if (target == null) {
                caller.displayClientMessage(Component.translatable("areaclaims.command.player_not_found", packet.playerName()), true);
                return;
            }

            ClaimRole role = ClaimEditService.parseRole(packet.role());
            ClaimEditService.Result result = ClaimEditService.applyRole(caller, claim, target, role);
            switch (result) {
                case OK -> {
                    caller.displayClientMessage(Component.translatable("areaclaims.command.role.set", packet.playerName(), claim.name(), role.name()), true);
                    ClaimSnapshotBuilder.sendTo(caller);
                }
                case INVALID_ROLE -> caller.displayClientMessage(Component.translatable("areaclaims.command.role.invalid", packet.role()), true);
                case NOT_AUTHORIZED -> caller.displayClientMessage(Component.translatable("areaclaims.command.no_permission"), true);
                case CANNOT_TARGET_OWNER -> caller.displayClientMessage(Component.translatable("areaclaims.command.role.cannot_target_owner"), true);
                default -> {}
            }
        });
    }
}
