package com.areaclaims.claim;

import net.minecraft.server.level.ServerPlayer;

/**
 * Einzige Stelle, an der die Betreten-Nachricht-Einstellungen eines Claims (Titel-Farbe/-Dauer,
 * Willkommensnachricht+Farbe+Dauer, Grenzfarbe+Verknüpfung, siehe {@link Claim}) tatsächlich
 * validiert und angewendet werden - sowohl {@code /areaclaims entrymsg} (Chat-Befehl) als auch
 * {@link com.areaclaims.network.SetEntryMessagePacket} (GUI-Editor, ROADMAP.md Phase 6-Nachtrag 2,
 * Punkt 2 "Betreten-Nachricht-GUI") rufen NUR {@link #apply} auf - exakt dasselbe "einzige Quelle
 * der Wahrheit"-Prinzip wie {@link ClaimEditService}/{@code AdminConfigService}.
 *
 * <p>{@code field} entscheidet, WAS {@code stringArg}/{@code longArg} bedeuten:
 * <ul>
 *   <li>{@code "color"} - stringArg = Hex-Farbe (Titel)</li>
 *   <li>{@code "duration"} - longArg = Ticks (Titel)</li>
 *   <li>{@code "welcome"} - stringArg = freier Text</li>
 *   <li>{@code "welcomecolor"} - stringArg = Hex-Farbe (Willkommensnachricht)</li>
 *   <li>{@code "welcomeduration"} - longArg = Ticks (Willkommensnachricht)</li>
 *   <li>{@code "boundarycolor"} - stringArg = Hex-Farbe (Grenze) - setzt außerdem
 *       {@code linkBoundaryColorToTitle} auf false (eine explizit gesetzte Grenzfarbe soll nicht
 *       von der Titelfarbe überschrieben werden) - der GUI-Screen sendet bei Bedarf danach noch
 *       ein separates {@code "linkboundarycolor"}-Feld, falls die Checkbox tatsächlich "verknüpft"
 *       stehen soll (Reihenfolge der gesendeten Felder entscheidet, siehe dortigen Kommentar).</li>
 *   <li>{@code "linkboundarycolor"} - longArg != 0 = verknüpft</li>
 * </ul>
 */
public final class EntryMessageService {

    private EntryMessageService() {}

    public enum Result { OK, NOT_AUTHORIZED, INVALID_COLOR, INVALID_FIELD }

    public static Result apply(ServerPlayer caller, Claim claim, String field, String stringArg, long longArg) {
        if (!ClaimEditService.canEdit(caller, claim)) return Result.NOT_AUTHORIZED;
        switch (field) {
            case "color" -> {
                Integer color = parseHexColor(stringArg);
                if (color == null) return Result.INVALID_COLOR;
                claim.setTitleColor(color);
            }
            case "duration" -> claim.setTitleDurationTicks((int) Math.max(1, longArg));
            case "welcome" -> claim.setWelcomeMessage(stringArg);
            case "welcomecolor" -> {
                Integer color = parseHexColor(stringArg);
                if (color == null) return Result.INVALID_COLOR;
                claim.setWelcomeColor(color);
            }
            case "welcomeduration" -> claim.setWelcomeDurationTicks((int) Math.max(1, longArg));
            case "boundarycolor" -> {
                Integer color = parseHexColor(stringArg);
                if (color == null) return Result.INVALID_COLOR;
                claim.setBoundaryColor(color);
                claim.setLinkBoundaryColorToTitle(false);
            }
            case "linkboundarycolor" -> claim.setLinkBoundaryColorToTitle(longArg != 0);
            default -> {
                return Result.INVALID_FIELD;
            }
        }
        ClaimManager.save();
        return Result.OK;
    }

    /** Akzeptiert "RRGGBB" oder "#RRGGBB", 000000-FFFFFF - null bei ungültigem Format/Bereich. */
    public static Integer parseHexColor(String hex) {
        if (hex == null) return null;
        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            long value = Long.parseLong(cleaned, 16);
            if (value < 0 || value > 0xFFFFFF) return null;
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
