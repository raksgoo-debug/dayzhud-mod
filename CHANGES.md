# dayzhud 2.12.5 - carrying, placing, rotating guns; window fits the screen

**4 changed files** (plus version bump). Unzip over the repo root, on top of 2.12.4.

## 1. Picked-up guns showed the small 1-slot icon - fixed

Vanilla draws the item on your cursor as its normal inventory icon (for a TACZ gun, the
small diagonal sprite), and that draw is private, so it can't be replaced directly. For a gun
that can be drawn flat, the carried item is now hidden for the length of vanilla's render
pass only (restored in a `finally`, before tooltips or anything else read it) and drawn
flat at its full footprint size instead. Its top-left cell sits under the cursor - the same
cell the green/red placement outline shows and a click anchors to.

## 2. Guns could be placed where they didn't fit - fixed (four separate holes)

Only a plain click was checked for fit. Everything else went straight to vanilla:

- **Drag-placing** - the actual "sometimes". If the mouse moves even a pixel between press
  and release while carrying something, vanilla treats it as a drag that spreads the stack
  across the slots passed over, and drops the gun into the first one. Now refused for
  multi-cell items on the server side, and on your side a press over the grid while carrying
  a gun is turned into a normal placement click immediately, so it just works.
- **Number keys** (1-9 over a grid cell) swapped a hotbar gun in unchecked. Now it must fit.
- **Shift-click** dropped a gun into the first empty index. Now it searches for the first spot
  where the whole footprint fits (turning it if that's what fits) - so shift-click works for
  guns now instead of being refused.
- **Small bags.** A backpack or corpse bag shows a fixed window of rows, but the bag behind
  it can have fewer slots. Those missing cells read as empty and silently drop anything
  written to them - so a gun could be placed hanging off the end of a small bag. Missing cells
  now count as occupied for both the fit check and the footprint reservation.

## 3. Rotating shrank the gun instead of turning it - fixed

R rotated the footprint but the gun stayed horizontal and shrank into the tall box. It now
turns with it - barrel up - and its length fits the box's height. Applies in the grid and to
the gun on your cursor. Guns in the loadout boxes always show horizontal.

## 4. Top of the window cut off - fixed

The window was 376 units tall; at GUI scale 3 on a 1080p screen there are only 360, so it
centred 8 units above the top edge. The left column now starts 12 units higher (the armor
column had spare room) with everything below shifted to match, and the window is sized to its
content: 348 units, leaving 6 above and below at 360. On smaller screens the top is kept on
screen and the bottom gets clipped instead. The corpse view uses the same height (its bag
ends at 342).
