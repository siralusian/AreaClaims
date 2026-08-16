# AreaClaims Wiki

Block-precise, freely-shaped **polygon** land claims for NeoForge 1.21.1 servers — no Cobblemon
dependency, works in any modpack.

See the [README](https://github.com/siralusian/AreaClaims#readme) for the full feature overview.
This wiki covers day-to-day usage.

## Getting started

1. Give a player permission to create claims (OP level 4 by default — server owners can lower
   this per-feature via the admin config screen, `/areaclaims config feature <feature> <0-4>`).
2. The player clicks **"Neuer Claim" / "New Claim"** in the editor (`/areaclaims open`) or the
   equivalent GUI button, then left-clicks with any held item to place polygon points and
   right-clicks (or clicks an empty area) to finish the selection.
3. Configure members, rules, entry messages, and pricing from the same editor.

## Pages

- [Commands](Commands) — full command reference.
- [Configuration](Configuration) — server-owner settings (feature-unlock levels, pricing, claim
  part limit, JourneyMap integration).

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.233+.
- No required dependencies. Optional: CobbleCompanion (CobbleDollars pricing), Cobblemon
  (prevent-capture rule), JourneyMap (claim boundaries on the map).
