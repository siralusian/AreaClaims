package com.areaclaims.integration;

import net.neoforged.fml.ModList;

/**
 * Nur ein Mod-ID-Check, KEINE Cobblemon-Klasse wird referenziert - siehe den Klassenkommentar von
 * {@code com.areaclaims.claim.RuleType} (Eintrag {@code PREVENT_COBBLEMON_CAPTURE}).
 * Ausgangspunkt für eine spätere Runde: Cobblemons Fang-Events sind Kotlin-APIs
 * (`com.cobblemon.mod.common.api.events.pokeball.*`), die in dieser Session ohne Quelltexte/
 * {@code javap} nicht sicher genug verifizierbar waren, um sie zu verdrahten. Die Regel
 * {@code PREVENT_COBBLEMON_CAPTURE} existiert bereits vollständig im Datenmodell/UI, nur die
 * tatsächliche Durchsetzung gegen Cobblemon fehlt noch (TODO, siehe ROADMAP.md Phase 6-Nachtrag).
 */
public final class CobblemonAvailability {

    private static boolean cobblemon = false;

    private CobblemonAvailability() {}

    public static void refresh() {
        cobblemon = ModList.get().isLoaded("cobblemon");
    }

    public static boolean isAvailable() {
        return cobblemon;
    }
}
