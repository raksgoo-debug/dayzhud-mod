# dayzhud 2.7.0 - loadout restyle, a real interactivity bug fix, taller guns

**6 changed files.** Unzip over the repo root, on top of 2.6.1.

## Fixed: multi-cell items became permanently unclickable once placed

The real bug behind "once i place them in the inv, i cant interact with it anymore." Sorry -
this was a mistake in the CI fix from the last drop, and it's a good example of exactly the
kind of thing local checking here can't catch, so worth explaining precisely.

`Slot.isActive()` doesn't just control rendering, the way last drop's fix assumed - it also
gates vanilla's own mouse hit-testing. A slot reporting `isActive() == false` isn't just drawn
as empty, it's **skipped entirely** when the game decides what you're hovering or clicking.
Once a multi-cell item was placed (making its slot report inactive, which is what suppressed
vanilla's small icon underneath), that same slot became invisible to the mouse - you could no
longer click it at all, to pick it up or anything else.

Fixed by not touching `isActive()` for this at all. The actual, much simpler realization: this
mod's own icon-drawing pass already runs *after* vanilla's normal render, every frame - so
just letting vanilla draw its ordinary small icon underneath, and painting a bigger one on top
of it afterward (same origin, strictly larger, so it fully covers the smaller one), gets the
same visual result with no need to suppress anything or touch interactivity at all. `GridSlot`
(last drop's addition) is gone entirely; both grid regions are back to plain `Slot`s, exactly
as they were before any of this multi-cell work started. Multi-cell items should now pick up
and place normally, including by clicking anywhere on their footprint (not just their own
top-left cell) - the anchor-redirect logic for that was already written last drop; it just
couldn't ever run, for the same reason.

## Rifles and SMGs are now 2 tall

`gunTypeFootprints` defaults changed: `rifle=4x1` -> `rifle=4x2`, `smg=3x1` -> `smg=3x2`.
Shotgun/sniper/MG/launcher unchanged.

**Existing `config/dayzhud-grid.toml` won't pick this up** - same trap as every default-value
change in this project's config files. Edit those two lines yourself, or delete the file so
Forge regenerates it with the new defaults.

## Loadout slots restyled and moved

PRIMARY/SECONDARY/HOLSTER/SHEATH are now a 2x2 block of bigger, individually-sized boxes -
primary/secondary wide (with a bound-key badge, "1"/"2"), holster/sheath narrower - each
labelled above rather than below, matching the reference image. Positioned where GEAR used to
start, right under the paperdoll; GEAR and CRAFTING both moved down to make room. OFFHAND
moved up into the paperdoll's side column, under the mask/back curio slots, since the row it
used to share with the loadout boxes doesn't exist in that spot anymore.

The window grew to fit: 322 -> 376 (corpse view too, 362 -> 376, since it has to be at least
as tall as the player's own left panel now needs regardless of the corpse's own content).

**Left deliberately alone:** the CRAFTING grid. The reference image doesn't show one, but
"removed 2x2 crafting" read to me as describing how that mockup was put together, not
necessarily a request to remove crafting from the real mod - removing an actual feature on a
guess felt like the wrong side to err on. Say so explicitly if you do want it gone; it's a
small removal once confirmed.

## Known rough edges, not fixed here

- **None of this was visually tested.** Box sizes, padding, badge position (top-left corner),
  and the new window height are all reasoned from the reference image and the existing
  layout's numbers, not seen in game. Expect to want to nudge something.
- **The right-hand column (INVENTORY/HOTBAR/BACKPACK) is much shorter than the new left
  column**, so there's a visible gap under it now. Only the left side needed the height.
- Hovering the edge of an occupied loadout box - inside the bigger visual box, but outside the
  real 16x16 clickable centre - shows no tooltip at all (neither vanilla's, since the mouse
  isn't really over the slot, nor this mod's own, which only covers the empty case). Same shape
  of gap as the shadow-cell tooltip rough edge already noted for the grid feature.

## Verified

Every changed file passes this project's usual filtered `javac -Xmaxerrs` check - real errors
only, missing-Minecraft/Forge symbols are expected noise with no MDK in this workspace. Checked
by hand again, not just grep, specifically for the interactivity fix and the new layout code -
nothing in the filtered error list names any symbol this changed.

**Nothing here has run in game.** First test should specifically try: placing a rifle or SMG
and confirming it's now 2 cells tall and reads right at that shape; placing any multi-cell
item and picking it up again afterward (the actual bug fix - try clicking a cell that isn't
its top-left too); dragging a pistol into PRIMARY (should still bounce) and into HOLSTER
(should still work); and just opening the inventory to see how the new loadout cluster and the
taller window actually look before nudging any of the numbers above.
