# dayzhud 2.8.0 - grid everywhere, and guns tilted flat

**8 changed/new files.** Unzip over the repo root, on top of 2.7.0.

## Multi-cell items now work in the worn backpack, and everywhere on a corpse

Previously only the player's own INVENTORY grid and an opened chest. Now also: the worn
BACKPACK, and (looting a corpse) its inventory, hotbar, and loot bag - all six regions, same
mechanism.

The backpack and corpse bag needed a real change, not just wiring: both are backed by another
mod's `IItemHandlerModifiable` (a bag item's own inventory), not a `Container`, and
`ItemGrid` only knew how to talk to a `Container`. It now works through a small new
`GridStorage` interface instead - `get(index)`/`set(index, stack)` - with an adapter for each,
so the placement/reservation logic itself didn't need to change at all, only what it's
allowed to talk to.

**Search-masking is no longer a reason to disable grid mechanics.** The last drop stood grid
placement down entirely on a container currently wrapped for search, reasoning that the two
systems "weren't tried together." Actually working through it: the only real interaction is
that the CLIENT's own copy of a not-yet-searched cell reads as empty (search hides the item
itself, not just its icon), so a placement touching one gets client-predicted optimistically
and then corrected by the server's next authoritative sync - the same ordinary correction
vanilla multiplayer containers already do constantly, not a new failure mode. Excluding
corpses specifically would have made this whole expansion nearly pointless, since a corpse is
normally exactly what's mid-search when a multi-cell item would matter.

One real, avoidable side effect of that got fixed rather than just accepted: a multi-cell
item's invisible shadow-cell markers are real, non-empty stacks, and the search sweep was
about to start counting each one as "another thing to search" - extra phantom delay per big
item for a reveal that would never visibly resolve into anything. `SearchProgress` and the
corpse-bag-occupied check both now treat a shadow marker as empty for search-pacing purposes,
same as a genuinely empty slot.

## Guns tilted toward lying flat

New: `grid.flatItemAngleX` in `dayzhud-grid.toml` (default 55 degrees). Applied as an extra
rotation on top of however TACZ normally renders a gun's GUI icon, tipping it further toward
a top-down view - both in the grid and in the loadout boxes, since both have the same "3D
model looks sheared when stretched into a wide cell" problem.

**This is a first guess, not a measured value.** Nobody has looked at an actual gun in an
actual grid cell in this game yet. 0 turns it off entirely; nudge toward 90 for more top-down.
Existing `dayzhud-grid.toml` won't pick up the new default either way - it's a new key, so
Forge will add it with its default on next load even to an existing file (unlike changing an
existing key's default, this one key is safe either way).

## What I can't verify here, in order of how much I'd double-check first

1. **The rotation itself** (`com.mojang.math.Axis.XP.rotationDegrees`) - the actual PoseStack
   rotation API for this Minecraft version. I'm reasonably confident this is right for 1.20.1,
   but local checking here has no real Forge jar to confirm an API shape against, which is
   exactly how the `renderSlot` mistake two drops ago happened. If guns render invisible,
   wildly distorted, or the game crashes opening the inventory, this line is the first thing
   to look at.
2. **Whether a reservation marker written into a corpse's worn bag ever outlives that
   corpse.** If a player takes the whole bag as a physical item (not just its contents) while
   it's holding a leftover marker, that marker would sit invisibly inside it until the next
   time ANY dayzhud grid-aware screen opens that same bag - reconcile() always fully recomputes
   from scratch, so it self-heals the moment that happens, but there's a window where an
   invisible, nameless junk item could show up in some completely unrelated inventory viewer
   (JEI, a different mod's bag UI) if someone happened to look at exactly the right moment.
3. Everything already flagged as unverified in the 2.6.0-2.7.0 changelogs still applies and
   hasn't changed: icon stretch/shear, box sizing, the new window height, none of it seen in
   game.

## Verified

Every changed file passes the usual filtered `javac -Xmaxerrs` check. This time I went further
than grepping the touched files: I pulled the FULL deduplicated list of every distinct error
*message* across the whole tree (not just which files they're in) and confirmed every one is
either an expected missing-package cascade or the same pre-existing ambiguous-overload
artifact already seen with `ScrollingBackpackView`'s constructor (caused by a Forge interface
that can't resolve locally, not a real conflict - `Container`-typed arguments to the same
`GridStorage.of` calls, right next to the flagged ones, resolve fine). No "incompatible
types", no "missing return", no "unreported exception" - the classes of error that WOULD mean
a real bug - anywhere in the output.

**Nothing here has run in game**, same as every drop before it. This one especially needs a
real look before trusting it: open a corpse mid-search with a rifle on it, watch a shadow cell
stop costing search time, place something in the worn backpack, and actually see what angle
55 degrees looks like.
