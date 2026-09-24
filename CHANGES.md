# dayzhud 2.12.3 - every gun sized to its real shape; wider loadout boxes

**4 changed/new files** (plus version bump). Unzip over the repo root, on top of 2.12.2.
No config edits needed this time.

## Per-gun footprints from each model's real proportions

Footprints were one size per gun *type*, but proportions vary a lot within a type - an M700
is nearly 7:1, a Vector 1.8:1 - so some guns looked squeezed or lost in their box. Each of
the 54 guns in TACZ's default pack now has its own footprint, sized from its actual model.

Why not measure live in game, as the renderer does: footprints decide what fits where, and
that is decided on the server, which never loads gun models (a dedicated server has none).
So sizes have to be data both sides share - a built-in table, `DefaultGunFootprints`.

How the numbers were made, from the TACZ jar you sent: each gun's default-configuration model
measured side-on over every vertex (the same measure the in-game renderer fits by), at one
shared scale - 9 model units per cell - so guns sit at a consistent real-world size relative
to each other, like a Tarkov grid. Then your rules: non-pistols at least 2 tall, pistols at
least 2 wide, nothing over 9x3.

Examples: Glock 2x1, Lonetrail 3x1, MP5 / Uzi / Vector 3x2, M4A1 / AK-47 5x2, HK416D / SCAR
4x2, SPAS-12 / FAL 6x2, AWP / M95 7x2, M107 7x3, minigun 6x3. The full check sheet is
attached - every gun rendered inside its new footprint at real grid pixel size, fitted by
the same rule the game uses.

Lookup order: a `gunIdFootprints` config entry beats the built-in table, which beats the
per-type defaults - so any gun can still be overridden, and guns from other packs keep their
type's size. Your existing config doesn't need touching.

Guns already sitting close together in an inventory may overlap at their new sizes; they draw
as 1x1 until dragged apart once.

Two measuring mistakes caught and fixed while building the table, noted so the numbers can be
trusted: a hide rule for hand bones also matched "Handguard", and an earlier pass hid
"muzzle_default" (a real muzzle device) - both made guns measure short. The final table uses
an audited hide list that removes only non-default variants (extended mags, alternative
"oem_" stocks) and non-geometry bones.

## Loadout boxes: yes, every gun fits - and now they're bigger

Every gun always fits, because the model is scaled down to fit the box, never cut off. The
real problem was size: at 60 px wide, a long sniper came out barely a quarter of the box
tall. PRIMARY/SECONDARY are now 100 px wide and HOLSTER/SHEATH 44 px, still inside the
section panel. The comparison sheet shows current vs new for the worst cases.

## Known: a couple of models have detached parts

The minigun (ammo feed hanging well below) and a few others have small separate pieces away
from the main body. They count toward the measured size, so those guns draw a little smaller
than their neighbours. That's the model, not the fit rule; override via `gunIdFootprints` if
one bothers you.
