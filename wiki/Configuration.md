# Configuration

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
