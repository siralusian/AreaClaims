**[🏠 Wiki home](Home)** — **[🇩🇪 Deutsche Version](Deutsch)**

# AreaClaims – Wiki (English)

This page walks through day-to-day usage of AreaClaims, plus the full command and configuration
reference. For a quick feature overview see the
[README](https://github.com/siralusian/AreaClaims#readme) — this page goes into more depth.

## Contents

- [Getting started](#getting-started)
- [All commands](#all-commands)
- [Server configuration](#server-configuration)
- [JourneyMap integration](#journeymap-integration)

## Getting started

1. Give a player permission to create claims (OP level 4 by default — server owners can lower
   this per-feature via the admin config screen, `/areaclaims config feature <feature> <0-4>`).
2. The player clicks **"Neuer Claim" / "New Claim"** in the editor (`/areaclaims open`) or the
   equivalent GUI button, then left-clicks with any held item to place polygon points and
   right-clicks (or clicks an empty area) to finish the selection.
3. Configure members, rules, entry messages, and pricing from the same editor.

## All commands

Base command `/areaclaims`, alias `/ac`. Most day-to-day actions have a GUI equivalent
(`/areaclaims open`) — commands are provided for scripting/console use and share the exact same
validation logic as the GUI.

| Command | Description |
|---|---|
| `/areaclaims open` | Opens the claim editor GUI showing your own claims (and any you're a member of). |
| `/areaclaims cancel` | Cancels an in-progress claim/sub-claim/expand point selection. |
| `/areaclaims delete <claim>` | Deletes a claim (and its sub-claims). |
| `/areaclaims show <claim>` | Toggles the boundary showcase (particles/tint) for a claim. |
| `/areaclaims showmode <particle\|tint\|off>` | Sets your preferred boundary display mode. |
| `/areaclaims role <claim> <player> <role>` | Sets a player's role (`NONE`/`MEMBER`/`STAFF`/`COOWNER`) on a claim. |
| `/areaclaims rule <claim> <rule> enable <minRole>` | Enables a deny-rule, bypassable from the given role upward. |
| `/areaclaims rule <claim> <rule> disable` | Disables a deny-rule entirely. |
| `/areaclaims buyout <claim> <rule>` | Permanently buys out a rule's configured price (if set). |
| `/areaclaims rename <claim> <newName>` | Renames a claim. |
| `/areaclaims entrymsg <claim> ...` | Configures entry title color, welcome message/color, boundary color, and color-linking. |
| `/areaclaims admin` | OP4: opens the admin claim browser (view/edit any claim on the server). |
| `/areaclaims config ...` | OP4: server-wide configuration (see below). |

`entrymsg` sub-arguments: `color <hex>` · `welcome <text>` · `welcomecolor <hex>` ·
`welcomeduration <ticks>` · `boundarycolor <hex>` · `linkboundarycolor <true|false>`.

Note: per-viewer display position, duration/permanent toggle, and text styling are configured by
each *viewer* individually via the display preferences screen, not by the claim owner through
this command.

## Server configuration

All server-owner settings are configurable in-game (OP level 4) via `/areaclaims config` or the
admin server-config GUI screen (reachable from `/areaclaims admin`) — there is no config file to
hand-edit.

| Setting | Command | Default |
|---|---|---|
| Feature unlock level (create claim / expand claim / rule buyout) | `/areaclaims config feature <feature> <0-4>` | 4 (OP4 only) |
| Max. parts per claim | `/areaclaims config maxparts <n>` | 8 |
| Block-tint boundary display range (chunks) | `/areaclaims config tintrange <chunks>` | 2 |
| Per-block claim/expand price, per-rule buyout price | admin GUI only | none |
| Price divisor ("X per N blocks") | admin GUI only | 1 |
| Wilderness exit message | `/areaclaims config show` toggle + text | on |
| JourneyMap integration on/off | admin server-config GUI toggle | on (if JourneyMap installed) |

OP level 4 always has full access regardless of these thresholds — the settings only control
what *other* players can do.

## JourneyMap integration

If [JourneyMap](https://modrinth.com/mod/journeymap) is installed on the server, every main and
sub-claim's boundary is automatically shown as a colored polygon overlay to all online players,
using the claim's own boundary color. No JourneyMap installation is required on the client for
AreaClaims itself to work — players without JourneyMap simply won't see the overlay. Admins can
turn this off server-wide from the server-config screen without needing to uninstall JourneyMap.
