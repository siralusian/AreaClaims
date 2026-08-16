package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.economy.AdminConfigService;
import com.areaclaims.economy.PriceConfig;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server: setzt eine KOMPLETTE Preis-Zeile auf einmal (ROADMAP.md Phase 6-Nachtrag 2,
 * Punkt 6 "Preis-UI-Neugestaltung") - {@code target} = "block" oder ein {@code RuleType}-Name,
 * {@code json} = ein {@link ServerConfigSnapshot.PriceEntry}-JSON (gleiche DTO-Form wie im
 * Server->Client-Snapshot, siehe dort - Wiederverwendung spart eine zweite fast identische DTO-
 * Klasse). Ersetzt die alten {@code price_none}/{@code price_item}/{@code price_dollars}-Aktionen
 * von {@link SetServerConfigPacket} komplett - das alte NONE/ITEM/DOLLARS-Umschalt-Feld gibt es im
 * neuen Preis-Modell nicht mehr (ein leeres Item-Feld bedeutet jetzt einfach "kein Preis über
 * diesen Slot", siehe {@link PriceConfig}-Klassenkommentar). JSON-Payload statt
 * {@code StreamCodec.composite}, da die verschachtelte Item-Liste + Kombinator-Liste zu komplex
 * für Letzteres wäre (gleiches Muster wie {@link ClaimSyncPacket} für GUI-Sync-Daten).
 */
public record SetPriceRowPacket(String target, String json) implements CustomPacketPayload {

    private static final Gson GSON = new Gson();

    public static final CustomPacketPayload.Type<SetPriceRowPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "set_price_row"));

    public static final StreamCodec<ByteBuf, SetPriceRowPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetPriceRowPacket::target,
        ByteBufCodecs.STRING_UTF8, SetPriceRowPacket::json,
        SetPriceRowPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Baut das JSON aus den rohen GUI-Feldwerten - siehe {@link com.areaclaims.client.gui.AreaClaimsServerConfigScreen}. */
    public static String buildJson(String dollarsText, List<String> itemIds, List<String> itemAmounts, List<String> combinatorNames) {
        ServerConfigSnapshot.PriceEntry dto = new ServerConfigSnapshot.PriceEntry();
        dto.dollars = dollarsText == null ? "" : dollarsText.trim();
        dto.items = new ArrayList<>();
        for (int i = 0; i < itemIds.size(); i++) {
            String id = itemIds.get(i) == null ? "" : itemIds.get(i).trim();
            if (id.isEmpty()) continue;
            ServerConfigSnapshot.ItemEntry item = new ServerConfigSnapshot.ItemEntry();
            item.itemId = id;
            try {
                item.amount = Integer.parseInt(itemAmounts.get(i).trim());
            } catch (RuntimeException e) {
                item.amount = 1;
            }
            dto.items.add(item);
        }
        dto.combinators = new ArrayList<>(combinatorNames);
        return GSON.toJson(dto);
    }

    public static void handle(SetPriceRowPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller) || !caller.hasPermissions(4)) return;

            ServerConfigSnapshot.PriceEntry dto;
            try {
                dto = GSON.fromJson(packet.json(), ServerConfigSnapshot.PriceEntry.class);
            } catch (RuntimeException e) {
                return;
            }
            if (dto == null) return;

            BigInteger dollars;
            try {
                dollars = dto.dollars == null || dto.dollars.isBlank() ? null : new BigInteger(dto.dollars.trim());
            } catch (NumberFormatException e) {
                dollars = null;
            }
            List<PriceConfig.ItemAmount> items = new ArrayList<>();
            if (dto.items != null) {
                for (ServerConfigSnapshot.ItemEntry item : dto.items) {
                    items.add(new PriceConfig.ItemAmount(item.itemId, item.amount));
                }
            }
            List<PriceConfig.Combinator> combinators = new ArrayList<>();
            if (dto.combinators != null) {
                for (String c : dto.combinators) {
                    try {
                        combinators.add(PriceConfig.Combinator.valueOf(c));
                    } catch (IllegalArgumentException ignored) {
                        combinators.add(PriceConfig.Combinator.AND);
                    }
                }
            }

            AdminConfigService.setPriceRow(packet.target(), dollars, items, combinators);
            ServerConfigSnapshotBuilder.sendTo(caller);
        });
    }
}
