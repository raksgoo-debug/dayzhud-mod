# dayzhud 2.12.4 - guns draw at full size, always the right way up

**2 changed files** (plus version bump). Unzip over the repo root, on top of 2.12.3.
Slot sizes are unchanged - pistols stay 2x1; the guns just draw bigger.

## Why guns looked too small (AK, pistols) - fixed

Every TACZ gun model carries two hand-position markers (`lefthand_pos` / `righthand_pos`):
opaque 4x12 boxes that reach far above the gun. TACZ hides them when it draws a gun, but not
before my size measurement ran, so they counted. Across the 54 default guns, 48 had their
measured height inflated - pistols worst (a Glock measured 25 units tall against a real 11).
The gun was then shrunk to fit a box that was mostly empty space. They're now explicitly
hidden, by bone name, for the measuring pass and the draw, and restored straight after. The
name match is anchored, so it can't catch parts like "Handguard".

The gun was also padded twice - 2 px by the panel and 2 px again inside the renderer - so a
2x1 slot (18 px tall) left the gun 10 px. Now it's 1 px inside the panel outline in the grid
and 2 px in the loadout boxes, nothing extra in the renderer.

Net effect (see render-size-before-after.png): pistols about 4.4-4.9x bigger in the same 2x1
slot; rifles about 1.3-1.5x.

## Why the SCAR was upside down - fixed, for every gun

The "which way is up" check compared the muzzle's height with the middle of the measured
model. The hand markers dragged that middle upward, and even without them the check fails on
4 guns (a tall scope sits well above an M700's muzzle; the HK416D, MP5 and P90 are too close
to call).

It turns out no per-gun guessing is needed. From TACZ's bytecode: every model is converted
the same way on load (Y flipped, X and Z kept), and every default gun is authored the same
way (muzzle toward -Z - true for all 52 that have a muzzle bone). So every gun is oriented
identically, and the renderer now applies one fixed rotation. Verified on all 54 full-detail
models before shipping - orientation-all-54-guns.png shows every one barrel-left and upright,
including the SCAR, M700, HK416D, MP5 and P90.

This also removes two things CI still had to confirm from 2.12.2 (the muzzle-bone walk used
`PoseStack.last().pose()` and JOML `transformPosition`).

## Debugging

`debugLogging = true` logs one line per gun: vertex count, full draw vs body only, how many
hand parts were hidden, and the measured length and height.
