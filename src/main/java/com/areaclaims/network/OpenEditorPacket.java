package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: Anweisung, den (Platzhalter-)Editor zu oeffnen. Kommandos laufen serverseitig
 * (auch im Singleplayer ueber den integrierten Server), das Oeffnen eines Screens muss deshalb per
 * Paket an genau den ausfuehrenden Spieler-Client zurueckgeschickt werden. Kein Payload-Inhalt
 * noetig fuer den Platzhalter-Editor - reiner Marker.
 */
public record OpenEditorPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenEditorPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            AreaClaims.MOD_ID, "open_editor"));

    public static final StreamCodec<ByteBuf, OpenEditorPacket> CODEC =
        StreamCodec.unit(new OpenEditorPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): der Verweis auf client-only Code hier wird auf dem Dedicated
    // Server nie ausgefuehrt (S2C-Paket) - gleiches Muster wie CreativeMenus OpenEditorPacket.
    public static void handle(OpenEditorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Der Editor ist NIE offen, während der Spieler im Klick-zum-Abstecken-Modus ist (siehe
            // ROADMAP.md Phase 7) - jedes Öffnen bedeutet also "Auswahl-Modus (falls aktiv) ist
            // vorbei" UND räumt ein eventuell noch angezeigtes Preisbestätigung-Popup auf (siehe
            // ClientPriceConfirmCache-Klassenkommentar - ein direkt danach eintreffendes
            // ShowPriceConfirmPacket überschreibt das wieder, falls tatsächlich eine Bestätigung ansteht).
            com.areaclaims.client.data.ClientSelectionState.setActive(false);
            com.areaclaims.client.data.ClientPriceConfirmCache.clear();
            com.areaclaims.client.ClientEditorHandler.openEditor();
        });
    }
}
