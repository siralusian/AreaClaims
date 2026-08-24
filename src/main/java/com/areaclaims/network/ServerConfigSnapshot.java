package com.areaclaims.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Reine Gson-Transport-DTO (gleicher Stil wie {@link ClaimEditorSnapshot}) für den Admin-
 * Server-Konfigurations-Screen (ROADMAP.md Phase 6-Nachtrag "Admin-GUI") - Freischaltungsschwellen,
 * Teile-Obergrenze und alle Preise (pro Block + pro Regel-Freikauf).
 *
 * <p>{@link PriceEntry} wurde in ROADMAP.md Phase 6-Nachtrag 2 ("Punkt 6: Preis-UI-Neugestaltung")
 * komplett neu gefasst - kein einzelner Preistyp (NONE/ITEM/COBBLE_DOLLARS) mehr, sondern ein
 * optionaler CobbleDollars-Betrag + eine Liste von Item-Preisen + eine Kombinator-Liste (UND/ODER
 * zwischen je zwei vorhandenen Komponenten) - siehe {@code com.areaclaims.economy.PriceConfig}.
 * Dieselbe DTO-Form wird auch für {@link SetPriceRowPacket} (Client -> Server) wiederverwendet.
 */
public class ServerConfigSnapshot {

    public static class ItemEntry {
        public String itemId;
        public int amount;
    }

    public static class PriceEntry {
        /** "" oder "0" = kein Dollars-Anteil. */
        public String dollars = "0";
        public List<ItemEntry> items = new ArrayList<>();
        /** {@code "AND"}/{@code "OR"}, Länge = items.size() + (dollars vorhanden ? 1 : 0) - 1. */
        public List<String> combinators = new ArrayList<>();
    }

    public static class RuleBuyoutPriceEntry {
        public String rule;
        public PriceEntry price;
    }

    public int minOpLevelCreateClaim;
    public int minOpLevelExpandClaim;
    public int minOpLevelBuyout;
    public int maxClaimParts;
    public int tintRangeChunks;
    public boolean cobbleDollarsAvailable;
    public PriceEntry perBlockPrice;
    /** Punkt 11 (Nachtrag 3): eigener Preis nur für neu abgesteckte/erweiterte Unterbereiche. */
    public PriceEntry subClaimPrice;
    /** Punkt 12 (Nachtrag 3): "X pro N Blöcke"-Teiler, siehe {@code com.areaclaims.economy.PriceCharger#chargeUnits}. */
    public int perBlockPriceDivisor = 1;
    public int subClaimPriceDivisor = 1;
    /** Nutzer-Vorgabe (2026-08-18, "Erweitern per Ziehen"): Rückerstattung pro verkleinertem Block - siehe {@code com.areaclaims.economy.PriceCharger#refundUnits} (rundet ab, anders als der Kauf-Preis). */
    public PriceEntry perBlockRefund;
    public PriceEntry subClaimRefund;
    public int perBlockRefundDivisor = 1;
    public int subClaimRefundDivisor = 1;
    /** Punkt 1 (Nachtrag 4): admin-konfigurierbare "Wildnis"-Austritts-Meldung. */
    public boolean wildernessMessageEnabled;
    public String wildernessMessageText = "";
    /** JourneyMap-Integration (2026-08-16): {@code false}, wenn JourneyMap nicht geladen ist ODER der Admin sie abgeschaltet hat. */
    public boolean journeyMapAvailable;
    public boolean journeyMapIntegrationEnabled;
    public List<RuleBuyoutPriceEntry> ruleBuyoutPrices = new ArrayList<>();
}
