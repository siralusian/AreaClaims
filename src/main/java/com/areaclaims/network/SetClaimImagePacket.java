package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimEditService;
import com.areaclaims.claim.ClaimManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: weist einem Claim (Haupt- ODER Unterbereich) das Bild mit diesem Inhalts-Hash
 * zu, oder entfernt die Zuweisung ({@code hash} leer) - siehe {@link ClaimEditService#setClaimImage}.
 * Ausgelöst vom {@code AreaClaimsImagePickerScreen} (Upload-Ergebnis oder Galerie-Auswahl).
 *
 * <p><b>Nachtrag 6, Punkt 1 (Bug-Fund):</b> ruft nach Erfolg zusätzlich
 * {@link com.areaclaims.event.ClaimEntryListener#invalidateAll()} - ohne das würde ein Betrachter,
 * der BEREITS im betroffenen Claim steht, die neue Bild-Zuweisung erst nach Verlassen+Wiederbetreten
 * sehen (siehe dortigen Klassenkommentar für die volle Root-Cause-Herleitung).
 */
public record SetClaimImagePacket(String claimId, String hash) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetClaimImagePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "set_claim_image"));

    public static final StreamCodec<ByteBuf, SetClaimImagePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetClaimImagePacket::claimId,
        ByteBufCodecs.STRING_UTF8, SetClaimImagePacket::hash,
        SetClaimImagePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetClaimImagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller)) return;
            Claim claim;
            try {
                claim = ClaimManager.getById(UUID.fromString(packet.claimId()));
            } catch (IllegalArgumentException e) {
                return;
            }
            if (claim == null) return;
            ClaimEditService.Result result = ClaimEditService.setClaimImage(caller, claim, packet.hash());
            if (result == ClaimEditService.Result.OK) {
                ClaimSnapshotBuilder.sendTo(caller);
                com.areaclaims.event.ClaimEntryListener.invalidateAll();
            }
        });
    }
}
