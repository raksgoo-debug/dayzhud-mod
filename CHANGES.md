# dayzhud 2.11.1 - back to real gun renders, keeping only the rotation/fit fix

**4 changed/removed files.** Unzip over the repo root, on top of 2.11.0, then apply
`DELETE.txt` (included in this zip) - this drop removes files, which unzipping alone can't do.

## What changed

2.11.0's TACZ HUD icons are gone from the grid. They turned out to be genuinely grayscale -
checked directly against the pixel data (R, G and B channels identical everywhere, no colour
variation at all) - and that's not what was wanted: real guns, in their own colours and
textures, not a flat monochrome icon standing in for them.

The grid now always uses the actual gun's own 3D render again, at its real colours - the
only two things this mod still changes about it are the two that were actually asked for:

- **Contained, not stretched.** Scaled uniformly (same factor both axes) to fit inside the
  footprint rather than forced independently in width and height to fill it - this is what
  fixed the sheared/thin-diagonal look from before, and it's kept.
- **Rotation** - `grid.flatItemAngleX` in `dayzhud-grid.toml`, still defaults to 0 (off),
  still available to tune if you want to angle it toward lying flatter.

`TaczHudIcons.java` and the 55 bundled icon PNGs are removed - dead weight once nothing
references them, not left behind disabled.

## DELETE.txt

```
src/main/java/com/dayzhud/mod/inventory/TaczHudIcons.java
src/main/resources/assets/dayzhud/textures/gui/tacz_hud/
```

## Verified

Diffed the full deduplicated error-message list against the pre-2.11.0 baseline (before
TaczHudIcons ever existed): identical. A clean revert should look exactly like never having
made the change, and it does.
