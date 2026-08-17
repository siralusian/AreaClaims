**[🏠 Wiki-Startseite](Home)** — **[🇬🇧 English version](English)**

# AreaClaims – Wiki (Deutsch)

Diese Seite erklärt die tägliche Nutzung von AreaClaims sowie die vollständige Befehls- und
Konfigurationsreferenz. Für einen schnellen Funktionsüberblick siehe das
[README](https://github.com/siralusian/AreaClaims#readme) — diese Seite geht tiefer ins Detail.

## Inhalt

- [Erste Schritte](#erste-schritte)
- [Alle Befehle](#alle-befehle)
- [Server-Konfiguration](#server-konfiguration)
- [JourneyMap-Integration](#journeymap-integration)

## Erste Schritte

1. Gib einem Spieler die Berechtigung, Claims zu erstellen (standardmäßig OP-Stufe 4 —
   Server-Betreiber können das pro Funktion über den Admin-Konfigurations-Screen absenken,
   `/areaclaims config feature <feature> <0-4>`).
2. Der Spieler klickt im Editor (`/areaclaims open`) auf **"Neuer Claim"** bzw. den
   entsprechenden GUI-Button, markiert dann per Linksklick mit einem beliebigen gehaltenen Item
   Polygon-Punkte und schließt per Rechtsklick (oder Klick auf eine leere Fläche) die Auswahl ab.
3. Mitglieder, Regeln, Betreten-Nachrichten und Preise werden im selben Editor konfiguriert.

## Alle Befehle

Basisbefehl `/areaclaims`, Alias `/ac`. Für die meisten alltäglichen Aktionen gibt es ein
GUI-Äquivalent (`/areaclaims open`) — die Befehle sind für Skripte/Konsole gedacht und nutzen
exakt dieselbe Validierungslogik wie das GUI.

| Befehl | Beschreibung |
|---|---|
| `/areaclaims open` | Öffnet den Claim-Editor mit deinen eigenen Claims (und allen, in denen du Mitglied bist). |
| `/areaclaims cancel` | Bricht eine laufende Claim-/Unterbereichs-/Erweitern-Punktauswahl ab. |
| `/areaclaims delete <claim>` | Löscht einen Claim (inklusive seiner Unterbereiche). |
| `/areaclaims show <claim>` | Schaltet die Grenzanzeige (Partikel/Einfärbung) für einen Claim um. |
| `/areaclaims showmode <particle\|tint\|off>` | Legt deinen bevorzugten Grenzanzeige-Modus fest. |
| `/areaclaims role <claim> <player> <role>` | Setzt die Rolle eines Spielers (`NONE`/`MEMBER`/`STAFF`/`COOWNER`) auf einem Claim. |
| `/areaclaims rule <claim> <rule> enable <minRole>` | Aktiviert eine Verbieten-Regel, umgehbar ab der angegebenen Rolle. |
| `/areaclaims rule <claim> <rule> disable` | Deaktiviert eine Verbieten-Regel komplett. |
| `/areaclaims buyout <claim> <rule>` | Kauft eine Regel dauerhaft zum konfigurierten Preis frei (falls gesetzt). |
| `/areaclaims rename <claim> <newName>` | Benennt einen Claim um. |
| `/areaclaims entrymsg <claim> ...` | Konfiguriert Titel-Farbe, Willkommensnachricht/-farbe, Grenzfarbe und Farbkopplung. |
| `/areaclaims admin` | OP4: öffnet den Admin-Claim-Browser (jeden Claim des Servers einsehen/bearbeiten). |
| `/areaclaims config ...` | OP4: serverweite Konfiguration (siehe unten). |

`entrymsg`-Unterargumente: `color <hex>` · `welcome <text>` · `welcomecolor <hex>` ·
`welcomeduration <ticks>` · `boundarycolor <hex>` · `linkboundarycolor <true|false>`.

Hinweis: Position, zeitgesteuert/dauerhaft und Textstil werden von jedem *Betrachter* selbst über
den Anzeige-Einstellungen-Screen konfiguriert, nicht vom Claim-Besitzer über diesen Befehl.

## Server-Konfiguration

Alle Server-Betreiber-Einstellungen sind ingame (OP-Stufe 4) über `/areaclaims config` oder den
Admin-Server-Konfigurations-Screen (erreichbar über `/areaclaims admin`) einstellbar — es gibt
keine Konfigurationsdatei zum manuellen Bearbeiten.

| Einstellung | Befehl | Standard |
|---|---|---|
| Freischaltungsschwelle (Claim erstellen / erweitern / Regel-Freikauf) | `/areaclaims config feature <feature> <0-4>` | 4 (nur OP4) |
| Max. Teile pro Claim | `/areaclaims config maxparts <n>` | 8 |
| Reichweite der Block-Einfärbung-Anzeige (Chunks) | `/areaclaims config tintrange <chunks>` | 2 |
| Preis pro Block beim Beanspruchen/Erweitern, Freikaufpreis pro Regel | nur Admin-GUI | keiner |
| Preis-Teiler ("X pro N Blöcke") | nur Admin-GUI | 1 |
| Wildnis-Austritts-Meldung | `/areaclaims config show` Umschalter + Text | an |
| JourneyMap-Integration an/aus | Umschalter im Admin-Server-Konfigurations-Screen | an (falls JourneyMap installiert) |

OP-Stufe 4 hat unabhängig von diesen Schwellen immer vollen Zugriff — die Einstellungen steuern
nur, was *andere* Spieler dürfen.

## JourneyMap-Integration

Falls [JourneyMap](https://modrinth.com/mod/journeymap) auf dem Server installiert ist, wird die
Grenze jedes Haupt- und Unterbereichs automatisch als eingefärbtes Polygon-Overlay für alle
Online-Spieler angezeigt, in der jeweils eingestellten Grenzfarbe des Claims. Für AreaClaims
selbst ist keine JourneyMap-Installation auf dem Client nötig — Spieler ohne JourneyMap sehen das
Overlay einfach nicht. Admins können das serverweit über den Server-Konfigurations-Screen
abschalten, ohne JourneyMap deinstallieren zu müssen.
