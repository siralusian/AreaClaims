# Commands

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
| `/areaclaims entrymsg <claim> ...` | Configures entry title color, welcome message/color, boundary color, and color-linking (see below). |
| `/areaclaims admin` | OP4: opens the admin claim browser (view/edit any claim on the server). |
| `/areaclaims config ...` | OP4: server-wide configuration (see [Configuration](Configuration)). |

## `entrymsg` sub-arguments

`color <hex>` · `welcome <text>` · `welcomecolor <hex>` · `welcomeduration <ticks>` ·
`boundarycolor <hex>` · `linkboundarycolor <true|false>`

Note: per-viewer display position, duration/permanent toggle, and text styling are configured by
each *viewer* individually via the display preferences screen, not by the claim owner through
this command — see the README's Features section.
