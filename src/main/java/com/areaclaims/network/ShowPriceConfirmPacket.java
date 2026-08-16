package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.client.data.ClientPriceConfirmCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: zeigt die Preisbestätigung-Übersicht für die gerade abgeschlossene Klick-zum-
 * Abstecken-Aktion (ROADMAP.md Phase 7-8) - wird IMMER direkt nach einem frischen
 * {@link ClaimSyncPacket} + {@link OpenEditorPacket} verschickt (der Editor ist also schon offen/
 * wird gerade geöffnet, wenn dieses Paket ankommt) - {@link AreaClaimsEditorScreen} liest den
 * Cache in seinem generation-basierten Neuaufbau-Mechanismus (siehe dortiges tick()).
 */
public record ShowPriceConfirmPacket(String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowPriceConfirmPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "show_price_confirm"));

    public static final StreamCodec<ByteBuf, ShowPriceConfirmPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(ShowPriceConfirmPacket::new, ShowPriceConfirmPacket::json);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShowPriceConfirmPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPriceConfirmCache.update(packet.json()));
    }
}
