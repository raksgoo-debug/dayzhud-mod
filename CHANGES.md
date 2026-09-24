# dayzhud 2.12.0 - guns lie flat in the grid as their real 3D model; all non-pistols 2 tall

**5 changed/new files.** Unzip over the repo root, on top of 2.11.1.

## Real models, side-on, barrel left

Guns in the grid (inventory, backpack, containers, corpses) now draw as TACZ's actual 3D
model, turned side-on with the barrel pointing left, scaled to fit their footprint without
stretching, on one panel spanning the whole footprint - the look from your reference.

**Why nothing earlier could do this** (verified by disassembling TACZ 1.1.8's gun renderer,
not assumed): in GUI slots TACZ deliberately draws only a small diagonal 64x64 sprite, never
the model. That sprite is what got stretched into a thin streak, and why no amount of tilting
it could ever become a side view. TACZ does render the real model in item frames (the
`FIXED` display context), posed by each model's own `fixed` bone - which is rotated 90
degrees, i.e. side-on. So the new `TaczFlatGunRenderer` asks Minecraft to draw the stack in
`FIXED` mode inside the grid box, and TACZ does all the model work with its own code -
attachments included, no reflection into TACZ, no TACZ-version coupling.

Two things are measured at runtime rather than guessed:

- **Size.** Every model is a different size, so the first draw of each gun (per attachment
  setup) renders once into a vertex recorder to get its real extent, cached after that.
- **Which way it faces.** A gun's muzzle end is thin and its stock/grip end is tall, so the
  two ends of the measured model are compared and the gun is turned 180 degrees if the
  barrel came out on the right. Turned, not mirrored - ejection ports stay on their real
  side. Could mis-guess on something equally thick at both ends (a plain launcher tube);
  that would only flip its facing.

Anything that isn't a TACZ gun draws exactly as before. `flatGunRender = false` in
`dayzhud-grid.toml` turns it off; it also switches itself off for the session with one log
warning if it ever throws, so it can't break the inventory screen.

Also raised above the new panels: item decorations, and the green/red placement outline -
otherwise a red "won't fit" over an existing gun would have been hidden behind it.

## Every gun except pistols is now 2 tall

`shotgun 4x1 -> 4x2`, `sniper 5x1 -> 5x2`, `mg 5x1 -> 5x2` (smg 3x2, rifle 4x2, rpg 4x2
already were; pistol stays 2x1).

**Your existing `config/dayzhud-grid.toml` still has the old sizes** - Forge never rewrites
existing keys. Edit `gunTypeFootprints`, or delete the file to regenerate it. (The new
`flatGunRender` key is added automatically either way.) Guns already sitting in a row may
now overlap their new height; they'll draw 1x1 until dragged apart once, as in 2.9.0.

## What CI has to confirm - read before trusting a green local check

These 1.20.1 vanilla calls could not be checked in this workspace (no Minecraft jar here;
same gap that caused the renderSlot, rotation and boxing CI failures):

1. `ItemRenderer.renderStatic(ItemStack, ItemDisplayContext, int, int, PoseStack, MultiBufferSource, Level, int)`
2. `Lighting.setupForFlatItems()` / `Lighting.setupFor3DItems()`
3. `GuiGraphics.bufferSource()` and `GuiGraphics.flush()`
4. `OverlayTexture.NO_OVERLAY` (confirmed to exist - TACZ references it)
5. The `VertexConsumer` method set on the recorder. Written with no `@Override` on purpose:
   an extra method is harmless, a missing one fails compilation loudly.
6. `Axis.YP.rotationDegrees` - same shape as the `Axis.XP` call that already built green.

If any fails, it fails at compile time with a clear message - none can fail silently.

## In-game things worth checking first

- Size and centring in the box, and whether flat-item lighting reads well or too bright.
- Barrel direction across several guns (the runtime check above).
- `debugLogging = true` logs each gun's measured size and facing decision the first time
  it draws.
