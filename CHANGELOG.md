# Changelog

## 0.2.0
- New: the "Adjust" button (next to "Expand") lets you fine-tune a claim's boundary block by
  block — each click toggles one block in/out, with a live color preview. Added blocks cost the
  normal price, removed blocks are refunded at an admin-configured rate (separate rate for main
  claims and sub-claims).
- Change: the "Mob Spawning" rule is now called "Hostile Mobs" — it only blocks hostile mob spawns
  (no longer harmless animals) and automatically teleports hostile mobs that end up inside a
  protected claim back outside.
- Fix: claim parts that touch or overlap now correctly count as ONE part toward the parts limit,
  instead of two separate ones.
- Fix: the particle boundary and JourneyMap overlay now show touching claim parts as one connected
  area instead of two separate ones.
- JourneyMap integration reworked to a client-only plugin/bridge, matching JourneyMap 6.x's API.

## 0.1.0
- Initial release.
- Free-form polygon land claims with main claims and nested sub-claims, multi-part claims.
- Member roles (Member/Staff/Co-Owner) with per-rule minimum-role bypass.
- Deny-rules: build/mine, container access, PvP, mob spawning, redstone, vehicles, item drop,
  fluid placement, farmland trampling, leashing, optional Cobblemon capture prevention.
- Customizable entry title/subtitle and welcome message, per-viewer display preferences
  (position, duration/permanent, color, font style, scale), custom claim images.
- Particle and block-tint boundary display.
- Optional JourneyMap integration — claim boundaries shown as live map overlays when JourneyMap
  is installed.
- Optional pricing (per-block claim price, per-rule buyout price) payable in items and/or
  CobbleDollars (if CobbleCompanion's CobbleDollars bridge is installed).
- Full in-game GUI editor for claims, members, rules, pricing, and display settings, plus an
  admin server-config screen and OP4 admin claim browser.
