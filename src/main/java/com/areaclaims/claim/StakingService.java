package com.areaclaims.claim;

import com.areaclaims.data.ActiveSelectionManager;
import com.areaclaims.data.ClaimShowcaseManager;
import com.areaclaims.data.FeatureConfigManager;
import com.areaclaims.data.PendingActionManager;
import com.areaclaims.data.SelectionManager;
import com.areaclaims.economy.PriceCharger;
import com.areaclaims.economy.PriceConfig;
import com.areaclaims.economy.PriceConfigManager;
import com.areaclaims.integration.CobbleCompanionBridge;
import com.areaclaims.geometry.PolygonUtil;
import com.areaclaims.geometry.Vertex;
import com.areaclaims.network.ClaimSnapshotBuilder;
import com.areaclaims.network.EnterSelectionModePacket;
import com.areaclaims.network.OpenEditorPacket;
import com.areaclaims.network.PriceConfirmSnapshot;
import com.areaclaims.network.ServerConfigSnapshot;
import com.areaclaims.network.ShowPriceConfirmPacket;
import com.areaclaims.network.ShowcaseGeometrySnapshotBuilder;
import com.google.gson.Gson;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * Zentrale Ablaufsteuerung für "Klick-zum-Abstecken" (ROADMAP.md Phase 7 - ersetzt den goldene-
 * Hacke-Werkzeug-Modus komplett) UND die Preisbestätigung, die danach folgen kann (ROADMAP.md
 * Phase 8) - EINZIGE Stelle, an der Claims/Erweiterungen/Unterbereiche über diesen neuen Weg
 * tatsächlich validiert, bepreist und angelegt werden (analog zum "einzige Quelle der Wahrheit"-
 * Prinzip von {@link ClaimEditService}/{@code AdminConfigService}).
 *
 * <p><b>Ablauf (siehe {@code ToolInteractionListener} für die Klick-Erkennung selbst):</b>
 * <ol>
 *   <li>Spieler klickt "Erweitern"/"Neuer SubClaim"/"Neuer Claim" im Editor -> {@code BeginSelectionPacket}
 *       -> {@link #begin} - entweder wird eine GEMERKTE Auswahl direkt zur Preisbestätigung
 *       geführt ({@link #showPriceConfirm}), oder der Spieler wechselt in den Punkte-Platzier-Modus
 *       ({@link ActiveSelectionManager}, Editor schließt sich, siehe {@link EnterSelectionModePacket}).</li>
 *   <li>Linksklicks fügen Punkte hinzu ({@link SelectionManager}, siehe ToolInteractionListener),
 *       ein Rechtsklick beendet die Auswahl -> {@link #end}: Geometrie wird OHNE Mutation geprüft
 *       (siehe {@code ClaimManager#validateNewMainClaim} & Co - der Claim soll erst nach bezahlter/
 *       kostenloser Bestätigung tatsächlich existieren, siehe dortigen Klassenkommentar), dann:
 *       <ul>
 *         <li>kein Preis konfiguriert -> sofort committen ({@link #commit}), Editor wieder öffnen.</li>
 *         <li>Preis konfiguriert -> Auswahl in {@link PendingActionManager} ablegen, Preisbestätigung
 *             zeigen ({@link #showPriceConfirm}), Editor wieder öffnen (Popup liegt darüber).</li>
 *       </ul></li>
 *   <li>Im Preisbestätigung-Popup: "Bestätigen" -> {@link #confirmPending} (committet + belastet
 *       JETZT erst tatsächlich); "Abbrechen" -> {@link #resumePending} (zurück in den Platzier-
 *       Modus, Punkte bleiben erhalten); "Auswahl merken" -> rein clientseitig, kein Server-Aufruf
 *       nötig (die Auswahl liegt bereits in {@link PendingActionManager}); "Beenden" ->
 *       {@link #discardPending} (verwirft alles).</li>
 * </ol>
 */
public final class StakingService {

    private static final Gson GSON = new Gson();

    private StakingService() {}

    public enum BeginResult { STARTED_PLACEMENT, RESUMED_PENDING, NOT_AUTHORIZED, TARGET_NOT_FOUND }

    /** Kleines lokales Ergebnis-Bündel für {@link #validate} - vereinheitlicht die verschiedenen ClaimManager-Ergebnis-Enums auf "ok?" + fertige Fehlermeldung. */
    private record ValidateOutcome(boolean ok, Component failureMessage) {}

    // ---------------------------------------------------------------- Beginnen

    public static BeginResult begin(ServerPlayer player, ActiveSelectionManager.ActionType action, UUID targetClaimId) {
        Claim target = null;
        switch (action) {
            case NEW_CLAIM -> {
                if (!FeatureConfigManager.canCreateClaim(player)) return BeginResult.NOT_AUTHORIZED;
            }
            case EXPAND_MAIN, EXPAND_SUB -> {
                if (!FeatureConfigManager.canExpandClaim(player)) return BeginResult.NOT_AUTHORIZED;
                target = ClaimManager.getById(targetClaimId);
                if (target == null) return BeginResult.TARGET_NOT_FOUND;
                if (!ClaimEditService.canEdit(player, target)) return BeginResult.NOT_AUTHORIZED;
            }
            case NEW_SUBCLAIM -> {
                if (!FeatureConfigManager.canExpandClaim(player)) return BeginResult.NOT_AUTHORIZED;
                target = ClaimManager.getById(targetClaimId);
                if (target == null) return BeginResult.TARGET_NOT_FOUND;
                if (!ClaimEditService.canEdit(player, target)) return BeginResult.NOT_AUTHORIZED;
            }
        }

        PendingActionManager.PendingAction pending = PendingActionManager.find(player.getUUID(), action, targetClaimId);
        if (pending != null) {
            showPriceConfirm(player, action, targetClaimId, pending.points());
            return BeginResult.RESUMED_PENDING;
        }

        SelectionManager.clear(player.getUUID());
        ActiveSelectionManager.start(player.getUUID(), action, targetClaimId);

        // Nutzer-Vorgabe (ROADMAP.md Phase 7, Punkt 7): beim Abstecken eines NEUEN Unterbereichs
        // sollen die Grenzen des Hauptbereichs UND aller anderen Unterbereiche sichtbar sein, damit
        // der Spieler sieht, was schon belegt ist - im aktuell aktiven Anzeigestil (Partikel/Tint).
        if (action == ActiveSelectionManager.ActionType.NEW_SUBCLAIM && target != null) {
            ClaimShowcaseManager.ensureShown(player.getUUID(), target.id());
            for (Claim c : ClaimManager.findAllClaims()) {
                if (!c.isMain() && target.id().equals(c.parentId())) {
                    ClaimShowcaseManager.ensureShown(player.getUUID(), c.id());
                }
            }
            ShowcaseGeometrySnapshotBuilder.sendTo(player);
        }

        PacketDistributor.sendToPlayer(player, new EnterSelectionModePacket());
        return BeginResult.STARTED_PLACEMENT;
    }

    // ---------------------------------------------------------------- Beenden (Rechtsklick)

    /** Aufgerufen, wenn der Spieler im aktiven Auswahl-Modus rechtsklickt (siehe ToolInteractionListener). */
    public static void end(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ActiveSelectionManager.ActiveState state = ActiveSelectionManager.get(uuid);
        if (state == null) return;
        ActiveSelectionManager.stop(uuid);
        List<Vertex> points = List.copyOf(SelectionManager.get(uuid));
        SelectionManager.clear(uuid);

        double area = PolygonUtil.area(points);
        int blocks = (int) Math.ceil(area);
        PriceConfig price = priceFor(state.action());

        ValidateOutcome outcome = validate(state.action(), state.targetClaimId(), player, points);
        if (!outcome.ok()) {
            player.displayClientMessage(outcome.failureMessage(), true);
            reopenEditor(player);
            return;
        }

        if (price.isFree()) {
            commit(player, state.action(), state.targetClaimId(), points);
            reopenEditor(player);
        } else {
            PendingActionManager.remember(uuid, state.action(), state.targetClaimId(), points);
            showPriceConfirm(player, state.action(), state.targetClaimId(), points);
        }
    }

    // ---------------------------------------------------------------- Popup-Aktionen

    public static void confirmPending(ServerPlayer player) {
        PendingActionManager.PendingAction pending = PendingActionManager.get(player.getUUID());
        if (pending == null) return;
        boolean success = commit(player, pending.action(), pending.targetClaimId(), pending.points());
        if (success) PendingActionManager.clear(player.getUUID());
        reopenEditor(player);
    }

    /** "Abbrechen" - zurück in den Punkte-Platzier-Modus, bereits gesetzte Punkte bleiben erhalten. */
    public static void resumePending(ServerPlayer player) {
        PendingActionManager.PendingAction pending = PendingActionManager.get(player.getUUID());
        if (pending == null) return;
        PendingActionManager.clear(player.getUUID());
        UUID uuid = player.getUUID();
        SelectionManager.clear(uuid);
        for (Vertex v : pending.points()) SelectionManager.addPoint(uuid, v.x(), v.z());
        ActiveSelectionManager.start(uuid, pending.action(), pending.targetClaimId());
        PacketDistributor.sendToPlayer(player, new EnterSelectionModePacket());
    }

    /** "Beenden" - verwirft die gemerkte Auswahl vollständig. */
    public static void discardPending(ServerPlayer player) {
        PendingActionManager.clear(player.getUUID());
        ActiveSelectionManager.stop(player.getUUID());
        SelectionManager.clear(player.getUUID());
        ClaimSnapshotBuilder.sendTo(player);
    }

    // ---------------------------------------------------------------- Validierung/Commit je Aktionstyp

    private static ValidateOutcome validate(ActiveSelectionManager.ActionType action, UUID targetClaimId, ServerPlayer player, List<Vertex> points) {
        String dimension = player.level().dimension().location().toString();
        return switch (action) {
            case NEW_CLAIM -> {
                ClaimManager.CreateResult result = ClaimManager.validateNewMainClaim(player.getUUID(), dimension, points);
                yield new ValidateOutcome(result == ClaimManager.CreateResult.OK, createFailureMessage(result));
            }
            case EXPAND_MAIN, EXPAND_SUB -> {
                Claim target = ClaimManager.getById(targetClaimId);
                if (target == null) yield new ValidateOutcome(false, Component.translatable("areaclaims.command.claim.not_found", "?"));
                ClaimManager.AddPartResult result = ClaimManager.validateAddPart(target, points);
                yield new ValidateOutcome(result == ClaimManager.AddPartResult.OK, addPartFailureMessage(result));
            }
            case NEW_SUBCLAIM -> {
                Claim parent = ClaimManager.getById(targetClaimId);
                if (parent == null) yield new ValidateOutcome(false, Component.translatable("areaclaims.command.claim.not_found", "?"));
                ClaimManager.CreateResult result = ClaimManager.validateNewSubClaim(parent, points);
                yield new ValidateOutcome(result == ClaimManager.CreateResult.OK, createFailureMessage(result));
            }
        };
    }

    /** @return true bei Erfolg (Claim wurde tatsächlich angelegt/erweitert UND bezahlt). */
    private static boolean commit(ServerPlayer player, ActiveSelectionManager.ActionType action, UUID targetClaimId, List<Vertex> points) {
        return switch (action) {
            case NEW_CLAIM -> commitNewClaim(player, points);
            case EXPAND_MAIN, EXPAND_SUB -> commitExpand(player, targetClaimId, points);
            case NEW_SUBCLAIM -> commitNewSubClaim(player, targetClaimId, points);
        };
    }

    private static boolean commitNewClaim(ServerPlayer player, List<Vertex> points) {
        String dimension = player.level().dimension().location().toString();
        String name = ClaimManager.nextAutoMainClaimName(player.getUUID());
        ClaimManager.CreateOutcome outcome = ClaimManager.createMainClaim(name, player.getUUID(), dimension, points);
        if (outcome.result() != ClaimManager.CreateResult.OK) {
            player.displayClientMessage(createFailureMessage(outcome.result()), true);
            return false;
        }
        int blocks = (int) Math.ceil(outcome.claim().totalArea());
        int units = PriceCharger.chargeUnits(blocks, PriceConfigManager.perBlockDivisor());
        PriceCharger.ChargeOutcome chargeOutcome = PriceCharger.chargeDetailed(player, PriceConfigManager.perBlockPrice(), units);
        if (chargeOutcome.result() != PriceCharger.Result.OK) {
            ClaimManager.deleteClaim(outcome.claim().id());
            player.displayClientMessage(priceFailureMessage(chargeOutcome.result()), true);
            return false;
        }
        logWalletCharge(player, chargeOutcome, "AreaClaims: Claim erstellt (" + blocks + " Blöcke)");
        player.displayClientMessage(Component.translatable("areaclaims.command.claim.success", name, points.size(), String.format("%.1f", outcome.claim().totalArea())), true);
        return true;
    }

    private static boolean commitExpand(ServerPlayer player, UUID targetClaimId, List<Vertex> points) {
        Claim claim = ClaimManager.getById(targetClaimId);
        if (claim == null) {
            player.displayClientMessage(Component.translatable("areaclaims.command.claim.not_found", "?"), true);
            return false;
        }
        ClaimManager.AddPartResult result = ClaimManager.addClaimPart(claim, points);
        if (result != ClaimManager.AddPartResult.OK) {
            player.displayClientMessage(addPartFailureMessage(result), true);
            return false;
        }
        // Punkt 11 (Nachtrag 3): claim.isMain() entscheidet zuverlässig Haupt- vs. SubClaim-Preis -
        // targetClaimId ist bei EXPAND_SUB die Unterbereichs-ID selbst (siehe begin()/buildSubColumn),
        // NICHT die des Hauptbereichs, also liest claim.isMain() hier immer den richtigen Preis-Typ.
        int newPartBlocks = (int) Math.ceil(PolygonUtil.area(points));
        PriceConfig expandPrice = claim.isMain() ? PriceConfigManager.perBlockPrice() : PriceConfigManager.subClaimPrice();
        int divisor = claim.isMain() ? PriceConfigManager.perBlockDivisor() : PriceConfigManager.subClaimDivisor();
        int units = PriceCharger.chargeUnits(newPartBlocks, divisor);
        PriceCharger.ChargeOutcome chargeOutcome = PriceCharger.chargeDetailed(player, expandPrice, units);
        if (chargeOutcome.result() != PriceCharger.Result.OK) {
            ClaimManager.removeLastPart(claim);
            player.displayClientMessage(priceFailureMessage(chargeOutcome.result()), true);
            return false;
        }
        logWalletCharge(player, chargeOutcome, "AreaClaims: Bereich erweitert (" + newPartBlocks + " Blöcke)");
        player.displayClientMessage(Component.translatable("areaclaims.command.expand.success", claim.name(), claim.parts().size(), String.format("%.1f", claim.totalArea())), true);
        return true;
    }

    private static boolean commitNewSubClaim(ServerPlayer player, UUID parentId, List<Vertex> points) {
        Claim parent = ClaimManager.getById(parentId);
        if (parent == null) {
            player.displayClientMessage(Component.translatable("areaclaims.command.claim.not_found", "?"), true);
            return false;
        }
        String name = ClaimManager.nextAutoSubClaimName(parent);
        ClaimManager.CreateOutcome outcome = ClaimManager.createSubClaim(parent, name, points);
        if (outcome.result() != ClaimManager.CreateResult.OK) {
            player.displayClientMessage(createFailureMessage(outcome.result()), true);
            return false;
        }
        int blocks = (int) Math.ceil(outcome.claim().totalArea());
        int units = PriceCharger.chargeUnits(blocks, PriceConfigManager.subClaimDivisor());
        PriceCharger.ChargeOutcome chargeOutcome = PriceCharger.chargeDetailed(player, PriceConfigManager.subClaimPrice(), units);
        if (chargeOutcome.result() != PriceCharger.Result.OK) {
            ClaimManager.deleteClaim(outcome.claim().id());
            player.displayClientMessage(priceFailureMessage(chargeOutcome.result()), true);
            return false;
        }
        logWalletCharge(player, chargeOutcome, "AreaClaims: Unterbereich erstellt (" + blocks + " Blöcke)");
        player.displayClientMessage(Component.translatable("areaclaims.command.subclaim.success", name, parent.name()), true);
        return true;
    }

    /** Nachtrag 7, Punkt 2: schreibt einen Wallet-Log-Eintrag NUR, wenn tatsächlich ein CobbleDollars-Betrag belastet wurde (0 bei reinem Item-Preis oder falls eine ODER-Kette stattdessen die Item-Komponente bezahlt hat, siehe {@link PriceCharger.ChargeOutcome}). */
    private static void logWalletCharge(ServerPlayer player, PriceCharger.ChargeOutcome outcome, String description) {
        if (outcome.dollarsCharged().signum() > 0) {
            CobbleCompanionBridge.logCharge(player, outcome.dollarsCharged(), description);
        }
    }

    // ---------------------------------------------------------------- Fehlermeldungen

    private static Component createFailureMessage(ClaimManager.CreateResult result) {
        return switch (result) {
            case TOO_FEW_POINTS -> Component.translatable("areaclaims.command.claim.too_few_points");
            case TOO_MANY_POINTS -> Component.translatable("areaclaims.command.claim.too_many_points_selection", PolygonUtil.MAX_POINTS_PER_PART);
            case SELF_INTERSECTING -> Component.translatable("areaclaims.command.claim.self_intersecting");
            case OVERLAPS_EXISTING -> Component.translatable("areaclaims.command.claim.overlaps");
            case OUTSIDE_PARENT -> Component.translatable("areaclaims.command.claim.outside_parent");
            case NAME_TAKEN -> Component.translatable("areaclaims.command.rename.name_taken");
            case OK -> Component.empty();
        };
    }

    private static Component addPartFailureMessage(ClaimManager.AddPartResult result) {
        return switch (result) {
            case TOO_FEW_POINTS -> Component.translatable("areaclaims.command.claim.too_few_points");
            case TOO_MANY_POINTS -> Component.translatable("areaclaims.command.claim.too_many_points_selection", PolygonUtil.MAX_POINTS_PER_PART);
            case SELF_INTERSECTING -> Component.translatable("areaclaims.command.claim.self_intersecting");
            case OVERLAPS_EXISTING -> Component.translatable("areaclaims.command.claim.overlaps");
            case OUTSIDE_PARENT -> Component.translatable("areaclaims.command.claim.outside_parent");
            case TOO_MANY_PARTS -> Component.translatable("areaclaims.command.claim.too_many_parts", FeatureConfigManager.maxClaimParts());
            case OK -> Component.empty();
        };
    }

    private static Component priceFailureMessage(PriceCharger.Result result) {
        return switch (result) {
            case INSUFFICIENT_ITEM -> Component.translatable("areaclaims.command.price.insufficient_item");
            case INSUFFICIENT_COBBLE_DOLLARS -> Component.translatable("areaclaims.command.price.insufficient_dollars");
            case INSUFFICIENT_PRICE -> Component.translatable("areaclaims.command.price.insufficient_price");
            case UNAVAILABLE -> Component.translatable("areaclaims.command.price.unavailable");
            case MISCONFIGURED -> Component.translatable("areaclaims.command.price.misconfigured");
            case OK -> Component.empty();
        };
    }

    // ---------------------------------------------------------------- Netzwerk-Hilfen

    private static void reopenEditor(ServerPlayer player) {
        ClaimSnapshotBuilder.sendTo(player);
        PacketDistributor.sendToPlayer(player, new OpenEditorPacket());
    }

    private static void showPriceConfirm(ServerPlayer player, ActiveSelectionManager.ActionType action, UUID targetClaimId, List<Vertex> points) {
        int blocks = (int) Math.ceil(PolygonUtil.area(points));
        // Punkt 11 (Nachtrag 3): EXPAND_SUB/NEW_SUBCLAIM nutzen den SubClaim-Preis, siehe priceFor()/
        // divisorFor() - für EXPAND_MAIN/EXPAND_SUB deckt sich das mit claim.isMain() aus commitExpand,
        // da targetClaimId hier bei EXPAND_SUB bereits die Unterbereichs-ID selbst ist.
        PriceConfig price = priceFor(action);
        int units = PriceCharger.chargeUnits(blocks, divisorFor(action));

        ClaimSnapshotBuilder.sendTo(player);
        PacketDistributor.sendToPlayer(player, new OpenEditorPacket());

        PriceConfirmSnapshot snapshot = new PriceConfirmSnapshot();
        snapshot.action = action.name();
        snapshot.targetClaimId = targetClaimId == null ? null : targetClaimId.toString();
        snapshot.price = toPriceDto(price);
        snapshot.blocks = blocks;
        snapshot.affordable = PriceCharger.canAfford(player, price, units);
        PacketDistributor.sendToPlayer(player, new ShowPriceConfirmPacket(GSON.toJson(snapshot)));
    }

    /** Siehe Klassenkommentar "Punkt 11" - EXPAND_SUB nutzt den SubClaim-Preis, weil {@code targetClaimId} dort die Unterbereichs-ID selbst ist (nicht die des Hauptbereichs). */
    private static PriceConfig priceFor(ActiveSelectionManager.ActionType action) {
        return switch (action) {
            case NEW_SUBCLAIM, EXPAND_SUB -> PriceConfigManager.subClaimPrice();
            case NEW_CLAIM, EXPAND_MAIN -> PriceConfigManager.perBlockPrice();
        };
    }

    private static int divisorFor(ActiveSelectionManager.ActionType action) {
        return switch (action) {
            case NEW_SUBCLAIM, EXPAND_SUB -> PriceConfigManager.subClaimDivisor();
            case NEW_CLAIM, EXPAND_MAIN -> PriceConfigManager.perBlockDivisor();
        };
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
}
