package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: Zustand EINES Anzeige-"Slots" (MAIN/SUB/WELCOME) der Betreten-Nachricht
 * (ROADMAP.md Phase 7-Nachtrag 3, Punkte 13-15 "Betreten-Nachricht-Overhaul") - ersetzt die
 * frühere Nutzung von {@code ClientboundSetTitleTextPacket}/{@code ClientboundSetSubtitleTextPacket}/
 * simulierter Actionbar-Wiederholung KOMPLETT durch ein eigenes, per-Slot unabhängig
 * positionier-/zeitsteuerbares Overlay (siehe {@code com.areaclaims.client.EntryDisplayOverlayRenderer}).
 *
 * <p><b>Warum ein eigenes Overlay auch für den "nicht-dauerhaften" Fall</b> (Judgment-Call, siehe
 * ausführliche Begründung in {@link com.areaclaims.display.PlayerDisplayPreferences}s
 * Klassenkommentar): Vanillas Titel/Untertitel-Pakete kennen KEINE Position - eine konfigurierbare
 * Position wäre über sie in keinem Modus umsetzbar gewesen.
 *
 * <p>JSON-Payload statt {@code StreamCodec.composite} - mit inzwischen 10 Feldern (siehe {@link Dto})
 * weit jenseits der von {@code StreamCodec.composite} unterstützten Obergrenze von 6 (verifiziert
 * in den dekompilierten {@code StreamCodec.java}-Sourcen) - gleiches Muster wie
 * {@link SetPriceRowPacket} für vergleichbar komplexe Payloads.
 *
 * <p><b>Punkt 2 (Nachtrag 4) - "zeitgesteuert DANN dauerhaft" statt "entweder/oder":</b> ein Slot
 * zeigt beim Betreten IMMER zuerst den normalen zeitgesteuerten Text an seiner normalen Position
 * für {@link Dto#durationTicks}. NUR wenn der Betrachter für diesen Slot "dauerhaft anzeigen"
 * aktiviert hat ({@link Dto#permanentAfter}), wechselt die Anzeige NACH Ablauf dieser Dauer
 * automatisch an {@link Dto#permanentPosX}/{@link Dto#permanentPosY} und bleibt dort, bis der
 * Bereich verlassen/gewechselt wird. Der komplette Übergang läuft rein CLIENT-seitig anhand eines
 * einzigen, beim Betreten gesendeten Pakets (siehe {@code EntryDisplayOverlayRenderer}) - kein
 * zweites Serverpaket zum Zeitpunkt des Übergangs nötig.
 */
public record EntryDisplaySlotPacket(String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EntryDisplaySlotPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "entry_display_slot"));

    public static final StreamCodec<ByteBuf, EntryDisplaySlotPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(EntryDisplaySlotPacket::new, EntryDisplaySlotPacket::json);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Reine Transport-DTO (Gson) - siehe Klassenkommentar. */
    public static class Dto {
        public String slot;
        public boolean visible;
        public String text = "";
        public int color = 0xFFFFFF;
        /** Position der ZEITGESTEUERTEN Phase (immer zuerst gezeigt, siehe Klassenkommentar "Punkt 2"). */
        public int posX;
        public int posY;
        public int durationTicks;
        /** Wechselt die Anzeige NACH {@link #durationTicks} zu {@link #permanentPosX}/{@link #permanentPosY} und bleibt dort - statt einfach zu verschwinden. */
        public boolean permanentAfter;
        public int permanentPosX;
        public int permanentPosY;
        /** Punkt 6 (Nachtrag 4): Inhalts-Hash eines vom Besitzer zugewiesenen Bilds (leer = keins) - siehe {@code Claim#imageHash}. Nur MAIN/SUB relevant, WELCOME schickt immer leer. */
        public String imageHash = "";
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe OpenEditorPacket-Klassenkommentar - der Verweis auf
    // client-only Code hier wird auf dem Dedicated Server nie ausgeführt (S2C-Paket).
    public static void handle(EntryDisplaySlotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.areaclaims.client.EntryDisplayOverlayRenderer.applyFromServer(packet.json()));
    }
}
