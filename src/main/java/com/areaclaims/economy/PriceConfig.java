package com.areaclaims.economy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Ein konfigurierbarer Preis (pro Block ODER pro Regel-Freikauf, siehe ROADMAP.md Phase 6) - seit
 * ROADMAP.md Phase 6-Nachtrag 2 ("Punkt 6: Preis-UI-Neugestaltung") KEIN einzelner Preistyp mehr,
 * sondern eine geordnete Liste von "Komponenten": optional ein CobbleDollars-Betrag (immer zuerst,
 * nur wählbar wenn {@link com.areaclaims.integration.ModAvailability#isCobbleDollarsAvailable()})
 * gefolgt von bis zu 5 Item-Preisen. Zwischen je zwei vorhandenen Komponenten steht ein
 * UND/ODER-{@link Combinator} - siehe {@link PriceCharger} für die genaue Auswertung. Ein leeres
 * Item-Feld bedeutet "kein Preis über diesen Slot" (Nutzer-Vorgabe) - das ersetzt das frühere
 * explizite NONE/ITEM-Umschalt-Feld komplett; ein "leerer" {@link PriceConfig} (kein Dollars-Betrag,
 * keine Items) ist weiterhin gleichbedeutend mit "kostenlos", siehe {@link #isFree()}.
 */
public record PriceConfig(BigInteger dollars, List<ItemAmount> items, List<Combinator> combinators) {

    public enum Combinator { AND, OR }

    /** Ein einzelner Item-Preis-Slot (Item-ID + Menge, beide immer gültig/nicht leer - leere Slots werden nie in dieser Liste gespeichert). */
    public record ItemAmount(String itemId, int amount) {}

    /** Maximale Anzahl Item-Preis-Slots (Nutzer-Vorgabe, ROADMAP.md Phase 6-Nachtrag 2, Punkt 6). */
    public static final int MAX_ITEM_SLOTS = 5;

    public PriceConfig {
        items = items == null ? List.of() : List.copyOf(items);
        combinators = combinators == null ? List.of() : List.copyOf(combinators);
    }

    public static PriceConfig none() {
        return new PriceConfig(null, List.of(), List.of());
    }

    /** Bequemlichkeits-Konstruktor für den (weiterhin häufigsten) Fall "genau ein Item-Preis, keine Kombination nötig" - z. B. von {@code /areaclaims config price} (Befehl, bewusst auf Einzel-Komponenten beschränkt, siehe AreaClaimsCommands). */
    public static PriceConfig singleItem(String itemId, int amount) {
        return new PriceConfig(null, List.of(new ItemAmount(itemId, amount)), List.of());
    }

    public static PriceConfig singleDollars(BigInteger amount) {
        return new PriceConfig(amount, List.of(), List.of());
    }

    public boolean hasDollars() {
        return dollars != null && dollars.signum() > 0;
    }

    public int componentCount() {
        return (hasDollars() ? 1 : 0) + items.size();
    }

    public boolean isFree() {
        return componentCount() == 0;
    }

    /**
     * Baut eine neue {@link PriceConfig} mit denselben Komponenten, aber auf eine gültige
     * {@code combinators}-Länge ({@code componentCount() - 1}, minimal 0) zurechtgestutzt/
     * aufgefüllt (fehlende Einträge werden mit {@link Combinator#AND} aufgefüllt) - Verteidigung
     * in der Tiefe für den Fall, dass eine gespeicherte/übertragene Liste nicht exakt passt.
     */
    public PriceConfig normalized() {
        int needed = Math.max(0, componentCount() - 1);
        if (combinators.size() == needed) return this;
        List<Combinator> fixed = new ArrayList<>(combinators.subList(0, Math.min(needed, combinators.size())));
        while (fixed.size() < needed) fixed.add(Combinator.AND);
        return new PriceConfig(dollars, items, fixed);
    }

    /** Nur bis {@link #MAX_ITEM_SLOTS} - überzählige Items werden abgeschnitten (Verteidigung in der Tiefe, die GUI verhindert das schon selbst). */
    public static PriceConfig of(BigInteger dollars, List<ItemAmount> items, List<Combinator> combinators) {
        List<ItemAmount> clamped = items.size() > MAX_ITEM_SLOTS ? items.subList(0, MAX_ITEM_SLOTS) : items;
        return new PriceConfig(dollars != null && dollars.signum() > 0 ? dollars : null, clamped, combinators).normalized();
    }
}
