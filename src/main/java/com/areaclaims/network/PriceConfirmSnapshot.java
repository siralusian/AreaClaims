package com.areaclaims.network;

/**
 * Reine Gson-Transport-DTO (gleicher Stil wie {@link ServerConfigSnapshot}) für die Preis-
 * Bestätigung-Übersicht (ROADMAP.md Phase 7-8 "Klick-zum-Abstecken + Preisbestätigung") - wird
 * NUR verschickt, wenn für die gerade abgeschlossene Aktion tatsächlich ein Preis konfiguriert ist
 * (siehe {@code StakingService}; ist der Preis {@code NONE}, wird sofort committet, kein Popup).
 */
public class PriceConfirmSnapshot {

    /** {@code ActiveSelectionManager.ActionType#name()} - für welche Aktion diese Bestätigung gilt. */
    public String action;
    /** UUID-String oder {@code null} (bei NEW_CLAIM) - Ziel-/Elternbereich, siehe {@code ActiveSelectionManager}. */
    public String targetClaimId;
    public ServerConfigSnapshot.PriceEntry price;
    /** Anzahl Blöcke der neu abgesteckten Fläche - der Preis-Multiplikator. */
    public int blocks;
    /** Ob der Preis mit dem AKTUELLEN Inventar/Guthaben des Spielers gerade bezahlbar wäre - steuert, ob "Bestätigen" ausgegraut ist. */
    public boolean affordable;
    /**
     * Nutzer-Vorgabe (2026-08-18, "Anpassen"-Button): NUR bei {@code ADJUST_MAIN}/{@code
     * ADJUST_SUB} befüllt (statt {@link #blocks}/{@link #price}) - eine Anpassen-Sitzung kann in
     * derselben Bestätigung SOWOHL Blöcke hinzufügen ALS AUCH entfernen, braucht also beide Beträge
     * gleichzeitig statt eines einzelnen Preis-/Rückerstattung-Felds (siehe
     * {@code AreaClaimsEditorScreen#renderPriceConfirmOverlay}).
     */
    public int addedBlocks;
    public ServerConfigSnapshot.PriceEntry addedPrice;
    public int removedBlocks;
    public ServerConfigSnapshot.PriceEntry removedPrice;
}
