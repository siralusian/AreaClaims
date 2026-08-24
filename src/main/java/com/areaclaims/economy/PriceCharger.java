package com.areaclaims.economy;

import com.areaclaims.integration.CobbleCompanionBridge;
import com.areaclaims.integration.ModAvailability;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Zieht einen konfigurierten {@link PriceConfig} vom Spieler ab. Seit ROADMAP.md Phase
 * 6-Nachtrag 2 ("Punkt 6: Preis-UI-Neugestaltung") kann ein Preis aus MEHREREN Komponenten
 * bestehen (optional CobbleDollars, gefolgt von bis zu 5 Item-Preisen), verknüpft über
 * UND/ODER-{@link PriceConfig.Combinator}e.
 *
 * <p><b>Auswertungsregel (eigene, hier dokumentierte Design-Entscheidung - Nutzer-Vorgabe war ein
 * sinnvoller Vorschlag, den ich so übernommen habe):</b> die Komponenten werden STRENG VON LINKS
 * NACH RECHTS zu einem einzigen "aktuellen Ergebnis" gefaltet (kein Operator-Vorrang, keine
 * Klammerung - genau die Reihenfolge, in der die Umschalt-Buttons zwischen den Feldern stehen):
 * <ul>
 *   <li>Start: Ergebnis = Komponente 0, falls bezahlbar (dann "erfolgreich mit {0}"), sonst
 *       "fehlgeschlagen".</li>
 *   <li>UND mit der nächsten Komponente i: nur erfolgreich, wenn das bisherige Ergebnis SCHON
 *       erfolgreich war UND Komponente i bezahlbar ist - das neue zu belastende Set ist dann
 *       "bisheriges Set + {i}". War das bisherige Ergebnis schon fehlgeschlagen, bleibt es das
 *       (kurzschließt, unabhängig von Komponente i - ein bereits gescheitertes UND kann durch
 *       nichts Nachfolgendes mehr gerettet werden).</li>
 *   <li>ODER mit der nächsten Komponente i: war das bisherige Ergebnis schon erfolgreich, bleibt
 *       es UNVERÄNDERT (die frühere/linke Option gewinnt, Komponente i wird ignoriert und NICHT
 *       belastet - "prefer earlier" gemäß Nutzer-Vorgabe). War es fehlgeschlagen, wird stattdessen
 *       Komponente i allein versucht (erfolgreich, falls bezahlbar, mit Set {i}; sonst weiterhin
 *       fehlgeschlagen).</li>
 * </ul>
 * Am Ende wird - falls erfolgreich - GENAU das im Ergebnis gesammelte Set an Komponenten
 * tatsächlich belastet (nicht mehr, nicht weniger). Diese linksassoziative Faltung reproduziert
 * exakt die vom Nutzer beschriebene paarweise Semantik ("AND: beide bezahlbar UND beide werden
 * belastet; OR: die erste bezahlbare wird belastet, nur bei KEINER bezahlbaren schlägt die ganze
 * Aktion fehl") und verallgemeinert sie verständlich auf beliebig lange Ketten.
 *
 * <p>Für den (weiterhin sehr häufigen) Fall genau EINER Komponente bleiben die alten, präzisen
 * Fehlermeldungen erhalten ({@link Result#INSUFFICIENT_ITEM}/{@link Result#INSUFFICIENT_COBBLE_DOLLARS});
 * bei mehreren Komponenten wird bei einem Fehlschlag pauschal {@link Result#INSUFFICIENT_PRICE}
 * gemeldet (bewusste UX-Vereinfachung - bei einer UND/ODER-Kette wäre "welche genau hat gefehlt"
 * oft mehrdeutig/wenig hilfreich formuliert, siehe ROADMAP.md).
 */
public final class PriceCharger {

    private PriceCharger() {}

    public enum Result { OK, INSUFFICIENT_ITEM, INSUFFICIENT_COBBLE_DOLLARS, INSUFFICIENT_PRICE, UNAVAILABLE, MISCONFIGURED }

    /**
     * Nachtrag 7, Punkt 2 (Wallet-Log): wie {@link Result}, aber zusätzlich mit dem TATSÄCHLICH
     * belasteten CobbleDollars-Betrag - 0, falls der Preis gar keine Dollar-Komponente hatte ODER
     * (bei einer ODER-Kette) stattdessen eine Item-Komponente bezahlt wurde. Nur dieser Wert darf
     * ins Wallet-Log geschrieben werden, NICHT einfach {@code price.dollars() * multiplier}, da
     * sonst bei ODER-Ketten ein Log-Eintrag entstünde, obwohl der Spieler in Wahrheit mit Items
     * bezahlt hat.
     */
    public record ChargeOutcome(Result result, BigInteger dollarsCharged) {
        static ChargeOutcome of(Result result) {
            return new ChargeOutcome(result, BigInteger.ZERO);
        }
    }

    /**
     * Punkt 12 (Nachtrag 3, Preis-UI "pro N Blöcke"): rechnet eine Blockfläche in Preis-Einheiten
     * um - IMMER AUFGERUNDET (Nutzer-Vorgabe: Teiler=100, Fläche=150 -> 2 Einheiten berechnen, nicht
     * abrunden auf 1). {@code divisor <= 0} wird wie 1 behandelt (kein Teiler konfiguriert = 1:1 wie
     * bisher, siehe {@link com.areaclaims.economy.PriceConfigManager}).
     */
    public static int chargeUnits(int blocks, int divisor) {
        int d = Math.max(1, divisor);
        int b = Math.max(0, blocks);
        return (b + d - 1) / d;
    }

    /**
     * Nutzer-Vorgabe (2026-08-18, "Erweitern per Ziehen"): Gegenstück zu {@link #chargeUnits} für
     * Rückerstattungen - IMMER ABGERUNDET (Nutzer-Zitat: "Hier müssen wir allerdings immer abrunden
     * wenn der Preis nicht Pro Block ist"), damit ein Admin nicht versehentlich mehr auszahlt, als
     * die verkleinerte Fläche tatsächlich hergibt. {@code divisor <= 0} wird wie 1 behandelt (dann
     * ändert Ab-/Aufrunden ohnehin nichts).
     */
    public static int refundUnits(int blocks, int divisor) {
        int d = Math.max(1, divisor);
        int b = Math.max(0, blocks);
        return b / d;
    }

    /**
     * Gegenstück zu {@link #chargeDetailed} für Rückerstattungen: zahlt ALLE konfigurierten
     * Komponenten aus (KEINE UND/ODER-Auswertung wie beim Bezahlen - diese Verknüpfung regelt nur,
     * WELCHE Komponente ein Spieler beim BEZAHLEN alternativ nutzen darf, für eine Auszahlung an den
     * Spieler ergibt "wähle nur eine aus" keinen Sinn, es wird schlicht alles ausgezahlt, was der
     * Admin für diese Preis-Zeile hinterlegt hat). Items ohne gültige ID werden übersprungen
     * (Verteidigung in der Tiefe - die GUI verhindert das schon selbst).
     *
     * @return der tatsächlich ausgezahlte CobbleDollars-Betrag (0, falls keine Dollars-Komponente
     *     konfiguriert ODER CobbleDollars nicht verfügbar ist) - fürs Wallet-Log wie bei {@link ChargeOutcome}.
     */
    public static BigInteger refund(ServerPlayer player, PriceConfig price, int multiplier) {
        int factor = Math.max(1, multiplier);
        BigInteger dollarsGranted = BigInteger.ZERO;
        if (price.hasDollars() && ModAvailability.isCobbleDollarsAvailable()) {
            BigInteger amount = price.dollars().multiply(BigInteger.valueOf(factor));
            if (amount.signum() > 0) {
                CobbleCompanionBridge.grant(player, amount);
                dollarsGranted = amount;
            }
        }
        for (PriceConfig.ItemAmount item : price.items()) {
            if (!isValidItem(item.itemId())) continue;
            long amount = (long) item.amount() * factor;
            if (amount <= 0) continue;
            giveItems(player, itemOf(item.itemId()), amount);
        }
        return dollarsGranted;
    }

    private static void giveItems(ServerPlayer player, Item item, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            int take = (int) Math.min(remaining, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, take);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= take;
        }
    }

    private sealed interface Comp permits Dollars, ItemComp {}
    private record Dollars(BigInteger amount) implements Comp {}
    private record ItemComp(String itemId, int amount) implements Comp {}

    public static Result charge(ServerPlayer player, PriceConfig price, int multiplier) {
        return chargeDetailed(player, price, multiplier).result();
    }

    /** Wie {@link #charge}, liefert zusätzlich {@link ChargeOutcome#dollarsCharged()} - siehe dortigen Klassenkommentar. Nachtrag 7, Punkt 2: von den echten Bezahl-Aufrufstellen (Claim anlegen/erweitern, SubClaim anlegen, Regel freikaufen) genutzt, um genau den tatsächlich belasteten Betrag ins Wallet-Log zu schreiben. */
    public static ChargeOutcome chargeDetailed(ServerPlayer player, PriceConfig price, int multiplier) {
        int factor = Math.max(1, multiplier);
        List<Comp> comps = toComponents(price);
        if (comps.isEmpty()) return ChargeOutcome.of(Result.OK);

        for (Comp c : comps) {
            if (c instanceof ItemComp ic && !isValidItem(ic.itemId())) return ChargeOutcome.of(Result.MISCONFIGURED);
            if (c instanceof Dollars && !ModAvailability.isCobbleDollarsAvailable()) return ChargeOutcome.of(Result.UNAVAILABLE);
        }

        List<Integer> chargeSet = computeChargeSet(player, price, comps, factor);
        if (chargeSet == null) {
            if (comps.size() == 1) return ChargeOutcome.of(preciseFailure(comps.get(0)));
            return ChargeOutcome.of(Result.INSUFFICIENT_PRICE);
        }
        BigInteger dollarsCharged = BigInteger.ZERO;
        for (int index : chargeSet) {
            Comp comp = comps.get(index);
            actuallyCharge(player, comp, factor);
            if (comp instanceof Dollars d) dollarsCharged = d.amount().multiply(BigInteger.valueOf(factor));
        }
        return new ChargeOutcome(Result.OK, dollarsCharged);
    }

    /**
     * Reine Bezahlbarkeits-Prüfung OHNE zu belasten (ROADMAP.md Phase 7-8 "Klick-zum-Abstecken +
     * Preisbestätigung" - die Preisbestätigung-Popup muss den "Bestätigen"-Button ausgrauen
     * können, OHNE den Spieler schon zu belasten). Nutzt dieselbe Faltungslogik wie {@link #charge}
     * (siehe {@link #computeChargeSet}), belastet aber nichts.
     */
    public static boolean canAfford(ServerPlayer player, PriceConfig price, int multiplier) {
        int factor = Math.max(1, multiplier);
        List<Comp> comps = toComponents(price);
        if (comps.isEmpty()) return true;
        for (Comp c : comps) {
            if (c instanceof ItemComp ic && !isValidItem(ic.itemId())) return false;
            if (c instanceof Dollars && !ModAvailability.isCobbleDollarsAvailable()) return false;
        }
        return computeChargeSet(player, price, comps, factor) != null;
    }

    /** Siehe Klassenkommentar "Auswertungsregel" - gemeinsame Faltungslogik für {@link #charge} und {@link #canAfford}. */
    private static List<Integer> computeChargeSet(ServerPlayer player, PriceConfig price, List<Comp> comps, int factor) {
        boolean[] affordable = new boolean[comps.size()];
        for (int i = 0; i < comps.size(); i++) affordable[i] = canAffordComponent(player, comps.get(i), factor);

        List<PriceConfig.Combinator> combinators = price.normalized().combinators();
        List<Integer> chargeSet = affordable[0] ? new ArrayList<>(List.of(0)) : null;
        for (int i = 1; i < comps.size(); i++) {
            PriceConfig.Combinator op = combinators.get(i - 1);
            if (op == PriceConfig.Combinator.AND) {
                if (chargeSet != null) {
                    if (affordable[i]) {
                        chargeSet.add(i);
                    } else {
                        chargeSet = null;
                    }
                }
            } else { // OR
                if (chargeSet == null && affordable[i]) {
                    chargeSet = new ArrayList<>(List.of(i));
                }
                // chargeSet != null (bereits erfolgreich) -> unverändert, frühere Option gewinnt.
            }
        }
        return chargeSet;
    }

    private static List<Comp> toComponents(PriceConfig price) {
        List<Comp> comps = new ArrayList<>();
        if (price.hasDollars()) comps.add(new Dollars(price.dollars()));
        for (PriceConfig.ItemAmount item : price.items()) comps.add(new ItemComp(item.itemId(), item.amount()));
        return comps;
    }

    private static boolean canAffordComponent(ServerPlayer player, Comp comp, int factor) {
        if (comp instanceof Dollars d) {
            BigInteger needed = d.amount().multiply(BigInteger.valueOf(factor));
            if (needed.signum() <= 0) return true;
            return CobbleCompanionBridge.getBalance(player).compareTo(needed) >= 0;
        }
        ItemComp ic = (ItemComp) comp;
        long needed = (long) ic.amount() * factor;
        if (needed <= 0) return true;
        return hasEnough(player, itemOf(ic.itemId()), needed);
    }

    private static void actuallyCharge(ServerPlayer player, Comp comp, int factor) {
        if (comp instanceof Dollars d) {
            BigInteger needed = d.amount().multiply(BigInteger.valueOf(factor));
            if (needed.signum() <= 0) return;
            CobbleCompanionBridge.charge(player, needed);
            return;
        }
        ItemComp ic = (ItemComp) comp;
        long needed = (long) ic.amount() * factor;
        if (needed <= 0) return;
        removeItems(player, itemOf(ic.itemId()), needed);
    }

    private static Result preciseFailure(Comp comp) {
        return comp instanceof Dollars ? Result.INSUFFICIENT_COBBLE_DOLLARS : Result.INSUFFICIENT_ITEM;
    }

    private static boolean isValidItem(String itemId) {
        ResourceLocation loc = itemId == null ? null : ResourceLocation.tryParse(itemId);
        return loc != null && BuiltInRegistries.ITEM.containsKey(loc);
    }

    private static Item itemOf(String itemId) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
    }

    private static boolean hasEnough(ServerPlayer player, Item item, long amount) {
        long count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) count += stack.getCount();
            if (count >= amount) return true;
        }
        return false;
    }

    private static void removeItems(ServerPlayer player, Item item, long amount) {
        long remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (!stack.is(item)) continue;
            int take = (int) Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }
}
