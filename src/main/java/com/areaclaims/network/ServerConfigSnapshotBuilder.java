package com.areaclaims.network;

import com.areaclaims.claim.RuleType;
import com.areaclaims.data.FeatureConfigManager;
import com.areaclaims.economy.PriceConfig;
import com.areaclaims.economy.PriceConfigManager;
import com.areaclaims.integration.ModAvailability;
import com.google.gson.Gson;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Baut den {@link ServerConfigSnapshot} und verschickt ihn als {@link ServerConfigSyncPacket} - analog zu {@link ClaimSnapshotBuilder}. */
public final class ServerConfigSnapshotBuilder {

    private static final Gson GSON = new Gson();

    private ServerConfigSnapshotBuilder() {}

    public static ServerConfigSnapshot build() {
        ServerConfigSnapshot snapshot = new ServerConfigSnapshot();
        snapshot.minOpLevelCreateClaim = FeatureConfigManager.minOpLevelCreateClaim();
        snapshot.minOpLevelExpandClaim = FeatureConfigManager.minOpLevelExpandClaim();
        snapshot.minOpLevelBuyout = FeatureConfigManager.minOpLevelBuyout();
        snapshot.maxClaimParts = FeatureConfigManager.maxClaimParts();
        snapshot.tintRangeChunks = FeatureConfigManager.tintRangeChunks();
        snapshot.cobbleDollarsAvailable = ModAvailability.isCobbleDollarsAvailable();
        snapshot.perBlockPrice = toDto(PriceConfigManager.perBlockPrice());
        snapshot.subClaimPrice = toDto(PriceConfigManager.subClaimPrice());
        snapshot.perBlockPriceDivisor = PriceConfigManager.perBlockDivisor();
        snapshot.subClaimPriceDivisor = PriceConfigManager.subClaimDivisor();
        snapshot.perBlockRefund = toDto(PriceConfigManager.perBlockRefund());
        snapshot.subClaimRefund = toDto(PriceConfigManager.subClaimRefund());
        snapshot.perBlockRefundDivisor = PriceConfigManager.perBlockRefundDivisor();
        snapshot.subClaimRefundDivisor = PriceConfigManager.subClaimRefundDivisor();
        snapshot.wildernessMessageEnabled = FeatureConfigManager.wildernessMessageEnabled();
        snapshot.wildernessMessageText = FeatureConfigManager.wildernessMessageText();
        snapshot.journeyMapAvailable = ModAvailability.isJourneyMapAvailable();
        snapshot.journeyMapIntegrationEnabled = FeatureConfigManager.journeyMapIntegrationEnabled();
        for (RuleType rule : RuleType.values()) {
            ServerConfigSnapshot.RuleBuyoutPriceEntry entry = new ServerConfigSnapshot.RuleBuyoutPriceEntry();
            entry.rule = rule.name();
            entry.price = toDto(PriceConfigManager.ruleBuyoutPrice(rule));
            snapshot.ruleBuyoutPrices.add(entry);
        }
        return snapshot;
    }

    private static ServerConfigSnapshot.PriceEntry toDto(PriceConfig price) {
        ServerConfigSnapshot.PriceEntry dto = new ServerConfigSnapshot.PriceEntry();
        dto.dollars = price.dollars() != null ? price.dollars().toString() : "0";
        for (PriceConfig.ItemAmount item : price.items()) {
            ServerConfigSnapshot.ItemEntry itemDto = new ServerConfigSnapshot.ItemEntry();
            itemDto.itemId = item.itemId();
            itemDto.amount = item.amount();
            dto.items.add(itemDto);
        }
        for (PriceConfig.Combinator c : price.normalized().combinators()) dto.combinators.add(c.name());
        return dto;
    }

    /** Kanal-geprüft (siehe Klassenkommentar in AreaClaims.java) - nur an genau diesen Spieler. */
    public static void sendTo(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection.getConnection(), ConnectionProtocol.PLAY, ServerConfigSyncPacket.TYPE.id())) {
            return;
        }
        String json = GSON.toJson(build());
        PacketDistributor.sendToPlayer(player, new ServerConfigSyncPacket(json));
    }
}
