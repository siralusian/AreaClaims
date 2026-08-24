# AreaClaims

[🇩🇪 Deutsche Version weiter unten](#deutsch)

## English

AreaClaims lets players and server owners define block-precise, freely-shaped **polygon** land
claims — not just simple rectangles. A main claim can contain any number of independent
sub-claims, each with its own name, entry title, member roles, and deny-rules. Standalone NeoForge
mod, works in any NeoForge modpack.

### Features

- **Free-form polygon claims** — mark points with any held item (left-click adds a point,
  right-click finishes), not restricted to a rectangle. Claims can consist of multiple disjoint
  parts under one name.
- **Main claims + sub-claims** — a main claim (e.g. a city) can contain nested sub-claims
  (e.g. districts/plots), each independently configurable.
- **Block-by-block boundary adjustment** — the "Adjust" button (next to "Expand") lets you toggle
  individual blocks in/out of an existing claim part with a live color preview; added blocks cost
  the normal price, removed blocks are refunded at an admin-configured rate.
- **Member roles** — Member / Staff / Co-Owner, each deny-rule individually gated by a minimum
  role that bypasses it.
- **Deny-rules**, each toggleable per claim: building/mining, container access, PvP, hostile mob
  spawning (hostile mobs that end up inside anyway get auto-teleported back out), redstone
  interaction, vehicle use/placement, item dropping, fluid placement, farmland trampling, leashing
  passive mobs, and (if [Cobblemon](https://modrinth.com/mod/cobblemon) is installed) preventing
  Pokémon capture inside the claim.
- **Entry title/subtitle** like a place sign when crossing a border, plus an optional welcome
  message — fully customizable per viewer (position, duration or permanent display, color, font
  style, scale) via a dedicated preferences screen, not just the claim owner's choice.
- **Custom claim images** — upload/pick an image to show instead of plain text for a claim's
  name.
- **Boundary display** — particle outline or a full-volume colored block-tint overlay
  (admin-configurable range), each independently toggleable per viewer.
- **Optional JourneyMap integration** — if [JourneyMap](https://modrinth.com/mod/journeymap) is
  installed on the server, every claim's boundary is automatically shown as a live map overlay
  to all players. Not required — AreaClaims works fully without it, and the overlay is skipped
  entirely if JourneyMap isn't present.
- **Pricing** — optional per-block claim price and per-rule buyout price, each payable in any
  item(s) and/or (if [CobbleCompanion](https://github.com/siralusian/CobbleCompanion)'s
  CobbleDollars bridge is installed) CobbleDollars — combinable with AND/OR. Never a hard
  CobbleDollars requirement.
- **Full in-game GUI editor** for claims, members, rules, pricing, entry-message styling and
  images — chat commands remain available for scripting/console use, but nothing requires them.
- **Admin tools** — OP-level-4 admins can browse/edit every claim on the server, configure
  feature-unlock OP-level thresholds, prices, and the maximum number of claim parts, all through
  a dedicated server-config screen.

### Building

No third-party mod dependencies required to run — this mod is fully standalone. Optional soft
dependencies (CobbleCompanion, Cobblemon, JourneyMap) are detected at runtime and simply enable
extra features when present; nothing is required in `libs/` unless you're compiling against them
yourself.

### Other CobbleCompanion-family projects

AreaClaims isn't part of the CobbleCompanion family, but it's made by the same author:

- [CobbleCompanion](https://github.com/siralusian/CobbleCompanion) and its extensions/bundles
- [MobTweaks](https://github.com/siralusian/MobTweaks) — granular per-ability mob/NPC behavior
  control, loot and spawn overrides.
- [InvSpy](https://github.com/siralusian/InvSpy) — lets admins open online/offline players'
  inventory and enderchest like a chest.
- [CreativeMenu](https://github.com/siralusian/CreativeMenu) — makes the Creative inventory menu
  freely customizable.
- [CopycatSign](https://github.com/siralusian/CopycatSign) — a Copycat block that displays a
  custom picture.
- [Create: Let's Do Automation](https://github.com/siralusian/CreateLetsDo) — automates Let's Do:
  Farm & Charm blocks with Create.

---

## Deutsch

AreaClaims erlaubt Spielern und Server-Betreibern, blockgenaue, **frei geformte Polygon**-
Gebietsansprüche zu definieren — nicht nur einfache Rechtecke. Ein Hauptbereich kann beliebig
viele unabhängige Unterbereiche enthalten, jeder mit eigenem Namen, Betreten-Titel,
Mitgliederrollen und Verbieten-Regeln. Eigenständige NeoForge-Mod, funktioniert in jedem
NeoForge-Modpack.

### Funktionen

- **Frei geformte Polygon-Claims** — Punkte mit jedem gehaltenen Item markieren
  (Linksklick fügt einen Punkt hinzu, Rechtsklick schließt ab), nicht auf ein Rechteck
  beschränkt. Ein Claim kann aus mehreren getrennten Teilen unter einem Namen bestehen.
- **Hauptbereiche + Unterbereiche** — ein Hauptbereich (z. B. eine Stadt) kann verschachtelte
  Unterbereiche (z. B. Grundstücke) enthalten, jeder einzeln konfigurierbar.
- **Block-genaue Grenzanpassung** — der "Anpassen"-Button (neben "Erweitern") lässt einzelne
  Blöcke eines bestehenden Claim-Teils per Klick ein-/ausschalten, mit Live-Farbvorschau;
  hinzugefügte Blöcke kosten den normalen Preis, entfernte werden zu einem vom Admin festgelegten
  Satz zurückerstattet.
- **Mitgliederrollen** — Mitglied / Mitarbeiter / Mitbesitzer, jede Verbieten-Regel einzeln mit
  einer Mindestrolle koppelbar, die sie umgeht.
- **Verbieten-Regeln**, jede einzeln pro Claim umschaltbar: Bauen/Abbauen, Truhenzugriff, PvP,
  feindliche Mobs (Feinde, die trotzdem hineingelangen, werden automatisch zurück nach draußen
  teleportiert), Redstone-Interaktion, Fahrzeugnutzung/-platzierung, Item-Drop, Flüssigkeiten
  platzieren, Ackerland zertrampeln, Anleinen zahmer Tiere, sowie (falls
  [Cobblemon](https://modrinth.com/mod/cobblemon) installiert ist) Pokémon-Fang im Claim
  verhindern.
- **Betreten-Titel/Untertitel** im Ortsschild-Stil beim Grenzübertritt, plus optionale
  Willkommensnachricht — pro Betrachter vollständig anpassbar (Position, zeitgesteuert oder
  dauerhaft, Farbe, Schriftstil, Skalierung) über einen eigenen Einstellungs-Screen, nicht nur
  vom Claim-Besitzer festgelegt.
- **Eigene Claim-Bilder** — ein Bild hochladen/auswählen, das statt reinen Textes für den
  Claim-Namen angezeigt wird.
- **Grenzanzeige** — Partikel-Umriss oder eine vollflächige, eingefärbte Block-Overlay-Anzeige
  (Admin-konfigurierbare Reichweite), beides unabhängig pro Betrachter umschaltbar.
- **Optionale JourneyMap-Integration** — falls [JourneyMap](https://modrinth.com/mod/journeymap)
  auf dem Server installiert ist, wird jede Claim-Grenze automatisch als Karten-Overlay für alle
  Spieler angezeigt. Nicht erforderlich — AreaClaims funktioniert vollständig auch ohne, das
  Overlay entfällt dann einfach komplett.
- **Preise** — optionaler Preis pro Block beim Beanspruchen sowie ein Freikaufpreis pro Regel,
  jeweils zahlbar in beliebigen Item(s) und/oder (falls
  [CobbleCompanions](https://github.com/siralusian/CobbleCompanion) CobbleDollars-Brücke
  installiert ist) CobbleDollars — UND/ODER-verknüpfbar. Nie eine zwingende
  CobbleDollars-Voraussetzung.
- **Vollständiger Ingame-GUI-Editor** für Claims, Mitglieder, Regeln, Preise,
  Betreten-Nachrichten-Gestaltung und Bilder — Chat-Befehle bleiben für Skripte/Konsole
  verfügbar, sind aber für nichts zwingend nötig.
- **Admin-Werkzeuge** — OP-Stufe-4-Admins können jeden Claim des Servers einsehen/bearbeiten
  sowie Freischaltungsschwellen, Preise und die maximale Teile-Anzahl pro Claim über einen
  eigenen Server-Konfigurations-Screen einstellen.

### Bauen

Keine Fremd-Mod-Abhängigkeiten zum Ausführen nötig — diese Mod ist komplett eigenständig.
Optionale weiche Abhängigkeiten (CobbleCompanion, Cobblemon, JourneyMap) werden zur Laufzeit
erkannt und schalten bei Vorhandensein einfach zusätzliche Funktionen frei; in `libs/` wird
nichts benötigt, außer man kompiliert selbst dagegen.
