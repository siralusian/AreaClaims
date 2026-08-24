package com.areaclaims.claim;

import com.areaclaims.data.ActiveSelectionManager;
import com.areaclaims.data.ClaimAdjustManager;
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
import com.areaclaims.network.AdjustPreviewSnapshot;
import com.areaclaims.network.AdjustPreviewSyncPacket;
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
import java.util.stream.Collectors;

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
 *
 * <p><b>"Anpassen" (2026-08-18, {@code ADJUST_MAIN}/{@code ADJUST_SUB}):</b> eigener, NEBEN
 * "Erweitern" existierender Modus (siehe {@link #beginAdjust}/{@link #endAdjust}) - statt Punkte zu
 * setzen, schaltet jeder Linksklick eine einzelne Block-Spalte "gehört zum Claim"/"gehört nicht
 * dazu" um (siehe {@link ClaimAdjustManager}). Ein Rechtsklick beendet die Sitzung, baut daraus die
 * neue Gesamt-Geometrie ({@link PolygonUtil#applyToggleSetToParts}) und zeigt eine Bestätigung, die
 * gleichzeitig einen Kauf-Preis (für neu hinzugefügte Blöcke) UND eine Rückerstattung (für entfernte
 * Blöcke) enthalten kann - ersetzt den vom Nutzer verworfenen Gedrückt-Halten-Ziehversuch an der
 * Grenze.
 */
public final class StakingService {

    private static final Gson GSON = new Gson();

    private StakingService() {}

    public enum BeginResult { STARTED_PLACEMENT, RESUMED_PENDING, NOT_AUTHORIZED, TARGET_NOT_FOUND }

    /** Kleines lokales Ergebnis-Bündel für {@link #validate} - vereinheitlicht die verschiedenen ClaimManager-Ergebnis-Enums auf "ok?" + fertige Fehlermeldung. */
    private record ValidateOutcome(boolean ok, Component failureMessage) {}

    // ---------------------------------------------------------------- Beginnen

    public static BeginResult begin(ServerPlayer player, ActiveSelectionManager.ActionType action, UUID targetClaimId) {
        if (action == ActiveSelectionManager.ActionType.ADJUST_MAIN || action == ActiveSelectionManager.ActionType.ADJUST_SUB) {
            return beginAdjust(player, action, targetClaimId);
        }

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
            default -> {}
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

    /**
     * Wie {@link #begin}, aber für {@code ADJUST_MAIN}/{@code ADJUST_SUB} - dieselben Berechtigungs-
     * prüfungen wie beim Erweitern (Nutzer-Vorgabe: "Anpassen" ist eine ALTERNATIVE Bedienweise
     * derselben Aktion, keine eigene Berechtigung). Zeigt IMMER sofort die Claim-Grenze an (Nutzer-
     * Fund: "das hat beim jetzigen Test nicht funktioniert" - {@code EXPAND_MAIN}/{@code EXPAND_SUB}
     * riefen bisher {@code ClaimShowcaseManager.ensureShown} NICHT auf, nur {@code NEW_SUBCLAIM} tat
     * das - hier jetzt nachgeholt, damit der Spieler sofort sieht, was er umschalten kann).
     */
    private static BeginResult beginAdjust(ServerPlayer player, ActiveSelectionManager.ActionType action, UUID targetClaimId) {
        if (!FeatureConfigManager.canExpandClaim(player)) return BeginResult.NOT_AUTHORIZED;
        Claim target = ClaimManager.getById(targetClaimId);
        if (target == null) return BeginResult.TARGET_NOT_FOUND;
        if (!ClaimEditService.canEdit(player, target)) return BeginResult.NOT_AUTHORIZED;

        PendingActionManager.PendingAction pending = PendingActionManager.find(player.getUUID(), action, targetClaimId);
        if (pending != null) {
            sendAdjustPreviewParts(player, target, pending.resizedParts());
            showAdjustConfirm(player, target, pending.addedBlocks(), pending.removedBlocks());
            return BeginResult.RESUMED_PENDING;
        }

        UUID uuid = player.getUUID();
        ClaimAdjustManager.start(uuid, targetClaimId);
        ActiveSelectionManager.start(uuid, action, targetClaimId);
        ClaimShowcaseManager.ensureShown(uuid, target.id());
        ShowcaseGeometrySnapshotBuilder.sendTo(player);
        // Nutzer-Vorgabe (2026-08-18, "Partikel-Linie verwirrend, stattdessen Block-Einfärbung"):
        // die Grenze muss SOFORT sichtbar sein, nicht erst nach dem ersten Klick - Kandidat = Original
        // (noch keine Spalte umgeschaltet).
        sendAdjustPreview(player, target, List.of());

        PacketDistributor.sendToPlayer(player, new EnterSelectionModePacket());
        return BeginResult.STARTED_PLACEMENT;
    }

    // ---------------------------------------------------------------- Beenden (Rechtsklick)

    /** Aufgerufen, wenn der Spieler im aktiven Auswahl-Modus (Klick-für-Klick) rechtsklickt (siehe ToolInteractionListener). Für {@code ADJUST_MAIN}/{@code ADJUST_SUB} ruft ToolInteractionListener stattdessen {@link #endAdjust} auf. */
    public static void end(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ActiveSelectionManager.ActiveState state = ActiveSelectionManager.get(uuid);
        if (state == null) return;
        ActiveSelectionManager.stop(uuid);
        List<Vertex> points = List.copyOf(SelectionManager.get(uuid));
        SelectionManager.clear(uuid);

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

    /**
     * Rechtsklick-Gegenstück zu {@link #end} für "Anpassen" - baut aus den umgeschalteten Spalten
     * (siehe {@link ClaimAdjustManager}) die Kandidaten-Geometrie, validiert sie und zeigt IMMER eine
     * Bestätigung (auch bei reinem Hinzufügen mit freiem Preis - Nutzer-Vorgabe war ein einfacher,
     * bewusster Bestätigungsschritt statt eines automatischen Sofort-Committens).
     */
    public static void endAdjust(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ActiveSelectionManager.ActiveState state = ActiveSelectionManager.get(uuid);
        ClaimAdjustManager.AdjustState adjust = ClaimAdjustManager.get(uuid);
        ActiveSelectionManager.stop(uuid);
        ClaimAdjustManager.stop(uuid);
        clearAdjustPreview(player);
        if (state == null || adjust == null) return;

        Claim claim = ClaimManager.getById(state.targetClaimId());
        if (claim == null) {
            reopenEditor(player);
            return;
        }
        if (adjust.isEmpty()) {
            reopenEditor(player);
            return;
        }

        List<int[]> toggledColumns = adjust.toColumnList();
        List<List<Vertex>> candidate = PolygonUtil.applyToggleSetToParts(claim.parts(), toggledColumns);

        ClaimManager.ResizeResult check = ClaimManager.validateResize(claim, candidate);
        if (check != ClaimManager.ResizeResult.OK) {
            player.displayClientMessage(resizeFailureMessage(check), true);
            reopenEditor(player);
            return;
        }

        int addedBlocks = 0, removedBlocks = 0;
        for (int[] c : toggledColumns) {
            boolean wasInside = claim.containsPoint(c[0] + 0.5, c[1] + 0.5);
            if (wasInside) removedBlocks++; else addedBlocks++;
        }
        if (addedBlocks == 0 && removedBlocks == 0) {
            reopenEditor(player);
            return;
        }

        ActiveSelectionManager.ActionType action = claim.isMain() ? ActiveSelectionManager.ActionType.ADJUST_MAIN : ActiveSelectionManager.ActionType.ADJUST_SUB;
        PendingActionManager.rememberAdjust(uuid, action, claim.id(), candidate, toggledColumns, addedBlocks, removedBlocks);
        showAdjustConfirm(player, claim, addedBlocks, removedBlocks);
    }

    // ---------------------------------------------------------------- "Anpassen"-Block-Einfärbung-Vorschau

    /** Berechnet die Kandidaten-Geometrie aus {@code toggledColumns} und schickt sie als Block-Einfärbung-Vorschau (siehe {@code ToolInteractionListener#onLeftClickBlock}, bei JEDEM Umschalten aufgerufen). */
    public static void sendAdjustPreview(ServerPlayer player, Claim claim, List<int[]> toggledColumns) {
        List<List<Vertex>> candidate = toggledColumns.isEmpty() ? claim.parts() : PolygonUtil.applyToggleSetToParts(claim.parts(), toggledColumns);
        sendAdjustPreviewParts(player, claim, candidate);
    }

    private static void sendAdjustPreviewParts(ServerPlayer player, Claim claim, List<List<Vertex>> parts) {
        if (claim == null) return;
        AdjustPreviewSnapshot snapshot = new AdjustPreviewSnapshot();
        snapshot.color = ClaimShowcaseManager.effectiveColorFor(player.getUUID(), claim);
        snapshot.parts = parts.stream()
            .map(part -> part.stream().map(v -> new int[] {v.x(), v.z()}).collect(Collectors.toList()))
            .collect(Collectors.toList());
        snapshot.nearbyClaims = nearbyOtherClaims(claim, parts);
        PacketDistributor.sendToPlayer(player, new AdjustPreviewSyncPacket(GSON.toJson(snapshot)));
    }

    /**
     * Nutzer-Vorgabe (2026-08-19, "Claims anderer Spieler am Boden mit einfärben"): sucht Claims
     * ANDERER Spieler (nicht des eigenen Besitzers) in der Nähe der Kandidaten-Geometrie und liefert
     * sie im selben Format wie {@link ShowcaseGeometrySnapshotBuilder}. Der Such-Bereich ist die
     * Bounding-Box der Kandidaten-Geometrie plus {@link #ADJUST_NEARBY_MARGIN} Blöcke Rand - großzügig
     * genug, damit alle relevanten fremden Claims schon im Client-Cache liegen, egal wo INNERHALB
     * dieses Bereichs der Spieler während der Sitzung steht (der Radius wird nicht bei jeder
     * Spielerbewegung neu vom Server geholt, nur bei jedem Umschalten).
     */
    private static List<com.areaclaims.network.ShowcaseGeometrySnapshot.ClaimGeometry> nearbyOtherClaims(Claim claim, List<List<Vertex>> candidateParts) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (List<Vertex> part : candidateParts) {
            for (Vertex v : part) {
                minX = Math.min(minX, v.x());
                maxX = Math.max(maxX, v.x());
                minZ = Math.min(minZ, v.z());
                maxZ = Math.max(maxZ, v.z());
            }
        }
        if (minX > maxX) return List.of();

        List<Claim> nearby = ClaimManager.findClaimsNear(claim.dimension(),
            minX - ADJUST_NEARBY_MARGIN, minZ - ADJUST_NEARBY_MARGIN, maxX + ADJUST_NEARBY_MARGIN, maxZ + ADJUST_NEARBY_MARGIN);

        List<com.areaclaims.network.ShowcaseGeometrySnapshot.ClaimGeometry> result = new java.util.ArrayList<>();
        for (Claim other : nearby) {
            if (other.owner().equals(claim.owner())) continue;
            com.areaclaims.network.ShowcaseGeometrySnapshot.ClaimGeometry geometry = new com.areaclaims.network.ShowcaseGeometrySnapshot.ClaimGeometry();
            geometry.id = other.id().toString();
            geometry.color = other.effectiveBoundaryColor();
            geometry.parts = other.parts().stream()
                .map(part -> part.stream().map(v -> new int[] {v.x(), v.z()}).collect(Collectors.toList()))
                .collect(Collectors.toList());
            result.add(geometry);
        }
        return result;
    }

    /** Muss mindestens {@code AdjustPreviewTintRenderer}s Anzeige-Radius (15 Blöcke) decken - eigener, großzügigerer Wert, da hier nur einmal je Umschalten statt jeden Frame nachgeladen wird. */
    private static final int ADJUST_NEARBY_MARGIN = 24;

    /** Räumt die Block-Einfärbung-Vorschau beim Beenden/Verwerfen von "Anpassen" (leere Nutzlast, siehe {@link AdjustPreviewSnapshot}-Klassenkommentar). */
    private static void clearAdjustPreview(ServerPlayer player) {
        AdjustPreviewSnapshot snapshot = new AdjustPreviewSnapshot();
        PacketDistributor.sendToPlayer(player, new AdjustPreviewSyncPacket(GSON.toJson(snapshot)));
    }

    // ---------------------------------------------------------------- Popup-Aktionen

    public static void confirmPending(ServerPlayer player) {
        PendingActionManager.PendingAction pending = PendingActionManager.get(player.getUUID());
        if (pending == null) return;
        boolean success = pending.resizedParts() != null
            ? commitAdjust(player, pending.targetClaimId(), pending.resizedParts(), pending.addedBlocks(), pending.removedBlocks())
            : commit(player, pending.action(), pending.targetClaimId(), pending.points());
        if (success) PendingActionManager.clear(player.getUUID());
        reopenEditor(player);
    }

    /** "Abbrechen" - zurück in den Platzier-/Anpassen-Modus, bereits gesetzte Punkte bzw. umgeschaltete Spalten bleiben erhalten. */
    public static void resumePending(ServerPlayer player) {
        PendingActionManager.PendingAction pending = PendingActionManager.get(player.getUUID());
        if (pending == null) return;
        PendingActionManager.clear(player.getUUID());
        UUID uuid = player.getUUID();

        if (pending.resizedParts() != null) {
            ClaimAdjustManager.start(uuid, pending.targetClaimId());
            ClaimAdjustManager.AdjustState state = ClaimAdjustManager.get(uuid);
            for (int[] c : pending.toggledColumns()) ClaimAdjustManager.restore(state, c[0], c[1]);
            ActiveSelectionManager.start(uuid, pending.action(), pending.targetClaimId());
            sendAdjustPreviewParts(player, ClaimManager.getById(pending.targetClaimId()), pending.resizedParts());
            PacketDistributor.sendToPlayer(player, new EnterSelectionModePacket());
            return;
        }

        SelectionManager.clear(uuid);
        for (Vertex v : pending.points()) SelectionManager.addPoint(uuid, v.x(), v.z());
        ActiveSelectionManager.start(uuid, pending.action(), pending.targetClaimId());
        PacketDistributor.sendToPlayer(player, new EnterSelectionModePacket());
    }

    /** "Beenden" - verwirft die gemerkte Auswahl/Anpassung vollständig. */
    public static void discardPending(ServerPlayer player) {
        PendingActionManager.clear(player.getUUID());
        ActiveSelectionManager.stop(player.getUUID());
        SelectionManager.clear(player.getUUID());
        ClaimAdjustManager.stop(player.getUUID());
        clearAdjustPreview(player);
        ClaimSnapshotBuilder.sendTo(player);
    }

    // ---------------------------------------------------------------- "Anpassen"-Commit/Preis

    private static boolean commitAdjust(ServerPlayer player, UUID claimId, List<List<Vertex>> newParts, int addedBlocks, int removedBlocks) {
        Claim claim = ClaimManager.getById(claimId);
        if (claim == null) {
            player.displayClientMessage(Component.translatable("areaclaims.command.claim.not_found", "?"), true);
            return false;
        }

        PriceCharger.ChargeOutcome chargeOutcome = null;
        if (addedBlocks > 0) {
            PriceConfig price = claim.isMain() ? PriceConfigManager.perBlockPrice() : PriceConfigManager.subClaimPrice();
            int divisor = claim.isMain() ? PriceConfigManager.perBlockDivisor() : PriceConfigManager.subClaimDivisor();
            int units = PriceCharger.chargeUnits(addedBlocks, divisor);
            chargeOutcome = PriceCharger.chargeDetailed(player, price, units);
            if (chargeOutcome.result() != PriceCharger.Result.OK) {
                player.displayClientMessage(priceFailureMessage(chargeOutcome.result()), true);
                return false;
            }
        }

        ClaimManager.applyResize(claim, newParts);

        if (chargeOutcome != null) {
            logWalletCharge(player, chargeOutcome, "AreaClaims: Bereich per Anpassen erweitert (" + addedBlocks + " Blöcke)");
        }
        if (removedBlocks > 0) {
            PriceConfig refund = claim.isMain() ? PriceConfigManager.perBlockRefund() : PriceConfigManager.subClaimRefund();
            int divisor = claim.isMain() ? PriceConfigManager.perBlockRefundDivisor() : PriceConfigManager.subClaimRefundDivisor();
            int units = PriceCharger.refundUnits(removedBlocks, divisor);
            if (units > 0 && !refund.isFree()) {
                PriceCharger.refund(player, refund, units);
            }
        }

        player.displayClientMessage(Component.translatable("areaclaims.adjust.success", claim.name(), addedBlocks, removedBlocks), true);
        return true;
    }

    private static void showAdjustConfirm(ServerPlayer player, Claim claim, int addedBlocks, int removedBlocks) {
        PriceConfig addPrice = claim.isMain() ? PriceConfigManager.perBlockPrice() : PriceConfigManager.subClaimPrice();
        int addDivisor = claim.isMain() ? PriceConfigManager.perBlockDivisor() : PriceConfigManager.subClaimDivisor();
        int addUnits = PriceCharger.chargeUnits(addedBlocks, addDivisor);

        PriceConfig removePrice = claim.isMain() ? PriceConfigManager.perBlockRefund() : PriceConfigManager.subClaimRefund();

        ClaimSnapshotBuilder.sendTo(player);
        PacketDistributor.sendToPlayer(player, new OpenEditorPacket());

        PriceConfirmSnapshot snapshot = new PriceConfirmSnapshot();
        snapshot.action = (claim.isMain() ? ActiveSelectionManager.ActionType.ADJUST_MAIN : ActiveSelectionManager.ActionType.ADJUST_SUB).name();
        snapshot.targetClaimId = claim.id().toString();
        snapshot.addedBlocks = addedBlocks;
        snapshot.addedPrice = toPriceDto(addPrice);
        snapshot.removedBlocks = removedBlocks;
        snapshot.removedPrice = toPriceDto(removePrice);
        // Eine Rückerstattung ist nie "unbezahlbar" - nur der Kauf-Anteil (falls vorhanden) entscheidet.
        snapshot.affordable = addedBlocks == 0 || PriceCharger.canAfford(player, addPrice, addUnits);
        PacketDistributor.sendToPlayer(player, new ShowPriceConfirmPacket(GSON.toJson(snapshot)));
    }

    private static Component resizeFailureMessage(ClaimManager.ResizeResult result) {
        return switch (result) {
            case TOO_MANY_POINTS -> Component.translatable("areaclaims.command.claim.too_many_points_selection", PolygonUtil.MAX_POINTS_PER_PART);
            case TOO_MANY_PARTS -> Component.translatable("areaclaims.command.claim.too_many_parts", FeatureConfigManager.maxClaimParts());
            case OVERLAPS_EXISTING -> Component.translatable("areaclaims.command.claim.overlaps");
            case OUTSIDE_PARENT -> Component.translatable("areaclaims.command.claim.outside_parent");
            case SUBCLAIM_WOULD_BE_ORPHANED -> Component.translatable("areaclaims.adjust.subclaim_would_be_orphaned");
            case OK, NO_CHANGE -> Component.empty();
        };
    }

    // ---------------------------------------------------------------- Validierung/Commit je Aktionstyp (Klick-für-Klick)

    private static ValidateOutcome validate(ActiveSelectionManager.ActionType action, UUID targetClaimId, ServerPlayer player, List<Vertex> points) {
        String dimension = player.level().dimension().location().toString();
        return switch (action) {
            case NEW_CLAIM -> {
                ClaimManager.CreateResult result = ClaimManager.validateNewMainClaim(player.getUUID(), dimension, points);
                yield new ValidateOutcome(result == ClaimManager.CreateResult.OK, createFailureMessage(result));
            }
            case EXPAND_MAIN, EXPAND_SUB, ADJUST_MAIN, ADJUST_SUB -> {
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

    /** @return true bei Erfolg (Claim wurde tatsächlich angelegt/erweitert UND bezahlt). Wird NIE mit ADJUST_MAIN/ADJUST_SUB aufgerufen (siehe {@link #confirmPending} - deren Preisbestätigung geht immer über {@code resizedParts}/{@link #commitAdjust}), die Fälle sind hier nur der Compile-Vollständigkeit halber auf EXPAND_MAIN/EXPAND_SUB gefaltet. */
    private static boolean commit(ServerPlayer player, ActiveSelectionManager.ActionType action, UUID targetClaimId, List<Vertex> points) {
        return switch (action) {
            case NEW_CLAIM -> commitNewClaim(player, points);
            case EXPAND_MAIN, EXPAND_SUB, ADJUST_MAIN, ADJUST_SUB -> commitExpand(player, targetClaimId, points);
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
        // Nutzer-Fund (2026-08-19): Preis muss vor dem Hinzufügen aus den bisherigen Teilen berechnet
        // werden - addClaimPart() unten hängt "points" selbst als neuen Teil an claim.parts() an, ein
        // Vergleich DANACH würde "points" fälschlich als bereits vorhanden erkennen.
        List<List<Vertex>> existingParts = List.copyOf(claim.parts());
        ClaimManager.AddPartResult result = ClaimManager.addClaimPart(claim, points);
        if (result != ClaimManager.AddPartResult.OK) {
            player.displayClientMessage(addPartFailureMessage(result), true);
            return false;
        }
        // Punkt 11 (Nachtrag 3): claim.isMain() entscheidet zuverlässig Haupt- vs. SubClaim-Preis -
        // targetClaimId ist bei EXPAND_SUB die Unterbereichs-ID selbst (siehe begin()/buildSubColumn),
        // NICHT die des Hauptbereichs, also liest claim.isMain() hier immer den richtigen Preis-Typ.
        // Nutzer-Fund (2026-08-19): nur die WIRKLICH neu hinzukommenden Blöcke berechnen, nicht die
        // gesamte gezogene Fläche (die konnte den bestehenden Claim überlappen) - siehe
        // PolygonUtil#netNewBlockCount-Klassenkommentar.
        int newPartBlocks = PolygonUtil.netNewBlockCount(points, existingParts);
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
        int blocks = blocksFor(action, targetClaimId, points);
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

    /**
     * Nutzer-Fund (2026-08-19): für EXPAND_MAIN/EXPAND_SUB nur die WIRKLICH neu hinzukommenden
     * Blöcke gegenüber dem AKTUELLEN (noch unveränderten) Claim zählen - siehe
     * {@code PolygonUtil#netNewBlockCount}-Klassenkommentar. Für NEW_CLAIM/NEW_SUBCLAIM gibt es
     * keinen bestehenden Claim, dort bleibt die volle gezogene Fläche korrekt.
     */
    private static int blocksFor(ActiveSelectionManager.ActionType action, UUID targetClaimId, List<Vertex> points) {
        if (action == ActiveSelectionManager.ActionType.EXPAND_MAIN || action == ActiveSelectionManager.ActionType.EXPAND_SUB) {
            Claim claim = ClaimManager.getById(targetClaimId);
            if (claim != null) return PolygonUtil.netNewBlockCount(points, claim.parts());
        }
        return (int) Math.ceil(PolygonUtil.area(points));
    }

    /** Siehe Klassenkommentar "Punkt 11" - EXPAND_SUB nutzt den SubClaim-Preis, weil {@code targetClaimId} dort die Unterbereichs-ID selbst ist (nicht die des Hauptbereichs). */
    private static PriceConfig priceFor(ActiveSelectionManager.ActionType action) {
        return switch (action) {
            case NEW_SUBCLAIM, EXPAND_SUB, ADJUST_SUB -> PriceConfigManager.subClaimPrice();
            case NEW_CLAIM, EXPAND_MAIN, ADJUST_MAIN -> PriceConfigManager.perBlockPrice();
        };
    }

    private static int divisorFor(ActiveSelectionManager.ActionType action) {
        return switch (action) {
            case NEW_SUBCLAIM, EXPAND_SUB, ADJUST_SUB -> PriceConfigManager.subClaimDivisor();
            case NEW_CLAIM, EXPAND_MAIN, ADJUST_MAIN -> PriceConfigManager.perBlockDivisor();
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
