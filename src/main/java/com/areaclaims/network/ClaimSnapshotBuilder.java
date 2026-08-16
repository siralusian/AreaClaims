package com.areaclaims.network;

import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.claim.ClaimRole;
import com.areaclaims.claim.RuleSetting;
import com.areaclaims.claim.RuleType;
import com.areaclaims.data.AdminViewManager;
import com.areaclaims.data.ClaimShowcaseManager;
import com.areaclaims.economy.PriceConfig;
import com.areaclaims.economy.PriceConfigManager;
import com.google.gson.Gson;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Baut den {@link ClaimEditorSnapshot} für "alle Claims, die Spieler X besitzt" und verschickt
 * ihn als {@link ClaimSyncPacket}. Zentral hier statt verstreut in Commands/Paket-Handlern, damit
 * "vor dem Öffnen des Editors" (Command) und "nach jeder erfolgreichen Mutation" (Pakete) exakt
 * denselben Aufbau-Code nutzen.
 */
public final class ClaimSnapshotBuilder {

    private static final Gson GSON = new Gson();

    private ClaimSnapshotBuilder() {}

    /**
     * Im OP4-Admin-Modus ({@link AdminViewManager}) werden ALLE Claims des Servers geschickt statt
     * nur die eigenen/Mitgliedschafts-Claims (ROADMAP.md Phase 6-Nachtrag "Admin-GUI") - die
     * OP4-Prüfung läuft hier NOCHMAL (nicht nur beim Umschalten), falls jemandem zwischenzeitlich
     * OP entzogen wurde.
     */
    public static ClaimEditorSnapshot build(MinecraftServer server, ServerPlayer viewer) {
        boolean adminView = viewer.hasPermissions(4) && AdminViewManager.isActive(viewer.getUUID());
        List<Claim> claims = adminView ? ClaimManager.findAllClaims() : ClaimManager.findAccessibleClaims(viewer.getUUID());
        ClaimEditorSnapshot snapshot = new ClaimEditorSnapshot();
        snapshot.showcaseMode = ClaimShowcaseManager.mode(viewer.getUUID()).name();
        Integer colorOverride = ClaimShowcaseManager.colorOverride(viewer.getUUID());
        snapshot.boundaryColorOverride = colorOverride == null ? "" : String.format("%06X", colorOverride & 0xFFFFFF);
        for (Claim claim : claims) {
            ClaimEditorSnapshot.ClaimEntry entry = new ClaimEditorSnapshot.ClaimEntry();
            entry.id = claim.id().toString();
            entry.name = claim.name();
            entry.main = claim.isMain();
            entry.parentId = claim.parentId() == null ? null : claim.parentId().toString();
            entry.ownerName = resolveName(server, claim.owner());
            entry.area = claim.totalArea();
            entry.titleColor = claim.titleColor();
            entry.titleDurationTicks = claim.titleDurationTicks();
            entry.welcomeMessage = claim.welcomeMessage();
            entry.welcomeColor = claim.welcomeColor();
            entry.welcomeDurationTicks = claim.welcomeDurationTicks();
            entry.boundaryColor = claim.boundaryColor();
            entry.linkBoundaryColorToTitle = claim.linkBoundaryColorToTitle();
            entry.imageHash = claim.imageHash();

            for (Map.Entry<UUID, ClaimRole> m : claim.members().entrySet()) {
                ClaimEditorSnapshot.MemberEntry me = new ClaimEditorSnapshot.MemberEntry();
                me.uuid = m.getKey().toString();
                me.name = resolveName(server, m.getKey());
                me.role = m.getValue().name();
                entry.members.add(me);
            }

            // Alle 8 Regeltypen IMMER mitschicken (auch wenn noch nicht gesetzt = disabled), damit
            // der Editor eine vollständige, feste Liste zeichnen kann statt Lücken zu erraten.
            for (RuleType rule : RuleType.values()) {
                RuleSetting setting = claim.rules().getOrDefault(rule, RuleSetting.disabled());
                ClaimEditorSnapshot.RuleEntry re = new ClaimEditorSnapshot.RuleEntry();
                re.rule = rule.name();
                re.enabled = setting.enabled();
                re.minRole = setting.minRoleToIgnore().name();
                re.boughtOut = claim.boughtOutRules().contains(rule);
                // Nur mitschicken, wenn tatsächlich ein (nicht-kostenloser) Freikauf-Preis
                // konfiguriert ist - der Editor zeigt dann statt des Rollen-Buttons einen
                // "Kaufen"-Button (ROADMAP.md Phase 7-Nachtrag, Punkt 4).
                PriceConfig buyoutPrice = PriceConfigManager.ruleBuyoutPrice(rule);
                if (!re.boughtOut && !buyoutPrice.isFree()) {
                    re.buyoutPrice = toPriceDto(buyoutPrice);
                }
                entry.rules.add(re);
            }

            snapshot.claims.add(entry);
        }
        return snapshot;
    }

    private static ServerConfigSnapshot.PriceEntry toPriceDto(PriceConfig price) {
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

    private static String resolveName(MinecraftServer server, UUID uuid) {
        if (server.getProfileCache() != null) {
            var profile = server.getProfileCache().get(uuid);
            if (profile.isPresent()) return profile.get().getName();
        }
        return uuid.toString().substring(0, 8);
    }

    /** Baut den Snapshot und schickt ihn - Kanal-geprüft (siehe Klassenkommentar in AreaClaims.java) - an genau diesen Spieler. */
    public static void sendTo(ServerPlayer player) {
        if (player.getServer() == null) return;
        if (!NetworkRegistry.hasChannel(player.connection.getConnection(), ConnectionProtocol.PLAY, ClaimSyncPacket.TYPE.id())) {
            return;
        }
        String json = GSON.toJson(build(player.getServer(), player));
        PacketDistributor.sendToPlayer(player, new ClaimSyncPacket(json));
    }
}
