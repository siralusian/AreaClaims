package com.areaclaims.client.data;

/**
 * Rein clientseitiger Merker "bin ich gerade im Klick-zum-Abstecken-Modus" (ROADMAP.md Phase 7) -
 * NUR für den {@code RightClickEmpty}-Sonderfall gebraucht (siehe
 * {@code com.areaclaims.client.ClientSelectionEndListener}-Klassenkommentar): dieses Vanilla-
 * Event wird laut NeoForge-Doc-Kommentar NIE an den Server gemeldet, ein Mod muss das selbst tun.
 * Für alle anderen Fälle (Block-/Entity-Interaktion) läuft die Erkennung normal serverseitig über
 * {@code ActiveSelectionManager}, dieses Flag hier ist reine Client-Redundanz für den einen
 * Spezialfall.
 */
public final class ClientSelectionState {

    private static boolean active = false;

    private ClientSelectionState() {}

    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }
}
