package com.areaclaims.event;

import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimProtectionManager;
import com.areaclaims.claim.RuleType;
import com.areaclaims.geometry.PolygonUtil;
import com.areaclaims.geometry.Vertex;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Verdrahtet die {@link RuleType}-Regeln gegen tatsächlich verifizierte NeoForge-Events (jede
 * verwendete Event-Klasse wurde vor dem Schreiben dieses Codes in den dekompilierten
 * NeoForge-21.1.233-Sourcen nachgeschlagen, nicht geraten). Für Regeln OHNE sauberen,
 * verifizierten Hook (CONTAINER_ITEM_TRANSFER, Druckplatten-Teil von REDSTONE_INTERACT,
 * Platzieren-Teil von VEHICLE) siehe die TODO-Kommentare hier UND
 * {@link RuleType}-Klassenkommentar sowie ROADMAP.md Phase 4.
 */
public class ClaimProtectionListener {

    // ---------------------------------------------------------------- BUILD
    // Nutzer-Frage (ROADMAP.md Phase 6-Nachtrag 2): deckt BUILD auch das Ernten von Feldfrüchten
    // ab? GEPRÜFT statt geraten - in den dekompilierten Vanilla-Sourcen haben CropBlock/StemBlock/
    // NetherWartBlock KEINE useItemOn/useWithoutItem/interact-Überschreibung, es gibt in Vanilla
    // 1.21.1 also KEINE zerstörungsfreie Ernte-Interaktion - Ernten läuft IMMER über das Abbauen
    // des Feldfrucht-Blocks, feuert also bereits {@link BlockEvent.BreakEvent} und ist damit schon
    // vollständig über BUILD abgedeckt - kein zusätzlicher Code nötig.

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;
        Player player = event.getPlayer();
        if (!checkAndMaybeDeny(player, level, event.getPos(), RuleType.BUILD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof Level level) || level.isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!checkAndMaybeDeny(player, level, event.getPos(), RuleType.BUILD)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- CONTAINER_OPEN + REDSTONE_INTERACT (Hebel/Knöpfe)
    // Beide über RightClickBlock abgefangen, BEVOR der Container geöffnet bzw. der Hebel/Knopf
    // umgelegt wird - siehe RuleType-Klassenkommentar für den nicht abgedeckten Druckplatten-Fall.

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if (!(event.getLevel() instanceof Level level)) return;
        BlockPos pos = event.getPos();
        Block block = level.getBlockState(pos).getBlock();

        RuleType rule = null;
        if (block instanceof LeverBlock || block instanceof ButtonBlock) {
            rule = RuleType.REDSTONE_INTERACT;
        } else if (level.getBlockEntity(pos) instanceof Container) {
            rule = RuleType.CONTAINER_OPEN;
        }
        if (rule == null) return;

        if (!checkAndMaybeDeny(player, level, pos, rule)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- PVP

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof ServerPlayer)) return; // nur Spieler-gegen-Spieler
        Player attacker = event.getEntity();
        if (!(attacker.level() instanceof Level level) || level.isClientSide()) return;
        if (!checkAndMaybeDeny(attacker, level, attacker.blockPosition(), RuleType.PVP)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- MOB_SPAWNING ("Feindliche Mobs")
    // Kein handelnder Spieler -> nutzt ClaimProtectionManager#isRuleActive statt #isAllowed.
    // Nutzer-Vorgabe (2026-08-18, Regel umbenannt/eingeschränkt): nur noch FEINDLICH gesinnte Mobs
    // werden geblockt (MobCategory.MONSTER - dieselbe Klassifizierung, die Vanilla selbst für
    // Schwierigkeitsgrad/Spawn-Limits nutzt, funktioniert automatisch auch für modifizierte
    // feindliche Mobs, solange sie sich korrekt als MONSTER registrieren). Friedliche/neutrale
    // Mobs (Kühe, Dorfbewohner, Wölfe, Cobblemon-Pokémon usw.) dürfen weiterhin normal spawnen.

    @SubscribeEvent
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Mob mob = event.getEntity();
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;
        String dimension = level.dimension().location().toString();
        if (ClaimProtectionManager.isRuleActive(dimension, (int) Math.floor(event.getX()), (int) Math.floor(event.getZ()), RuleType.MOB_SPAWNING)) {
            event.setSpawnCancelled(true);
        }
    }

    // ---------------------------------------------------------------- MOB_SPAWNING: bereits vorhandene feindliche Mobs zurückdrängen
    // Nutzer-Vorgabe (2026-08-18): das Spawn-Verbot oben verhindert nur NEUES Entstehen IM Claim -
    // ein Mob, der außerhalb spawnt und in den Claim hineinläuft, wird davon nicht erfasst. Prüft
    // deshalb periodisch (gleiche Kadenz wie ClaimEntryListener) ALLE geladenen feindlichen Mobs
    // und teleportiert sie zurück nach draußen, sobald sie sich in einem Claim mit aktiver Regel
    // befinden ("laufen gegen eine unsichtbare Wand", feste Wahl statt konfigurierbarem
    // Töten-Modus, siehe Nutzer-Entscheidung).

    private static final int MOB_PUSHBACK_INTERVAL_TICKS = 10;
    private static final int MOB_PUSHBACK_MAX_STEPS = 200;

    private int mobPushbackTickCounter = 0;

    @SubscribeEvent
    public void onServerTickPushHostileMobs(ServerTickEvent.Post event) {
        mobPushbackTickCounter++;
        if (mobPushbackTickCounter % MOB_PUSHBACK_INTERVAL_TICKS != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            String dimension = level.dimension().location().toString();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                if (mob.getType().getCategory() != MobCategory.MONSTER) continue;

                Claim claim = ClaimProtectionManager.resolveActiveRuleClaim(
                    dimension, mob.getBlockX(), mob.getBlockZ(), RuleType.MOB_SPAWNING);
                if (claim != null) pushHostileMobOutside(mob, claim);
            }
        }
    }

    /**
     * Sucht den konkreten Claim-TEIL, in dem der Mob gerade steht, bildet dessen (groben)
     * Flächenschwerpunkt und schiebt den Mob radial vom Schwerpunkt weg, in Schritten von 1 Block,
     * bis er außerhalb des Teils liegt (plus 1 Block Sicherheitsabstand). Funktioniert unabhängig
     * von der Polygonform (kein exaktes "nächster Punkt außerhalb" nötig, siehe PolygonUtil-
     * Klassenkommentar) - bei sehr großen/entarteten Claims (kein Ausstieg innerhalb
     * MOB_PUSHBACK_MAX_STEPS gefunden) wird bewusst NICHTS getan statt an eine falsche Position zu
     * teleportieren. Y bleibt unverändert (Claims sind reine XZ-Grundrisse, siehe Claim-
     * Klassenkommentar) - kein Höhenkarten-Nachschlagen nötig.
     */
    private void pushHostileMobOutside(Mob mob, Claim claim) {
        double mobX = mob.getX();
        double mobZ = mob.getZ();

        List<Vertex> part = null;
        for (List<Vertex> candidate : claim.parts()) {
            if (PolygonUtil.pointInPolygon(candidate, mobX, mobZ)) {
                part = candidate;
                break;
            }
        }
        if (part == null || part.isEmpty()) return;

        double centerX = 0.0, centerZ = 0.0;
        for (Vertex v : part) {
            centerX += v.x();
            centerZ += v.z();
        }
        centerX /= part.size();
        centerZ /= part.size();

        double dirX = mobX - centerX;
        double dirZ = mobZ - centerZ;
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1e-6) {
            dirX = 1.0;
            dirZ = 0.0;
        } else {
            dirX /= len;
            dirZ /= len;
        }

        for (int step = 1; step <= MOB_PUSHBACK_MAX_STEPS; step++) {
            double candidateX = mobX + dirX * step;
            double candidateZ = mobZ + dirZ * step;
            if (!PolygonUtil.pointInPolygon(part, candidateX, candidateZ)) {
                mob.teleportTo(candidateX + dirX, mob.getY(), candidateZ + dirZ);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- VEHICLE (nur Benutzen/Aufsitzen, siehe TODO oben)

    @SubscribeEvent
    public void onEntityMount(EntityMountEvent event) {
        if (!event.isMounting()) return;
        if (!(event.getEntityMounting() instanceof Player player)) return;
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;
        if (!checkAndMaybeDeny(player, level, player.blockPosition(), RuleType.VEHICLE)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- LEASH_PASSIVE_MOBS
    // Nutzer-Klarstellung: "friedliche Mobs abgreifen" meint konkret ANLEINEN (Wolf/Katze/Lama/
    // Dorfbewohner/etc. mit der Leine), NICHT Eimer-Fang - siehe RuleType-Klassenkommentar für die
    // Begründung, warum EntityInteract statt des vom Nutzer vorgeschlagenen (in NeoForge 21.1.233
    // nicht existierenden) PlayerLeashEntityEvent verwendet wird.

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if (!(event.getLevel() instanceof Level level)) return;
        if (!event.getItemStack().is(Items.LEAD)) return;

        Entity target = event.getTarget();
        if (!checkAndMaybeDeny(player, level, target.blockPosition(), RuleType.LEASH_PASSIVE_MOBS)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- PLACE_FLUID
    // Eimer-Platzierung von Wasser/Lava läuft NICHT über EntityPlaceEvent (verifiziert - dafür
    // gibt es in NeoForge einen dedizierten Hook, siehe RuleType-Klassenkommentar). Bewusst
    // GETRENNT von TRAMPLE_FARMLAND (siehe dort) - zwei unterschiedliche Spieler-Aktionen, passt
    // zum bestehenden Muster "eine Regel pro klar abgrenzbarer Aktion" (wie BUILD/ITEM_DROP/PVP).

    @SubscribeEvent
    public void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof Level level) || level.isClientSide()) return;
        // FluidPlaceBlockEvent hat keinen handelnden Spieler (kann auch von Fließ-Physik ausgelöst
        // werden, z. B. Lava, die auf einen Cobblestone-Generator trifft) - deshalb wie
        // MOB_SPAWNING über die spielerlose isRuleActive-Variante geprüft, nicht isAllowed. Ein
        // Spieler, der selbst Eimer benutzt, wird stattdessen schon vom RightClickBlock/UseItem-
        // Verhalten der Bucket-Item-Klasse ausgelöst, das intern trotzdem diesen Block-Platzierungs-
        // Pfad durchläuft - das Event feuert also auch für spielerausgelöste Platzierung.
        String dimension = level.dimension().location().toString();
        if (ClaimProtectionManager.isRuleActive(dimension, event.getPos().getX(), event.getPos().getZ(), RuleType.PLACE_FLUID)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- TRAMPLE_FARMLAND
    // Ackerboden -> Erde durch Draufspringen läuft NICHT über BreakEvent (verifiziert - dafür gibt
    // es in NeoForge einen dedizierten Hook, siehe RuleType-Klassenkommentar).

    @SubscribeEvent
    public void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;
        if (event.getEntity() instanceof Player player) {
            if (!checkAndMaybeDeny(player, level, event.getPos(), RuleType.TRAMPLE_FARMLAND)) {
                event.setCanceled(true);
            }
        } else {
            // Nicht-Spieler-Entities (z. B. Mobs), die auf Ackerboden springen, haben keinen
            // handelnden Spieler - wie MOB_SPAWNING/PLACE_FLUID über die spielerlose Variante.
            String dimension = level.dimension().location().toString();
            if (ClaimProtectionManager.isRuleActive(dimension, event.getPos().getX(), event.getPos().getZ(), RuleType.TRAMPLE_FARMLAND)) {
                event.setCanceled(true);
            }
        }
    }

    // ---------------------------------------------------------------- ITEM_DROP

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (!(player.level() instanceof Level level) || level.isClientSide()) return;
        if (!checkAndMaybeDeny(player, level, player.blockPosition(), RuleType.ITEM_DROP)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- Hilfsfunktion

    private boolean checkAndMaybeDeny(Player player, Level level, BlockPos pos, RuleType rule) {
        String dimension = level.dimension().location().toString();
        boolean allowed = ClaimProtectionManager.isAllowed(player.getUUID(), dimension, pos.getX(), pos.getZ(), rule);
        if (!allowed && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("areaclaims.rule.denied"), true);
        }
        return allowed;
    }
}
