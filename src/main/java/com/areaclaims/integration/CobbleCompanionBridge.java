package com.areaclaims.integration;

import com.cobblecompanion.api.CobbleDollarsAccess;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * EINZIGE Klasse in AreaClaims, die {@code com.cobblecompanion.api.*} referenziert (siehe
 * CobbleCompanion/docs/API_EXTENSIONS.md Abschnitt 7/8, Checkliste Punkt 6 - 1:1 das Muster aus
 * {@code InvSpy/.../integration/CobbleCompanionBridge.java}). Jeder Aufrufer MUSS vorher
 * {@link ModAvailability#isCobbleDollarsAvailable()} geprüft haben, bevor irgendeine Methode
 * dieser Klasse aufgerufen wird - Java lädt diese Klasse (und damit transitiv
 * {@code com.cobblecompanion.api.CobbleDollarsAccess}) erst beim ersten tatsächlichen
 * Methodenaufruf, nicht schon durch den bloßen Quelltext-Verweis anderer Klassen. Ohne diese
 * Disziplin würde ein Server ohne installiertes CobbleCompanion mit {@code NoClassDefFoundError}
 * abstürzen, sobald hier eine Methode aufgerufen wird. Einzige Ausnahme:
 * {@link #isCobbleDollarsAvailable()} selbst wird NUR aus {@link ModAvailability#refresh()}
 * aufgerufen, das seinerseits erst NACH der eigenen {@code cobbleCompanion}-Prüfung dorthin
 * kurzschließt.
 *
 * <p><b>Skalierungs-Bugfix (2026-08-16):</b> {@code CobbleDollarsAccess} nimmt seit heute
 * {@link BigDecimal} in ECHTEN Cobbledollars entgegen (rechnet intern selbst auf CobbleDollars'
 * eigenes ×10-Rohformat um) - vorher nahm es rohe {@link BigInteger}-Werte ohne Umrechnung entgegen,
 * wodurch hier übergebene Preise (z. B. 300 fuer 300 Blöcke à 1$) am Ende nur als 30 Cobbledollars
 * abgebucht wurden (300 als Rohwert interpretiert = 30,0$ bei CobbleDollars' ×10-Skala). AreaClaims'
 * eigenes Preis-Modell ({@code PriceCharger}/{@code PriceConfig}) rechnet weiterhin in ganzen
 * Cobbledollars als {@link BigInteger} - die Umrechnung auf {@link BigDecimal} passiert NUR hier an
 * der Bridge-Grenze, der Rest von AreaClaims muss sich um nichts kümmern.</p>
 */
public final class CobbleCompanionBridge {

    private CobbleCompanionBridge() {}

    static boolean isCobbleDollarsAvailable() {
        return CobbleDollarsAccess.isAvailable();
    }

    public static BigInteger getBalance(ServerPlayer player) {
        return CobbleDollarsAccess.getBalance(player).toBigInteger();
    }

    public static boolean canAfford(ServerPlayer player, BigInteger amount) {
        return CobbleDollarsAccess.canAfford(player, new BigDecimal(amount));
    }

    public static boolean charge(ServerPlayer player, BigInteger amount) {
        return CobbleDollarsAccess.charge(player, new BigDecimal(amount));
    }

    public static boolean grant(ServerPlayer player, BigInteger amount) {
        return CobbleDollarsAccess.grant(player, new BigDecimal(amount));
    }

    /** Trägt einen Eintrag im Wallet-Log des Spielers ein (sichtbar im Wallet-Tab). */
    public static void logCharge(ServerPlayer player, BigInteger amount, String description) {
        CobbleDollarsAccess.logExternalCharge(player, new BigDecimal(amount), description);
    }
}
