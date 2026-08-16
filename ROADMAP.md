# AreaClaims – Roadmap

Eigenständige NeoForge-Mod (MC 1.21.1 / NeoForge 21.1.233) für blockgenaue, frei geformte
Gebietsansprüche (Claims) mit Haupt-/Unterbereichen, Betreten-Nachrichten (Ortsschild-Stil),
Mitgliederrollen mit rollenabhängig freischaltbaren Verbieten-Regeln, und einer optionalen
Preis-Brücke zu CobbleDollars (falls installiert) oder frei wählbaren Items. Keine Cobblemon-
Abhängigkeit im Kern; CobbleDollars-Anbindung folgt exakt dem Soft-Dependency-Muster aus
`CobbleCompanion/docs/API_EXTENSIONS.md` (ModAvailability + eigener optionaler Netzwerk-Namespace).

## Phase 0 – Fundament (erledigt, ungetestet)
- Eigenständiges Gradle/NeoForge-Projekt (build.gradle, gradle.properties, settings.gradle, Wrapper)
- Mod-Grundgerüst (`AreaClaims`), `neoforge.mods.toml`
- Commands `/areaclaims` (Alias `/ac`): `open`
- Netzwerk-Grundgerüst: `OpenEditorPacket` (S2C, Platzhalter-Editor-Screen, seit Phase 5 der
  echte GUI-Editor)
- EN/DE-Lokalisierung von Anfang an
- Platzhalter-Editor-Screen (Titel + Schließen-Button) - seit Phase 5 durch `AreaClaimsEditorScreen` ersetzt

**Berechtigungsmodell (Nutzer-Entscheidung, rückgängig gemacht):** ursprünglich gab es hier eine
eigenständige `AreaClaimsAdminManager`-Admin-Liste (Konsole/bestehender Admin schaltet weitere
Admins frei, unabhängig von Vanilla-OP - analog zum CreativeMenu-Muster). Der Nutzer hat das
explizit rückgängig gemacht (gleiche Entscheidung wie bei MobTweaks/InvSpy): `AreaClaimsAdminManager`
wurde komplett gelöscht, `/areaclaims admin <Name>` entfernt. Admin-artige Rechte (z. B. fremde
Claims per Chat-Befehl/GUI-Editor bearbeiten, siehe `ClaimEditService#canEdit`) laufen jetzt
ausschließlich über die normale Vanilla-OP-Stufe 4 (`player.hasPermissions(4)` /
`source.hasPermission(4)`) - keine eigene Datei/Persistenz mehr dafür nötig.

## Phase 1 – Auswahl-Werkzeug (erledigt, ungetestet)

Umgesetzt: `ToolInteractionListener` (Linksklick = Punkt hinzufügen, Rechtsklick = letzten Punkt
entfernen + vanilla-Ackerbau unterdrückt, beides nur bei `/areaclaims tool` = AN), `SelectionManager`
(In-Memory-Auswahl pro Spieler), `ToolStateManager` (persistierter Toggle,
`areaclaims_tool_state.json`), Actionbar-Feedback (Punktzahl + Fläche via Shoelace-Formel ab 3
Punkten), Partikel-Umriss (`ParticleTypes.END_ROD`, alle 15 Ticks, nur an den auswählenden Spieler
via `ServerLevel#sendParticles`). Committen der Auswahl zu einem echten Claim läuft über den neuen
Befehl `/areaclaims claim <Name>` (siehe Phase 2). Kein In-Game-Test in dieser Session möglich
(kein Zugriff auf ein laufendes Spielfenster) - nur `./gradlew compileJava` verifiziert.

Ursprüngliche Entscheidungsnotiz (Nutzer, 2026-08-14, final):
- **Kein eigenes Item** – die echte Vanilla `minecraft:golden_hoe` wird als Werkzeug benutzt (an die
  bekannte Claim-Mod angelehnt, deren Wahl der Nutzer nachvollziehbar findet).
- Das besondere Verhalten ist **per Befehl pro Spieler ein-/ausschaltbar** (`/areaclaims tool` bzw.
  `/ac tool`), NICHT permanent an das Item gebunden: ist der Modus für einen Spieler AUS, verhält sich
  seine goldene Hacke zu 100% vanilla (Ackerbau). Ist er AN: Linksklick auf einen Block fügt dessen
  X/Z-Position als Punkt zur aktuellen Polygon-Auswahl hinzu, Rechtsklick entfernt den letzten Punkt
  bzw. schließt (bei ≥3 Punkten + Bestätigung) die Auswahl ab – dabei muss das vanilla
  Rechtsklick-Ackerbau-Verhalten der Hacke in diesem Modus unterdrückt/gecancelt werden.
- **Polygon ist reiner 2D-Grundriss auf der XZ-Ebene, KEIN Y-Bereich** – anders als bei WorldEdit ist
  die Höhe hier komplett irrelevant, ein abgesteckter Bereich erstreckt sich immer über die gesamte
  Weltbauhöhe (Bedrock/Void-Grenze bis Bauhöhengrenze). Das ist eine bewusste, finale Nutzer-Entscheidung
  – nicht mehr die frühere Standardannahme mit Y-Min/Max.
- Visualisierung der aktuellen Auswahl (Partikel oder Linien-Rendering entlang der Kanten) sowie
  laufende Flächen-/Block-Anzeige im Actionbar/Chat.
- Toggle-Zugriff vorerst offen für alle Spieler (Phase 6 ergänzt später die Freischaltungs-/Preis-Gates
  aus der Server-Konfiguration on top – Admin/OP4 hat ohnehin immer Zugriff).

## Phase 2 – Claim-Datenmodell (erledigt, ungetestet)

> **Architektur-Nachtrag (siehe Phase-5-Nachtrag "Erweitern v2" unten):** die hier ursprünglich
> beschriebene Ein-Polygon-pro-Claim-Annahme gilt NICHT mehr. Seit dem "Erweitern"-Redesign hat
> jeder Claim eine Liste unabhängiger Polygon-Teile (`Claim.parts()`) statt eines einzelnen
> `polygon`-Felds. Punkt-in-Claim/Fläche/Chunk-Index/Überlappungsprüfung sind entsprechend auf
> "über alle Teile" umgestellt - Details dort, dieser Abschnitt beschreibt bewusst nur noch den
> historischen Ausgangsstand.

Umgesetzt: `Claim`/`ClaimManager`/`ClaimRole`/`RuleType`/`RuleSetting` im neuen Package
`com.areaclaims.claim`, Geometrie-Helfer in `com.areaclaims.geometry.PolygonUtil` (Ray-Casting
Punkt-in-Polygon, Shoelace-Fläche, Selbstüberschneidungs- und Polygon-Überlappungs-Test,
Unterbereichs-Containment-Test). Persistenz als Gson-JSON (`areaclaims_claims.json`, Weltordner,
gleicher Stil wie `ToolStateManager`). Chunk-Index (`Map<ChunkKey, List<UUID>>`, Dimension +
Chunk-Koordinate) für Punktabfragen. Überlappungsprüfung beim Erstellen eines Hauptbereichs gegen
alle bestehenden Hauptbereiche anderer Besitzer (gleiche Dimension). `ClaimManager.createSubClaim(...)`
ist fertig implementiert (inkl. Containment-Check), aber es gibt noch KEINEN Befehl, der sie nutzt -
das war in der finalisierten Phase-2-Vorgabe nicht gefordert (nur das Datenmodell) und wird
sinnvollerweise Teil des GUI-Editors in Phase 5, oder kann bei Bedarf vorher per Kurz-Befehl
nachgezogen werden. Bekannter grober Randfall (wie vom Nutzer bei der Beauftragung akzeptiert):
reine Kantenberührung ohne echte Flächenüberlappung wird sowohl bei der Überlappungs- als auch bei
der Containment-Prüfung konservativ als "überlappt"/"nicht enthalten" behandelt.

- Hauptbereich: Polygon (reiner XZ-Grundriss, kein Y-Bereich – siehe Phase 1), Name, Besitzer,
  Zeitstempel.
- Unterbereiche: eigenes Polygon INNERHALB des Hauptbereichs (geometrisch geprüft – ein Unterbereich,
  der über die Hauptbereichsgrenze hinausragt, wird abgelehnt), eigener Haupt-/Nebentitel.
- Punkt-in-Polygon-Test (Ray-Casting/Winding), Chunk-Index für Performance (nur Claims in relevanten
  Chunks prüfen, nicht bei jedem Block-Event alle Claims der Welt durchgehen).
- Überlappungsprüfung zwischen fremden Hauptbereichen (keine zwei Besitzer für dieselbe Fläche, außer
  evtl. bewusst erlaubte Sonderfälle – bei Phase-Start klären).

## Phase 3 – Betreten-Nachricht (erledigt, ungetestet)

Umgesetzt: `ClaimEntryListener` prüft alle 10 Ticks pro Online-Spieler die aktuelle
Haupt-/Unterbereichs-Position (`ClaimManager.findMainClaimAt`/`findSubClaimAt`), mit
2-Prüfungen-Hysterese (= 20 Ticks) gegen Grenz-Zittern. Title = Hauptbereichsname oder "Wildnis"
außerhalb aller Claims, Subtitle = Unterbereichsname (leer, falls keiner). Versand über
`ClientboundSetTitleTextPacket`/`ClientboundSetSubtitleTextPacket`/`ClientboundSetTitlesAnimationPacket`
(alle drei in den dekompilierten Sourcen verifiziert, nicht geraten). Zustand wird beim Logout
aufgeräumt (`PlayerEvent.PlayerLoggedOutEvent`).

- Title/Subtitle-Einblendung beim Wechsel zwischen Haupt-/Unterbereich bzw. beim Verlassen aller
  Bereiche ("Wildnis" o. ä.). Haupttitel = Hauptbereichsname (z. B. Stadtname), Untertitel =
  Unterbereichsname (z. B. Ortsteil) – nur Haupttitel, falls kein Unterbereich betreten wurde.
- Cooldown/Hysterese gegen Nachrichten-Spam bei Grenz-Zittern (Spieler steht exakt auf der Kante).

## Phase 4 – Regeln + Mitgliederrollen (erledigt, größtenteils verdrahtet, ungetestet)

Umgesetzt: `ClaimRole` (NONE < MEMBER < STAFF < COOWNER), `RuleType` (die 8 bestätigten Regeln),
`RuleSetting` (enabled + Mindestrolle-zum-Ignorieren), Mitgliederliste + Regel-Map pro Claim
(Haupt- oder Unterbereich getrennt), `ClaimProtectionManager.isAllowed(...)` als zentraler
Check (Haupt- UND Unterbereich müssen beide erlauben - Unterbereich kann nur verschärfen, nie
lockern; Besitzer ignoriert immer alles). Neue Befehle `/areaclaims role <Claim> <Spieler> <Rolle>`
und `/areaclaims rule <Claim> <Regel> enable <MindestRolle>|disable` (Besitzer oder Vanilla-OP-Stufe 4,
siehe Phase-0-Abschnitt zur entfernten Admin-Liste), da es vor dem GUI-Editor (Phase 5) sonst keinen
Weg gäbe, Phase 4 überhaupt zu benutzen/zu testen.

Durchsetzung (`ClaimProtectionListener`) - jede verwendete Event-Klasse wurde vor dem Schreiben in
den dekompilierten NeoForge-21.1.233-Sourcen nachgeschlagen (`neoforge-21.1.233-sources.jar` unter
`build/moddev/artifacts` nach dem ersten Gradle-Sync), nicht geraten:

| Regel | Status | Event |
|---|---|---|
| BUILD (Bauen/Abbauen) | ✅ verdrahtet | `BlockEvent.BreakEvent` + `BlockEvent.EntityPlaceEvent` |
| CONTAINER_OPEN (Truhen öffnen) | ✅ verdrahtet | `PlayerInteractEvent.RightClickBlock` (vor dem Öffnen abgefangen, Ziel implementiert `net.minecraft.world.Container`) |
| CONTAINER_ITEM_TRANSFER (Items entnehmen/hineinlegen) | ❌ TODO | Kein sauberes NeoForge-Event gefunden - `PlayerContainerEvent.Open/Close` sind NICHT cancelbar und decken ohnehin nur das Öffnen/Schließen ab, nicht einzelne Slot-Klicks. Bräuchte vermutlich einen Hook auf `ServerGamePacketListenerImpl#handleContainerClick` (Mixin) - nächste Runde. |
| PVP | ✅ verdrahtet | `AttackEntityEvent` (nur wenn Ziel ein `ServerPlayer` ist) |
| MOB_SPAWNING | ✅ verdrahtet | `FinalizeSpawnEvent#setSpawnCancelled` - bewusst simpel (blockt aktuell ALLE Spawns im Claim, unterscheidet noch nicht friedlich/feindlich, siehe Nutzer-Vorgabe "nicht überentwickeln") |
| REDSTONE_INTERACT (Hebel/Knöpfe/Druckplatten) | ⚠️ teilweise | Hebel/Knöpfe über `RightClickBlock` (`LeverBlock`/`ButtonBlock`) abgedeckt. Druckplatten TODO - werden über Entity-Bewegung ausgelöst, kein Interact-Event gefunden, bräuchte vermutlich einen Mixin auf `PressurePlateBlock`. |
| VEHICLE (Boote/Minecarts benutzen/platzieren) | ⚠️ teilweise | Aufsitzen/Benutzen über `EntityMountEvent` (nur `isMounting()`) abgedeckt. Platzieren TODO - läuft über `Item#use`/`useOn`, kein eindeutig verifizierbares Event gefunden. |
| ITEM_DROP | ✅ verdrahtet | `ItemTossEvent` |

- Rollen: Mitglied, Mitarbeiter, Mitbesitzer (aufsteigende Rechte), pro Haupt- oder Unterbereich
  getrennt vergebbar.
- Verbieten-Regeln (Default: alles erlaubt, Besitzer schaltet gezielt Verbote scharf) – vom Nutzer
  bestätigter Regelsatz für den Start:
  - Bauen/Abbauen
  - Truhen/Lager öffnen
  - Items aus Truhen/Lagern entnehmen oder hineinlegen
  - PvP im Bereich erlauben/verbieten
  - Mob-Spawning im Bereich unterbinden (friedlich schützen oder feindlich verhindern)
  - Redstone-Interaktion (Hebel/Knöpfe/Druckplatten) für Nicht-Mitglieder sperren – praktisch für
    Türen/Tore
  - Fahrzeuge (Boote/Minecarts) benutzen oder platzieren
  - Item-Drop verbieten (gegen Littering/Duping-Verstecke)
- Pro Regel einstellbare Mindest-Rolle, ab der die Regel ignoriert wird ("Keine Rolle" bis
  "Mitbesitzer") – Besitzer selbst ignoriert immer alles.
- Regeln pro Haupt- oder Unterbereich einzeln (Unterbereich kann Hauptbereichs-Regeln verschärfen
  oder eigene zusätzliche Regeln haben – NICHT lockern, sonst wäre der Hauptbereichs-Schutz wirkungslos;
  bei Phase-Start final festlegen).

## Phase 5 – GUI-Editor (erledigt, ungetestet)

Umgesetzt: `AreaClaimsEditorScreen` (`com.areaclaims.client.gui`, ersetzt den bisherigen
`AreaClaimsPlaceholderScreen` - der wurde gelöscht, `OpenEditorPacket` bleibt als reines
"öffne den Screen"-Signal bestehen) - links eine Liste der vom öffnenden Spieler besessenen
Claims (`ClaimManager.findClaimsOwnedBy`), rechts für den ausgewählten Claim die Mitgliederliste
(Rolle im Kreis MEMBER→STAFF→COOWNER weiterschalten, X-Button entfernt, Textfeld + Button zum
Hinzufügen per Spielername - muss online sein, exakt wie beim Chat-Befehl) und alle 8 Regeln
(Aktiv/Deaktiviert-Umschalter + Mindestrolle im Kreis weiterschalten). `FixedScaleScreen` 1:1 aus
`CreativeMenu/.../FixedScaleScreen.java` portiert, alle Text-/Layout-Werte als benannte
Justierschrauben-Konstanten oben in der Klasse (Titel, Listen-Raster, Button-Breiten, Farben usw.).

Netzwerk: neues C2S `SetRolePacket`/`SetRulePacket` sowie S2C `ClaimSyncPacket` (Claims des
Spielers inkl. aufgelöster Mitgliedernamen + vollständigem Regelsatz, als JSON-String via Gson -
bewusst KEINE komplexe verschachtelte StreamCodec-Struktur, siehe `ClaimEditorSnapshot`-
Klassenkommentar). `/areaclaims open` schickt jetzt IMMER erst den frischen Snapshot, dann das
`OpenEditorPacket`; jede erfolgreiche Mutation über die Paket-Handler schickt danach ebenfalls
einen frischen Snapshot (`ClaimSnapshotBuilder.sendTo`), der offene Screen erkennt die Änderung
über einen Generationszähler in `ClientClaimCache` und baut seine Widgets neu auf (`tick()`).
Jeder Client->Server-Versand ist über das neue `com.areaclaims.client.data.ClientNetworkUtil`
(1:1 aus CobbleCompanion portiert, siehe `docs/API_EXTENSIONS.md` Abschnitt 5) kanal-geprüft;
der Server prüft vor dem Sync-Versand ebenfalls per `NetworkRegistry.hasChannel(...)`
(`ClaimSnapshotBuilder.sendTo`), analog zu CobbleCompanions `hasClientChannel`-Muster.

Validierung/Mutation wurde aus den bestehenden `/areaclaims role`/`/areaclaims rule`-Befehlen in
eine neue gemeinsame `ClaimEditService` extrahiert - Befehl UND Pakete rufen jetzt exakt dieselben
`applyRole`/`applyRule`-Methoden auf, keine zwei parallelen Implementierungen mehr.

Nutzer-Vorgabe für diese Runde: KEINE Unterbereichs-Erstellung, KEINE Server-Konfiguration/Preise
im Editor - beides explizit auf die nächste Runde (zusammen mit Phase 6) verschoben. `/areaclaims
open` ist ab jetzt für alle Spieler offen (nicht mehr nur Admins) - der Editor zeigt die eigenen
Claims, das war vorher (Phase 0-Platzhalter) noch anders geregelt.

- Bereichsliste (eigene + ggf. Admin-Übersicht aller), Regel-Editor pro Bereich/Unterbereich,
  Mitgliederliste mit Rollenzuweisung. `FixedScaleScreen` + Justierschrauben-Konvention.

### Phase 5-Nachtrag – Löschen/Grenzen anzeigen/Erweitern (erledigt, ungetestet)

Drei vom Nutzer nach dem ersten GUI-Test gemeldete Lücken, alle auf bereits eigene Claims begrenzt:

**1. Claim löschen.** `ClaimManager.deleteClaim(id)` entfernt einen Claim UND kaskadiert rekursiv
über beliebig viele Ebenen auf seine Unterbereiche (das Datenmodell schränkt die Verschachtelungstiefe
nicht ein, auch wenn `createSubClaim` aktuell nur eine Ebene erzeugt und es dafür noch keinen Befehl
gibt), baut danach den Chunk-Index komplett neu (einfacher und weniger fehleranfällig als gezielt zu
bereinigen). Neuer Befehl `/areaclaims delete <Name>` sowie ein "Löschen"-Button pro Zeile in der
Claim-Liste des Editors, beide über die neue `ClaimEditService#deleteClaim` (Besitzer oder OP4).
Klick-zum-Bestätigen 1:1 aus `CreativeMenu/.../CreativeMenuEditorScreen.java` portiert (dortiges
`deleteArmedSlotId`/`deleteArmedUntil`-Muster): erster Klick "armiert" NUR diese Zeile und ändert den
Button-Text zu "Sicher?"/"Confirm?", zweiter Klick auf DIESELBE Zeile innerhalb von 4 Sekunden löst
die Löschung aus; läuft die Zeit ohne zweiten Klick ab, entwaffnet sich der Button von selbst
(geprüft in `tick()`, da vanilla-Button-Widgets ihr Label nicht jeden Frame neu lesen). Neues
`DeleteClaimPacket` (C2S) für den GUI-Weg, ruft dieselbe Service-Methode wie der Befehl auf.

**2. Grenzen eines bestehenden Claims erneut anzeigen.** Die Partikel-Umriss-Visualisierung aus
Phase 1 (`ToolInteractionListener`) lief bisher nur für die laufende, noch nicht committete
`SelectionManager`-Auswahl. Neuer `ClaimShowcaseManager` (in-memory, wie `SelectionManager`) merkt
sich pro Spieler, welchen bereits committeten Claim er sich gerade "anzeigen" lässt.
`ToolInteractionListener#onServerTick` rendert im selben 15-Tick-Intervall wie die Live-Auswahl auch
aktive Showcases - über dieselbe, dafür aus `visualizeSelection` in eine wiederverwendbare
`renderPolygonOutline`-Methode umbenannte Partikel-Zeichenlogik (keine zweite Implementierung).
Neuer Befehl `/areaclaims show <Name>` und "Grenzen anzeigen"-Button im Editor (unter der
Claim-Liste), Button nutzt neues `ShowClaimPacket` (C2S). **Redesign nach Nutzer-Feedback:**
ursprünglich lief das als 15-Sekunden-Timer (300 Ticks, Countdown in `ClaimShowcaseManager`) - der
Nutzer wollte stattdessen einen reinen Ein/Aus-Zustand ohne Ablauf. `ClaimShowcaseManager` ist jetzt
`Map<UUID Spieler, UUID claimId>` statt einer Countdown-Struktur; `/areaclaims show <Name>` bzw. der
Editor-Button SCHALTEN um (`toggle(...)`) - nochmal derselbe Claim schaltet aus, ein anderer Claim
wechselt einfach. Bleibt an bis explizit ausgeschaltet (nochmal derselbe Befehl/Button, oder
`/areaclaims cancel`, das weiterhin BEIDES abbricht: Werkzeug-Auswahl UND Grenzen-Vorschau). Beim
Abmelden räumt `ClaimEntryListener#onLogout` jetzt zusätzlich `ClaimShowcaseManager` (und zur
Konsistenz auch `SelectionManager`) für den jeweiligen Spieler auf, damit kein Zustand für
Offline-Spieler hängen bleibt.

**3. Bestehenden Claim erweitern - Architektur-Änderung: Claims sind jetzt mehrteilig.** Die
ursprüngliche Umsetzung dieser Runde ("neu auswählen, muss das alte Polygon komplett enthalten,
ersetzt es") hat der Nutzer nach Test verworfen: gewünscht ist stattdessen, ein komplett
UNABHÄNGIGES, separates Stück Land unter demselben Claim-Namen hinzuzufügen - "Dabei ist es
unwichtig, ob die Claims einen Schnittpunkt haben oder nicht" (Nutzer-Zitat). Das erforderte eine
echte Datenmodell-Änderung, keine reine Befehls-Anpassung:

- **Vorher:** `Claim` hatte ein einzelnes Polygon (`List<Vertex> polygon`).
- **Jetzt:** `Claim` hat eine Liste unabhängiger Polygon-`parts` (`List<List<Vertex>> parts`, siehe
  `Claim.java`) - bewusst KEIN echtes Polygon-Union (deutlich schwierigere Geometrie, vom Nutzer
  explizit nicht gefordert), sondern eine simple Sammlung eigenständiger Polygone, die zusammen als
  EIN logisches Territorium behandelt werden. Teile dürfen sich überschneiden, berühren oder komplett
  getrennt sein - das Datenmodell macht dazu keine Aussage und prüft es auch nicht.
- **Punkt-in-Claim** (`Claim#containsPoint`, genutzt von `ClaimManager.findMainClaimAt`/
  `findSubClaimAt`, damit indirekt auch von `ClaimProtectionManager` und `ClaimEntryListener`) =
  Punkt liegt in IRGENDEINEM Teil (ODER-Verknüpfung über alle Teile).
- **Fläche** (`Claim#totalArea`) = Summe der Shoelace-Flächen aller Teile.
- **Chunk-Index** (`ClaimManager`) indiziert JEDEN Teil einzeln (`registerPart`/`rebuildChunkIndex`
  iterieren jetzt über `claim.parts()` statt über ein einzelnes Polygon).
- **Überlappungsprüfung** beim Erstellen (`createMainClaim`) UND beim Erweitern (`addClaimPart`,
  siehe unten) prüft den NEUEN Teil gegen JEDEN Teil JEDES ANDEREN Hauptbereichs mit anderem
  Besitzer (`overlapsAnyPart`) - AUSDRÜCKLICH NICHT gegen die eigenen anderen Teile desselben
  Claims (die dürfen sich beliebig zueinander verhalten, siehe oben).
- **Unterbereichs-Containment** (`createSubClaim`) - da der Elternbereich jetzt mehrteilig ist, muss
  ein Unterbereich komplett innerhalb EINES EINZELNEN Teils des Elternbereichs liegen (nicht über
  mehrere getrennte Elternteile hinweg verstreut).
- **Persistenz:** `ClaimDto.polygon` (List<int[]>) wurde zu `ClaimDto.parts` (List<List<int[]>>).
  Keine Migration alter Speicherstände (Nutzer-Vorgabe: noch keine Produktivdaten, ein altes
  Test-Save mit dem alten Schema lässt sich einfach löschen/neu erzeugen statt es zu migrieren).

**Neuer Workflow:** `ClaimManager.addClaimPart(claim, newPart)` ersetzt das frühere
`expandMainClaim` (inkl. `ExpandResult` -> `AddPartResult`-Enum-Umbenennung, `DOES_NOT_CONTAIN_OLD`
entfällt ersatzlos). Spieler steckt mit der Hacke (Werkzeug-Modus AN, wie beim Erstellen) eine neue
Fläche ab, `/areaclaims expand <Name>` hängt die aktuelle Werkzeug-Auswahl als neuen Teil an -
geprüft wird NUR Selbstüberschneidung + fremde Überlappung (siehe oben), NICHT mehr Enthaltensein
im alten Polygon. Nur bei Erfolg wird die Werkzeug-Auswahl geleert. Neues `AddClaimPartPacket` (C2S)
für den GUI-Weg - anders als ursprünglich geplant DOCH mit Editor-Button ("Auswahl als neues Teil
hinzufügen", unter "Grenzen anzeigen"), da die Umsetzung ("nimm die aktuelle Werkzeug-Auswahl")
genauso simpel per Klick auslösbar ist wie der Befehl, ohne eigene Dialog-Logik im GUI zu brauchen.

### Phase 5-Nachtrag 2 – 3-Spalten-Layout + kaskadierende Unterbereichs-Mitgliedschaft (erledigt, ungetestet)

Nach dem ersten GUI-Test wollte der Nutzer ein grundlegend anderes Layout für
`AreaClaimsEditorScreen` statt der bisherigen 2-Spalten-Aufteilung (Claim-Liste links, Mitglieder+
Regeln untereinander rechts). Komplett neu gebaut, `PANEL_X`/`rulesStartY` (dynamische
vertikale Stapelung) entfallen zugunsten eines festen 3-Spalten-Rasters:

- **Spalte 1 (Hauptbereiche):** ein Button pro Hauptbereich, darunter eine Info-Zeile
  (`ownerName • Fläche Blöcke²`, via neue `ClaimEditorSnapshot.ClaimEntry.ownerName`/`area`-Felder,
  von `ClaimSnapshotBuilder` befüllt aus `Claim.owner()`/`Claim.totalArea()`). "X"-Löschen-Button
  daneben (siehe unten).
- **Spalte 2 (Unterbereiche):** nur sichtbar, wenn ein Hauptbereich in Spalte 1 ausgewählt ist -
  ein Button pro Unterbereich dieses Hauptbereichs, darunter nur die Flächen-Info-Zeile (Besitzer
  ist bei Unterbereichen immer identisch zum Hauptbereich, daher weggelassen).
- **Spalte 3 (Detail-Panel):** optisch durch eine dünne Trennlinie (`GuiGraphics#fill`,
  halbtransparent) abgesetzt, zeigt den Namen des gerade FOKUSSIERTEN Claims als Überschrift
  (Unterbereich, falls einer in Spalte 2 ausgewählt ist, sonst der Hauptbereich selbst -
  `focusedClaim()`), darunter "Grenzen anzeigen"/"Auswahl als neues Teil hinzufügen" für genau
  diesen fokussierten Claim, dann nebeneinander die Mitglieder- (links) und Regel-Liste (rechts) -
  inhaltlich unverändert übernommen aus der vorherigen Version, nur repositioniert und dadurch vom
  vorherigen "Regeln fangen erst nach Mitgliederliste an"-Stapel-Layout befreit (beide Unterspalten
  haben jetzt einen FESTEN gemeinsamen Start-Y, da sie nebeneinander statt übereinander stehen).

**Spalten-1-Umfang erweitert** (Nutzer-Vorgabe, notwendig damit "Besitzer"-Anzeige überhaupt Sinn
ergibt): zeigt jetzt Hauptbereiche, die der Spieler BESITZT ODER in deren Mitgliederliste er
steht (`ClaimManager.findAccessibleClaims`, ersetzt das alte `findClaimsOwnedBy`) - ausdrücklich
NICHT "alle Claims des Servers" (das wäre eine eigene Admin-Übersicht, nicht Teil dieser Runde).
Spalte 2 zeigt dann IMMER alle Unterbereiche des ausgewählten Hauptbereichs, unabhängig von der
eigenen Rolle auf dem jeweiligen Unterbereich selbst - wer den Hauptbereich sehen darf, darf auch
dessen volle Unterbereichs-Liste sehen.

**Löschen-Button-Tweak:** zeigt im Ruhezustand jetzt ein schlichtes "X"-Glyph statt des Textes
"Löschen"/"Delete" (keine echte Icon-Textur vorhanden/nötig) - Klick-zum-Bestätigen-Verhalten
unverändert, der bestätigte Zustand zeigt weiterhin "Sicher?"/"Confirm?" als Text.

**Kaskadierende Unterbereichs-Mitgliedschaft (neues Verhalten, nicht nur Anzeige):** wird ein
Spieler über `/areaclaims role`/`SetRolePacket` Mitglied eines UNTERBEREICHS (jede Rolle außer
NONE), wird er automatisch auch Mitglied des zugehörigen HAUPTBEREICHS - Default-Rolle dort:
MEMBER (niedrigste Stufe), NUR wenn er dort noch keinen Eintrag hat oder aktuell niedriger
eingestuft ist (kein Downgrade eines bestehenden höherrangigen Eintrags - eigene Entscheidung,
"nicht runterstufen" schien die eindeutig unüberraschendere Wahl). Implementiert in
`ClaimEditService.applyRole` (`cascadeMembershipToParent`), also GENAU an der einen Stelle, die
sowohl der Chat-Befehl als auch `SetRolePacket` (GUI) aufrufen - automatisch für beide Wege
konsistent, keine zweite Implementierung. Läuft NUR vorwärts (Unterbereich -> Hauptbereich) -
Entfernen aus einem Unterbereich kaskadiert NICHT rückwärts (der Spieler könnte weiterhin direkt
Mitglied des Hauptbereichs sein oder noch in einem anderen Unterbereich).

## Phase 6 – Server-Konfiguration & Preis-Brücke (erledigt, ungetestet)

Letzte geplante Phase. CobbleCompanion stellt jetzt eine echte, öffentliche
`com.cobblecompanion.api.CobbleDollarsAccess`-Fassade bereit (Abschnitt 7 in dessen
`docs/API_EXTENSIONS.md`, delegiert an die interne `CobbleDollarsBridge`, die gegen die echte
CobbleDollars-Mod programmiert ist) - `AreaClaims/libs/CobbleCompanion-0.1.0.jar` liegt bereit.

**Soft-Dependency-Aufbau** - 1:1 das bei InvSpy bereits etablierte Muster (`InvSpy/build.gradle`,
dessen `neoforge.mods.toml`, `com.invspy.integration.{ModAvailability,CobbleCompanionBridge}`),
nicht neu erfunden:
- `build.gradle`: `compileOnly fileTree(dir: 'libs', include: ['CobbleCompanion*.jar'])` - NIEMALS
  `implementation`.
- `neoforge.mods.toml`: `[[dependencies.areaclaims]]` für `cobblecompanion`, `type="optional"`.
- `com.areaclaims.integration.ModAvailability` - referenziert NUR den Mod-ID-String
  (`ModList.get().isLoaded("cobblecompanion")`), NIE eine CobbleCompanion-Klasse direkt.
- `com.areaclaims.integration.CobbleCompanionBridge` - die EINZIGE Klasse in AreaClaims, die
  `com.cobblecompanion.api.*` referenziert, aufgerufen NUR hinter
  `ModAvailability.isCobbleDollarsAvailable()` (die ihrerseits erst nach der eigenen
  `cobblecompanion`-Prüfung per Kurzschlussauswertung dorthin verzweigt - ein Server ohne
  CobbleCompanion lädt die Bridge-Klasse also nie, kein `NoClassDefFoundError`-Risiko).

**1. Server-weite Freischaltungsschwellen (Default AUS außer OP4).** `FeatureConfigManager`
(Gson-JSON, `areaclaims_feature_config.json`) - vier OP-Stufen-Schwellenwerte (0-4, Default JEWEILS
4 = "nur OP4"), dieselbe Schwellen-Konvention wie CreativeMenus `minOpLevelAddRemove` (bewusst
KEINE echten Rollen/Gruppen oder eigene Erlaubnisliste - siehe auch die auf Nutzer-Vorgabe entfernte
Admin-Liste aus Phase 0, gleicher Geist): `minOpLevelUseTool`, `minOpLevelCreateClaim`,
`minOpLevelExpandClaim`, `minOpLevelBuyout` (eigene Schwelle für den Regel-Freikauf, siehe Punkt 3 -
sonst wäre ein nicht konfigurierter, also kostenloser Freikauf-Preis unbeabsichtigt für jeden
Claim-Besitzer sofort nutzbar gewesen). Jeder Wert wird beim Setzen auf [0,4] geklemmt - dadurch
erfüllt echte Vanilla-OP-Stufe 4 IMMER jede mögliche Schwelle, ganz ohne separate Sonderprüfung:
OP4 hat also automatisch und unumgehbar immer Zugriff, exakt wie gefordert. Geprüft in
`AreaClaimsCommands` (Werkzeug-Toggle, Claim/Erweitern-Befehle) UND zusätzlich als
Verteidigung-in-der-Tiefe direkt in `ToolInteractionListener`s Klick-Handlern (falls ein Spieler
das Werkzeug schon vor einer Config-Verschärfung eingeschaltet hatte) sowie in `AddClaimPartPacket`
(GUI-Weg). Ausschalten des Werkzeugs ist immer erlaubt, unabhängig von der Schwelle.

**2. Preis pro geclaimtem Block.** `PriceConfigManager.perBlockPrice()` - EIN gemeinsamer Preis für
sowohl `/areaclaims claim` (Fläche des neuen Claims, aufgerundet auf ganze Blöcke) als auch
`/areaclaims expand`/GUI-"Auswahl als neues Teil hinzufügen" (NUR die Fläche des NEUEN Teils, nicht
des ganzen Claims - Nutzer-Vorgabe). Bezahlt wird ERST NACHDEM die Geometrie-Prüfung (Punktzahl,
Selbstüberschneidung, Überlappung) erfolgreich war - reicht die Zahlung nicht, wird der frisch
erstellte Claim (`ClaimManager.deleteClaim`) bzw. nur der frisch hinzugefügte Teil
(neues `ClaimManager.removeLastPart` - Rollback-Hilfsmethode) wieder rückgängig gemacht, statt
einen halb bezahlten Zustand zu hinterlassen.

**3. Preis pro Regel-Freikauf.** `PriceConfigManager.ruleBuyoutPrice(RuleType)` - ein Preis JE
Regeltyp. Neuer Befehl `/areaclaims buyout <Claim> <Regel>` (Besitzer oder OP4, zusätzlich hinter
`minOpLevelBuyout` gegated) kauft eine Regel für den GANZEN Claim dauerhaft frei
(`Claim.boughtOutRules()`, persistiert) - ein SEPARATER geld-/itembasierter Mechanismus zusätzlich
zum rollenbasierten Ignorieren aus Phase 4, geprüft in `ClaimProtectionManager` (freigekaufte
Regeln gelten für ALLE als deaktiviert, unabhängig von Rolle/`enabled`-Flag). Bewusst NUR als
Befehl umgesetzt, kein GUI-Button - hätte einen laufenden Preis-Sync zum Client gebraucht (neue
Snapshot-Felder), zusätzlicher Netzwerk-/Screen-Aufwand für ein Feature, das laut Vorgabe ohnehin
Ermessenssache war.

**4. Item-ODER-CobbleDollars-Dualität.** `PriceConfig` (Record: `NONE`/`ITEM`/`COBBLE_DOLLARS`) +
`PriceCharger` (zieht Items direkt aus `player.getInventory().items` oder ruft
`CobbleCompanionBridge.charge` auf, IMMER hinter `ModAvailability.isCobbleDollarsAvailable()`
geprüft). Ein Admin kann die CobbleDollars-Preis-Option beim Konfigurieren nur wählen, wenn
CobbleDollars tatsächlich verfügbar ist (`/areaclaims config price ... dollars` schlägt sonst mit
klarer Fehlermeldung fehl) - niemals eine harte Pflicht, die Item-Variante funktioniert immer.
Item-IDs werden schon BEIM Konfigurieren validiert (`BuiltInRegistries.ITEM.containsKey`), damit
zur Laufzeit kein Tippfehler unbemerkt bleibt; eine trotzdem ungültige/korrupte Persistenz führt
zu einer eigenen `MISCONFIGURED`-Fehlermeldung ("fail closed") statt stillschweigend kostenlos
durchzulassen.

**5. Admin-Konfiguration.** Neuer Unterbaum `/areaclaims config` (nur OP-Stufe 4):
`config feature <tool|createclaim|expandclaim|buyout> <0-4>`,
`config price block <none|item <Item> <Menge>|dollars <Betrag>>`,
`config price rule <Regel> <none|item ...|dollars ...>`, `config show` (Zusammenfassung aller
Schwellen/Preise). Bewusst NUR als Befehl (keine GUI) - server-seitige, selten benutzte
Einmal-Konfiguration, kein häufig genutztes Spieler-Feature (siehe Nutzer-Vorgabe "your call
whether command-only is sufficient here").

Damit waren nach diesem Durchlauf alle sechs ursprünglich geplanten Phasen umgesetzt - siehe aber
"Phase 6-Nachtrag" unten für einen großen Feedback-Batch danach.

### Phase 6-Nachtrag – 7-Punkte-Feedback-Batch (erledigt/teils dokumentiert offen, ungetestet)

Nach dem ersten Live-Test kam ein großer Batch mit 7 Punkten zurück (plus 4 Korrekturen/Ergänzungen
mitten in der Bearbeitung). Der Reihe nach:

**1. Layout-Fix: verschwindender Button-Text (echter Bug, kein reines Kosmetik-Problem).**
Nutzer-Fund: ein Button, dessen Text nicht in seine Breite passt, löst vanillas eingebautes
Scrolling-Text-Rendering aus - das ruft `enableScissor()` mit Koordinaten auf, die in einem
`FixedScaleScreen` NICHT korrekt umgerechnet werden (derselbe dokumentierte Bug aus der
CobbleCompanion-Modfamilie: der Button rendert bei jeder GUI-Größe außer 2 komplett LEER). Fix in
`AreaClaimsEditorScreen`: Rollen-Buttons (Mitglieder-Rolle, Regel-Mindestrolle) und der Regel-
Aktiv/Deaktiviert-Umschalter werden jetzt zur LAUFZEIT auf Basis der tatsächlichen Textbreite
dimensioniert (`roleButtonWidth()`/`toggleButtonWidth()`, längster Text aller Enum-Werte + Rand) -
Scrolling kann dadurch nie mehr auslösen, unabhängig von der Sprache. Mitgliederzeilen sind dafür
jetzt zweizeilig (Name oben als reiner Text, Rolle-Button + Entfernen-Button darunter) statt
nebeneinander gequetscht.

**2. OP4-Admin-GUI.** Neuer Befehl `/areaclaims admin` (OP4) schaltet den Admin-Modus um: der
GUI-Editor zeigt danach ALLE Claims des Servers statt nur eigene/Mitgliedschafts-Claims
(`AdminViewManager`, in-memory Ein/Aus pro Spieler; `ClaimSnapshotBuilder.build` verzweigt auf
`ClaimManager.findAllClaims()` statt `findAccessibleClaims`, wenn Admin-Modus aktiv UND der
Spieler tatsächlich OP4 ist - Prüfung läuft nochmal serverseitig, nicht nur beim Umschalten).
Der bestehende `AreaClaimsEditorScreen` wird dafür 1:1 WEITERVERWENDET (kein zweiter Screen nötig) -
da alle Mutationen (Rolle/Regel/Löschen/Erweitern/SubClaim/Freikauf) serverseitig ohnehin schon
"Besitzer ODER OP4" prüfen (`ClaimEditService#canEdit`), funktioniert "fremde Claims bearbeiten wie
die eigenen" automatisch, sobald die Liste sie überhaupt zeigt - keine Client-Änderung nötig.
Zusätzlich: neuer `AreaClaimsServerConfigScreen`, erreichbar über einen "Server-Konfiguration"-
Button im Editor (nur sichtbar für OP4, echte Prüfung läuft nochmal serverseitig in
`RequestServerConfigPacket`) - bildet `/areaclaims config feature|price` jetzt auch als GUI ab
(Freischaltungsschwellen als Zyklus-Buttons 0-4, Preise als Typ-Zyklus-Button + Item-ID-/Mengen-
Textfelder + Übernehmen-Button, ein Paket `SetServerConfigPacket` mit Aktions-/Ziel-String statt
vieler fast identischer Pakete - bewusste Vereinfachung). **Bekannte Einschränkung** (dokumentiert
statt stillschweigend in Kauf genommen): der Server-Konfigurations-Screen ist eine einspaltige
Liste mit vielen Zeilen (selten genutztes Admin-Werkzeug, keine häufig bediente Oberfläche, daher
Funktionalität vor Layout-Politur priorisiert) und das Umschalten eines Preistyp-Buttons baut die
Zeile neu auf, wodurch noch nicht übernommener Tipptext in den Textfeldern verloren geht.

**3. Betreten-Nachricht: Farbe/Dauer pro Text + Willkommensnachricht.** `Claim` hat jetzt
`titleColor`/`titleDurationTicks` sowie eine eigene `welcomeMessage`/`welcomeColor`/
`welcomeDurationTicks` - JEDER Claim (Haupt- ODER Unterbereich) einzeln, nicht geteilt. Titel/
Untertitel weiterhin über die vanilla Titel-Pakete (jetzt eingefärbt via `Style.withColor`); da
Vanilla nur EIN Animationspaket (Ein-/Anzeige-/Ausblend-Dauer) für Titel+Untertitel ZUSAMMEN hat,
gewinnt bei gleichzeitiger Anzeige beider die LÄNGERE der beiden konfigurierten Dauern (eigene
Entscheidung, dokumentiert in `ClaimEntryListener`, damit kein Text vor seiner eigenen
konfigurierten Zeit verschwindet). Die Willkommensnachricht läuft über die Actionbar (dritter
Kanal) - **verifiziert**: die Actionbar hat KEINE eigene Dauer-Steuerung (`displayClientMessage(msg,
true)` sendet nur den Text, keinen Dauer-Parameter; die Overlay-Nachricht blendet nach einer festen
Vanilla-Zeit aus) - "Dauer" wird deshalb durch wiederholtes Senden derselben Nachricht alle 20 Ticks
simuliert, bis die konfigurierte Gesamtdauer erreicht ist (eigene, dokumentierte Design-Entscheidung,
da Vanilla dafür keine native Lösung bietet). Konfiguration über neuen Befehl `/areaclaims entrymsg
<Claim> [sub <Name>] color|duration|welcome|welcomecolor|welcomeduration|boundarycolor|
linkboundarycolor <Wert>` (Hex-Farben als RRGGBB/#RRGGBB) - bewusst NUR Befehl, kein GUI-Zugriff in
dieser Runde (Zeitgründe, siehe Gesamt-Priorisierung unten).

**4. Neue Regel(n) für friedliche Mobs/Cobblemon - als ZWEI getrennte Regeln (Nutzer-Korrektur
mitten in der Bearbeitung).** Ursprünglich als EINE kombinierte Regel gebaut, dann auf
Nutzer-Klarstellung in zwei geteilt:
- `LEASH_PASSIVE_MOBS` (vanilla, IMMER funktionsfähig) - verdrahtet über
  `PlayerInteractEvent.EntityInteract` (Item = Leine), gecancelt BEVOR das eigentliche Anleinen
  passiert. Nutzer schlug `PlayerLeashEntityEvent` vor - **erschöpfend geprüft**: existiert in
  NeoForge 21.1.233 NICHT (weder als Datei im Sourcen-Jar noch als Erwähnung in `EventHooks.java`)
  - vermutlich ein aus altem Forge übernommener, in NeoForge nie migrierter Name.
  `EntityInteract` erreicht denselben Effekt.
- `PREVENT_COBBLEMON_CAPTURE` - NUR Datenmodell + generische Regel-UI (Aktivieren/Mindestrolle/
  Freikauf funktionieren automatisch, da komplett enum-generisch) - KEINE tatsächliche
  Durchsetzung. Cobblemons Fang-Events (`com.cobblemon.mod.common.api.events.pokeball.*`) sind
  Kotlin-APIs; in dieser Umgebung war weder `javap` verfügbar noch existieren entkompilierte
  Quelltexte dafür - ohne eines von beidem wäre das Verdrahten reines Raten gewesen, was explizit
  vermieden werden sollte. **TODO für eine spätere Runde**: mit `javap` (oder Kotlin-Quelltexten,
  falls auftreibbar) die tatsächliche Event-API von `PokeBallCaptureCalculatedEvent`/
  `PokemonCapturedEvent` klären und verdrahten. `com.areaclaims.integration.CobblemonAvailability`
  (reiner Mod-ID-Check, keine Cobblemon-Klasse referenziert) liegt als Ausgangspunkt schon bereit.

**5. Sub-Claim-Erstellung UX-Klarstellung.** War zuvor unsichtbar/unerreichbar. Jetzt im Detail-
Panel: fokussierter HAUPTbereich zeigt "Erweitern" (neuer Teil FÜR DEN Hauptbereich) UND "SubClaim
anlegen" (Namensfeld + Button, nutzt endlich `ClaimManager.createSubClaim`, das seit Phase 2 schon
fertig war, aber nie einen Aufrufer hatte) NEBENEINANDER; fokussierter UNTERbereich zeigt NUR
"Erweitern" (fügt einen neuen Teil ZUM UNTERBEREICH SELBST hinzu). Dafür brauchte
`ClaimManager.addClaimPart` eine Erweiterung: Unterbereiche waren schon immer teile-fähig (Claims
sind seit "Erweitern v2" grundsätzlich mehrteilig, unabhängig von Haupt-/Unterbereich-Status - das
Datenmodell musste dafür NICHT geändert werden), aber `addClaimPart` prüfte bislang nicht, ob ein
neuer Teil eines UNTERbereichs noch innerhalb des Elternbereichs liegt - das wurde ergänzt (gleiche
Containment-Regel wie bei `createSubClaim`, neuer `AddPartResult.OUTSIDE_PARENT`).

**6. Mehrere gleichzeitige, farbige Grenzen-Vorschauen + zwei Anzeigemodi.**
- **Mehrere gleichzeitig, pro Betrachter getrennt:** `ClaimShowcaseManager` ist jetzt
  `Map<Spieler, Set<ClaimId>>` statt "ein Claim ersetzt den vorherigen" - `toggle(...)` schaltet
  GENAU einen Claim im Set um, andere bleiben unberührt. Zustand ist pro ANSCHAUENDEM Spieler
  getrennt (ein OP4, der fremde Claims prüft, beeinflusst nicht, was Besitzer selbst gerade
  angezeigt bekommen, und umgekehrt) - erledigt praktisch von selbst, da der Zustand ohnehin schon
  immer pro Spieler-UUID lag, jetzt nur mit einem Set statt einem einzelnen Wert.
- **Eigene Farbe pro Claim:** `Claim.boundaryColor` + `linkBoundaryColorToTitle` (Standard: an,
  Komfort-Kopplung an die Titel-Farbe aus Punkt 3) - `Claim#effectiveBoundaryColor()` liefert die
  tatsächlich zu nutzende Farbe. Partikel sind jetzt `DustParticleOptions` (RGB-fähig, via
  `Vec3.fromRGB24(farbe).toVector3f()` - verifiziert in den dekompilierten Sourcen) statt dem
  fixen `ParticleTypes.END_ROD` (das bleibt nur für die farblose Live-Werkzeug-Auswahl).
- **Überlappende/angrenzende Teile zeigten eine sichtbare "Naht" (Nutzer-Fund, Screenshot).** Wurde
  jeder Teil eines mehrteiligen Claims komplett einzeln umrandet, entstand mitten durch einen
  eigentlich zusammenhängend wirkenden Bereich eine Partikellinie. Fix in
  `ToolInteractionListener#isEdgeCoveredByOtherPart`: für jede Kante wird ein Punkt knapp
  AUSSERHALB des Kanten-Mittelpunkts abgetastet (die Richtung, die nicht ins eigene Teil zeigt) -
  liegt dieser Punkt in einem ANDEREN Teil DESSELBEN Claims, gilt die Kante als "innen liegend" und
  wird übersprungen. Bewusst ein pragmatischer Kanten-Test statt eines vollständigen Polygon-Union-
  Algorithmus (Nutzer-Vorgabe) - deckt den häufigen Fall ab (zwei sich überschneidende/berührende
  Teile), ist aber keine für jede denkbare Geometrie exakte Lösung.
- **Modus 1 (Partikel-Umriss):** fertig, das oben Beschriebene.
- **Modus 2 (halbtransparente Block-Einfärbung, ~30-40%, spielerseitig einstellbar):** NICHT
  umgesetzt, bewusst als TODO dokumentiert statt riskiert geraten. Ein performanter, "nur was der
  Spieler gerade sehen kann"-Renderer bräuchte einen eigenen `RenderLevelStageEvent`-Hook plus
  einen Occlusion-/Sichtweite-bewussten Block-Iterationsalgorithmus - ohne Möglichkeit, das in
  dieser Session tatsächlich in einem laufenden Client zu testen, wäre das Verdrahten eines
  potenziell FPS-kostenden Custom-Renderers reines Blindfliegen. Nutzer-Korrektur bereits notiert
  für die Umsetzung: Radius auf ~20 Blöcke um den Spieler (bzw. dessen Chunk ± etwas Rand) begrenzen
  statt beliebig große Claims voll zu rendern - das vereinfacht die Aufgabe erheblich und sollte in
  einer Runde mit Zugriff auf einen laufenden Client umgesetzt werden.

**Korrektur zwischendurch: Obergrenze für Teile pro Claim.** `FeatureConfigManager.maxClaimParts`
(Default 8 - großzügig genug für normale Nutzung, aber nicht unbegrenzt, verhindert z. B. exzessiv
viele Chunk-Index-Einträge für einen einzelnen Claim). **Design-Entscheidung** (Nutzer-Vorgabe war
mehrdeutig, ob Haupt-/Unterbereiche eigene Obergrenzen brauchen): EIN gemeinsamer Wert für beide -
zwei getrennte Werte hätten die Konfiguration nur unnötig verkompliziert, ohne einen erkennbaren
Anwendungsfall für unterschiedliche Obergrenzen. Durchgesetzt in `ClaimManager.addClaimPart`
(deckt "Erweitern" für Haupt- UND Unterbereiche ab). "Create-Sub-Claim" wurde NICHT gesondert
geprüft, da eine neu erstellte Unterbereich immer mit genau 1 Teil startet - bei einer Mindest-
Obergrenze von 1 (im Setter erzwungen) kann das nie verletzt werden, eine Prüfung dort wäre toter
Code gewesen. Admin-Befehl `/areaclaims config maxparts <n>`, auch im Admin-GUI (Punkt 2) enthalten.

**Gesamt-Priorisierung dieser Runde** (Nutzer-Vorgabe: "1-5 vor 6"): Punkte 1, 2, 4, 5 sowie die
Teile-Obergrenze-Korrektur sind vollständig umgesetzt. Punkt 3 ist funktional vollständig, aber nur
per Befehl konfigurierbar (kein GUI-Zugriff für Farbe/Dauer/Willkommensnachricht). Punkt 6 ist zur
Hälfte umgesetzt (Mehrfach-Showcases + Farben + Kanten-Fix: fertig; Block-Einfärbung: TODO,
dokumentiert oben). Nichts von alldem wurde in dieser Session in einem echten Spielfenster
getestet (kein Zugriff auf ein laufendes Spiel) - jeder Schritt wurde ausschließlich über
`./gradlew compileJava` (inkl. mindestens eines vollständig sauberen `clean compileJava`)
verifiziert.

### Phase 6-Nachtrag 2 – 9-Punkte-Feedback-Batch (erledigt, ungetestet)

Erneuter großer Feedback-Batch, diesmal ohne explizite Priorisierungs-Vorgabe ("nine items, take
your time"). Der Reihe nach:

**1. Partikel-Umriss wirkte "nicht so schön wie die alte, originale Anzeige".** Vorher (reines
`END_ROD`, funkelnder Vanilla-Partikel) wirkte optisch voller als die seit dem letzten Batch
farbige Variante (`DustParticleOptions`, Schrittweite 1.0 Block, Skalierung 1.0 - flacher, ohne
Eigenanimation). Farbe bleibt eine bestätigt gewollte Funktion, also kein Zurück zu END_ROD.
Stattdessen in `ToolInteractionListener`: deutlich dichtere Abtastung entlang der Kanten NUR für
Claim-Umrisse (0.35 statt 1.0 Blöcke Schrittweite, `SHOWCASE_PARTICLE_STEP_BLOCKS`) und größere
Partikel (Skalierung 1.5 statt 1.0, `SHOWCASE_PARTICLE_SCALE`) - die Live-Werkzeug-Auswahl bleibt
unverändert bei END_ROD/Schrittweite 1.0 (kein gemeldetes Problem dort). **Judgment call:** eigene,
nicht live vergleichbare Design-Entscheidung - falls das immer noch nicht überzeugt, wäre der
nächste Schritt vermutlich ein tatsächlich animierter/pulsierender Partikel-Typ, was hier bewusst
nicht riskiert wurde.

**2. Betreten-Nachricht-Einstellungen jetzt auch als GUI.** Neuer `AreaClaimsEntryMessageScreen`
(erreichbar über einen "Betreten-Nachricht"-Button im Detail-Panel des Editors) mit Feldern für
Titel-Farbe/-Dauer, Willkommenstext+Farbe+Dauer, Grenzfarbe + "Verknüpft mit Titelfarbe"-Umschalt-
Button - alle Felder werden gemeinsam über "Übernehmen" gesendet. Die zuvor in
`AreaClaimsCommands` direkt inline implementierte Feld-Validierung/Mutation wurde dafür in eine
neue geteilte `EntryMessageService` extrahiert (gleiches "einzige Quelle der Wahrheit"-Prinzip wie
`ClaimEditService`/`AdminConfigService`) - der Chat-Befehl `/areaclaims entrymsg` und das neue
`SetEntryMessagePacket` rufen jetzt beide nur noch `EntryMessageService#apply` auf. **Judgment
call:** die "Grenzfarbe"-Feld-Reihenfolge beim Übernehmen ist bewusst so gewählt, dass
`linkboundarycolor` IMMER zuletzt geschickt wird, damit die Checkbox das letzte Wort hat (das
Setzen einer expliziten Grenzfarbe schaltet serverseitig automatisch "verknüpft" aus - siehe
`EntryMessageService`-Klassenkommentar).

**3. Claim umbenennen.** `ClaimEditService#renameClaim` (neue, geteilte Mutation, mit
Namens-Eindeutigkeits-Prüfung über `ClaimManager#isSiblingNameTaken`) - erreichbar über
`/areaclaims rename <Claim> <neuerName>` (+ `... sub <subName> <neuerName>` für Unterbereiche,
gleiches Muster wie `entrymsg`) UND über eine neue Umbenennen-Zeile (Textfeld + Button) direkt
unter der Claim-Namen-Überschrift im Editor-Detail-Panel (funktioniert dort für Haupt- UND
Unterbereiche gleichermaßen, da per UUID statt Name adressiert - siehe `RenameClaimPacket`).
**Judgment call:** Namens-Eindeutigkeit wird NUR beim Umbenennen geprüft, nicht rückwirkend bei der
Erstellung (`createMainClaim`/`createSubClaim` prüfen weiterhin nicht auf Namensdopplung, das war
nicht Teil dieser Anfrage) - bereits bestehende Namensdopplungen aus der Erstellung bleiben also
möglich und werden von dieser Prüfung nicht rückwirkend aufgeräumt.

**4. Zwei Button-Beschriftungs-Fixes.** "Erweitern"-Button zeigt jetzt NUR noch "Erweitern" (vorher
"Erweitern (Auswahl als neues Teil hinzufügen)", was den Button unnötig breit machte) -
`EXPAND_BUTTON_WIDTH` wurde durch eine gemessene Breite ersetzt (`measuredButtonWidth`, dasselbe
Muster wie die Rollen-/Umschalt-Buttons aus dem vorherigen Batch - verhindert automatisch, dass der
Scrolling-Text-Bug durch eine künftige Retranslation je wieder auftreten kann). "Unterbereich aus
Auswahl anlegen" wurde zu "Als SubClaim anlegen" verkürzt.

**5. Server-Konfiguration: OP-Stufen-Anzeige.** "Mindest-OP-Stufe -"-Wortlaut aus den vier
Freischaltungsschwellen-Beschriftungen im `AreaClaimsServerConfigScreen` entfernt; die Steuerung
selbst ist jetzt ein Zyklus-Button "Spieler"/"OP 1"/"OP 2"/"OP 3"/"OP 4" statt einer nackten Zahl
(0-4), Klick schaltet weiter, gleiche vier Schwellen wie vorher (tool/createclaim/expandclaim/
buyout). **Judgment call (Scope-Grenze):** die Wortlaut-Entfernung gilt NUR für diese
Bildschirm-Beschriftung - die `/areaclaims config`-Befehlsausgaben ("Mindest-OP-Stufe für '%s' ist
jetzt %s.") wurden NICHT angefasst, da die Nutzer-Vorgabe sich explizit auf "diese Steuerelemente"
(die GUI) bezog, nicht auf Befehlsausgaben.

**6. Preis-UI komplett neu gestaltet - die größte Einzeländerung dieses Batches.**
`com.areaclaims.economy.PriceConfig` ist kein einzelner Preistyp (NONE/ITEM/COBBLE_DOLLARS) mehr,
sondern eine geordnete Liste von Komponenten: optional ein CobbleDollars-Betrag (immer zuerst,
nur bei verfügbarem CobbleDollars) + bis zu 5 Item-Preise, verbunden über UND/ODER-Umschalt-Buttons
zwischen je zwei sichtbaren Feldern. Ein LEERES Item-Feld bedeutet jetzt automatisch "kein Preis
über diesen Slot" - das alte Typ-Umschalt-Feld (und damit der in der letzten Runde dokumentierte
"Tipptext geht beim Typ-Umschalten verloren"-Bug) existiert dementsprechend nicht mehr; **bestätigt
statt angenommen**: der neue `AreaClaimsServerConfigScreen` hat tatsächlich KEINEN Typ-Umschalt-
Button mehr, der Bug kann strukturell nicht mehr auftreten. Ein clientseitiger Entwurfszustand
(`PriceRowDraft`, ein eigener pro Preis-Zeile) fängt jetzt zusätzlich JEDE Struktur-Änderung (Item
hinzufügen/entfernen, UND/ODER umschalten) ab, indem er die aktuellen Feldwerte VOR dem
Widget-Neuaufbau sichert - Tipptext geht also generell nicht mehr verloren, auch nicht bei den
verbliebenen Struktur-Änderungen.
- **Belastungs-Semantik (eigene, dokumentierte Entscheidung - der Nutzer-Vorschlag wurde 1:1
  übernommen):** linksassoziative Faltung ohne Operator-Vorrang - UND scheitert sofort und
  endgültig, sobald eine Komponente nicht bezahlbar ist (kein Nachfolgendes kann das retten); ODER
  behält ein bereits erfolgreiches Ergebnis unverändert bei (frühere/linke Option gewinnt, spätere
  Komponente wird weder geprüft noch belastet) und versucht bei einem bisherigen Fehlschlag die
  nächste Komponente allein. Am Ende wird GENAU das im Faltungs-Ergebnis gesammelte Komponenten-Set
  belastet. Vollständig dokumentiert in `PriceCharger`-Klassenkommentar (inkl. eines
  durchgerechneten Beispiels für den "erste ODER zweite UND dritte"-Fall).
- **Layout-Entscheidung:** Preis-Zeilen sind vertikal gestapelt (eine Zeile pro Item-Slot) statt
  horizontal in eine einzige, potenziell sehr breite Zeile gequetscht - der Normalfall (0-1
  Item-Preise, wie es die allermeisten Server nutzen werden) bleibt dabei genauso kompakt wie
  vorher, nur eine tatsächlich genutzte Mehrkomponenten-Kombination lässt die jeweilige Preis-
  Gruppe wachsen. Bewusst so gewählt, um das Layout ohne Live-Test nicht zu riskieren (siehe
  `AreaClaimsServerConfigScreen`-Klassenkommentar).
- **Bekannter Randfall (dokumentiert, nicht behoben):** wird ein MITTLERER Item-Slot leer gelassen,
  während ein SPÄTERER gefüllt bleibt (z. B. Item 1 gefüllt, Item 2 leer, Item 3 gefüllt), wird
  beim Übernehmen der leere Slot herausgefiltert, aber die verbliebenen UND/ODER-Werte werden
  einfach der Reihe nach (erste N-1 Werte) den übrig gebliebenen Lücken zugeordnet, statt die
  "semantisch richtige" Zuordnung zu erraten - bei kontinuierlich von oben nach unten ausgefüllten
  Slots (der erwartbare Normalfall) tritt das nie auf.
- Befehl `/areaclaims config price` bleibt bewusst auf GENAU EINE Komponente beschränkt (kein
  UND/ODER über Chat-Argumente setzbar - wäre unhandlich) - die volle Mehrkomponenten-Bearbeitung
  ist GUI-only (`SetPriceRowPacket`, JSON-Payload wie `ClaimSyncPacket`).
- Neues generisches Preis-Fehlschlag-Ergebnis `PriceCharger.Result.INSUFFICIENT_PRICE` für den
  Mehrkomponenten-Fall; bei GENAU einer Komponente bleiben die alten, präziseren Meldungen
  (`INSUFFICIENT_ITEM`/`INSUFFICIENT_COBBLE_DOLLARS`) erhalten.

**7. Block-Einfärbung-Anzeigemodus umgesetzt (vorher als Option 2 zurückgestellt).** Neuer
`com.areaclaims.client.BoundaryTintRenderer`, Hook `RenderLevelStageEvent`, Stage `AFTER_PARTICLES`
(laut Doc-Kommentar im dekompilierten `RenderLevelStageEvent.java` explizit für Transluzenz
empfohlen), Render-Typ `RenderType.debugQuads()` (verifiziert: exakt dieselbe Definition, die
Vanilla für die F3+G-Chunk-Grenzen-Anzeige nutzt - `POSITION_COLOR`-Format, transluzent, doppelseitig,
kein Textur-/Lightmap-Bedarf). `ClaimShowcaseManager` trägt jetzt zusätzlich einen Anzeigemodus
(`PARTICLES`/`TINT`) PRO SPIELER; ein neues `SetShowcaseModePacket` (+ `/areaclaims showmode`-Befehl
+ Zyklus-Button im Editor) schaltet um. Geometrie wird NUR im TINT-Modus über ein neues
`ShowcaseGeometrySnapshot`/`ShowcaseGeometrySyncPacket` an den Client geschickt (der Partikel-Modus
braucht das nicht, der Server rendert dort direkt) - `ClientBoundaryGeometryCache` hält sie
clientseitig, `PolygonUtil`/`Vertex` sind rein geometrisch und daher gefahrlos auch clientseitig
wiederverwendbar.
- **Ursprünglich (dieser Batch) vereinfacht auf EINE horizontale Ebene auf Spieler-Y-Höhe** (eine
  reine "Boden-Einfärbung", die sich mit der Spielerhöhe mitbewegt) - **in einem direkten
  Nachbesserungs-Auftrag noch in derselben Session auf volle Tiefe erweitert** (siehe Phase
  6-Nachtrag 3 unten): der 20-Block-Radius gilt jetzt als Zylinder um den Spieler (horizontal Kreis
  wie vorher, vertikal zusätzlich ±20 Blöcke, geklemmt auf die Bauhöhe der Welt) - Blöcke ober- und
  unterhalb des Spielers werden jetzt ebenfalls eingefärbt, nicht mehr nur die Ebene auf Fußhöhe.
- **Performance (Nutzer-Vorgabe: 20-Block-Radius um den Spieler, jetzt in 3D statt nur 2D
  verteilt):** die teure Iteration läuft weiterhin NUR neu, wenn sich entweder die Geometrie (neuer
  Server-Snapshot) oder die Spieler-BLOCKPOSITION (jetzt X/Y/Z, vorher nur X/Z) ändert - nicht jeden
  Frame (siehe `BoundaryTintRenderer`-Klassenkommentar). Um die durch die dritte Dimension
  vervielfachte Blockzahl nicht komplett naiv als lauter überlappende horizontale Vollflächen zu
  rendern, wird pro Kandidat-Block zusätzlich geprüft, ob der Nachbar EINEN Block höher Luft ist
  (eine "sichtbare Oberfläche von oben") - deckt Geländeoberfläche, Höhlenböden und schwebende
  Inseln ab, ohne für jede durchgehend massive Untergrundschicht eine eigene (ohnehin unsichtbare)
  Ebene zu zeichnen. Bewusst NICHT weiter optimiert (Sichtweiten-/Frustum-Culling, Block-Change-
  Listener für sofortige Aktualisierung bei fremden Bauaktionen in der Nähe) - Nutzer-Vorgabe war
  ausdrücklich "don't over-engineer, if a full naive per-block-in-radius iteration is fine at that
  range, that's fine". Alpha weiterhin ~36% (`ALPHA = 92`).

**8. Neue/verifizierte Regeln.**
- **Ernten von Feldfrüchten - GEPRÜFT statt angenommen: bereits vollständig über BUILD abgedeckt.**
  In den dekompilierten Vanilla-Sourcen haben `CropBlock`/`StemBlock`/`NetherWartBlock` KEINE
  `useItemOn`/`useWithoutItem`/`interact`-Überschreibung - es gibt in Vanilla 1.21.1 also KEINE
  zerstörungsfreie Ernte-Interaktion, Ernten läuft IMMER über das Abbauen des Feldfrucht-Blocks und
  feuert damit bereits `BlockEvent.BreakEvent` (BUILD). Kein zusätzlicher Code nötig, nur ein
  dokumentierter Befund (siehe `ClaimProtectionListener`-Kommentar bei `onBreak`).
  Nutzer-Vermutung/-Vorgabe damit bestätigt.
- **`PLACE_FLUID`** (Eimer-Platzierung von Wasser/Lava) - verifiziert über
  `BlockEvent.FluidPlaceBlockEvent` (existiert, in den dekompilierten Sourcen nachgeschlagen, nicht
  geraten - genau der vom Nutzer vermutete Name).
- **`TRAMPLE_FARMLAND`** (Ackerboden -> Erde durch Draufspringen) - verifiziert über
  `BlockEvent.FarmlandTrampleEvent` (ebenfalls existiert, exakt der vom Nutzer vermutete Name).
- **Judgment call (getrennt statt kombiniert):** beide als SEPARATE `RuleType`s umgesetzt (nicht zu
  einer Regel zusammengefasst) - zwei unterschiedliche Spieler-/Physik-Aktionen, passt zum
  bestehenden Muster "eine Regel pro klar abgrenzbarer Aktion" (wie schon bei BUILD/ITEM_DROP/PVP/
  LEASH_PASSIVE_MOBS).

**9. Cobblemon-Fang-Verifizierung - jetzt tatsächlich verdrahtet (vorheriger Batch hatte dies als
TODO offengelassen).** `javap` war weiterhin nicht installierbar, aber die echte Cobblemon-Jar
(`CobbleCompanion/libs/Cobblemon-neoforge-1.7.3+1.21.1.jar`) war diesmal verfügbar - Verifikation
per `java.lang.reflect` (Feld-/Methodenliste der relevanten Klassen, mit Minecraft-/NeoForge-Jar
UND Kotlin-Stdlib-Jar auf dem Klassenpfad) UND zusätzlich einem eigenständigen Testquelltext, der
erfolgreich mit `javac` GEGEN DIE ECHTE JAR kompiliert wurde (nicht nur reflektiert - siehe
`CobblemonBridge`-Klassenkommentar für die vollständige Herleitung). Ergebnis:
`CobblemonEvents.THROWN_POKEBALL_HIT` ist eine ECHT abbrechbare `CancelableObservable` (im
Gegensatz zu `POKE_BALL_CAPTURE_CALCULATED`/`POKEMON_CAPTURED`, die beide nur beobachtbare,
NICHT-abbrechbare `EventObservable`s sind - ein rein aus dem Klassennamen NICHT ersichtlicher
Unterschied). `ThrownPokeballHitEvent#getPokeBall()` liefert eine `EmptyPokeBallEntity`, die
`net.minecraft.world.entity.projectile.ThrowableItemProjectile` erweitert (ECHTE Vanilla-Klasse) -
darüber liefert das geerbte `Projectile#getOwner()` den werfenden Spieler, ganz ohne
Cobblemon-spezifische API dafür zu brauchen. Neue `com.areaclaims.integration.CobblemonBridge`
(einzige Klasse mit Cobblemon-Klassen-Referenzen, gleiches Muster wie `CobbleCompanionBridge`)
cancelt den Ball-Treffer, BEVOR die Fangberechnung beginnt, wenn `PREVENT_COBBLEMON_CAPTURE` am
Wurfort für den werfenden Spieler aktiv ist. Cobblemon-Jar + Kotlin-Stdlib wurden dafür als
`compileOnly`-Abhängigkeiten ergänzt (`build.gradle`, `libs/Cobblemon-neoforge-1.7.3+1.21.1.jar`,
`neoforge.mods.toml` optionale Abhängigkeit) - exakt dasselbe "niemals `implementation`"-Prinzip
wie bei CobbleCompanion.

**Gesamtstatus:** alle neun Punkte wurden umgesetzt (Punkt 9 war im vorherigen Batch noch ein
offenes TODO, ist jetzt erledigt). Judgment calls/Vereinfachungen sind an den jeweiligen Punkten
oben markiert - die gewichtigsten sind der Preis-Belastungs-Algorithmus (Punkt 6, eigene, aber vom
Nutzer vorgeschlagene Faltungs-Semantik), die vereinfachte "Boden-Ebene statt Vollvolumen"-Geometrie
der Block-Einfärbung (Punkt 7) und der bewusst nicht rückwirkend behobene Randfall bei mittig
leeren Preis-Slots (Punkt 6). Nichts von alldem wurde in dieser Session in einem echten
Spielfenster getestet (kein Zugriff auf ein laufendes Spiel) - jeder Schritt wurde ausschließlich
über `./gradlew compileJava` (inkl. eines vollständig sauberen `clean compileJava`) verifiziert.

### Phase 7-8 – Klick-zum-Abstecken + Preisbestätigung (Kern erledigt, ungetestet) + Block-Einfärbung-Nachbesserung + großes visuelles Redesign (TEILWEISE, Rest als TODO)

Der bisher größte Einzel-Batch: acht Punkte für ein umfassendes UX-Redesign des Editors PLUS,
zwischenzeitlich vom Nutzer nachgereicht, Live-Test-Feedback zur Block-Einfärbung aus einer
früheren Runde. Nutzer-Vorgabe: "prioritize getting the core interaction model (item 7-8) correct
... items 1-6 are more independently choppable if you run low on capacity". Genau danach vorgegangen.

**Vollständig umgesetzt: Punkte 7+8 (Klick-zum-Abstecken + Preisbestätigung) - die goldene Hacke
ist komplett Geschichte.**
- `ToolStateManager` (Werkzeug-Ein/Aus-Zustand) und die Befehle `/areaclaims tool`/`claim`/
  `expand`/`subclaim` wurden ERSATZLOS ENTFERNT. Neuer Ablauf: Klick auf "Erweitern"/"Neuer
  SubClaim"/"Neuer Claim" im Editor schickt NUR ein `BeginSelectionPacket` - der Server
  entscheidet (`StakingService#begin`, neue zentrale Ablaufsteuerung, gleiches "einzige Quelle der
  Wahrheit"-Prinzip wie `ClaimEditService`): entweder eine GEMERKTE Auswahl (siehe unten) führt
  direkt zur Preisbestätigung, oder der Spieler wechselt in den Punkte-Platzier-Modus
  (`ActiveSelectionManager`, neu) - der Editor schließt sich dafür automatisch
  (`EnterSelectionModePacket`).
- Im Platzier-Modus fängt `ToolInteractionListener` (grundlegend umgebaut) JEDEN Links-/
  Rechtsklick ab, UNABHÄNGIG vom gehaltenen Item: Linksklick auf einen Block fügt einen Punkt
  hinzu (`SelectionManager`, unverändert wiederverwendet); JEDER Rechtsklick (Block, Entity, Item-
  Nutzung) beendet die Auswahl. **Verifiziert statt geraten** (dekompilierte
  `ServerPlayerGameMode.java`): Canceln von `LeftClickBlock` verhindert Blockabbau in JEDEM
  Spielmodus, auch Kreativ (die `event.isCanceled()`-Prüfung steht in
  `handleBlockBreakAction` VOR der Kreativmodus-Sonderbehandlung - kein Schlupfloch). Rechtsklick
  wird über `RightClickBlock`/`RightClickItem`/`EntityInteract`/`EntityInteractSpecific`
  abgedeckt - **ein dokumentierter, nicht übersehener Sonderfall**: `RightClickEmpty`
  (Rechtsklick mit LEERER Hand in offene Luft, kein Ziel in Reichweite) wird laut NeoForges
  eigenem Doc-Kommentar NUR clientseitig gefeuert und NIE an den Server gemeldet, außerdem nicht
  abbrechbar - dafür ein neues `EndSelectionPacket` + `ClientSelectionEndListener`
  (client-only), der genau diesen einen Fall manuell an den Server meldet (da eine leere Hand
  ohnehin keine Nebenwirkung hat, reicht "sag dem Server Bescheid" ohne Cancel-Bedarf).
- **Preisbestätigung (Punkt 8):** nach dem beendenden Rechtsklick wird die Geometrie OHNE
  Mutation geprüft (`ClaimManager` bekam dafür neue `validateNewMainClaim`/`validateAddPart`/
  `validateNewSubClaim`-Methoden, die intern von den mutierenden Original-Methoden
  wiederverwendet werden - EIN Regelwerk für beide Wege) - der Claim soll erst nach bezahlter/
  kostenloser Bestätigung TATSÄCHLICH existieren, sonst würde eine "Auswahl merken"-Fläche
  anderen Spielern schon als belegt erscheinen, obwohl sie noch gar nicht bezahlt ist. Kein Preis
  konfiguriert -> sofortiges Committen (unverändertes Verhalten). Preis konfiguriert -> die
  Auswahl landet in `PendingActionManager` (neu, EIN Slot pro Spieler - Judgment call: KEINE
  mehreren parallelen gemerkten Aktionen unterstützt, ein selteneres Nutzungsmuster, siehe
  dortigen Klassenkommentar), Editor öffnet sich wieder MIT einem Preisbestätigung-Overlay
  darüber (`ShowPriceConfirmPacket`/`PriceConfirmSnapshot`, `ClientPriceConfirmCache`).
  - **Bestätigen** - committet+belastet jetzt erst wirklich (`StakingService#confirmPending`),
    ausgegraut wenn `PriceCharger.canAfford(...)` (neuer, aus `charge()` extrahierter Prüfpfad
    ohne zu belasten) gerade `false` liefert.
  - **Abbrechen** - `StakingService#resumePending`: Punkte werden zurück in `SelectionManager`
    geschrieben, `ActiveSelectionManager` reaktiviert, Editor schließt sich wieder (zurück in den
    Platzier-Modus, NICHTS geht verloren).
  - **Auswahl merken** - bewusst OHNE eigenes Server-Paket: die Auswahl liegt durch das Beenden
    bereits in `PendingActionManager`, der Button schließt nur clientseitig das Overlay. Klick auf
    denselben Auslöse-Button später erkennt sie automatisch wieder (`PendingActionManager#find`).
  - **Beenden** - `StakingService#discardPending`: verwirft alles, Editor bleibt in Normalansicht.
  - Preis für ALLE VIER Aktionen (Erweitern-Haupt, Erweitern-Unter, Neuer SubClaim, Neuer Claim)
    ist derselbe Pro-Block-Preis aus Phase 6 - **Judgment call:** "Neuer SubClaim" war bisher
    KOSTENLOS (der alte `/areaclaims subclaim`-Befehl hatte nie eine Preisprüfung, ein bislang
    unbemerkter Bestandsfehler) - Punkt 8 zählt es explizit zu den "vier Aktionen" mit
    Preisbestätigung, jetzt konsistent bepreist wie die anderen drei.
  - **Auto-Benennung** (Punkt 7): "Neuer Claim" -> `ClaimManager.nextAutoMainClaimName` ("Claim
    1", "Claim 2", ...); "Neuer SubClaim" -> `nextAutoSubClaimName` ("SubClaim 1", ...) - beide
    überspringen bereits vergebene Nummern, kein Namens-Eingabe-Schritt mehr nötig, danach über
    das Umbenennen-Feld frei änderbar.
  - "Neuer SubClaim": zeigt beim Abstecken automatisch die Grenzen des Hauptbereichs UND aller
    Geschwister-Unterbereiche im aktuell aktiven Anzeigestil (`ClaimShowcaseManager#ensureShown`,
    neu - fügt hinzu, ohne bereits Angezeigtes abzuschalten).
- Button-Verschiebung (Teil von Punkt 7, mit umgesetzt, da eng an die Interaktion gekoppelt):
  "Neuer Claim" jetzt oberhalb der Hauptbereichs-Spalte, "Erweitern" direkt unter jeder
  Hauptbereichs-/Unterbereichs-Zeile, "Neuer SubClaim" nach der letzten Unterbereichs-Zeile - nicht
  mehr im Detail-Panel.

**Block-Einfärbung-Nachbesserung (Live-Test-Feedback, während der Arbeit an obigem nachgereicht,
gleiche Priorität):**
- Fester 20-Block-Radius ersetzt durch admin-konfigurierbare Sichtweite in CHUNKS
  (`FeatureConfigManager#tintRangeChunks`, Default 2 - Nutzer-Vorschlag war "2-3", die untere
  Grenze gewählt, weil die neue Rendering-Technik pro Block teurer ist, siehe unten; Klemmung
  [1,8]) - per Befehl (`/areaclaims config tintrange`) UND im Server-Konfigurations-Screen
  (gleiches Zeilen-Muster wie die Teile-Obergrenze), an den Client über
  `ShowcaseGeometrySnapshot#rangeBlocks` übertragen.
- Nur noch VOLLE WÜRFEL werden eingefärbt (`BlockState#isCollisionShapeFullBlock` - verifiziert in
  den dekompilierten `BlockBehaviour.java`-Sourcen: intern exakt
  `Block.isShapeFullBlock(state.getCollisionShape(...))`, bereits gecacht, dieselbe Methode, die
  Vanilla für Redstone-Leiter-Prüfungen nutzt) - Stufen/Treppen/Zäune werden jetzt korrekt
  ausgefiltert.
- VOLLE 6-seitige Flächen-Kulierung statt nur einer Oberseite: jede Würfelseite wird nur gezeichnet,
  wenn der Nachbar auf dieser Seite NICHT ebenfalls ein voller Würfel ist (sonst ohnehin komplett
  verdeckt) - dasselbe Grundprinzip wie Vanillas eigener Chunk-Mesher. Ergebnis: sichtbare
  Seitenwände (Klippen, Höhlenwände) statt nur einer flachen Boden-Ebene.
- **Referenz-Abgleich (Nutzer-Vorgabe):** die tatsächliche Forgematica/Litematica-Jar
  (`Server/mods/forgematica-0.4.1+mc1.21.1.jar`) wurde inspiziert (gleiche Methode wie bei der
  Cobblemon-Jar-Untersuchung - Klassen extrahiert, Konstanten-Pool-Strings gelesen). Ergebnis:
  Litematicas eigener `OverlayRenderType` für Schematic-Übersichten nutzt EXAKT dasselbe Format/
  denselben Modus (`DefaultVertexFormat.POSITION_COLOR` + `VertexFormat.Mode.QUADS`) wie das hier
  bereits verwendete `RenderType.debugQuads()` - eine Bestätigung, dass der bestehende Ansatz kein
  Fehlgriff war. Litematicas VOLLE VBO-/Chunk-Renderer-Dispatcher-Infrastruktur (Worker-Threads,
  eigene Chunk-Mesh-Pipeline für texturierte Schematic-Blockmodelle) wurde bewusst NICHT übernommen
  - für eine reine Farb-Einfärbung wäre das deutlich überdimensioniert gewesen (Judgment call).
  Die Performance-Vorgabe ("naive Iteration ist ok, don't over-engineer") blieb unverändert
  gültig - `BoundaryTintRenderer` cacht weiterhin nur bei Bewegung/neuer Geometrie neu.

**Punkt 4 (Rote-X-Stil + fehlende Unterbereich-Löschung) - vollständig umgesetzt:** Datenlage
(`ClaimManager#deleteClaim`) unterstützte kaskadierendes Löschen bereits unabhängig davon, ob der
Start-Claim Haupt- oder Unterbereich ist - **verifiziert, der Fehlende Teil war rein die UI**: die
Unterbereichs-Spalte hatte schlicht keinen Löschen-Button. Jetzt ergänzt (gleiches Klick-zum-
Bestätigen-Muster wie Hauptbereiche, geteilter `deleteArmedClaimId`-Zustand). **Nicht umgesetzt:**
der spezifische CobbleCompanion-Rote-X-GLYPH-Stil (`"✗"`-Textglyph statt Button-Widget, siehe
`CompanionScreen`-Referenz) - Löschen-Buttons bleiben vorerst normale Vanilla-`Button`-Widgets mit
"X"-Text, siehe TODO-Liste unten.

**NICHT umgesetzt in dieser Runde (Punkte 1, 2, 3, 5 [teilweise], 6) - klar dokumentierter
Rückstand für eine Folgerunde, wie vom Nutzer ausdrücklich erlaubt:**
- **Punkt 1 (Name-Bearbeiten-Popup mit Regenbogen-Farbwähler):** das Umbenennen-Textfeld aus der
  vorherigen Runde blieb unverändert (kein Stift-Icon, kein In-Menü-Popup, kein Regenbogen-/Hue-
  Farbwähler-Widget für Titel-/Grenzfarbe). Die Betreten-Nachricht-Einstellungen (Farbe/Dauer)
  bleiben auf dem separaten `AreaClaimsEntryMessageScreen` aus der vorherigen Runde (reine Hex-
  Texteingabe, kein visueller Picker).
- **Punkt 2 (Detail-Panel-Neugliederung):** keine Unterbereichs-Namensliste mit je eigenem Stift-
  Button im Hauptbereich-Detail-Panel, keine Sonderbehandlung für den Fokus auf einen Unterbereich
  (zeigt weiterhin nur den fokussierten Claim selbst, nicht zusätzlich eine Referenzzeile zum
  Hauptbereich).
- **Punkt 3 (eigener Mitglieder-Screen):** Mitglieder werden weiterhin INLINE im Detail-Panel
  bearbeitet (Rollen-Button + "X" wie bisher), kein separater Screen, kein CobbleCompanion-Rote-X-
  Glyph-Stil, kein "Mitglieder"-Überschrift-Stift-Button. Die Button-Beschriftung "Als Mitglied
  hinzufügen" wurde NICHT zu "Mitglied hinzufügen" umbenannt (unabhängig kleine Änderung, aber im
  Zuge des Zeit-/Kapazitätsdrucks mit dem Rest des Mitglieder-Screens zurückgestellt).
- **Punkt 5 (Regel-Panel-Restyling + Freikauf-Integration) - TEILWEISE:** die OP-Stufen-Anzeige-
  Umstellung (Zyklus-Button "Spieler"/"OP 1"-"OP 4" statt nackter Zahl, "Mindest-OP-Stufe"-Wortlaut
  entfernt) war bereits aus einer VORHERIGEN Runde vorhanden und blieb unverändert bestehen. NICHT
  umgesetzt: Regeln rechts/rechtsbündig statt in der aktuellen Position, grüner Haken/rotes Kreuz
  statt "Aktiv"/"Deaktiviert"-Text, neue Spaltenreihenfolge (Haken/Kreuz -> Name -> Rolle-Button),
  "Ignoriert ab"-Kopfzeile, Umbenennen der Überschrift "Regeln" -> "Erlaube", "Kaufen"-Button +
  Bestätigen-Popup für konfigurierte, noch nicht freigekaufte Regel-Preise.
- **Punkt 6 (Grenzen-Anzeige-Steuerung nach unten, "Einstellungen"-Umbenennung, Farbwahl-Popup):**
  "Grenzen anzeigen"/Anzeigestil-Button bleiben im Detail-Panel (nicht an den unteren Bildschirmrand
  verschoben), der Anzeigestil-Button heißt weiterhin nach seinem aktuellen Zustand (nicht
  "Einstellungen"), kein Popup mit "Verwende Claim-Farbe"-Checkbox/Hex-Feld/Regenbogen-Picker/
  Partikel-Einfärben-Umschalter dahinter.

**Begründung für diese Priorisierung:** die Nutzer-Vorgabe war explizit, den Kern-Interaktionsablauf
(Punkte 7+8) zuerst korrekt fertigzustellen, da "everything else builds on it" - das ist der
funktional wichtigste und riskanteste Teil (Event-Cancel-Semantik, Preisbestätigung mit
persistentem Zustand) und wurde vollständig umgesetzt und verifiziert (inkl. der nicht
offensichtlichen `RightClickEmpty`-Lücke, die ein weniger gründlicher Ansatz übersehen hätte). Das
gleichzeitig nachgereichte Block-Einfärbung-Feedback war ebenfalls klar umrissen und wurde
vollständig umgesetzt. Für die verbleibenden, primär visuellen Punkte 1/2/3/5/6 reichte die Zeit in
dieser Runde nicht mehr - sie sind rein additiv (kein bestehender Code muss dafür rückgebaut
werden) und können unabhängig voneinander in einer Folgerunde nachgezogen werden.

Nichts von alldem wurde in dieser Session in einem echten Spielfenster getestet (kein Zugriff auf
ein laufendes Spiel) - jeder Schritt wurde ausschließlich über `./gradlew compileJava` (inkl.
mindestens eines vollständig sauberen `clean compileJava`) verifiziert.

### Phase 7-Nachtrag 2 – Die zurückgestellten Punkte 1-5 nachgezogen (erledigt, ungetestet)

Direkte Fortsetzung von Phase 7-Nachtrag: alle fünf damals dokumentierten TODO-Punkte wurden jetzt
umgesetzt. Der Reihe nach, mit allen Judgment Calls:

**Neuer, wiederverwendbarer Baustein: `GlyphButton`.** Ein klickbarer, einfarbiger Text-Glyph OHNE
Button-Rahmen (echtes `AbstractWidget`, siehe dessen Klassenkommentar) - für den CobbleCompanion-
Stil (grünes ✓ `0xFF55FF55`/rotes ✗, aus `CompanionScreen`s Freundesliste/Anfragen-Zeilen). **Judgment
call:** CompanionScreen zeichnet diese Glyphen dort NICHT als echte Widgets, sondern als reinen
Text + eine eigene manuelle Klick-Trefferprüfung (`handleFriendsClicks`). `GlyphButton` bildet
stattdessen ein ECHTES Widget, das sich normal über `addRenderableWidget` registriert - visuell
identisch (farbiger Glyph, kein Rahmen), aber die Klick-Erkennung läuft über Vanillas bereits
vorhandene, mit `FixedScaleScreen`s Koordinaten-Umrechnung verifiziert zusammenarbeitende Widget-
Maschinerie, statt eine zweite, eigene Trefferprüfung zu pflegen, die bei einem skalierten Screen
leicht hätte auseinanderlaufen können.

**Neuer Baustein: `HueBarWidget` ("Regenbogen"-Farbwähler).** Ein horizontaler Farbton-Streifen
(Sättigung/Helligkeit fest auf Maximum), Klick liefert die Farbe an der geklickten X-Position.
**Judgment call (bewusste Vereinfachung):** eindimensional statt eines vollen 2D-Farbrads/
Sättigung-Helligkeit-Quadrats - zusammen mit dem parallel angebotenen Hex-Textfeld (für exakte/
entsättigte/abgedunkelte Werte) deckt das den praktischen Bedarf ab, ohne die deutlich aufwändigere
2D-Auswahl-Logik zu brauchen. Reine eigene HSV-zu-RGB-Mathematik, keine `java.awt`-Abhängigkeit.

**Punkt 1 (Name-Bearbeiten-Popup) - erledigt.** Ersetzt das Umbenennen-Textfeld+Button aus der
vorherigen Runde: ein Stift-Symbol ("✎") öffnet ein In-Menü-Popup (Overlay, kein neuer Screen) mit
Name, Textfarbe (Hex + `HueBarWidget`), Anzeigedauer (Ticks) und Grenzfarbe (Hex + `HueBarWidget`)
plus der "Grenzfarbe = Textfarbe?"-Verknüpfungs-Checkbox. Für die Willkommensnachricht ein
gleichartiges, aber eigenes Popup (Text + Farbe + Dauer, KEINE Grenzfarbe, da Willkommensnachrichten
keine eigene Grenzfarbe haben). Das alte `AreaClaimsEntryMessageScreen` (separater Screen aus einer
früheren Runde) wurde ENTFERNT, komplett durch diese beiden Popups ersetzt. **Judgment call:**
Übernehmen sendet die Felder in fester Reihenfolge, `linkboundarycolor` bewusst ZULETZT (identische
Begründung wie beim alten Screen: das Setzen einer expliziten Grenzfarbe schaltet serverseitig
automatisch "verknüpft" aus, die Checkbox soll aber das letzte Wort haben).

**Punkt 2 (Detail-Panel-Neugliederung) - erledigt.** Hauptbereich fokussiert: Name+Stift,
"SubClaims:"-Überschrift, Namensliste ALLER Unterbereiche (je mit eigenem Stift, gedeckelt auf 6
sichtbare Zeilen wie die bestehende Mitgliederliste - Judgment call, gleiche Konvention wie
anderswo im Screen), Willkommensnachricht-Vorschau (gekürzt auf 24 Zeichen + "...") + Stift.
Unterbereich fokussiert: NUR eine schreibgeschützte "Hauptbereich: X"-Referenzzeile (kein Stift),
dann NUR dieses Unterbereichs eigener Name+Stift und eigene Willkommensnachricht+Stift - GENAU wie
vorgegeben, nicht die volle Geschwisterliste.

**Punkt 3 (eigenständiger Mitglieder-Screen) - erledigt.** Neuer `AreaClaimsMemberScreen`: je Zeile
Rollen-Button ZUERST, dann Name, dann der rote "✗"-`GlyphButton` NACH dem Namen - dessen X-Position
wird aus der tatsächlichen Textbreite DIESER Zeile berechnet (`nameX + Textbreite(name) + Abstand`),
verschiebt sich also mit der Namenslänge, keine feste Spalte. "Mitglied hinzufügen"-Beschriftung wie
gefordert (vorher "Als Mitglied hinzufügen"). Im Haupt-Detail-Panel ist der Mitglieder-Bereich jetzt
rein schreibgeschützter Text ("Name - Rolle" pro Zeile) + ein Stift-Button, der zu diesem Screen
navigiert.

**Punkt 4 (Regel-Panel-Restyling + Freikauf) - erledigt, inkl. der beiden nachgefragten Prüfungen.**
- Grünes Häkchen/rotes Kreuz (`GlyphButton`, exakt dieselben Farben wie die CompanionScreen-
  Referenz) ersetzt den alten "Aktiv"/"Deaktiviert"-Textbutton. **Wichtiger Bedeutungs-Punkt (nicht
  geraten, aus der Vorgabe abgeleitet):** grünes Häkchen = ERLAUBT = Regel ist DEAKTIVIERT (nichts
  wird eingeschränkt, Standardzustand einer frischen Regel) - rotes Kreuz = NICHT ERLAUBT = Regel
  ist AKTIV (schränkt ein). Das ist bewusst invertiert gegenüber der internen `enabled`-Variable.
- Neue Spaltenreihenfolge: Häkchen/Kreuz → Regelname → Rollen-Button (bzw. "Kaufen"-Button, siehe
  unten).
- "Ignoriert ab"-Kopfzeile über der Rollen-Button-Spalte, Überschrift "Regeln" -> "Erlaube"
  umbenannt (beide Lang-Dateien).
- **Freikauf-Integration:** ist für eine Regel ein Preis konfiguriert UND noch nicht freigekauft
  (neue `RuleEntry.buyoutPrice`/`boughtOut`-Felder im Snapshot, siehe `ClaimSnapshotBuilder`),
  erscheint statt des Rollen-Buttons ein "Kaufen"-Button. Klick öffnet ein Bestätigen-Popup mit dem
  Preis; "Bestätigen" schickt ein neues `BuyoutRulePacket`. Die Freikauf-LOGIK selbst wurde aus
  `AreaClaimsCommands#buyoutRule` in einen neuen, geteilten `BuyoutService` extrahiert (gleiches
  "einzige Quelle der Wahrheit"-Prinzip wie überall sonst) - Chat-Befehl `/areaclaims buyout` und
  das neue Paket rufen jetzt exakt dieselbe Stelle auf.
- **"Double-check" zum Haupt-Löschen-Button (explizit nachgefragt):** war TATSÄCHLICH noch ein
  normaler Vanilla-`Button` mit "X"-Text (nur der neu ergänzte Unterbereich-Löschen-Button aus der
  vorherigen Runde folgte demselben Muster) - beide sind jetzt auf den roten `GlyphButton`-Stil
  umgestellt. **Judgment call:** die Klick-zum-Bestätigen-Breite wurde von 18 auf 40 Blöcke
  vergrößert, damit der übersetzte "Sicher?"-Bestätigungstext (der im bestätigten Zustand weiterhin
  als Wort statt eines einzelnen Glyphs angezeigt wird) nicht über die Spaltenbreite hinausragt.
- **"Double-check" zur Regel-Panel-Neupositionierung (explizit nachgefragt):** war ebenfalls noch
  NICHT umgesetzt (das Regel-Panel stand weiterhin an der alten, von Spalte 3 abhängigen Position)
  - jetzt korrigiert: `rulesPanelX()` berechnet sich aus `this.width - Panel-Breite - Rand` (rechts-
  bündig zum BILDSCHIRMRAND, nicht mehr an Spalte 3 gekoppelt), oben ausgerichtet auf derselben
  Höhe wie die Überschrift des Detail-Panels. Spalten 1/2/3 (Hauptbereiche/Unterbereiche/Mitglieder)
  bleiben dabei unverändert an ihren bisherigen, zueinander fluchtenden Positionen - nur das Regel-
  Panel wurde "herausgelöst".

**Punkt 5 (Grenzen-Anzeige-Steuerung nach unten + Einstellungen-Popup) - erledigt.**
- "Grenzen anzeigen" und der vorherige Anzeigestil-Button sitzen jetzt als eigene Zeile direkt über
  der Schließen-Zeile, nahe der Trennlinie am unteren Bildschirmrand (Y dynamisch aus
  `this.height` berechnet, nicht mehr Teil des Detail-Panel-Flusses).
- Der zweite Button heißt jetzt "Einstellungen" (vorher zeigte er direkt "Partikel"/"Einfärbung"
  als eigenen Text) und öffnet ein Popup statt sofort umzuschalten.
- Popup-Inhalt: "Verwende Claim-Farbe"-Umschalt-Button (Ja/Nein) - bei "Nein" erscheinen zusätzlich
  ein Hex-Feld + `HueBarWidget` für eine PERSÖNLICHE Grenzfarbe-Überschreibung, PLUS weiterhin der
  Partikel/Einfärben-Umschalt-Button (unverändert aus der vorherigen Runde, schaltet sofort um,
  kein Übernehmen-Schritt nötig, da rein binär).
- **Neue Server-Funktionalität, die es so noch NICHT gab (Nutzer-Vorgabe ging fälschlich davon aus,
  sie sei "bereits als per-Betrachter-Zustand gebaut"** - tatsächlich existierte bisher NUR der
  Anzeigemodus (Partikel/Einfärbung) pro Betrachter, KEINE Farb-Überschreibung. Neu ergänzt:
  `ClaimShowcaseManager#colorOverride`/`effectiveColorFor` (pro Betrachter, `null` = benutze die
  vom Claim-Besitzer konfigurierte Farbe), neues `SetBoundaryColorOverridePacket`, `ClaimEditorSnapshot#boundaryColorOverride`
  für die Popup-Vorbefüllung. Sowohl der Partikel-Renderer (`ToolInteractionListener`) als auch der
  Block-Einfärbung-Renderer (`ShowcaseGeometrySnapshotBuilder`) wurden umgestellt, diese
  Überschreibung zu berücksichtigen. **Judgment call:** die Überschreibung überlebt bewusst
  `/areaclaims cancel` (anders als der Anzeigemodus) - eine Farbvorliebe ist eine dauerhaftere
  Einstellung als "was zeige ich mir gerade an".

**Alle fünf Punkte aus Phase 7-Nachtrag sind damit erledigt** - keine offenen visuellen TODOs mehr
aus diesem Batch. Wie immer: nichts davon wurde in dieser Session in einem echten Spielfenster
getestet (kein Zugriff auf ein laufendes Spiel) - jeder Schritt wurde ausschließlich über
`./gradlew compileJava` verifiziert, inkl. eines abschließenden, vollständig sauberen
`clean compileJava`. Eine (nicht näher untersuchte, nicht blockierende) Gradle-Meldung über die
Verwendung einer veralteten API tauchte bereits VOR diesem Batch auf und wurde nicht dieser Runde
zugeordnet - falls sie stört, lohnt sich `-Xlint:deprecation` in einer künftigen Runde.

### Phase 7-Nachtrag 3 – Erster echter In-Game-Testdurchlauf, 15-Punkte-Feedback-Batch (erledigt, ungetestet)

Erstes echtes In-Game-Test-Feedback nach allen bisherigen (nur `compileJava`-verifizierten) Runden.
15 Punkte, Punkte 1-12 kleinere Bugs/Politur, Punkte 13-15 ein größerer Architektur-Umbau der
Betreten-Nachricht-Anzeige.

**Punkt 1 (Erweitern-Button nur beim fokussierten Claim) - erledigt.** `buildMainColumn()`/
`buildSubColumn()` zeigten vorher JEDE Zeile ihren eigenen "Erweitern"-Button gleichzeitig -
jetzt nur noch, wenn genau dieser Haupt-/Unterbereich gerade ausgewählt ist.

**Punkt 2 (Spalte-2-Ausrichtung) - erledigt.** `COL2_Y` war eine feste Konstante (= `COL1_Y`) -
jetzt `col2Y()`, berechnet aus dem Index des ausgewählten Hauptbereichs × Zeilenabstand, sodass die
Unterbereichsliste auf derselben Höhe wie die fokussierte Hauptbereichs-Zeile beginnt.

**Punkt 3 (Titelfarbe bereits in den Listen) - erledigt.** Haupt-/Unterbereichs-Namensbuttons in
Spalte 1/2 sind jetzt `GlyphButton`s mit `claim.titleColor()` statt vanilla-`Button`s mit fester
Farbe; die Namens-Überschriften im Detail-Panel (`renderDetailPanelText`) nutzen ebenfalls
`claim.titleColor()`/`sub.titleColor()` statt der festen `TEXT_COLOR`.

**Punkt 4 (Häkchen kräftiger) - erledigt.** `GlyphButton` rendert seinen Text jetzt IMMER fett
(`ChatFormatting.BOLD`), plus eine leichte Hover-Hervorhebung (`0x30FFFFFF`-Füllung), da der Glyph
sonst keinerlei Button-Andeutung hatte - Nebeneffekt: betrifft ALLE Glyph-Verwendungen (Lösch-X,
Regel-Häkchen/-Kreuz, Stift-Symbole, jetzt auch die neuen Namensbuttons aus Punkt 3), nicht nur das
Häkchen.

**Punkt 5 (kaskadierende Mitgliedschaft "funktioniert nicht") - Root Cause gefunden, KEIN Logik-Bug.**
`ClaimEditService#applyRole`/`cascadeMembershipToParent` wurden vollständig gelesen und sind
strukturell korrekt (richtiger Ordinal-Vergleich, richtiger Parent-Lookup, richtiger
Persistenz-Aufruf) - die Kaskadierungs-LOGIK war nie kaputt. Tatsächliche Ursache: eine
Navigations-Regression aus der Aufteilung in einen eigenständigen `AreaClaimsMemberScreen` - dessen
`returnToEditor()` baute den Editor bisher über den PARAMETERLOSEN Konstruktor neu auf, wodurch die
zuvor gewählte Spalte-1/2-Auswahl verloren ging (`ensureSelection()` springt dann auf den ERSTEN
Hauptbereich). Ergebnis: nach dem Bearbeiten eines Unterbereichs-Mitglieds landete man ggf. auf dem
FALSCHEN Hauptbereich und sah dessen (leere) Mitgliederliste - sah wie ein Kaskadierungs-Bug aus,
war aber ein reines UI-Zustands-Problem. **Fix:** neuer Konstruktor
`AreaClaimsEditorScreen(String preselectMainId, String preselectSubId)`, `AreaClaimsMemberScreen
#returnToEditor()` berechnet jetzt Haupt-/Unterbereichs-ID aus dem gerade bearbeiteten Claim
(`claim.main ? claim.id : claim.parentId` bzw. `!claim.main ? claim.id : null`) und übergibt sie.

**Punkt 6 (Layout: "Keine Unterbereiche"/"SubClaim anlegen"-Überlappung) - erledigt.** Bei leerer
Unterbereichsliste standen Platzhaltertext und "Neuer SubClaim"-Button vorher auf exakt derselben
Y-Höhe (`buildSubColumn` reservierte keine Zeile für den Fall "leer", `renderSubColumnInfo` schon
nicht mehr synchron dazu). **Entscheidung (Nutzer-Vorgabe erlaubte beide Varianten):** Button eine
Zeile nach unten verschoben (auf Höhe der "Erweitern"-Zeile des Hauptbereichs), Platzhaltertext
bleibt an seiner Position - liest sich sauberer, als den Text zu verschieben.

**Punkt 7 (Platzhalter "Noch keine Mitglieder" zu tief) - erledigt.** Neue Justierschraube
`NO_MEMBERS_TEXT_Y_NUDGE = -3`, wie vom Nutzer-Gedächtnis-Eintrag zu neuen GUI-Text-Elementen
gefordert.

**Punkt 8 (Stift-Buttons sitzen zu hoch ohne Unterbereich) - Root Cause gefunden und behoben.**
Drei PARALLELE Y-Berechnungen für denselben Detail-Panel-Abschnitt (`buildDetailPanel` baut Widgets,
`renderDetailPanelText` zeichnet Text, `memberListStartY` berechnet die Mitgliederlisten-Startzeile)
- nur ZWEI der drei reservierten bei leerer Unterbereichsliste eine zusätzliche Zeile
(`Math.max(shown, 1)`), `buildDetailPanel` tat das NICHT, wodurch der Willkommensnachricht-Stift
(und alles darunter) zu weit oben landete. Fix: dieselbe `if (shown == 0) y += DETAIL_ROW_HEIGHT`-
Reservierung auch in `buildDetailPanel` ergänzt. **Wiederkehrendes Muster in dieser Datei** (auch
Ursache von Punkt 6): parallele Y-Layout-Berechnungen in Build- vs. Render-Methoden laufen über
mehrere Bearbeitungsrunden leicht auseinander - ein gemeinsamer Layout-Berechnungs-Helfer wäre die
strukturell sauberere Lösung, wurde aber aus Zeitgründen NICHT umgesetzt (gezielte Patches statt
Refactor).

**Punkt 9 (Screenshot-bestätigter Bug: Labels überlappen Eingabefelder) - erledigt.** Root Cause:
fester `OVERLAY_LABEL_WIDTH = 90`-Wert reichte für längere Labels wie "Titel-Dauer (Ticks)" nicht -
der Text lief rechts in das Feld hinein. Ersetzt durch `overlayLabelWidth()`, dieselbe
`measuredButtonWidth(...)`-Technik wie überall sonst in dieser Klasse. **Zusätzlich beim Umbauen
entdeckt und mit behoben (nicht separat gemeldet, aber direkt angrenzend):** der dunkle
Hintergrund-Kasten der Name-/Willkommen-Popups deckte den Label-Bereich vorher GAR NICHT ab (Kasten
begann nur 10px links vom Eingabefeld, Labels standen faktisch links außerhalb) und war dafür
rechts um die (ungenutzte) Labelbreite zu breit - jetzt über neue `overlayPopupBoxLeft()`/
`overlayPopupFieldX()`-Hilfsmethoden konsistent (Build- UND Render-Methoden nutzen dieselbe Stelle,
um genau das Punkt-6/8-Muster hier nicht zu wiederholen). Betrifft sowohl das Name-Bearbeiten- als
auch das Willkommensnachricht-Popup (beide geprüft, wie gefordert).

**Punkt 10 (Server-Konfiguration braucht einen Scrollbalken) - erledigt.** Einfacher, geklemmter
`scrollOffset` (Mausrad, `SCROLL_PIXELS_PER_NOTCH = 16`) + sichtbarer Balken rechts
(`renderScrollbar`). **Judgment call:** BEWUSST kein hartes Scissor-Clipping - Vanillas
`Screen.render()` zeichnet alle Widgets (inkl. des unten fixierten "Schließen"-Buttons) in EINEM
Durchlauf; ein korrektes Scissoring, das den Schließen-Button dabei ausspart, bräuchte einen
zweiten, separaten Render-Durchlauf, was ohne Live-Test als zu riskant eingestuft wurde. Folge:
beim Herunterscrollen können oberste Zeilen in Extremfällen kurz unter der Titelzeile durchscheinen
- funktional aber immer erreichbar/klickbar, rein kosmetisch.

**Punkt 11 (eigener SubClaim-Preis) - erledigt.** Neues `PriceConfigManager#subClaimPrice`
(eigene Persistenz-DTO, eigenes Feld in `areaclaims_prices.json`), neue Preis-Zeile "SubClaim
Preis" im Server-Konfigurations-Screen (identische UI wie "Claim Preis", `target = "subclaim"` in
`SetPriceRowPacket`/`AdminConfigService#setPriceRow`). **Judgment call zur Anwendung:** `StakingService`
nutzt den SubClaim-Preis für `NEW_SUBCLAIM` UND `EXPAND_SUB` (Erweitern eines BESTEHENDEN
Unterbereichs) - nicht nur für das Neuanlegen. Begründung: `EXPAND_SUB`s `targetClaimId` ist bereits
die Unterbereichs-ID selbst (nicht die des Hauptbereichs), `claim.isMain()` entscheidet in
`commitExpand` deshalb zuverlässig zwischen Haupt- und SubClaim-Preis. Das war explizit nicht
vorgegeben, aber die logisch konsistente Lesart ("wessen Fläche wird gekauft" statt "welche Aktion").

**Punkt 12 (Preis-UI-Relabel + Preis-pro-N-Blöcke) - erledigt.** "Preis pro geclaimten Block" ->
"Claim Preis" (beide Lang-Dateien) - **bewusst NUR die GUI-Beschriftung**, `/areaclaims config`-
Befehlsausgaben zeigen weiterhin das rohe `"block"`-Ziel (gleiche Abgrenzung wie schon bei der
OP-Stufen-Anzeige in Phase 6-Nachtrag 2 getroffen). Neues Teiler-Feld ("pro wie vielen Blöcken:")
für BEIDE Preise (Claim + SubClaim), `PriceConfigManager#perBlockDivisor`/`subClaimDivisor`
(Default 1 = unverändertes Verhalten). Abrechnung IMMER aufgerundet:
`PriceCharger#chargeUnits(blocks, divisor) = (blocks + divisor - 1) / divisor` (Teiler=100,
Fläche=150 -> 2 Einheiten, wie gefordert).

**Punkte 13-15 (Betreten-Nachricht-Overhaul) - erledigt, größter Teil dieser Runde.** Kompletter
Architektur-Umbau: Anzeigedauer/-Position/"dauerhaft anzeigen" sind keine Claim-Besitzer-Einstellung
mehr, sondern eine rein PERSÖNLICHE, pro-Betrachter geltende Einstellung.

- **Verifizierter Render-Hook (wie ausdrücklich gefordert, nicht geraten):** `RenderGuiEvent.Post`
  aus den dekompilierten NeoForge-Sourcen (`net/neoforged/neoforge/client/event/RenderGuiEvent.java`)
  geprüft - "fired AFTER the HUD is rendered", auf `NeoForge.EVENT_BUS`, nur clientseitig. Genau der
  vom Nutzer selbst vorgeschlagene Kandidat, bestätigt.
- **Wichtigster Judgment-Call der ganzen Runde (Architektur-Abweichung von der wörtlichen Vorgabe,
  hier bewusst dokumentiert):** die Vorgabe sagte "nicht-dauerhafter Modus nutzt weiterhin Titel/
  Untertitel/Actionbar-Pakete". Das ist mit "Position konfigurierbar" (ebenfalls gefordert, für
  BEIDE Modi) technisch unvereinbar - Vanillas Titel-/Untertitel-/Actionbar-Pakete kennen KEINE
  Positionierung (fest bildschirmmittig, in den dekompilierten Vanilla-Sourcen verifiziert) und
  hätten eine konfigurierbare Position in KEINEM Modus ermöglicht. Zusätzliches Indiz: die
  Willkommensnachricht hat laut Vorgabe explizit KEINEN Dauerhaft-Umschalter, aber TROTZDEM ein
  Positionsfeld - unter der wörtlichen Lesart wäre dieses Feld dann für die Willkommensnachricht
  NIEMALS wirksam gewesen. Entscheidung: EIN EINHEITLICHES eigenes Overlay
  (`EntryDisplayOverlayRenderer`) übernimmt jetzt BEIDE Fälle - "dauerhaft" bleibt sichtbar bis der
  Bereich verlassen/gewechselt wird, "nicht dauerhaft" blendet nach `durationTicks` (Client-seitiger
  Tick-Zähler, kein erneutes Senden nötig) automatisch aus. Das ist eine strikte Erweiterung
  gegenüber Vanilla-Paketen, kein Funktionsverlust - Positionierung funktioniert jetzt überall,
  inkl. Willkommensnachricht. **Falls diese Lesart nicht gewollt war: bitte melden, der Rückbau auf
  zwei parallele Anzeigewege wäre ein gezielter Folge-Task.**
- Neuer per-Spieler-JSON-Datenspeicher `PlayerDisplayPreferencesManager` (`areaclaims_display_prefs.json`,
  EINE Datei mit `UUID -> PlayerDisplayPreferences`-Abbildung - **Judgment call:** dasselbe "eine
  Datei, interne Map"-Muster wie `PriceConfigManager`, da dieser Mod bisher KEINE
  "eine-Datei-pro-Spieler"-Vorlage hat, an der man sich stattdessen hätte orientieren können).
- Neuer, separater `AreaClaimsDisplayPrefsScreen` (öffentlich für JEDEN Spieler, kein OP-Level
  nötig - eigener Button im Editor, gestapelt über dem Admin-"Server-Konfiguration"-Button, falls
  beide sichtbar sind): drei Abschnitte (Hauptbereichsname/Unterbereichsname/Willkommensnachricht)
  wie vorgegeben - Haupt-/Unterbereichsname mit Dauer+Einheiten-Zyklus-Taste (Ticks/Sekunden/
  Minuten, rechnet beim Umschalten den angezeigten Zahlenwert um), normaler Position X/Y,
  Dauerhaft-Umschalter, EIGENER Dauerhaft-Position X/Y; Willkommensnachricht mit Dauer+Einheit +
  EINER Position, bewusst OHNE Dauerhaft-Umschalter (wie explizit vorgegeben - nicht ungefragt
  hinzugefügt).
- Namens-/Willkommen-Bearbeiten-Popups des Claim-BESITZERS (`AreaClaimsEditorScreen`) verloren die
  Dauer-Felder ersatzlos (Besitzer behält: Name, Titelfarbe, Grenzfarbe, Willkommenstext+-farbe -
  GENAU wie vorgegeben).
- `ClaimEntryListener` komplett neu geschrieben: dieselbe 10-Tick-Hysterese-Ortung wie vorher, aber
  personalisierte `EntryDisplaySlotPacket`e (JSON-Payload, da 8 Felder über `StreamCodec.composite`s
  Obergrenze von 6 hinausgehen - in den dekompilierten `StreamCodec.java`-Sourcen verifiziert) je
  Slot (MAIN/SUB/WELCOME) statt der alten Vanilla-Pakete/Actionbar-Wiederholungs-Simulation.
- **Bekannte, bewusst nicht behobene Nebenwirkung:** die frühere "Wildnis"-Titel-Meldung beim
  VERLASSEN eines Claims entfällt (kein Ersatztext gesendet) - war nicht Teil des 15-Punkte-
  Feedbacks, ein sinnvoller Ersatz hätte eine serverseitige statt der bisherigen client-seitigen
  Übersetzungsauflösung gebraucht.
- **Bekannte, bewusst nicht behobene Alt-Reste:** `Claim#titleDurationTicks`/`welcomeDurationTicks`
  (Datenmodell) sowie der Chat-Befehl `/areaclaims entrymsg duration`/`welcomeduration` existieren
  weiterhin (Feld-Entfernung hätte in Snapshot/Persistenz/Netzwerk kaskadiert, ohne Verhaltensnutzen,
  da das neue Personal-System sie ohnehin komplett überschreibt) - der Chat-Befehl wirkt jetzt aber
  wirkungslos, was für einen Admin, der ihn benutzt, wie ein stiller Bug aussehen könnte. Folge-Task-
  Kandidat: Befehl entweder entfernen oder mit einem Hinweis auf die neuen persönlichen Einstellungen
  versehen.

Wie immer: nichts aus diesem Batch wurde in einem echten Spielfenster getestet - jeder Schritt wurde
ausschließlich über `./gradlew compileJava` verifiziert (inkl. eines abschließenden, vollständig
sauberen Durchlaufs nach allen Änderungen).

### Phase 7-Nachtrag 4 – Bestätigung + 6 weitere Punkte, inkl. großem Bild-Feature (erledigt, ungetestet)

Direkte Fortsetzung von Phase 7-Nachtrag 3: die Positions-/Dauerhaft-Architektur-Entscheidung aus
Nachtrag 3 wurde vom Nutzer bestätigt. Zusätzlich zwei dringende Nachbesserungen zu Nachtrag 3
selbst (Cascade-Bug erneut untersucht, Button-Beschriftung), dann sechs weitere Punkte, der letzte
(Punkt 6) ein großes, explorativ eingestuftes neues Feature.

**Dringende Nachbesserung 1 (Button-Beschriftung) - erledigt.** "Als SubClaim anlegen" ->
"SubClaim anlegen" (nur `de_de.json`, einzige Fundstelle).

**Dringende Nachbesserung 2 (Kaskadierung "funktioniert immer noch nicht") - ECHTER Root Cause
gefunden, diesmal per Datei-Abgleich der echten Testwelt-Daten (`run/saves/Testwelt/areaclaims_claims.json`),
nicht nur Code-Lesen.** Die gespeicherten Daten zeigten: der als "Mitglied" zum Unterbereich
hinzugefügte Spieler war exakt derselbe UUID wie der Claim-BESITZER (Solo-Test, nur ein Account
verfügbar) - `Claim#roleOf` liefert für den Besitzer immer COOWNER, `cascadeMembershipToParent`
erkennt das am Hauptbereich korrekt und trägt (richtigerweise) nichts ein, während `applyRole` auf
dem Unterbereich SELBST keinen solchen Schutz hatte und den Besitzer trotzdem redundant als
"MEMBER" eintrug - Asymmetrie, sah wie eine kaputte Kaskadierung aus. **Fix:** `ClaimEditService#applyRole`
lehnt jetzt IMMER ab, wenn Ziel-Spieler == Claim-Besitzer (neuer `Result.CANNOT_TARGET_OWNER`,
eigene Fehlermeldung in GUI-Paket UND Chat-Befehl) - Besitzer erscheinen dadurch konsistent NIRGENDS
mehr in `members()`, weder auf dem Unterbereich noch kaskadiert. Die Kaskadierungs-LOGIK selbst war
die ganze Zeit korrekt.

**Punkt 1 (Wildnis-Meldung zurück, admin-konfigurierbar) - erledigt.** Neue
`FeatureConfigManager`-Einstellungen `wildernessMessageEnabled`(Default an)/`wildernessMessageText`
(Default "Wildnis"), neue Zeilen im Server-Konfigurations-Screen, neue `SetServerConfigPacket`-
Aktionen `wildernessmsgenabled`/`wildernessmsgtext`. `ClaimEntryListener` sendet den MAIN-Slot beim
Verlassen jedes Claims jetzt wieder (falls aktiviert) - der frühere Nachtrag-3-Judgment-Call ("kein
sinnvoller Ersatz ohne serverseitige Übersetzungsauflösung") ist damit hinfällig, der Text kommt
jetzt direkt vom Admin als roher String.

**Punkt 2 (zeitgesteuert DANN dauerhaft, echte Lücke) - erledigt.** `EntryDisplaySlotPacket.Dto`
trägt jetzt IMMER die zeitgesteuerte Position/Dauer, PLUS `permanentAfter`/`permanentPosX`/
`permanentPosY` als zusätzliche, rein informative Felder. `EntryDisplayOverlayRenderer` hat jetzt
eine echte Zustandsmaschine (`Phase.TIMED` -> `Phase.PERMANENT`, KEIN gemeinsamer "entweder/oder"-
Zustand mehr wie vorher): jeder Slot zeigt beim Betreten IMMER zuerst normal/zeitgesteuert, wechselt
NUR bei aktivierter "Dauerhaft anzeigen"-Einstellung nach Ablauf der Dauer automatisch auf die
Dauerhaft-Position - komplett client-seitig anhand des einen Pakets, kein zweites Serverpaket zum
Übergangszeitpunkt.

**Punkt 3 (Text-Stil-Kontrolle) - erledigt.** Neue `PlayerDisplayPreferences.TextStyle` (Größe,
Fett/Kursiv/Unterstrichen unabhängig, Schriftart, Schatten, Umriss) - EIGENE Instanz je Text-Typ
(Haupt-/Unterbereich/Willkommensnachricht). Schriftart-Auswahl bewusst NUR die drei bekannten
eingebauten Vanilla-Schriften (`minecraft:default`/`uniform`/`alt`, verifiziert in den dekompilierten
`Minecraft.java`-Sourcen) - kein Versuch, Ressourcenpaket-Schriften zu erraten (Nutzer-Vorgabe).
Schatten nutzt `GuiGraphics#drawString`s natives `dropShadow`-Argument; Umriss ist KEIN natives
Vanilla-Feature (verifiziert) - manueller 8-fach-Mehrfachdurchlauf (1px-Versatz in jede Richtung,
schwarz, dann der echte Text obendrauf). **Judgment call (Architektur):** Stil wird NICHT über das
Server-Paket übertragen (das kennt nur Text/Farbe/Position/Zeit, KLAIM-Daten) - der Client liest den
Stil direkt aus seinen EIGENEN lokalen Einstellungen beim Zeichnen, da Stil eine rein persönliche
Einstellung ist, die der Server ohnehin nicht kennen muss - ändert sich sofort, auch während eine
Anzeige bereits läuft, ohne auf ein neues Serverpaket zu warten.

**Punkt 4 (Regenbogen-Farbwähler zu dunkel) - Root Cause gefunden, KEIN Fehler in `hsvToRgb`.**
Das eigentliche Problem: der dunkle Popup-Hintergrund-Kasten (`OVERLAY_BG_COLOR`, ~88% deckend) wurde
bisher NACH den Widgets gezeichnet (`renderXPopup()` lief nach `super.renderScaled()`, das die
Widgets inkl. `HueBarWidget` rendert) - der Kasten legte sich jeden Frame über die schon
gezeichneten, satten Farben und dunkelte sie sichtbar ab (am auffälligsten beim Regenbogen-Streifen,
aber strukturell JEDES Popup-Widget betreffend). **Fix:** neue `renderActivePopupBackground()`-
Methode zeichnet den Kasten JETZT VOR den Widgets (`renderBackground` -> Kasten -> `renderWidgets`
-> Titel/Labels), für alle 5 Popup-Typen (Name/Willkommen/Einstellungen/Freikauf-Bestätigung/
Preis-Bestätigung). Die Kasten-`fill()`-Aufrufe wurden aus den 5 einzelnen `renderXPopup()`-Methoden
entfernt (jetzt nur noch dort in der neuen zentralen Methode).

**Punkt 5 (Live-Positionsvorschau) - erledigt.** Neues kleines Vorschau-Feld oben rechts im
Anzeige-Einstellungen-Screen, zeigt Punkte für alle 5 konfigurierbaren Positionen (Haupt/Unter
normal+dauerhaft, Willkommensnachricht) gleichzeitig, aktualisiert sich JEDEN Frame direkt aus den
aktuell in den Feldern stehenden Werten (auch vor "Speichern"). **Judgment call:** "grob" wörtlich
genommen - nutzt eine feste angenommene Referenz-Auflösung (640x360 virtuelle GUI-Pixel) statt der
tatsächlichen Auflösung des Spielers beim eigentlichen Anzeigen (die kennt dieser Screen nicht),
für eine rohe Lageeinschätzung ausreichend.

**Punkt 6 (Bild statt Text, großes exploratives Feature) - erledigt, größter Teil dieser Runde.**
Claim-Besitzer können jetzt ein Bild statt des Textnamens für Haupt- ODER Unterbereiche zuweisen -
modelliert auf CopycatSigns bereits bewährter Upload-Pipeline (`ServerImageStore`/`ImageMetadata`/
`ImageFileCache`, `ChunkSender`/`SegmentManager`, `ImageUploader`/`ClientImageManager`/
`NativeImageDecoder`, TinyFileDialogs-Datei-Dialog - alle explizit vom Nutzer benannten Referenz-
Dateien wurden gelesen), aber als FRISCHE, EIGENSTÄNDIGE Parallel-Implementierung unter
`com.areaclaims.image`/`com.areaclaims.network` - AreaClaims hat weiterhin KEINE Abhängigkeit zu
CopycatSign.

- **Wichtiger Unterschied zu CopycatSign, von Anfang an eingebaut (Nutzer-Vorgabe):**
  `ServerImageStore#getAll()` - eine durchstöberbare Galerie aller bereits hochgeladenen Bilder, die
  es in CopycatSign selbst noch nicht gibt (bestätigt außerhalb von dessen Umfang).
- **`NativeImageDecoder`-Falle 1:1 übernommen:** `NativeImage.read(byte[])` überläuft LWJGLs
  64-KiB-MemoryStack bei größeren Bildern (reproduzierbarer Absturz, bereits einmal in CopycatSign
  gefunden/behoben) - hier von Anfang an mit demselben Fix (Off-Heap-`MemoryUtil`-Puffer statt
  Stack) übernommen, um denselben Fehler nicht erneut zu reproduzieren.
- **Feste Basis-Höhe (Nutzer-Vorgabe):** jedes hochgeladene Bild wird SERVERSEITIG beim Upload auf
  256px Höhe skaliert (Breite proportional, `Graphics2D`+bilineare Interpolation), bevor es
  gehasht/gespeichert wird - konsistente Basis-Anzeige für alle Betrachter.
- **Judgment call (Persistenz):** Metadaten als Gson-JSON-Datei im Weltordner (`areaclaims_images.json`)
  statt CopycatSigns `SavedData`/NBT-Ansatz - konsistent mit dem einzigen in DIESEM Mod etablierten
  Persistenz-Stil, kein neues Muster nur zum 1:1-Spiegeln eingeführt. Bild-Bytes weiterhin als Flach-
  Dateien (`areaclaims_images/<hash>.png`), wie bei CopycatSign.
  Funktioniert für Haupt- UND Unterbereiche (`Claim#imageHash`, neuer `SetClaimImagePacket`).
- **Drei EIGENSTÄNDIGE Bild-Umschalter** (Nutzer-Vorgabe wörtlich: "three SEPARATE toggles", nicht
  sechs) - `PlayerDisplayPreferences.NameDisplayPrefs#showImage` je Haupt-/Unterbereich, PLUS ein
  dritter, GLOBALER `permanentShowImage`-Umschalter nur für die Dauerhaft-Phase (gilt für beide
  Slots gemeinsam). Zoom-Faktor (`imageZoom`) ebenfalls je Haupt-/Unterbereich, rein persönlich.
- Neuer `AreaClaimsImagePickerScreen` (Datei-Upload via natives Betriebssystem-Dialog + Galerie-Liste
  mit Mini-Vorschaubildern + "Verwenden"/"Kein Bild"-Buttons), geöffnet über einen neuen "Bild
  wählen"-Button im Namens-Bearbeiten-Popup des Besitzers.
- `EntryDisplayOverlayRenderer` entscheidet PRO FRAME, ob Bild oder Text gezeigt wird (Bild nur wenn
  zugewiesen UND Betrachter-Umschalter an UND, falls gerade in der Dauerhaft-Phase, auch der dritte
  Umschalter an) - fällt automatisch auf Text zurück, solange die Textur noch lädt.
- **Bewusst NICHT umgesetzt (Zeitbudget, vom Nutzer als "genuinely large... fine if some corner
  needs to be a follow-up" vorab freigegeben):** kein Bild für die Willkommensnachricht (nur
  Haupt-/UnterbereichsNAME, wie explizit angefragt - "instead of the text NAME"), keine
  Bild-Löschung aus der Galerie/dem Datei-Cache (Bilder sammeln sich unbegrenzt an - ein Admin-
  Aufräum-Befehl wäre ein sinnvoller Folge-Task), keine serverseitige Prüfung, ob ein
  zugewiesener Hash tatsächlich noch existiert (zeigt einfach nichts, siehe Klassenkommentar
  `ClaimEditService#setClaimImage`), Galerie-Liste ungefiltert/unsortiert und auf 8 sichtbare
  Einträge gedeckelt (kein Scroll/Paginierung/Suche - bei vielen Bildern unpraktisch, aber
  funktional nicht blockierend).

Wie immer: nichts aus diesem Batch wurde in einem echten Spielfenster getestet - jeder Schritt wurde
ausschließlich über `./gradlew compileJava` verifiziert, inkl. eines abschließenden, vollständig
sauberen `clean compileJava` nach ALLEN Änderungen dieser Runde.

### Phase 7-Nachtrag 5 – Politur-Runde auf Anzeige-Einstellungen + Bild-Feature (erledigt, ungetestet)

Nutzer selbst: "Perfektionismus" - reine Nachschärfung bereits gebauter Funktionalität aus Nachtrag
4, keine neue Funktionalität außer der neuen Admin-Bild-Nutzungsübersicht (Punkt 8).

**Punkt 1 (ziehbare Positionsvorschau) - erledigt.** Die kleine Punkt-Vorschau (Nachtrag 4, Punkt 5)
ist jetzt interaktiv: `mouseClickedScaled` testet beim Klick, ob ein Punkt nah genug getroffen wurde
(`PREVIEW_DRAG_HIT_RADIUS`), `mouseDraggedScaled` schreibt die neue Position anschließend LIVE in
dieselben X/Y-Eingabefelder zurück, die auch per Tastatur editierbar bleiben - Zeichnen und
Trefftest nutzen dieselbe `collectPreviewDots()`-Liste, damit beides nicht auseinanderlaufen kann
(dasselbe wiederkehrende Muster-Risiko wie bei früheren Build/Render-Divergenzen in diesem Mod).

**Punkt 2 ("Große Vorschau") - erledigt.** Neuer Button unter dem kleinen Vorschau-Feld öffnet
`AreaClaimsDisplayPreviewScreen` - ein Vollbild-Mockup mit Platzhaltertext ("Beispiel-Hauptbereich"
usw., dieser Screen kennt keinen echten Claim) an der tatsächlich konfigurierten Position, mit dem
tatsächlich konfigurierten Stil. **Judgment call:** bewusst KEIN `FixedScaleScreen` (anders als
praktisch jeder andere Screen dieses Mods) - die Positionen sind Versätze im REALEN
Bildschirmkoordinatensystem (`Minecraft#getWindow()#getGuiScaledWidth/Height`, dasselbe System, das
`EntryDisplayOverlayRenderer` tatsächlich zur Laufzeit nutzt), eine virtuelle Auflösung hätte die
Vorschau verfälscht. Nutzt `EntryDisplayOverlayRenderer#drawStyledText` - jetzt eine eigenständige,
PUBLIC-static herausgezogene Methode, DENSELBEN Zeichenpfad wie die echte Anzeige, damit die
Vorschau garantiert nicht abweichen kann.

**Punkt 3 (Label kürzen) - erledigt.** "Stil (Größe/B/I/U/Schatten/Umriss/Schrift):" -> "Textdesign:"
(Nutzer-Vorschlag direkt übernommen).

**Punkt 4 (Bild-Umschalter-Restrukturierung) - erledigt, größte Datenmodell-Änderung dieser Runde.**
Ersetzt das Nachtrag-4-Design (`showImage` je Haupt-/Unterbereich + EIN globaler
`permanentShowImage`-Umschalter, "drei Umschalter insgesamt") durch die neue, vom Nutzer explizit
vorgegebene Zeilenfolge je Sektion: Position -> "Bild Anzeigen" (zeitgesteuert) + Zoom -> "Dauerhaft"
(eigene Zeile, vorher in denselben Button-Text wie ein Bild-Umschalter gequetscht) -> Dauerhaft-
Position -> "Bild Anzeigen" (dauerhaft). Datenmodell entsprechend umgebaut:
`NameDisplayPrefs#showImage` -> `showImageTimed`/`showImagePermanent` (JE Haupt-/Unterbereich EIGENES
Paar), der globale `PlayerDisplayPreferences#permanentShowImage` ersatzlos entfernt. Neuer
gemeinsamer `buildToggleRow()`-Baustein (kurzes Klartext-Label + separater "Ein/Aus"-Button) ersetzt
JEDEN vorher überladenen Einzel-Button-Text in diesem Screen (auch "Dauerhaft").

**Punkt 5 (Willkommensnachricht-Bild-Umschalter entfernen) - bereits erfüllt, keine Änderung nötig.**
Geprüft: `buildWelcomeSection()` hatte nie einen Bild-Umschalter (Willkommensnachrichten unterstützen
laut Nachtrag 4 ohnehin keine Bilder) - nichts zu entfernen.

**Punkt 6 (X/Y-Ausrichtung) - erledigt.** "Position X:"/"Dauerhaft-Position X:" -> "Position:"/
"Dauerhaft-Position:" + eigenständiges "X"-Label direkt vor dem Feld, das "X"-Label selbst beginnt
an derselben X-Koordinate wie das "Dauer"-Feld darüber (`DURATION_FIELD_X`) - neuer gemeinsamer
`buildPosRow()`-Baustein für ALLE Positions-Zeilen dieses Screens (Haupt/Unter × normal+dauerhaft,
Willkommensnachricht).

**Punkt 7 (Bild-Auswahl-Screen: großes Kachel-Raster + Scrollen) - erledigt.** "Verwenden" sitzt
jetzt UNTER dem jeweiligen Vorschaubild statt weit rechts daneben, Vorschaubilder deutlich größer
(64px, an Windows Explorers "Extra große Symbole" angelehnt, Nutzer-Vorgabe) in einem echten
Mehrspalten-Raster statt einer schmalen Liste. Scrollen nach demselben, bereits etablierten Muster
wie `AreaClaimsServerConfigScreen` (Phase 7-Nachtrag 3, Punkt 10) - geklemmter Offset + sichtbarer
Balken, bewusst weiterhin OHNE hartes Scissor-Clipping (siehe dortigen Klassenkommentar für die
unverändert gültige Begründung) - Kacheln, die komplett aus dem Sichtbereich gescrollt sind, bekommen
aber jetzt KEINEN Button mehr gebaut, um keine unsichtbaren-aber-klickbaren Reste zu hinterlassen.

**Punkt 8 (Admin-Bild-Nutzungsübersicht) - erledigt, NEUE Funktionalität in dieser sonst reinen
Politur-Runde.** Neuer OP4-Admin-Screen `AreaClaimsImageUsageScreen` (Button neben "Server-
Konfiguration") zeigt für JEDES hochgeladene Bild, welche Claims (server-weit) es nutzen -
ungenutzte Bilder werden farblich als "Aufräum-Kandidat" hervorgehoben. **Judgment call (Scope,
vom Nutzer als "your call" ausdrücklich freigegeben):** NUR Sichtbarkeit, KEINE Lösch-Funktion in
dieser Runde - tatsächliches Löschen bräuchte zusätzlich eine Bestätigung "wirklich niemand nutzt
das mehr" UND eine Behandlung des Wettlaufs "Admin hat den Screen offen, während ein Spieler das
Bild gerade neu zuweist", beides ohne Live-Test riskant genug für einen eigenen Folge-Task.

**Punkt 9 (Basis-Höhe 256 -> 128px) - erledigt.** Reine Konstanten-Änderung
(`ImageUploadPacket#BASELINE_HEIGHT`). Bereits VOR dieser Änderung hochgeladene Bilder bleiben bei
ihrer ursprünglichen (256px-)Größe gespeichert - keine rückwirkende Neuverarbeitung der Galerie
(out of scope, bestehende Bilder bleiben einfach wie sie sind).

**Punkt 10 (Zoom als Prozentsatz) - erledigt.** `NameDisplayPrefs#imageZoom` (Float-Multiplikator
ab 1.0) -> `imageZoomPercent` (Ganzzahl, geklemmt [1,500], Default 100) - Eingabefeld zeigt jetzt ein
separates "%"-Zeichen direkt daneben statt eines rohen Multiplikators.

Wie immer: nichts aus diesem Batch wurde in einem echten Spielfenster getestet - jeder Schritt wurde
ausschließlich über `./gradlew compileJava` verifiziert, inkl. eines abschließenden, vollständig
sauberen `clean compileJava` nach ALLEN Änderungen dieser Runde.

### Phase 7-Nachtrag 6 – Größenverifikation, Bild-Löschung, Zurück-Navigation, Vorschau-Nachbesserung + Textur-Filterung (erledigt, ungetestet)

**Punkt 1 (Größenverifikation "wirkt immer noch groß") - untersucht, KEIN Bug, Fehlalarm bestätigt.**
Statt den Code aus Nachtrag 5 nur erneut zu lesen, wurde ein eigenständiges, außerhalb des Mod-Builds
laufendes Verifikationsprogramm (`ResizeVerify.java`, im Scratch-Verzeichnis, nicht Teil des Repos)
geschrieben: kopiert `ImageUploadPacket`s Resize-Algorithmus wortwörtlich, erzeugt ein echtes
512x384-Test-PNG, führt es durch die Skalierung, schreibt das Ergebnis auf die Platte und parst dessen
IHDR-Chunk anschließend UNABHÄNGIG von Hand (rohe Byte-Offsets 16-23, ohne `ImageIO`/`BufferedImage`).
Ergebnis: PASS, 171x128 Ausgabe, Bytegröße schrumpft 2729->624 wie erwartet - der Resize-Pfad ist
nachweislich korrekt. Koordinator hat den ursprünglich gemeldeten Befund anschließend als Fehlalarm
zurückgezogen: der Nutzer hatte ein Bild betrachtet, das VOR der 128px-Umstellung (Nachtrag 5, Punkt 9)
hochgeladen wurde - alte Uploads behalten laut damaliger, bewusster Entscheidung ihre ursprüngliche
Größe (keine rückwirkende Neuverarbeitung). Frischer Re-Upload bestätigt die korrekte Funktion.

Bei der Untersuchung wurde jedoch ein ECHTER, separater Bug gefunden und behoben: `ClaimEntryListener`s
10-Tick-Hysterese löst `fireEntryDisplay` nur bei einem tatsächlichen Standort-WECHSEL aus - weist ein
Admin einem Claim ein neues Bild zu, während ein Spieler bereits DRIN steht, aktualisiert sich dessen
Anzeige nie von selbst. Neue statische Methode `ClaimEntryListener#invalidateAll()` leert `lastFired`
für alle Online-Spieler, wodurch die nächste periodische Prüfung für jeden neu auslöst. **Judgment
call:** bewusst GROB (alle Spieler, nicht nur die im betroffenen Claim) statt eines präzisen,
claim-gezielten Ansatzes - einfacher/sicherer, Kosten sind harmlose, unnötige Neuversände an
unbeteiligte Spieler. Wird von `DeleteImagePacket` (Punkt 2) mitverwendet.

**Punkt 2 (echte Lösch-Funktion in der Bild-Nutzungsübersicht) - erledigt.** Neues Client->Server-Paket
`DeleteImagePacket` (OP4-only): leert `imageHash` auf jedem Claim, der den gelöschten Hash referenziert,
ruft `ServerImageStore.delete(hash)`, `ClaimEntryListener.invalidateAll()` und sendet dem Admin per
`ImageUsageRequestPacket.sendTo(player)` seine eigene Ansicht aktualisiert zurück. UI in
`AreaClaimsImageUsageScreen` nutzt dasselbe zweistufige Bestätigungsmuster wie bereits
`AreaClaimsEditorScreen` (rotes "✗" -> erster Klick bewaffnet einen gelben "Sicher?"-Zustand für
`DELETE_CONFIRM_WINDOW_MS` = 4000ms, zweiter Klick innerhalb des Fensters bestätigt; ein `tick()`-
Override entwaffnet automatisch nach Ablauf).

**Punkt 3 ("Zurück"-Button neben "Schließen") - erledigt, "Sticky-Parent"-Muster eingeführt.** Für
Screens, die per Server-Anfrage/Antwort-Umlauf geöffnet werden (`ClientServerConfigCache`,
`ClientDisplayPrefsCache`, `ClientImageUsageCache`), ruft jetzt der tatsächliche AUSLÖSER (in
`AreaClaimsEditorScreen`) VOR dem Senden der Anfrage explizit eine neue `openFrom(Screen parent)`-
Methode am jeweiligen Cache auf; der Elternscreen wird in einem eigenen, vom Antwort-Handler
(`updateAndOpen`/`update`) GETRENNTEN statischen Feld gespeichert. **Grund für die Trennung:** diese
Antwort-Handler können innerhalb derselben Sitzung mehrfach neu feuern (z. B. nach dem Anwenden einer
Einstellung oder - neu in dieser Runde - nach jedem Bild-Löschvorgang) und dürfen dabei NICHT den
gerade offenen, gleich zu ersetzenden Screen selbst als neuen "Elternteil" überschreiben. Alle vier
betroffenen Screens (`AreaClaimsServerConfigScreen`, `AreaClaimsDisplayPrefsScreen`,
`AreaClaimsImageUsageScreen`, `AreaClaimsImagePickerScreen`) haben jetzt einen
`Screen parentScreen`-Konstruktorparameter und einen "Zurück"-Button links neben "Schließen"
(`areaclaims.editor.back`, neuer Lang-Key in beiden Sprachen). Für `AreaClaimsImagePickerScreen` wurde
die Schließen-Logik dafür in `onClose()` (voller Vanilla-Close-Pfad) und ein neues
`returnToParentOrClose()` (Texturbereinigung + Rückkehr zum Elternteil, oder Fallback auf
`super.onClose()` falls keiner gesetzt ist) aufgeteilt - "Verwenden", "Clear" und der Upload-Erfolgspfad
nutzen jetzt `returnToParentOrClose()`. Abschließender Grep über den gesamten Quellbaum bestätigt: KEINE
verbliebene parameterlose Konstruktion dieser vier Screens irgendwo im Code. **Judgment call:**
`AreaClaimsMemberScreen`s "Schließen" bewusst UNVERÄNDERT gelassen - der verhält sich dort bereits wie
ein "Zurück" (kehrt direkt zum Editor zurück), es gibt also nichts nachzuziehen. Wie vom Nutzer
vorhergesagt: eine kleine, mechanische Änderung, keine neue Navigations-Stack-Architektur nötig, da die
Navigation in diesem Mod tatsächlich linear/baumartig ist.

**Punkt 4a (Pixelierung in der "Großen Vorschau") - erledigt, Ursache gefunden.** Vanilla `Gui.java`s
eigene `renderTitle`-Methode wurde vollständig dekompiliert gelesen: nutzt exakt dieselbe Technik wie
bereits `EntryDisplayOverlayRenderer.drawStyledText` (Integer-Position-Translate + Pose-Stack-Scale,
`4.0F`/`2.0F` für Titel/Untertitel) - die Zeichentechnik selbst war also von Anfang an korrekt und
deckt sich exakt mit Vanilla. Der tatsächliche Bug: `TextStyle.scale` akzeptierte beliebige,
nicht-ganzzahlige Float-Werte, was bei der Bitmap-Glyphen-zu-Pixel-Abbildung zu ungleichmäßigen,
blockigen Artefakten führt. Fix: `scale = Math.max(1f, Math.round(style.scale))` direkt an der
Zeichenstelle in `drawStyledText` - der gespeicherte/im UI angezeigte Wert bleibt unverändert
(Judgment call: Rundung nur beim Rendern, nicht rückwirkend in den Eingabefeldern, damit der Nutzer
weiterhin z. B. "150%" eintragen kann, auch wenn intern auf den nächsten ganzzahligen Skalierungsfaktor
gerundet gezeichnet wird).

**Punkt 4b (Ziehen fehlt in der großen Vorschau) - erledigt.** `AreaClaimsDisplayPreviewScreen`
überschreibt jetzt `mouseClicked`/`mouseDragged`/`mouseReleased` nach demselben Muster wie die kleine
Vorschau (Nachtrag 5, Punkt 1) - Trefftest gegen die tatsächlich gerenderte Textbreite/-höhe (nicht nur
einen kleinen Punktradius, da hier echter Text in echter Größe dargestellt wird), schreibt beim Ziehen
live in dieselben `EditBox`-Felder des übergeordneten `AreaClaimsDisplayPrefsScreen` zurück. Dafür wurde
`LargePreviewEntry` um die Quell-`EditBox`-Paare (X/Y je Eintrag) erweitert und `collectLargePreviewEntries`
entsprechend angepasst; neue `centerXFor`/`centerYFor`-Hilfsmethoden am Elternscreen liefern denselben
Bildschirmmittelpunkt, den auch die Positions-Offsets selbst als Bezugspunkt nutzen.

**Zusatzpunkt (Textur-Filterung, "Pixel Suppe"-Bug) - erledigt, per System-Hinweis nachgereicht.**
Gemeldetes Symptom: ein scharfes, hochgeladenes Banner-PNG rendert im Spiel blockig/verpixelt.
Root-Cause via dekompilierter Quellen bestätigt: `AbstractTexture#setFilter(boolean blur, boolean
mipmap)` steuert `GL_LINEAR` (blur=true) vs. `GL_NEAREST` (blur=false) - `DynamicTexture` (von allen
Claim-Bild-Texturen genutzt) ruft diese Methode nie auf und erbt daher stillschweigend
`blur=false, mipmap=false` (hartes Nearest-Neighbor, keinerlei Glättung). Fix in
`ClientImageManager#registerTexture`: `texture.setFilter(!pixelArt, false)`, standardmäßig also glatt
gefiltert. Neuer, standardmäßig AUSgeschalteter Umschalter "Pixelkunst" (`NameDisplayPrefs#
pixelArtFiltering`, je Haupt-/Unterbereich eigener Wert) für Spieler, die den blockigen Look bewusst
wollen. **Judgment call (Zwei-Texturen-Cache):** da der GL-Filtermodus eine Eigenschaft des
Textur-OBJEKTS ist, nicht des Zeichenaufrufs, und verschiedene Betrachter für dasselbe Bild
unterschiedliche Präferenzen haben können, cached `ClientImageManager` jetzt pro Bild BEIDE Varianten
gleichzeitig unter einem zusammengesetzten Schlüssel (`hash + "|" + "smooth"/"pixel"`) statt einer
gemeinsam genutzten Textur - Mehrkosten (doppeltes Dekodieren bei Bedarf) akzeptiert, da das Feature
Opt-in und selten ist. **Lizenz-Hinweis (explizit vom Koordinator vorgegebene Einschränkung):** als
Referenz für die reine TECHNIK (nicht den Code) wurde erwähnt, dass Immersive Paintings
(GPL-3.0-only) ein ähnliches glatt/pixelig-Umschalten anbietet - deren Code wurde bewusst NICHT gelesen
oder übernommen, um keinen Copyleft-Lizenzkonflikt mit AreaClaims' MIT-Lizenz zu riskieren; die
Umsetzung stützt sich ausschließlich auf bereits unabhängig verifizierte Vanilla-/NeoForge-APIs
(`AbstractTexture#setFilter`).

Wie immer: nichts aus diesem Batch wurde in einem echten Spielfenster getestet - jeder Schritt wurde
über `./gradlew compileJava` verifiziert, inkl. eines abschließenden, vollständig sauberen
`clean compileJava` nach ALLEN Änderungen dieser Runde (Punkte 1-4 plus Textur-Filterung zusammen).

### Phase 7-Nachtrag 6, Folgepunkte – "Große Vorschau" verschwommen/geisterhaft + fünf eindeutige Platzhaltertexte (erledigt, ungetestet)

Nutzer-Screenshot der "Großen Vorschau" (Nachtrag 6, Punkt 4) zeigte sowohl einen verschwommenen
Welt-Hintergrund als auch doppelt/geisterhaft wirkenden Platzhaltertext. Nutzer-Vermutung: falsche
Render-Reihenfolge (Blur-Nacheffekt) oder ein kaputter Umriss-Mehrfach-Durchlauf. Beide Theorien
gezielt am tatsächlichen Code + dekompilierten Vanilla-Quellen geprüft - KEINE davon war die
Ursache, zwei GETRENNTE, andere Ursachen gefunden:

**Verschwommener Hintergrund - Ursache gefunden, behoben.** `render()` rief bis hierhin Vanillas
`Screen#renderBackground(...)` auf (dekompiliert verifiziert). Diese Methode blurt die Welt per
Post-Processing-Shader und legt danach eine dunkle, halbtransparente Kacheltextur darüber - das
Standard-Pausemenü-Aussehen. Die vom Nutzer vermutete Reihenfolge stimmte übrigens NICHT als
Erklärung: der Blur wird korrekt VOR dem eigenen Text gezeichnet, der Text selbst blieb also scharf.
Das eigentliche Problem: dieser Screen soll laut eigenem Klassenkommentar (Nachtrag 5, Punkt 2) eine
1:1-Vorschau der ECHTEN Betreten-Anzeige sein - und die hat im echten Spiel NIE einen
verschwommenen/abgedunkelten Hintergrund (`EntryDisplayOverlayRenderer` zeichnet rein additiv über
die scharfe, normal beleuchtete Welt). Fix: der `renderBackground()`-Aufruf entfällt ersatzlos - die
Welt läuft ohnehin unverändert weiter (`isPauseScreen()` gibt bereits `false` zurück) und bleibt
jetzt scharf und unabgedunkelt sichtbar, exakt wie im echten Spiel.

**Doppelter/geisterhafter Text - Ursache gefunden, KEIN Rendering-Bug.** Die Umriss-Theorie wurde
geprüft und verworfen: die Mehrfach-Durchlauf-Versätze (±1px) liegen bereits im skalierten
Koordinatensystem und erzeugen bei normaler Skalierung nur einen dünnen, korrekten Umriss - keine
"leicht versetzten Doppel-Kopien". Tatsächliche Ursache: `collectLargePreviewEntries` zeichnete für
Haupt- UND Unterbereich (sofern "Dauerhaft" aktiviert ist) die zeitgesteuerte UND die dauerhafte
Variante GLEICHZEITIG mit demselben, wiederverwendeten Platzhaltertext ("Beispiel-Hauptbereich" für
BEIDE) - bei den Standard-Positionen (`PlayerDisplayPreferences#defaultMain()`: `posY=-40` vs.
`permanentPosY=-60`, nur 20px auseinander) und der Standard-2x-Skalierung (~18-20px Texthöhe) liegen
beide Kopien fast deckungsgleich übereinander - daher der "Geister"-Eindruck. Kein Bug, zwei
tatsächlich unterschiedliche, absichtlich gleichzeitig sichtbare Einträge, die nur identisch
BESCHRIFTET waren.

**Fünf eindeutige Platzhaltertexte (zweiter Nutzer-Auftrag in dieser Nachlieferung) - erledigt,
behebt den "Geister"-Eindruck direkt mit.** Neue, je Slot eigene Lang-Keys statt der bisherigen
wiederverwendeten drei: `preview_sample_main` -> "Hauptbereich", NEU
`preview_sample_main_permanent` -> "Hauptbereich Dauerhaft", `preview_sample_sub` -> "Unterbereich",
NEU `preview_sample_sub_permanent` -> "Unterbereich Dauerhaft", `preview_sample_welcome` ->
"Willkommensnachricht" (alle ohne das bisherige "Beispiel-"-Präfix, exakt die vom Nutzer
vorgegebene Wortliste). `collectLargePreviewEntries` in `AreaClaimsDisplayPrefsScreen` entsprechend
angepasst. Die beiden sich weiterhin räumlich nah überlappenden Einträge (Punkt/Standardabstand
20px bleibt unverändert - out of scope für diese Nachlieferung) sind jetzt aber klar an
UNTERSCHIEDLICHEN Beschriftungen als zwei separate, absichtliche Einträge erkennbar statt wie ein
Rendering-Fehler auszusehen.

**Judgment call:** die 20px-Standardabstand zwischen zeitgesteuerter und dauerhafter Position wurde
NICHT vergrößert - das war nicht Teil des Auftrags (nur "verschwommen/geisterhaft" beheben plus
eindeutige Labels), und eine Abstandsänderung hätte bestehende, bereits vom Nutzer eingestellte
Positionen beeinflusst/wäre eine eigene Design-Entscheidung gewesen; mit unterschiedlicher
Beschriftung ist die Überlappung jetzt eindeutig als Design (zwei gleichzeitig sichtbare Phasen),
nicht als Fehler lesbar.

Wie immer: nichts aus diesem Batch wurde in einem echten Spielfenster getestet - verifiziert über
`./gradlew compileJava` (sauber, `EXIT_CODE=0`).

## Phase 7-Nachtrag 7 – Erster echter Produktions-Batch nach dem Live-Deploy (erledigt, ungetestet)

Der Nutzer hat live deployed - dieser Batch besteht ausschließlich aus ECHTEN, im laufenden
Betrieb gefundenen Bugs (kein Kosmetik-Polish mehr). Reihenfolge nach Priorität wie vom Koordinator
vorgegeben: 1/2/4 (Geld-Korrektheit + echter Exploit) und 3 (still datenverlust-artiger Bug) vor den
kosmetischen Punkten 5/6/7 - alle sieben plus ein spät nachgereichter achter Punkt wurden bearbeitet.

**Punkt 1 (CobbleDollars-Preisbug, 300 Blöcke à 1$ luden nur 30$ ab) - vom Koordinator selbst an der
Quelle behoben (`com.cobblecompanion.api.CobbleDollarsAccess`, ×10-Rohformat-Konvertierung), hier
NUR verifiziert.** `javap -c` gegen das frisch ausgelieferte `libs/CobbleCompanion-0.1.0.jar`
bestätigt die exakte Bytecode-Formel: `SCALE = BigInteger.TEN`; `toRaw(decimal) = decimal.multiply(
new BigDecimal(SCALE)).setScale(0, HALF_UP).toBigInteger()`; `toDecimal(raw) = raw / SCALE`. Zur
Verifikation wurde (gleiche Methodik wie die `ResizeVerify.java`-Standalone-Probe aus Nachtrag 6) ein
eigenständiges `PricingVerify.java` geschrieben, das `PriceCharger#chargeUnits` UND die dekompilierte
`toRaw`/`toDecimal`-Formel Ende-zu-Ende gegen genau die beiden Bug-Report-Beispiele durchrechnet:
**1$/Block × 300 Blöcke -> 300 abgebucht (PASS)**, **1$/10 Blöcke × 30 Blöcke -> 3 abgebucht
(PASS)** - zusätzlich reproduziert eine Referenzrechnung mit dem ALTEN (fehlerhaften) Pfad exakt den
gemeldeten "30 statt 300"-Fehler, was den Root-Cause zusätzlich bestätigt. `PriceCharger`/
`PriceConfig` selbst brauchten keine Änderung (Bridge-Vertrag unverändert, wie vom Koordinator
angekündigt).

**Punkt 2 (Wallet-Log-Anbindung) - erledigt.** `CobbleCompanionBridge#logCharge` existierte bereits
(vom Koordinator mitgeliefert) - hier an allen 4 tatsächlichen CobbleDollars-Bezahlstellen verdrahtet
(Claim anlegen/erweitern, SubClaim anlegen in `StakingService`, Regel-Freikauf in `BuyoutService`).
**Judgment call (Präzision statt Vermutung):** `PriceCharger#charge` gab bisher NUR `Result` zurück,
nicht den tatsächlich belasteten Dollar-Betrag - bei einer ODER-Preiskette hätte ein naives "loggen,
wenn `price.dollars()` gesetzt ist" fälschlich geloggt, selbst wenn der Spieler in Wahrheit mit Items
bezahlt hat. Neue `PriceCharger.ChargeOutcome`/`#chargeDetailed(...)` liefert zusätzlich
`dollarsCharged` (0, falls keine Dollar-Komponente TATSÄCHLICH belastet wurde) - nur dann wird
geloggt. Beschreibungstexte (z. B. "AreaClaims: Claim erstellt (300 Blöcke)", "AreaClaims: Regel
freigekauft (PVP)") sind bewusst literale deutsche Strings (kein Übersetzungsschlüssel) - das
Wallet-Log-Feld ist ein roher `String`, keine lokalisierte `Component`, dieselbe Einschränkung gilt
für JEDEN Aufrufer dieser API. **Judgment call:** Regelname im Log als roher Enum-Name (z. B. "PVP",
"CONTAINER_OPEN") statt einer hübsch formatierten Version - es gibt im Code keinen fertigen
"schöner Name ohne Component"-Baustein, einen eigens für diese eine Log-Zeile zu bauen wäre
Overengineering.

**Punkt 3 (zu viele Punkte -> stiller Abbruch beim Rechtsklick) - untersucht, generelle Absicherung
statt einer einzelnen geratenen Ursache.** Ausführliche Root-Cause-Recherche (siehe
`PolygonUtil#MAX_POINTS_PER_PART`-Klassenkommentar für die volle Herleitung): kein Paket im
eigentlichen Erstellungs-Pfad (`StakingService#end`/`showPriceConfirm`) trägt tatsächlich die rohe
Punktliste (`PriceConfirmSnapshot` enthält nur die Blockzahl, `ClaimSyncPacket` keine Geometrie) -
ABER `ShowcaseGeometrySyncPacket` (Grenzen-Anzeige) trägt sie sehr wohl als JSON-String über Vanillas
`ByteBufCodecs#STRING_UTF8` (dekompiliert verifiziert: Standard-Obergrenze 32767 Zeichen), UND die
eigene O(n²)-Kantenprüfung (`PolygonUtil#isSelfIntersecting`) war laut eigenem Klassenkommentar nie
für mehr als "typischerweise &lt;&lt; 100 Punkte" ausgelegt, aber NIE durchgesetzt. Ohne einen
einzelnen "Rauchenden Colt" gefunden zu haben, wurde die Punktzahl selbst statt einer einzelnen
Vermutung großzügig, aber verbindlich gedeckelt: neue Konstante `PolygonUtil#MAX_POINTS_PER_PART = 500`
- durchgesetzt SOWOHL sofort beim Linksklick selbst (`ToolInteractionListener`, klare
Chat-Rückmeldung statt eines stillen Nicht-Hinzufügens) ALS AUCH nochmal bei der Commit-Validierung
(neuer `CreateResult.TOO_MANY_POINTS`/`AddPartResult.TOO_MANY_POINTS` in `ClaimManager`, Verteidigung
in der Tiefe für z. B. `StakingService#resumePending`). ZUSÄTZLICH (Koordinator-Vorschlag "Limit
anheben, falls willkürlich niedrig") bekommen `ShowcaseGeometrySyncPacket` UND `ClaimSyncPacket`
jeweils eine eigene, deutlich großzügigere, aber weiterhin ENDLICHE Paket-Obergrenze (500.000 Zeichen
statt Vanillas knappem 32767er-Standard) als Verteidigung in der Tiefe, unabhängig von der neuen
Punktzahl-Grenze. Neue Lang-Keys `areaclaims.tool.too_many_points`/
`areaclaims.command.claim.too_many_points_selection` in beiden Sprachen. **Bestehende, bereits vor
diesem Fix gespeicherte Claims mit mehr als 500 Punkten sind NICHT betroffen** (Grenze gilt nur bei
NEUER Punktzahl-Validierung, keine rückwirkende Prüfung beim Laden).

**Punkt 4 (Exploit-Fund: Regel-Umschalter vor Freikauf) - erledigt, server- UND clientseitig
geschlossen.** Root-Cause bestätigt: `ClaimEditService#applyRule` prüfte VORHER überhaupt nicht, ob
für die Regel ein noch unbezahlter Freikauf-Preis konfiguriert ist - der grüne-Haken/rotes-X-Button
im Editor blieb trotz des rechts danebenstehenden "Kaufen"-Buttons uneingeschränkt anklickbar, UND
(wichtiger) ein direkt gesendetes `SetRulePacket` (unter Umgehung der GUI) hätte ohnehin funktioniert,
da die eigentliche Durchsetzung fehlte. Fix: `applyRule` prüft jetzt zuerst `claim.boughtOutRules()`
gegen den konfigurierten `PriceConfigManager#ruleBuyoutPrice(rule)` - neuer
`ClaimEditService.Result.RULE_NOT_BOUGHT_OUT`, in BEIDEN Aufrufwegen behandelt (`/areaclaims rule`-
Befehl UND `SetRulePacket`, dieselbe "einzige Quelle der Wahrheit"-Methode wie immer in dieser
Klasse - siehe deren Klassenkommentar). Zusätzlich clientseitig: `GlyphButton` bekommt einen neuen
"gesperrt"-Render-Zustand (`active`-Feld ausgewertet, Farbe halbiert, kein Hover-Effekt), der
Regel-Umschalter in `AreaClaimsEditorScreen#buildRulePanel` setzt `active = !locked` (locked =
`rule.buyoutPrice != null`, dasselbe Feld, das rechts schon den "Kaufen"-Button auslöst) - rein für
sofortiges visuelles Feedback, die eigentliche Sicherheit kommt aus der Server-Prüfung (ein
client-seitig umgangenes Paket hätte OHNE die `applyRule`-Änderung weiterhin funktioniert).

**Punkt 5 (GUI-Skalierung 3: 3 linke Buttons im Preisbestätigung-Popup leer) - erledigt, derselbe
bereits bekannte Bug-Musterfund wie Phase 6-Nachtrag Punkt 1.** War ein fester Viertel-der-
Popup-Breite-Wert (53px) - passte zufällig nur für "Beenden"(7 Zeichen), NICHT für "Bestätigen"(10)/
"Abbrechen"(9)/"Auswahl merken"(14 inkl. Leerzeichen). Zu schmaler Text löst Vanillas Scrolling-Text-
Rendering aus, das in einem `FixedScaleScreen` bei jeder GUI-Größe außer 2 wegen falsch umgerechneter
Scissor-Koordinaten komplett LEER rendert. Fix nach demselben, etablierten `measuredButtonWidth(...)`-
Muster (funktioniert automatisch für jede Sprache - Englisch "Remember Selection" ist sogar noch
länger als "Auswahl merken"). Neue `priceConfirmButtonWidth()`/`priceConfirmWidth()`-Methoden, LETZTERE
MUSS an allen DREI Stellen identisch verwendet werden, die die Popup-Breite kennen mussten
(`buildPriceConfirmOverlay` fürs Layout, `renderPriceConfirmOverlay` fürs Zentrieren der Texte,
`renderActivePopupBackground` fürs Hintergrund-Rechteck) - vorher hatten alle drei denselben
Wert 260 unabhängig voneinander hartcodiert, nur EINEN davon zu ändern hätte den Hintergrund-Kasten/
die Textzentrierung vom neuen Button-Layout auseinanderlaufen lassen.

**Punkt 6 ("SubClaim anlegen"-Button in Englisch komplett leer) - untersucht, Koordinator-Hypothese
widerlegt, tatsächliche Ursache identisch zu Punkt 5.** Per Skript (PowerShell-JSON-Diff) verglichen:
der Lang-Key `areaclaims.editor.create_subclaim` ist in BEIDEN Sprachdateien vorhanden und nicht leer
- KEIN fehlender/vertauschter Schlüssel, anders als vom Koordinator vermutet. Tatsächliche Ursache:
derselbe Scrolling-Text-Bug wie Punkt 5 - der Button war fest auf `COL2_WIDTH` (100px) verdrahtet,
worauf Deutsch "SubClaim anlegen" gerade noch passt, das längere/großbuchstabenreichere Englisch
"Create as Sub-Claim" aber nicht mehr. Fix: neue `createSubclaimButtonWidth()` nach demselben
`measuredButtonWidth(...)`-Muster, NIE schmaler als die bisherige Spaltenbreite (visuell unverändert
für Deutsch), aber breit genug für jede tatsächlich gerenderte Sprache.

**Punkt 7 ("Große Vorschau" weiterhin verpixelt/verschwommen trotz Nachtrag-6-Fix) - erledigt,
ZWEITER, tieferliegender Ursachenfund.** Der Nachtrag-6-Fix hatte den EXPLIZITEN
`this.renderBackground(...)`-Aufruf am ANFANG von `render()` entfernt - aber am ENDE stand
weiterhin `super.render(...)`. Dekompiliert verifiziert (`Screen#render`, Vanilla-
Standardimplementierung): DIE ruft INTERN selbst NOCHMAL `this.renderBackground(...)` auf, bevor sie
die registrierten Widgets zeichnet. Der Blur/die Abdunklung liefen also die GANZE ZEIT weiter - nur
jetzt NACH statt VOR dem eigenen Text (der `super.render()`-Aufruf steht in dieser Klasse ganz am
Ende) - GENAU die vom Nutzer selbst vermutete falsche Reihenfolge, nur eine Ebene tiefer versteckt
als beim ersten Fund. Fix (Nutzer hatte explizit freigegeben, den Effekt einfach ersatzlos zu
entfernen statt die Interaktion weiter zu debuggen): `super.render(...)` entfällt, stattdessen wird
die "nur Widgets zeichnen"-Hälfte von Vanillas Standardimplementierung manuell nachgebildet (Schleife
über `this.renderables`, direkt aus den dekompilierten Quellen übernommen) - OHNE den Hintergrund-
Aufruf. Kein Blur/keine Abdunklung mehr an irgendeiner Stelle in diesem Screen.

**Spät nachgereichter Punkt (Blockzahl ragt in die SubClaim-Spalte hinein) - erledigt.** Die
kombinierte Zeile "Besitzername • X Blöcke²" unter jedem Hauptbereich-Button (`renderMainColumnInfo`)
wurde bei längeren Besitzernamen/größeren Flächen breiter als `COL1_WIDTH` (112px) und lief sichtbar
in Spalte 2s eigene "X Blöcke²"-Zeile der Unterbereiche hinein (`renderSubColumnInfo`, ungeclipptes
`drawString`). Fix: aus der EINEN kombinierten Zeile wurden ZWEI gestapelte Zeilen (Besitzername
oben, Blockzahl darunter, 10px Zeilenabstand) - jede Zeile für sich bleibt deutlich schmaler als
vorher die kombinierte Zeile. `MAIN_ROW_INFO_HEIGHT` dafür von 10 auf 20 verdoppelt (der
Erweitern-Button darunter rutscht dadurch automatisch mit, da er relativ zu dieser Konstante
positioniert wird). Lang-Key `areaclaims.editor.claim_info` von "%s • %s Blöcke²" auf reines "%s"
vereinfacht (nur noch Besitzername), die Blockzahl-Zeile nutzt den bereits bestehenden
`areaclaims.editor.claim_area`-Schlüssel ("%s Blöcke²") wieder, denselben, den auch die
Unterbereichs-Spalte nutzt.

Wie immer: nichts aus diesem Batch wurde in einem echten Spielfenster getestet - jeder Schritt wurde
über `./gradlew compileJava` verifiziert, inkl. eines abschließenden, vollständig sauberen
`clean compileJava` nach ALLEN Änderungen dieser Runde (Punkte 1-7 plus den spät nachgereichten
Blockzahl-Layout-Punkt zusammen). Gegeben dass der Nutzer kurz vor einem erneuten Live-Test steht:
JEDE Änderung dieser Runde ist ausschließlich statisch/über Code-Lesen + Dekompilierung verifiziert,
keine davon wurde tatsächlich im Spiel angeklickt.
