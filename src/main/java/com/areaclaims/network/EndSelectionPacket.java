package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.StakingService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: manuelles "meine Auswahl ist fertig" (ROADMAP.md Phase 7 "Klick-zum-
 * Abstecken") - NUR für den einen Sonderfall gebraucht, den NeoForges {@code RightClickEmpty}-
 * Event laut eigenem Doc-Kommentar NIE an den Server meldet (Rechtsklick mit LEERER Hand in
 * offene Luft, kein Block/Entity in Reichweite) - siehe
 * {@code com.areaclaims.client.ClientSelectionEndListener}, der dieses Paket in genau diesem Fall
 * verschickt. Alle anderen Rechtsklick-Fälle (Block, Entity, gehaltenes Item) werden bereits
 * server-seitig direkt über die jeweiligen {@code PlayerInteractEvent}-Varianten erkannt (siehe
 * {@code ToolInteractionListener}) - dieses Paket ist dafür NICHT nötig, aber schadet auch nicht
 * (der Aufruf ist über {@link StakingService#end} ohnehin idempotent gegen "war gar nicht aktiv").
 */
public record EndSelectionPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EndSelectionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "end_selection"));

    public static final StreamCodec<ByteBuf, EndSelectionPacket> CODEC =
        StreamCodec.unit(new EndSelectionPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EndSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer caller) {
                StakingService.end(caller);
            }
        });
    }
}
