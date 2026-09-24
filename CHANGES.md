# dayzhud 2.12.2 - flat guns: leftover icon, facing, stocks, missing guns, loadout boxes

**2 changed files** (plus version bump). Unzip over the repo root, on top of 2.12.1.

## TACZ's small icon still showing in the corner - fixed

A depth-order bug of mine. Vanilla draws a slot's item at z ~250 (its own +100 for slots,
plus +150 per item), and the panel meant to cover TACZ's little diagonal icon sat at 190 -
underneath it. All flat-gun layers now sit above vanilla's item layer: panel 280, gun 330,
hover 345, decorations 350, placement outline 360. The carried item (~382) and tooltips (400)
stay on top. Since the panel now also covers vanilla's hover highlight, a hover highlight is
drawn on the panel instead.

## Some guns facing right - fixed

2.12.1 guessed the muzzle as "the thinner end". That held for the low-detail models I tested
with, but high-detail models (muzzle brakes, rails, suppressors) break it - your AK. The muzzle
now comes from the model's own muzzle-flash bone, located exactly the way TACZ itself locates
it (verified in its bytecode). The thin-end guess only remains as a fallback for a model that
has no such bone.

## Stocks cut off - fixed

The size measurement walked only the gun body. TACZ draws stocks, scopes and the like as
separate attachment models on top (including built-in ones), so those guns measured too short
and the stock spilled past the box. Measurement now runs TACZ's complete draw, attachments
included. TACZ insists on using Minecraft's global render buffer, so for that one measuring
pass the global buffer is briefly swapped for a recorder and restored straight after
(render-thread only, restored in a `finally`). It is found by identity, not by field name, so
obfuscation doesn't matter. The only other global state TACZ's draw touches is the stencil
buffer (checked in bytecode). If the swap ever isn't possible, it falls back to body-only
measurement with one log warning.

## Some guns not getting the new render - likely fixed

Most likely those guns' packs ship only a low-detail model, which returned nothing from the
call 2.12.1 used. TACZ's own renderer falls back to the low-detail model in that case, and so
does this now. If any gun still shows the old icon, the log will now say why: "measured no
geometry for gun X".

## Loadout boxes (PRIMARY / SECONDARY / HOLSTER)

An equipped TACZ gun now draws as its real side-on model filling the box, same as the grid.
The key badge ("1" / "2") is lifted above it. A knife in SHEATH, or any non-TACZ item, still
shows its normal icon.

## What CI has to confirm

Confirmed by TACZ's own bytecode: `Minecraft.renderBuffers()`, `RenderBuffers.bufferSource()`,
`BufferSource.getBuffer` / `endBatch(RenderType)`, `PoseStack.last().pose()`.

Not yet proven (each fails at compile time if wrong - none can fail silently):
- the `MultiBufferSource.BufferSource(BufferBuilder, Map)` constructor
- `new BufferBuilder(int)`
- no-arg `BufferSource.endBatch()` (has `@Override`, so a wrong one fails loudly)
- JOML `Matrix4f.transformPosition(Vector3f)`

## Debugging

`debugLogging = true` logs one line per gun: vertex count, whether it measured the full draw
or body only, the length axis, the muzzle end and whether it came from the muzzle bone or the
fallback guess, and which way is up.
