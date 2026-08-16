package com.areaclaims.claim;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Mitgliederrollen, aufsteigend sortiert (Ordinal = Rechte-Stufe). Der Claim-Besitzer selbst
 * ist hier NICHT enthalten - er ignoriert grundsätzlich alle Regeln, unabhängig von seiner
 * (nicht vorhandenen) Eintragung in der Mitgliederliste. Siehe ROADMAP.md Phase 4.
 */
public enum ClaimRole {
    NONE,
    MEMBER,
    STAFF,
    COOWNER;

    /**
     * Übersetzter Anzeigename (siehe {@code areaclaims.role.*} in den Lang-Dateien) - NICHT
     * {@link #name()} für UI-Text verwenden, das zeigt nur den rohen Java-Enum-Bezeichner an
     * (Nutzer-Fund: GUI-Editor zeigte "COOWNER" statt einer lokalisierten Bezeichnung).
     */
    public Component translatable() {
        return Component.translatable("areaclaims.role." + name().toLowerCase(Locale.ROOT));
    }
}
