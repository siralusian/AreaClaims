package com.areaclaims.integration;

import com.areaclaims.claim.ClaimProtectionManager;
import com.areaclaims.claim.RuleType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * EINZIGE Klasse in AreaClaims, die {@code com.cobblemon.mod.common.api.*}/{@code entity.pokeball.*}
 * referenziert (siehe {@link CobbleCompanionBridge}-Klassenkommentar für dasselbe Muster/dieselbe
 * Disziplin-Anforderung: jeder Aufrufer MUSS vorher {@link CobblemonAvailability#isAvailable()}
 * geprüft haben). Verdrahtet {@link RuleType#PREVENT_COBBLEMON_CAPTURE} (ROADMAP.md Phase
 * 6-Nachtrag 2 "Cobblemon-Fang-Verifizierung" - vorherige Runde hatte dies mangels verfügbarem
 * {@code javap} als TODO offengelassen).
 *
 * <p><b>Verifikation (kein Raten):</b> {@code javap} war in dieser Umgebung weiterhin nicht
 * installierbar, aber die echte Cobblemon-Jar ({@code CobbleCompanion/libs/Cobblemon-neoforge-1.7.3+1.21.1.jar})
 * war diesmal verfügbar. Vorgehen: (1) {@code java.lang.reflect} gegen die echte Jar (mit
 * Minecraft-/NeoForge-Jar und Kotlin-Stdlib-Jar auf dem Klassenpfad) - liefert die vollständige
 * Feld-/Methodenliste von {@code CobblemonEvents}, {@code Observable}/{@code EventObservable}/
 * {@code CancelableObservable} und {@code Cancelable}; (2) zur Bestätigung zusätzlich ein
 * eigenständiger Testquelltext mit exakt der hier verwendeten API, erfolgreich mit {@code javac}
 * GEGEN DIE ECHTE JAR kompiliert (nicht nur reflektiert) - siehe ROADMAP.md für den genauen Ablauf.
 * Ergebnisse:
 * <ul>
 *   <li>{@code CobblemonEvents.THROWN_POKEBALL_HIT} ist eine
 *       {@code CancelableObservable<ThrownPokeballHitEvent>} - ECHT abbrechbar. Im Gegensatz dazu
 *       sind {@code POKE_BALL_CAPTURE_CALCULATED} und {@code POKEMON_CAPTURED} beide nur
 *       nicht-abbrechbare {@code EventObservable}s (reines Beobachten, {@code cancel()} hätte dort
 *       keine Wirkung auf den Fang) - ein leicht zu übersehender Unterschied, der allein aus dem
 *       Klassennamen NICHT ersichtlich gewesen wäre.</li>
 *   <li>{@code ThrownPokeballHitEvent extends com.cobblemon.mod.common.api.events.Cancelable}
 *       (Cobblemons eigene Kotlin-Basisklasse mit {@code cancel()}/{@code isCanceled()} - NICHT
 *       NeoForges {@code ICancellableEvent}, aber funktional gleichwertig).</li>
 *   <li>{@code ThrownPokeballHitEvent#getPokeBall()} liefert eine {@code EmptyPokeBallEntity}, die
 *       (verifiziert über deren Konstante-Pool-Einträge) {@code net.minecraft.world.entity.projectile.ThrowableItemProjectile}
 *       erweitert - eine ECHTE Vanilla-Klasse. Darüber liefert das geerbte {@code Projectile#getOwner()}
 *       den werfenden Spieler, ganz ohne eine Cobblemon-spezifische "wer hat geworfen"-API zu
 *       brauchen.</li>
 *   <li>{@code Observable#subscribe} hat neben der Kotlin-{@code Function1}-Variante eine
 *       Java-freundliche {@code subscribe(java.util.function.Consumer<T>)}-Überladung - kein
 *       Kotlin-Lambda-Interop-Hack nötig, ein normales Java-Lambda reicht.</li>
 * </ul>
 *
 * <p>Cancelt den Ball-Treffer BEVOR die eigentliche Fangberechnung überhaupt beginnt (die
 * folgenden Cobblemon-internen Schritte - Fangratenberechnung, Ball-Schütteln, tatsächliches
 * Einfangen - laufen dann gar nicht erst an), wenn {@link RuleType#PREVENT_COBBLEMON_CAPTURE} am
 * Wurfort für den werfenden Spieler aktiv ist. Die Subscription läuft GENAU EINMAL pro JVM-Leben
 * (nicht pro Weltstart) - {@code CobblemonEvents} ist ein globales Kotlin-{@code object}, keine
 * pro-Welt-Instanz, ein erneutes Abonnieren bei jedem {@code onServerStarting} (z. B. bei
 * Singleplayer-Weltwechseln innerhalb derselben laufenden Instanz) würde sonst denselben Handler
 * mehrfach registrieren.
 */
public final class CobblemonBridge {

    private static boolean subscribed = false;

    private CobblemonBridge() {}

    public static void subscribeIfNeeded() {
        if (subscribed) return;
        subscribed = true;
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(CobblemonBridge::onThrownPokeballHit);
    }

    private static void onThrownPokeballHit(ThrownPokeballHitEvent event) {
        EmptyPokeBallEntity ball = event.getPokeBall();
        if (ball.level().isClientSide()) return;
        Entity ownerEntity = ball.getOwner();
        if (!(ownerEntity instanceof ServerPlayer player)) return;

        String dimension = ball.level().dimension().location().toString();
        BlockPos pos = ball.blockPosition();
        boolean allowed = ClaimProtectionManager.isAllowed(
            player.getUUID(), dimension, pos.getX(), pos.getZ(), RuleType.PREVENT_COBBLEMON_CAPTURE);
        if (!allowed) {
            event.cancel();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("areaclaims.rule.denied"), true);
        }
    }
}
