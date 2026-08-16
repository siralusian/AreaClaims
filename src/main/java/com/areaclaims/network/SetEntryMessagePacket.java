package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.claim.EntryMessageService;
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
 * Client -> Server: GUI-Editor-Äquivalent von {@code /areaclaims entrymsg} (ROADMAP.md Phase
 * 6-Nachtrag 2, Punkt 2 "Betreten-Nachricht-GUI") - EIN generisches Feld-Paket statt sieben fast
 * identischer (gleiches Muster wie {@link SetServerConfigPacket}). {@code field} entscheidet, WAS
 * {@code stringArg}/{@code longArg} bedeuten - siehe {@link EntryMessageService}-Klassenkommentar
 * für die vollständige Liste. Ruft für die eigentliche Validierung/Mutation NUR
 * {@link EntryMessageService#apply} auf - dieselbe Stelle, die auch {@code /areaclaims entrymsg}
 * (Chat-Befehl) nutzt.
 */
public record SetEntryMessagePacket(String claimId, String field, String stringArg, long longArg) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetEntryMessagePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "set_entry_message"));

    public static final StreamCodec<ByteBuf, SetEntryMessagePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetEntryMessagePacket::claimId,
        ByteBufCodecs.STRING_UTF8, SetEntryMessagePacket::field,
        ByteBufCodecs.STRING_UTF8, SetEntryMessagePacket::stringArg,
        ByteBufCodecs.VAR_LONG, SetEntryMessagePacket::longArg,
        SetEntryMessagePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetEntryMessagePacket packet, IPayloadContext context) {
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

            EntryMessageService.Result result = EntryMessageService.apply(caller, claim, packet.field(), packet.stringArg(), packet.longArg());
            switch (result) {
                case OK -> {
                    caller.displayClientMessage(Component.translatable("areaclaims.command.entrymsg.set", claim.name()), true);
                    ClaimSnapshotBuilder.sendTo(caller);
                }
                case INVALID_COLOR -> caller.displayClientMessage(Component.translatable("areaclaims.command.entrymsg.invalid_color"), true);
                case NOT_AUTHORIZED -> caller.displayClientMessage(Component.translatable("areaclaims.command.no_permission"), true);
                case INVALID_FIELD -> {}
            }
        });
    }
}
