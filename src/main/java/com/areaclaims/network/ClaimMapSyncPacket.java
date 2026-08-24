package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client (an ALLE Online-Spieler, siehe {@link ClaimMapSnapshotBuilder#sendToAll}):
 * öffentliche Übersicht aller Claims für die JourneyMap-Kartenanzeige, siehe
 * {@link ClaimMapSnapshot}-Klassenkommentar für den vollen Hintergrund. JSON-String-Payload,
 * gleiches Muster wie {@link ShowcaseGeometrySyncPacket}.
 *
 * <p>Kein {@code @OnlyIn(Dist.CLIENT)}: siehe {@link OpenEditorPacket}-Klassenkommentar - der
 * Verweis auf client-only Code hier läuft auf dem Dedicated Server nie (S2C-Paket), der Handler
 * fasst außerdem nur eine reine Datenhalter-Klasse an (siehe {@code ClientClaimMapCache}).
 */
public record ClaimMapSyncPacket(String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimMapSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "claim_map_sync"));

    /** Siehe {@link ShowcaseGeometrySyncPacket} - deutlich über Vanillas 32767-Zeichen-Standard. */
    private static final int MAX_JSON_LENGTH = 500_000;

    public static final StreamCodec<ByteBuf, ClaimMapSyncPacket> CODEC =
        ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH).map(ClaimMapSyncPacket::new, ClaimMapSyncPacket::json);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClaimMapSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.areaclaims.client.data.ClientClaimMapCache.update(packet.json()));
    }
}
