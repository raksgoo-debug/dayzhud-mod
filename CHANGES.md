# dayzhud 2.11.0 - real per-gun icons in the grid, no more stretch; loadout icon simplified

**59 changed/new files.** Unzip over the repo root, on top of 2.10.2.

## The grid no longer stretches guns - and 55 of them now use their real icon

Your screenshots showed exactly the problem: a shotgun rendered as a thin diagonal streak
across its footprint, instead of looking like image 1's normal, correctly-proportioned icon.

Two things, from actually opening the jar you sent rather than guessing further:

1. **The fallback path (any gun) no longer stretches.** The 3D GUI render is now scaled
   uniformly - same factor on both axes - and fitted inside the footprint, centred, rather
   than forced independently in width and height to fill the rectangle exactly. A gun at its
   real proportions just made bigger looks like a gun; the old non-uniform stretch is what
   turned it into a shear.

2. **55 guns now skip the 3D render in the grid entirely**, using TACZ's own flat icon
   instead. Your jar's default gun pack ships one pre-rendered, already-alpha'd icon per
   gun - `textures/gun/hud/<gunid>.png`, almost certainly what powers TACZ's own weapon-select
   wheel. They're not reachable through Minecraft's normal resource system (they live under a
   path TACZ loads through its own bespoke gun-pack code, not a standard `assets/tacz/...`
   location), so all 55 are copied into this mod's own resources and looked up by gun id.
   Confirmed against your own screenshot: `db_long` is exactly "DB-4 Ursus" from image 1.
   Before/after with that real icon attached, next to the code's actual footprint-box math -
   not a mockup.

   Any gun from a pack other than TACZ's own default one - not covered by this jar, so not
   something I can bundle - falls through to the fixed 3D-render path above: no longer
   stretched, just possibly smaller within its footprint than a matched icon would be.

## Loadout slots: equipped now shows nothing extra, as asked

Removed the flat-icon overlay for an occupied PRIMARY/SECONDARY/HOLSTER/SHEATH slot
entirely, along with the brightness-tint code that went with it. What's left underneath -
vanilla's own ordinary small icon, which was always there, just previously painted over -
is all that shows now. The empty-slot ghost icon is unchanged.

## Verified

Diffed the full deduplicated error-message list against the 2.10.0 baseline: identical, no
new error class. Specifically checked every line mentioning the two new symbols
(`TaczHudIcons`, `drawContainedIcon`) by hand - both only ever fail to resolve on the same
missing-Minecraft-package grounds as everything else here, never on their own names.

The grid comparison image was built from the actual bundled `db_long.png` and the actual
box-sizing formula in `drawBigGridIcon`, not a hand-drawn approximation - as close to seeing
it in game as this workspace gets. Native rendering (blit call, in-game icon crispness at
real size) is still unverified, same caveat as every icon change so far this thread.
