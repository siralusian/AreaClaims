package com.areaclaims.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Punkt 8 (Nachtrag 5): Admin-Übersicht "welche Claims nutzen welches hochgeladene Bild" - die
 * SICHTBARKEITS-Hälfte des in Nachtrag 4 als TODO dokumentierten "kein Galerie-/Datei-Aufräumen"-
 * Punkts (tatsächliches Löschen bleibt bewusst manuelle/künftige Arbeit, siehe
 * {@code AreaClaimsImageUsageScreen}-Klassenkommentar für die Scope-Begründung).
 */
public class ImageUsageEntry {
    public String hash;
    public int width;
    public int height;
    public String uploaderName;
    public List<ClaimRef> usedBy = new ArrayList<>();

    public static class ClaimRef {
        public String claimName;
        public String ownerName;
        public boolean main;
    }
}
