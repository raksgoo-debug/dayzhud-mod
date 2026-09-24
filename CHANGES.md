# dayzhud 2.12.1 - flat 3D guns actually draw now

**1 changed file** (plus version bump). Unzip over the repo root, on top of 2.12.0.

## Why 2.12.0 showed nothing new

Your log confirmed 2.12.0 loaded and never errored, so the renderer ran and quietly decided
it couldn't draw. The reason, found in TACZ's bytecode: to size each gun, 2.12.0 drew it once
into a recording buffer to measure it. But TACZ's model renderer ignores any buffer you hand
it - it grabs Minecraft's global buffer itself and flushes it itself. The recorder never saw a
single vertex, the measurement came back empty, and every gun fell back to the old sprite
without a word. I had checked the top of that call chain last time, not the bottom.

## The fix

One level down, TACZ's model parts DO accept a buffer (`BedrockPart.render(..., VertexConsumer, ...)`),
and `BedrockModel.getShouldRender()` is the exact list of parts the model draws - confirmed
in bytecode that the model's own render walks precisely that list. So measuring now walks
those parts straight into the recorder.

Drawing changed too: instead of the item-frame path (where TACZ chooses the pose and its
orientation had to be guessed), TACZ's model render is now called directly with a pose built
here. TACZ still draws its own model with its own code, attachments included; all TACZ calls
are by reflection, so TACZ stays optional.

Orientation now comes from the measured geometry itself:
- length axis = the longer horizontal extent,
- muzzle = the thinner end (barrels are thin; stocks and grips are tall),
- up = the side the muzzle sits on (the bore runs along the top; grips, mags and stock drops
  hang below).

Tested before shipping by porting that exact logic to Python and running it on real vertex
data from six of your guns (DB-4, M4A1, AK-47, MP5, M870, Glock), in both Bedrock's raw axes
and the flipped axes TACZ probably uses internally: all twelve came out barrel-left and
upright, and both conventions gave identical results - so it doesn't depend on guessing
TACZ's internal axis convention.

An empty measurement now logs a warning naming the gun, instead of failing silently.

## What CI has to confirm

Everything this uses compiled in 2.12.0 except one call: `RenderType.entityCutoutNoCull(ResourceLocation)`.
Every TACZ method is verified against your jar's bytecode.

## If it still doesn't show

Set `debugLogging = true` in `dayzhud-grid.toml` and open the inventory with a gun in it. Each
gun logs one line: vertex count, length axis, muzzle end, and which way is up. That line (or
the warning if it measured nothing) says exactly where it stopped.
