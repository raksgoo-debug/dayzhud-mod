# dayzhud 2.6.1 - multi-cell items (drag, rotate, true placement) - CI fix

**15 changed/new files** (same set as before - two of them are what actually changed for
this fix; the rest are unchanged from 2.6.0, included again so this zip fully replaces it).
Unzip over the repo root, on top of 2.5.0 - or straight on top of 2.6.0 if you already
unzipped that one; either way you end up in the same place. 2.6.0 didn't compile; see
"CI fix" below.

## What this is

Guns and other big items can now take up more than one grid cell, and placement works the
way the reference gif did: drag to any free spot, press R to rotate, and it reserves that
exact footprint. Applies to the player's own INVENTORY grid and to an opened chest's grid.

**Not covered by this pass:** the worn BACKPACK and a corpse's loot bag (both are backed by
another mod's flat slot list or a scrolling window - see "Why backpack/corpse loot aren't in
this pass" below), the hotbar, and the loadout/armor/curio slots. Those all stay exactly 1x1.

## How big things are

Config-driven, in `config/dayzhud-grid.toml`:

- TACZ guns default by **type** - pistol 2x1, SMG/shotgun 3x1/4x1, rifle 4x1, sniper/MG 5x1,
  launcher 4x2 (width x height, width is horizontal). Override any specific gun by its own id.
- Anything else (backpacks, meds, resources) is 1x1 unless you add it to `itemFootprints`.
- `enabled = false` puts everything back to 1x1, as before.
- Lists are read once and cached - an edit needs a restart, same trap as `maskEmptySlots`.

## How placement actually works

No new placement packet - this reuses the same click routing vanilla containers already use
(so client-side prediction and the server's authoritative check are the same code path,
`TarkovInventoryMenu#clicked`), which is the standard, tested way this kind of override is
done. Rotation is the one thing that genuinely isn't a slot click, so R does send its own tiny
packet.

**The interesting problem was making a "reserved but empty" cell mean anything to code this
mod doesn't control** - vanilla auto-pickup, a hopper feeding a chest, anything else that
just checks "is this slot empty?" before writing. A UI-level "don't allow clicking here" (the
kind WeaponSlots.mayPlace does) only stops clicks through this screen; it does nothing about
any of those. So a covered cell isn't left empty - it holds a real, invisible marker item
(`dayzhud:grid_reserved`, blank name and texture) tagged with which cell it belongs to.
Anything checking `isEmpty()` - which is the standard contract for inserting into a container -
correctly sees it as occupied and leaves it alone.

Those markers are never hand-maintained. `ItemGrid.reconcile()` recomputes every shadow cell
in a region from scratch, every server tick (`broadcastChanges`, which already ran every tick
for other reasons in this menu). That makes the whole thing self-healing: however a region
got into whatever state it's in - a placement, a pickup, a hopper, something this didn't
anticipate - the next tick's reconcile pass derives the correct shadows from whatever's
actually sitting there now, rather than every possible change needing to remember to clean up
after itself.

## Interaction rules, briefly

- **Pick up**: click anywhere on a multi-cell item (any of its cells) with an empty cursor.
- **Place**: click an empty cell while carrying a multi-cell item. A green/red outline while
  hovering shows whether it fits before you commit to the click.
- **Rotate**: R while carrying a multi-cell item.
- **Shift-click (quick move) refuses** on a multi-cell item - drag it instead. Vanilla's
  quick-move logic finds the first empty *index*; it has no idea a footprint needs several
  free cells in a specific rectangle, so letting it run on a big item would silently corrupt
  the grid rather than fail loudly.
- Everything about a normal 1x1 item, anywhere - inside or outside a grid region - is
  untouched vanilla behaviour.

## Why backpack/corpse loot aren't in this pass

Both are backed by storage this mod doesn't own: a worn backpack's slots come from whichever
compatible bag mod is equipped, and a corpse's loot bag is a scrolling window over a
similarly foreign handler. The marker-item trick this relies on needs the SAME container
object to still be there and still be checked by whatever else touches it, which is true for
the player's own vanilla Inventory and for a plain chest, but isn't a safe assumption for
someone else's bag implementation. Worth a second pass once this core proves out in game.

Grid mechanics also stand down on a container that's currently wrapped for search
(`SearchedContainer`) - the two systems weren't tried together, and I'd rather ship "plain
1x1 while both features are active on the same chest" than a guess at how they interact.

## Known rough edges, not fixed here

- **Icon stretch.** Big icons render by scaling the item's normal icon to fill the whole
  footprint rectangle, matching the reference look. TACZ guns render in 3D even in the GUI,
  and a non-uniform stretch on a 3D model can look mildly sheared at a wide aspect ratio -
  this was reasoned through, not seen in game. If it looks wrong, the fix is either a
  purpose-made flat icon per gun (a config option pointing at a texture) or a uniform,
  centred scale instead of a fill - flagging both as options rather than picking one blind.
- **R isn't rebindable.** Hardcoded, matching the fixed key shown in the reference. Making it
  a real KeyMapping (shows up in Controls, remappable) is a small follow-up if wanted.
- **Hovering the exact pixel of a covered cell shows a blank tooltip** instead of the big
  item's tooltip, since that pixel really is a different (reservation) slot underneath. The
  big icon's OWN cell still shows the real tooltip normally; this only affects cells it's
  visually spilling into.
- No "ghost" preview of the item itself following the cursor at its rotated size while
  dragging - only the destination outline shows the footprint. The carried icon stays normal
  vanilla size.

## CI fix (2026-09-23)

The first drop of this failed CI: `AbstractContainerScreen.renderSlot` turned out to be
**private**, not protected, so overriding it doesn't compile - a mistake local checking here
can't catch, since there's no real Forge jar in this workspace to check an override against,
only whichever symbols happen to resolve.

Fixed without that override at all. `Slot.isActive()` - public, virtual, and already relied
on elsewhere in this same screen to hide a backpack slot that doesn't exist - is the actual
sanctioned hook for "vanilla draws nothing here." `GridSlot` (new; replaces the plain `Slot`
both grid regions used to construct) reports `isActive() == false` for exactly a reservation
marker or a real multi-cell item, which suppresses vanilla's normal icon/decoration/highlight
draw with no override needed; the big icon is now drawn by `TarkovInventoryScreen.drawGridIcons`,
called from the same custom render pass the loadout-slot decorations already use.

One thing this surfaced that's worth recording: `isActive()` was already load-bearing
elsewhere in this file (the backpack-slot background loop skips drawing a box for an inactive
slot, and the search-cover loop uses it too), and both of those needed a second look once a
SECOND meaning got attached to the same flag. The background-box loop now special-cases
`GridSlot` so a big item still gets its per-cell boxes. The search-cover interaction needed
an actual fix, not just a note: `GridSlot` takes a `gridEligible` flag, false for a
container's slots when that container is wrapped for search, so a rifle sitting in an
unsearched chest slot still reports active and still gets masked normally - grid mechanics
were already meant to stand down on a searched container (see "Why backpack/corpse loot
aren't in this pass" above), but the first pass only enforced that in the placement logic,
not in this new rendering hook.

## Verified

This build fixes the compile error above. Every new/changed file passes this project's usual filtered `javac -Xmaxerrs` check - real
errors only, missing-Minecraft/Forge symbols are expected noise with no MDK in this workspace
(see `ragdollgore-local-verification-limits.md`). Went through the error list by hand this
time rather than just grepping for the file names, specifically to rule out a genuine typo in
one of the new symbols (`ItemGrid`, `Footprint`, `gridFits`, etc.) hiding behind the noise -
every unresolved symbol in the new/changed files is a legitimate vanilla or Forge type, never
one of ours.

**None of the actual grid logic has been run** - no Minecraft here to run it in. First
in-game test should specifically try: placing a 4-wide rifle across a row, rotating it into a
1x4 column, picking it up by clicking its far end rather than its origin, shift-clicking it
(should refuse), placing two items so their footprints would overlap (should bounce, outline
should show red), removing an item that was blocking another item's spot and confirming the
second item's shadow cells reappear on the very next action, and a hopper aimed at a chest
that has a big item in it.
