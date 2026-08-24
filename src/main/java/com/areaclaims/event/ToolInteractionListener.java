package com.areaclaims.event;

import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.claim.StakingService;
import com.areaclaims.data.ActiveSelectionManager;
import com.areaclaims.data.ClaimAdjustManager;
import com.areaclaims.data.ClaimShowcaseManager;
import com.areaclaims.data.SelectionManager;
import com.areaclaims.geometry.PolygonUtil;
import com.areaclaims.geometry.Vertex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * Verwaltet den "Klick-zum-Abstecken"-Modus (ROADMAP.md Phase 7 - ersetzt den früheren goldene-
 * Hacke-Werkzeug-Modus vollständig, siehe {@link ActiveSelectionManager}). Ausgelöst NUR über die
 * GUI-Buttons "Erweitern"/"Neuer SubClaim"/"Neuer Claim"/"Anpassen" ({@code BeginSelectionPacket} ->
 * {@code StakingService#begin}) - solange ein Spieler danach aktiv ist, fängt diese Klasse JEDEN
 * Links-/Rechtsklick ab, UNABHÄNGIG vom gehaltenen Item (vorher war das an die goldene Hacke
 * gebunden). Bei "Erweitern"/"Neuer SubClaim"/"Neuer Claim" fügt ein Linksklick einen Punkt hinzu;
 * bei "Anpassen" ({@code ADJUST_MAIN}/{@code ADJUST_SUB}, 2026-08-18) schaltet ein Linksklick
 * stattdessen die angeklickte Block-Spalte "gehört zum Claim"/"gehört nicht dazu" um (siehe
 * {@link ClaimAdjustManager}) - JEDER Rechtsklick (Block, Entity, Item-Nutzung) beendet die aktuelle
 * Sitzung ({@code StakingService#end}/{@code #endAdjust}).
 *
 * <p><b>Wichtige Nebenwirkungs-Unterdrückung (Nutzer-Vorgabe, nicht übersehen):</b> da der Spieler
 * jetzt ein BELIEBIGES Item halten kann (nicht mehr nur eine Hacke ohne besondere Aktion), MUSS
 * jede normale Konsequenz von Links-/Rechtsklick unterdrückt werden, solange die Auswahl aktiv
 * ist - sonst würde der Spieler versehentlich Blöcke abbauen, Mobs angreifen, Items benutzen
 * (Eimer, Nahrung, Tränke, ...). Verifiziert (dekompilierte {@code ServerPlayerGameMode.java}):
 * das Canceln von {@link PlayerInteractEvent.LeftClickBlock} verhindert BEIDES - normales
 * Abbauen UND den Sofort-Abbau im Kreativmodus (die Events-Prüfung
 * {@code if (event.isCanceled()) return;} steht in {@code handleBlockBreakAction} VOR der
 * Kreativmodus-Sonderbehandlung, kein Kreativmodus-Schlupfloch). Rechtsklick wird über
 * {@link PlayerInteractEvent.RightClickBlock}/{@link PlayerInteractEvent.RightClickItem}/
 * {@link PlayerInteractEvent.EntityInteract}/{@link PlayerInteractEvent.EntityInteractSpecific}
 * abgedeckt - EIN Sonderfall bleibt: laut NeoForge-Doc-Kommentar wird
 * {@link PlayerInteractEvent.RightClickEmpty} (Rechtsklick mit LEERER Hand in reine Luft, kein
 * Block/Entity in Reichweite) NUR clientseitig gefeuert und NIE an den Server gemeldet, ist außerdem
 * nicht abbrechbar - dafür gibt es {@code com.areaclaims.client.ClientSelectionEndListener} +
 * {@code EndSelectionPacket} als client-initiierten Ersatzweg (siehe deren Klassenkommentare).
 *
 * <p>Zusätzlich rendert derselbe Tick-Loop wie vorher die aktuelle Punkt-Auswahl UND alle aktiven
 * "Grenzen anzeigen"-Vorschauen (siehe {@link ClaimShowcaseManager}) - unverändert gegenüber den
 * vorherigen Runden, nur die Gating-Bedingung für die Live-Auswahl wechselte von
 * {@code ToolStateManager} auf {@link ActiveSelectionManager}. "Anpassen" selbst rendert NICHT über
 * diesen Partikel-Tick-Loop (Nutzer-Fund 2026-08-18: die Partikel-Linie war "teilweise sehr
 * verwirrend") - stattdessen schickt {@code StakingService#sendAdjustPreview} bei jedem Umschalten
 * die Kandidaten-Geometrie an den Client, der sie über
 * {@code com.areaclaims.client.AdjustPreviewTintRenderer} als Block-Einfärbung der äußersten
 * Blöcke zeigt (siehe dortigen Klassenkommentar). Die unveränderte Original-Kontur desselben Claims
 * wird währenddessen bewusst NICHT zusätzlich als Partikel gezeichnet (s.u.).
 */
public class ToolInteractionListener {

    /** Alle 15 Ticks (im vorgegebenen 10-20-Tick-Fenster) neu zeichnen, nicht jeden Tick. */
    private static final int PARTICLE_INTERVAL_TICKS = 15;
    private static final double PARTICLE_STEP_BLOCKS = 1.0;
    /** Siehe ROADMAP.md Phase 6-Nachtrag 2, Punkt 1 - dichtere/größere Partikel nur für Claim-Umrisse, nicht die Live-Auswahl. */
    private static final double SHOWCASE_PARTICLE_STEP_BLOCKS = 0.35;

    private int tickCounter = 0;

    // ---------------------------------------------------------------- Linksklick: Punkt hinzufügen ODER Spalte umschalten

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!isRealConnectedPlayer(serverPlayer)) return;
        if (!ActiveSelectionManager.isActive(serverPlayer.getUUID())) return;

        // IMMER canceln, solange aktiv (verhindert Blockabbau, siehe Klassenkommentar) - Punkt
        // aber nur beim START eines Linksklicks hinzufügen (CLIENT_HOLD/STOP/ABORT ignorieren,
        // sonst würde gehaltenes Linksklicken viele Punkte auf einmal hinzufügen).
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;

        UUID uuid = serverPlayer.getUUID();
        BlockPos pos = event.getPos();

        // Nutzer-Vorgabe (2026-08-18, "Anpassen"-Button): bei ADJUST_MAIN/ADJUST_SUB schaltet jeder
        // Linksklick die angeklickte Block-Spalte um, statt einen Polygon-Punkt zu setzen - ersetzt
        // den zuvor per Nutzer-Feedback verworfenen Gedrückt-Halten-Ziehversuch an der Grenze.
        ActiveSelectionManager.ActiveState state = ActiveSelectionManager.get(uuid);
        if (state != null && (state.action() == ActiveSelectionManager.ActionType.ADJUST_MAIN
            || state.action() == ActiveSelectionManager.ActionType.ADJUST_SUB)) {
            ClaimAdjustManager.AdjustState adjust = ClaimAdjustManager.get(uuid);
            if (adjust != null) {
                ClaimAdjustManager.toggle(adjust, pos.getX(), pos.getZ());
                // Nutzer-Vorgabe (2026-08-18, "Partikel-Linie verwirrend"): Block-Einfärbung-Vorschau
                // statt Partikel - bei JEDEM Umschalten neu berechnen und an genau diesen Spieler schicken.
                Claim claim = ClaimManager.getById(adjust.claimId);
                if (claim != null) {
                    StakingService.sendAdjustPreview(serverPlayer, claim, adjust.toColumnList());
                }
            }
            return;
        }

        // Nachtrag 7, Punkt 3 ("zu viele Punkte -> stiller Abbruch beim Rechtsklick"): sofortiges,
        // klares Feedback BEIM Klicken statt eines stillen Fehlschlags erst beim späteren
        // Rechtsklick-Abschluss - siehe PolygonUtil#MAX_POINTS_PER_PART-Klassenkommentar für die
        // vollständige Root-Cause-Recherche. Der Klick wird schlicht NICHT gezählt (Auswahl bleibt
        // unverändert), der Spieler kann trotzdem normal abschließen (Rechtsklick) oder den letzten
        // Punkt entfernen und anders weitermachen.
        if (SelectionManager.get(uuid).size() >= PolygonUtil.MAX_POINTS_PER_PART) {
            serverPlayer.displayClientMessage(
                Component.translatable("areaclaims.tool.too_many_points", PolygonUtil.MAX_POINTS_PER_PART), true);
            return;
        }

        SelectionManager.addPoint(uuid, pos.getX(), pos.getZ());
        sendFeedback(serverPlayer);
    }

    // ---------------------------------------------------------------- Linksklick auf Entity: nur unterdrücken, kein Punkt (kein Block-Ziel)

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (ActiveSelectionManager.isActive(serverPlayer.getUUID())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- Rechtsklick (alle Varianten): Auswahl beenden

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (cancelAndEndIfActive(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (cancelAndEndIfActive(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (cancelAndEndIfActive(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) return;
        if (cancelAndEndIfActive(event.getEntity())) event.setCanceled(true);
    }

    /**
     * Gemeinsamer Rechtsklick-Handler für alle abbrechbaren Interact-Varianten: liest den
     * "war aktiv"-Zustand VOR dem Beenden aus (damit der Aufrufer weiß, ob ER selbst canceln
     * muss - {@code event.setCanceled} kann nicht hier drin passieren, jeder Event-Typ hat eine
     * eigene Klasse) und beendet die Auswahl GENAU EINMAL - {@link ActiveSelectionManager#isActive}
     * als Guard verhindert ein doppeltes Auslösen, falls für denselben physischen Klick mehrere
     * dieser Events nacheinander feuern (z. B. RightClickBlock gefolgt von RightClickItem): der
     * erste Handler, der tatsächlich {@code StakingService#end}/{@code #endAdjust} aufruft, entfernt
     * den Spieler bereits aus {@link ActiveSelectionManager} - ein nachfolgender Handler im selben
     * Tick sieht dann bereits "nicht mehr aktiv" und tut (und cancelt) nichts mehr.
     *
     * @return true, wenn die Auswahl gerade aktiv WAR (der Aufrufer sein Event also canceln muss).
     */
    private boolean cancelAndEndIfActive(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (!isRealConnectedPlayer(serverPlayer)) return false;
        UUID uuid = serverPlayer.getUUID();
        ActiveSelectionManager.ActiveState state = ActiveSelectionManager.get(uuid);
        boolean wasActive = state != null;
        if (wasActive) {
            if (state.action() == ActiveSelectionManager.ActionType.ADJUST_MAIN
                || state.action() == ActiveSelectionManager.ActionType.ADJUST_SUB) {
                StakingService.endAdjust(serverPlayer);
            } else {
                StakingService.end(serverPlayer);
            }
        }
        return wasActive;
    }

    /**
     * Fake-Spieler (z. B. Create-Deployer, ggf. per Spielerkopf mit der UUID eines echten Spielers
     * verkleidet - siehe Crash-Analyse 2026-08-17) haben keine echte Netzwerkverbindung. Ohne diesen
     * Guard matcht {@link ActiveSelectionManager#isActive} versehentlich auf die UUID eines echten,
     * gerade aktiven Spielers und {@link StakingService#end}/{@code reopenEditor} versucht, dem
     * Fake-Spieler ein GUI-Sync-Paket zu schicken - {@code Connection.channel()} ist dann {@code null},
     * was zu einer harten Server-Crash-NPE führt.
     */
    private static boolean isRealConnectedPlayer(ServerPlayer player) {
        return player.connection != null && player.connection.getConnection().channel() != null;
    }

    private void sendFeedback(ServerPlayer player) {
        List<Vertex> selection = SelectionManager.get(player.getUUID());
        int count = selection.size();
        if (count >= 3) {
            double area = PolygonUtil.area(selection);
            player.displayClientMessage(
                Component.translatable("areaclaims.tool.selection_status_area", count, String.format("%.1f", area)), true);
        } else {
            player.displayClientMessage(Component.translatable("areaclaims.tool.selection_status", count), true);
        }
    }

    // ---------------------------------------------------------------- Rendern (unverändert gegenüber vorherigen Runden, nur Gating-Quelle geändert)

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % PARTICLE_INTERVAL_TICKS != 0) return;

        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;

            if (ActiveSelectionManager.isActive(player.getUUID())) {
                List<Vertex> selection = SelectionManager.get(player.getUUID());
                if (selection.size() >= 2) {
                    renderPolygonOutline(level, player, selection, ParticleTypes.END_ROD, PARTICLE_STEP_BLOCKS);
                }
            }

            // Nur im Partikel-Modus rendern (siehe ClaimShowcaseManager.DisplayMode) - im TINT-
            // Modus übernimmt com.areaclaims.client.BoundaryTintRenderer rein clientseitig, ohne
            // Server-Partikel.
            if (ClaimShowcaseManager.mode(player.getUUID()) == ClaimShowcaseManager.DisplayMode.PARTICLES) {
                ClaimAdjustManager.AdjustState adjust = ClaimAdjustManager.get(player.getUUID());
                for (UUID claimId : ClaimShowcaseManager.activeClaimIds(player.getUUID())) {
                    // Nutzer-Vorgabe (2026-08-18): die gerade angepasste Grenze wird per Block-
                    // Einfärbung gezeigt (siehe com.areaclaims.client.AdjustPreviewTintRenderer,
                    // StakingService#sendAdjustPreview) - die unveränderte Original-Kontur zusätzlich
                    // als Partikel zu zeigen würde nur verwirren.
                    if (adjust != null && claimId.equals(adjust.claimId)) continue;
                    Claim claim = ClaimManager.getById(claimId);
                    if (claim == null) continue;
                    renderClaimOutline(level, player, claim);
                }
            }
        }
    }

    /**
     * Rendert die AUSSENGRENZE eines Claims (Nutzer-Vorgabe 2026-08-18, Rundum-Überarbeitung: die
     * vorherige Kanten-Abdeckungsprüfung testete nur EINEN Punkt pro Kante - bei Teilen, die sich
     * nur TEILWEISE überschneiden/berühren, lieferte das falsche Ergebnisse, fehlende oder
     * überzählige Grenzlinien). Nutzt jetzt {@link PolygonUtil#extractBoundaryEdges} - dieselbe
     * Grundidee wie {@code BoundaryTintRenderer} (Nutzer-Vorschlag: "nur wissen, welche Blöcke zum
     * Claim gehören"): statt Kanten der EINZELNEN Polygon-Teile zu prüfen, wird pro Block-Spalte
     * geprüft, ob sie zu IRGENDEINEM Teil gehört, und die Grenzlinie exakt dort gezogen, wo sich
     * dieser Zugehörigkeits-Status zum Nachbarn ändert - funktioniert dadurch automatisch korrekt
     * für beliebig viele sich berührende/überlappende Teile, ganz ohne Sonderfall-Behandlung.
     *
     * <p>Nutzer-Vorgabe: dieselben Partikel wie beim Claim-ABSTECKEN (siehe
     * {@code onRightClickBlock}/{@code sendFeedback} weiter oben, {@link ParticleTypes#END_ROD}) -
     * keine besitzer-/betrachterspezifische Einfärbung mehr für die Partikel-Vorschau (die bleibt
     * weiterhin im TINT-Anzeigemodus über {@code BoundaryTintRenderer} verfügbar).
     */
    private void renderClaimOutline(ServerLevel level, ServerPlayer player, Claim claim) {
        for (int[] edge : PolygonUtil.extractBoundaryEdges(claim.parts())) {
            Vertex a = new Vertex(edge[0], edge[1]);
            Vertex b = new Vertex(edge[2], edge[3]);
            renderEdge(level, player, a, b, ParticleTypes.END_ROD, SHOWCASE_PARTICLE_STEP_BLOCKS);
        }
    }

    private void renderPolygonOutline(ServerLevel level, ServerPlayer player, List<Vertex> points, ParticleOptions particle, double stepBlocks) {
        int edgeCount = points.size() >= 3 ? points.size() : points.size() - 1;
        for (int i = 0; i < edgeCount; i++) {
            Vertex a = points.get(i);
            Vertex b = points.get((i + 1) % points.size());
            renderEdge(level, player, a, b, particle, stepBlocks);
        }
    }

    private void renderEdge(ServerLevel level, ServerPlayer player, Vertex a, Vertex b, ParticleOptions particle, double stepBlocks) {
        double y = player.getY() + 0.1;
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.round(length / stepBlocks));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            double px = a.x() + 0.5 + dx * t;
            double pz = a.z() + 0.5 + dz * t;
            level.sendParticles(player, particle, false, px, y, pz, 1, 0, 0, 0, 0);
        }
    }
}
