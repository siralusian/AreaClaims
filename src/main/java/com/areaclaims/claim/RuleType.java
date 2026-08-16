package com.areaclaims.claim;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Der in ROADMAP.md Phase 4 bestätigte Verbieten-Regelsatz. Default ist jede Regel NICHT in
 * {@link Claim#rules()} enthalten = deaktiviert = erlaubt; der Besitzer schaltet gezielt
 * einzelne Regeln scharf (siehe {@link RuleSetting}).
 *
 * Durchsetzungsstatus (siehe {@link com.areaclaims.event.ClaimProtectionListener} und
 * ROADMAP.md Phase 4 "Durchsetzung"-Abschnitt für Details/TODOs):
 * <ul>
 *   <li>BUILD - verifiziert verdrahtet (BlockEvent.BreakEvent + BlockEvent.EntityPlaceEvent)</li>
 *   <li>CONTAINER_OPEN - verifiziert verdrahtet (PlayerInteractEvent.RightClickBlock, vor dem
 *       Öffnen des Containers abgefangen)</li>
 *   <li>CONTAINER_ITEM_TRANSFER - NICHT verdrahtet (TODO, siehe ROADMAP.md - bräuchte einen Hook
 *       auf einzelne Slot-Klicks/ContainerClickPacket, kein sauberes NeoForge-Event gefunden)</li>
 *   <li>PVP - verifiziert verdrahtet (AttackEntityEvent)</li>
 *   <li>MOB_SPAWNING - verifiziert verdrahtet (FinalizeSpawnEvent#setSpawnCancelled)</li>
 *   <li>REDSTONE_INTERACT - TEILWEISE verdrahtet: Hebel/Knöpfe über RightClickBlock abgedeckt,
 *       Druckplatten NICHT (TODO - die werden über Entity-Bewegung ausgelöst, kein
 *       Interact-Event; bräuchte vermutlich einen Mixin)</li>
 *   <li>VEHICLE - TEILWEISE verdrahtet: Benutzen/Aufsitzen über EntityMountEvent abgedeckt,
 *       Platzieren NICHT (TODO - Boot/Minecart-Platzierung läuft über Item#use, kein eindeutig
 *       verifizierbares Event gefunden)</li>
 *   <li>ITEM_DROP - verifiziert verdrahtet (ItemTossEvent)</li>
 *   <li>LEASH_PASSIVE_MOBS - verifiziert verdrahtet über PlayerInteractEvent.EntityInteract
 *       (Item = Leine), gecancelt BEVOR Entity#interact läuft (das eigentliche Anleinen). Nutzer
 *       schlug {@code PlayerLeashEntityEvent} vor - existiert in NeoForge 21.1.233 NICHT
 *       (erschöpfend geprüft: weder als Datei im NeoForge-Sourcen-Jar noch als Erwähnung in
 *       {@code EventHooks.java} - vermutlich ein aus altem Forge übernommener, in NeoForge nie
 *       migrierter Name). EntityInteract erreicht denselben Effekt (verhindert das Anleinen VOR
 *       dem eigentlichen Interact-Aufruf), siehe ROADMAP.md Phase 6-Nachtrag.</li>
 *   <li>PREVENT_COBBLEMON_CAPTURE - verifiziert verdrahtet (ROADMAP.md Phase 6-Nachtrag 2 "Cobblemon-
 *       Fang-Verifizierung"): {@code javap} war in dieser Umgebung weiterhin nicht installierbar,
 *       aber die echte Cobblemon-Jar ({@code CobbleCompanion/libs/Cobblemon-neoforge-1.7.3+1.21.1.jar})
 *       war diesmal verfügbar - Verifikation per {@code java.lang.reflect} (Feld-/Methodenliste der
 *       relevanten Klassen) UND einer tatsächlichen {@code javac}-Testkompilation eines kleinen
 *       Quelltexts gegen die echte Jar (nicht nur Reflektion - siehe ROADMAP.md für den genauen
 *       Ablauf). Ergebnis: {@code CobblemonEvents.THROWN_POKEBALL_HIT} ist eine
 *       {@code CancelableObservable<ThrownPokeballHitEvent>} (ECHT abbrechbar, im Gegensatz zu
 *       {@code POKE_BALL_CAPTURE_CALCULATED}/{@code POKEMON_CAPTURED}, die beide nur nicht-
 *       abbrechbare {@code EventObservable}s sind, dort würde {@code cancel()} den Fang NICHT
 *       verhindern). Siehe {@link com.areaclaims.integration.CobblemonBridge}, die einzige Klasse,
 *       die diese API tatsächlich referenziert, für die vollständige Herleitung. Cancelt den
 *       Ball-Treffer, BEVOR die Fangberechnung überhaupt beginnt.</li>
 *   <li>PLACE_FLUID - verifiziert verdrahtet (BlockEvent.FluidPlaceBlockEvent) - Eimer-Platzierung
 *       von Wasser/Lava (kein EntityPlaceEvent, siehe ROADMAP.md Phase 6-Nachtrag 2).</li>
 *   <li>TRAMPLE_FARMLAND - verifiziert verdrahtet (BlockEvent.FarmlandTrampleEvent) - Ackerboden
 *       wird durch Draufspringen zu Erde (kein BreakEvent, siehe ROADMAP.md Phase 6-Nachtrag 2).</li>
 * </ul>
 */
public enum RuleType {
    BUILD,
    CONTAINER_OPEN,
    CONTAINER_ITEM_TRANSFER,
    PVP,
    MOB_SPAWNING,
    REDSTONE_INTERACT,
    VEHICLE,
    ITEM_DROP,
    LEASH_PASSIVE_MOBS,
    PREVENT_COBBLEMON_CAPTURE,
    PLACE_FLUID,
    TRAMPLE_FARMLAND;

    /**
     * Übersetzter Anzeigename (siehe {@code areaclaims.rule.*} in den Lang-Dateien) - NICHT
     * {@link #name()} für UI-Text verwenden, das zeigt nur den rohen Java-Enum-Bezeichner an
     * (Nutzer-Fund: GUI-Editor zeigte z. B. "CONTAINER_ITEM_TRANSFER" statt einer lokalisierten
     * Bezeichnung).
     */
    public Component translatable() {
        return Component.translatable("areaclaims.rule." + name().toLowerCase(Locale.ROOT));
    }
}
