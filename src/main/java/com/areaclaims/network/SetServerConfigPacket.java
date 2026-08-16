package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.economy.AdminConfigService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: generisches Admin-Konfigurations-Paket für die Freischaltungsschwellen und die
 * Teile-Obergrenze (ROADMAP.md Phase 6-Nachtrag "Admin-GUI"). {@code action} entscheidet, WAS
 * {@code target} bedeutet:
 * <ul>
 *   <li>{@code "feature"} - target = "tool"/"createclaim"/"expandclaim"/"buyout", longArg = neue OP-Stufe (0-4)</li>
 *   <li>{@code "maxparts"} - target wird ignoriert, longArg = neue Teile-Obergrenze</li>
 *   <li>{@code "tintrange"} - target wird ignoriert, longArg = neue Block-Einfärbung-Sichtweite in Chunks</li>
 *   <li>{@code "pricedivisor"} - target = "block"/"subclaim", longArg = neuer Teiler ("X pro N Blöcke", Punkt 12 Nachtrag 3)</li>
 *   <li>{@code "wildernessmsgenabled"} - target wird ignoriert, longArg = 0/1 (Punkt 1 Nachtrag 4)</li>
 *   <li>{@code "wildernessmsgtext"} - target wird ignoriert, stringArg = neuer Text (Punkt 1 Nachtrag 4)</li>
 *   <li>{@code "journeymapenabled"} - target wird ignoriert, longArg = 0/1 (JourneyMap-Integration, 2026-08-16)</li>
 * </ul>
 * Preise laufen seit ROADMAP.md Phase 6-Nachtrag 2 ("Punkt 6: Preis-UI-Neugestaltung") NICHT mehr
 * über dieses Paket, sondern über das eigene {@link SetPriceRowPacket} (die alte NONE/ITEM/DOLLARS-
 * Typ-Umschaltung, die dieses Paket vorher abbildete, gibt es im neuen Preis-Modell nicht mehr -
 * siehe dortigen Klassenkommentar). Ruft für die eigentliche Validierung/Mutation NUR
 * {@link AdminConfigService} auf - dieselbe Stelle, die auch {@code /areaclaims config} (Chat-
 * Befehl) nutzt.
 */
public record SetServerConfigPacket(String action, String target, String stringArg, long longArg) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetServerConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "set_server_config"));

    public static final StreamCodec<ByteBuf, SetServerConfigPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetServerConfigPacket::action,
        ByteBufCodecs.STRING_UTF8, SetServerConfigPacket::target,
        ByteBufCodecs.STRING_UTF8, SetServerConfigPacket::stringArg,
        ByteBufCodecs.VAR_LONG, SetServerConfigPacket::longArg,
        SetServerConfigPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetServerConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller) || !caller.hasPermissions(4)) return;

            switch (packet.action()) {
                case "feature" -> AdminConfigService.setFeatureLevel(packet.target(), (int) packet.longArg());
                case "maxparts" -> AdminConfigService.setMaxClaimParts((int) packet.longArg());
                case "tintrange" -> AdminConfigService.setTintRangeChunks((int) packet.longArg());
                case "pricedivisor" -> AdminConfigService.setPriceDivisor(packet.target(), (int) packet.longArg());
                case "wildernessmsgenabled" -> AdminConfigService.setWildernessMessageEnabled(packet.longArg() != 0);
                case "wildernessmsgtext" -> AdminConfigService.setWildernessMessageText(packet.stringArg());
                case "journeymapenabled" -> AdminConfigService.setJourneyMapIntegrationEnabled(packet.longArg() != 0);
                default -> {}
            }

            ServerConfigSnapshotBuilder.sendTo(caller);
        });
    }
}
