# dayzhud 2.10.0 - flat weapon icons, crafting removed, hotbar relocated

**7 changed/new files.** Unzip over the repo root, on top of 2.9.0.

## Flat weapon icons in the loadout slots

Your reference image, sliced into three icons (rifle, pistol, knife) and alpha-extracted
from luminance - the same technique already used for this mod's skill icons, since the
source was the same shape: a faint outline on a near-black background, so alpha comes from
brightness and colour comes entirely from the tint applied at draw time.

These replace two different things at once:

- The crude flat-fill ghost shapes for empty PRIMARY/SECONDARY/HOLSTER/SHEATH slots.
- The risky 3D-tilt render for occupied slots (`renderTiltedItem`, the thing 2.8.2 already
  defaulted off out of suspicion). Loadout boxes no longer touch TACZ's model renderer or
  the rotation math at all - just a flat texture, tinted dim for empty and bright for
  equipped, exactly the same `blit` + `RenderSystem.setShaderColor` pattern this file
  already uses elsewhere (the stat icons, the corpse portrait), copied rather than guessed.

**The trade-off, stated plainly:** an equipped slot now shows the generic shape for its
category (any non-pistol gun in PRIMARY/SECONDARY looks like the rifle icon, regardless of
whether it's actually an SMG or a launcher), not the specific gun. That's what the supplied
art actually is - one shape per category, not per gun - and it's a firm trade for guaranteed,
tested-looking icons over a render that might come out sheared or (per 2.8.2) nearly
invisible. The grid (inventory/backpack/container/corpse) is untouched by this - it still
uses the real item render, tilt included, since that request was specifically about "the
weapon slot."

**Preview attached separately**, composed from the actual processed icons at the loadout
boxes' real pixel dimensions and real colours (SLOT_BG, SLOT_BORDER, the ghost and equipped
tints), upscaled 4x for legibility - not a game screenshot, but the closest thing to one
without a running game: real assets, real geometry, real colours, nothing invented.

## Crafting grid removed; hotbar moved into its place

The 2x2 crafting grid, its result slot, the arrow between them, and all the server-side
recipe-matching code behind it are gone - fully removed, not just hidden, per your explicit
"remove the 2x2 crafting" (last drop I'd left it in place on a guess; this time it's a
confirmed removal). The standalone crafting-TABLE button (top right of the INVENTORY header,
opens a full 3x3) is untouched - that's a separate feature from the embedded 2x2 grid.

HOTBAR now sits where CRAFTING used to be, in the left column under GEAR - freeing the gap
it used to sit in between INVENTORY and BACKPACK in the right column, which is the actual
"decrowding" this was asked for. The hotbar's own game logic didn't move at all - it's still
backed by the exact same inventory slots (indices 0-8, keys 1-9 still select the same
things), only where it's drawn on screen changed.

## Also fixed while in this code: three stale background boxes from 2.7.0

While repositioning things, found that several of the panel's recessed background boxes
(`SECTION_BG` fills in `renderBg`) were never updated when the loadout/gear/crafting layout
was overhauled two drops ago - they were drawing at their pre-2.7.0 pixel coordinates, no
longer matching where GEAR, the loadout cluster, or crafting/hotbar actually sit. All of them
recomputed against the current layout in this pass. This was never reported - found by
reading the code closely while already in the area, not from a symptom - so it's possible it
wasn't very noticeable, but it's a real, independent fix either way.

## Verified

Diffed the full deduplicated error-message list against the pre-crafting-removal baseline:
identical except for two error types that *disappeared* (the crafting-related imports no
longer exist to fail resolving), and nothing new appeared. That's the expected shape for a
clean removal.

**Nothing here has run in game.** The one thing most worth checking first: the loadout icons
at their actual in-game pixel size (the preview is upscaled 4x, and small pixel art can read
differently at native size than enlarged), and that pressing 1-9 still selects the right
hotbar item now that it's drawn somewhere else on screen.
