# dayzhud 2.13.9 - CAPS AWIM pants and boots are 2x2

**3 changed files** (plus version bump). On top of 2.13.8.

CAPS AWIM leggings and boots now take 2x2, like its helmets, and get the same big render in
the grid and while carried (their own icon scaled into the footprint - CAPS's items are flat
icons, the grid draws helmets and chest rigs the same way).

The sizes come from `armorTypeFootprints`, whose default now lists `leggings=2x2` and
`boots=2x2`. A config file keeps its old list, though, so types it doesn't mention now fall
back to built-in sizes (`DefaultItemFootprints.ARMOR_TYPES`, same values as the default) -
existing configs get pants and boots at 2x2 without editing. To keep one a single cell, list
it as `=1x1`.

# dayzhud 2.13.8 - carried magazines stay visible, pistol magazines are 1x1

**6 changed files, 1 new** (plus version bump). On top of 2.13.7.

## A carried magazine vanished over the grid

TaCZ: Magazines' FIXED render starts with `translate(0.5, 0.5, 0.5)`. Turned side-on, that
half block became DEPTH: a carried magazine drew about 30 units behind where it should (at
~353 instead of 385), so the grid's placement preview (at 360) covered it - visible over empty
background, gone over the slots. `FlatModelRenderer` now cancels that translate exactly, and
squashes every flat model's depth to a quarter so nothing drawn off-centre in depth (CAPS
bags sit on the wearer's back) can reach through the grid's hover/preview layers again.

## Pistol magazines back to 1x1

TaCZ: Magazines uses one item, `taczmagazines:magazine`, for every gun's magazine, so 2.13.7
made a Glock's magazine 1x2 like an M4's. New `MagazineFootprints`: the stack's
`MagazineFamily` NBT -> TaCZ: Magazines' `MagazineFamilySystem.getRepresentativeGun` -> that
gun's TACZ type. A pistol's standard magazine is 1x1; rifles, SMGs and extended pistol
magazines stand 1x2. Both sides read the same data, so they agree; anything unresolvable stays
1x2. (`TaczMarketCompat.gunTypeOfId` added for this, not gated by the market's TACZ switch.)

# dayzhud 2.13.7 - magazines stand up, carried armour stays big, whole weapon box clicks

**4 changed files** (plus version bump; `FlatBackpackRenderer` from 2.13.6 is now
`FlatModelRenderer`). On top of 2.13.6.

## TaCZ: Magazines - real render, standing

Its item renderer draws each magazine from its gun's TACZ model, but in the inventory it uses a
tilted 3/4 view; scaled up across two cells that came out as a thin diagonal sliver with the
count off to one side. In every other context it draws the magazine in the gun model's own
axes (checked in its bytecode), so `FlatModelRenderer` now asks it for the FIXED render, turns
it side-on exactly like the guns, and fits/centres it by its visible pixels (PixelProbe) -
the magazine stands the way it sits in the gun. Rifle/SMG magazines are now **1x2** (were
2x1); pistol magazines stay 1x1 and get the same render (1x1 grid items with a model render
are drawn by the grid now). Reached through Forge's `IClientItemExtensions`, no reflection.

## Carried CAPS armour (and any multi-cell item) stays big

Only items with a model render used to be drawn at their footprint size while carried;
everything else fell back to vanilla's small 16x16 cursor icon. Now every multi-cell item is:
its model render if it has one, otherwise its own icon scaled into the footprint, exactly as
the grid draws it, plus its count/durability bar.

## Primary / secondary / holster / sheath: click anywhere in the box

The loadout slots' real Slot is a 16x16 square in the middle of the drawn box, and only that
square took clicks. The screen now overrides `isHovering(int, int, int, int, double, double)` -
which vanilla routes every slot hover and click test through - to answer for the whole box when
the position is a weapon slot's. An empty box now highlights as a whole on hover too.

# dayzhud 2.13.6 - backpacks get grid sizes and real-model renders

**4 changed files, 3 new** (plus version bump). On top of 2.13.5.

The 12 fieldkit backpacks and the 7 CAPS AWIM ones now take several cells in the grid and are
drawn as their real 3D model, front-on (pockets toward you), filling and centred in their box -
the same treatment as guns.

- Sizes (`DefaultItemFootprints.ITEMS`, config `itemFootprints` still overrides): assault and
  every CAPS bag 2x2 except the sports bag; pilgrim and sports bag 2x3; raid, gunslinger and
  frame 3x3. Sized offline from each worn model at one shared scale (both mods model bags in
  player pixels), capped at 3x3 so any bag fits the 9x3 inventory.
- New `FlatModelRenderer`: fieldkit bags through the item renderer (their item model is the
  3D model, pockets at -z); CAPS bags through the model and texture of CAPS's own worn
  renderer for that bag, found by reflection (CAPS's item icon is a flat sprite). A bag that
  fails to draw logs once and falls back to its normal icon.
- New `PixelProbe`: the off-screen visible-pixel measurement, moved out of
  TaczFlatGunRenderer so guns and bags share it. New `FlatItems` picks the renderer; the grid,
  the carried item and big-icon fallback go through it. Loadout (weapon) boxes stay guns-only.
- Unequipped bags already in an inventory now need 4-9 cells; like any footprint change, one
  without room shows as 1x1 until moved.
- `flatGunRender = false` turns the bag renders off too.

# dayzhud 2.13.5 - attachments make the box bigger, not the gun smaller

**5 changed files, 2 new** (plus version bump). On top of 2.13.4.

In 2.13.4 a gun with attachments was squeezed into its plain footprint - a suppressed MP5
drew at 58% of its normal size. Now a gun is always drawn at the size that fills its footprint
with nothing fitted, and the footprint grows to make room: a suppressor adds width, a big scope
plus a grip or extended mag can add a row.

- New `GunSizes` + `/dayzhud/gun_sizes.txt` (in the jar): each default gun's visible size and,
  for each of the 1155 gun/attachment pairs that reach past the plain gun, how far on each side
  (measured offline from the TACZ jar, same method as the footprint table). Several
  attachments combine per side by the furthest reach.
- The server reads the fitted attachments from the gun's NBT (`IGun.getAttachmentId`, new
  `TaczMarketCompat.attachmentIdsOf`) - no models needed, so it works on a dedicated server.
- `ItemFootprints.baseFootprintOf` includes the growth; `plainFootprintOf` is the old value.
- Limits: 9 wide, 3 tall. With the default pack the biggest full loadout is 9x2 (M95, AWP) and
  rifles only reach 3 tall with a tall scope plus a grip or extended mag. Pistols grow to 2x2
  with a sight or extended mag, since they fill their one row exactly.
- Other packs' guns/attachments have no data: they don't grow, and fall back to fitting the box.
- A gun that already carries attachments in someone's inventory may now need more room than it
  has; like any footprint change it shows as 1x1 until moved.

# dayzhud 2.13.4 - guns fill their grid and sit in the middle of it

**3 changed files** (plus version bump). On top of 2.13.3.

## Why guns were small and off-centre

Every measurement so far counted vertices, and TACZ draws geometry you never see - scope
reticle and lens planes that only show through a stencil mask, transparent cubes and the like.
That made the measured box bigger than the gun: the AK drew at about two thirds of its 5x2, the
M4 sat high and the scoped AUG sat low. The 2.13.1 self-centring chased the same inflated box,
so it couldn't fix this.

## Measured from pixels now

The first time a gun (or LR item) is drawn, it is also drawn once into a small off-screen buffer
with a stencil, just like the screen, and its box is read from the pixels that came out opaque.
That box is what gets fitted and centred, cached per gun and attachment setup. The per-frame
vertex correction is gone. Everything the measurement changes in GL (framebuffer, viewport,
projection, model-view, scissor) is restored straight after.

## Sizes

- Guns now fill their footprint, 2 px in from the border, instead of being drawn at a shared
  scale that could leave up to a cell empty.
- Every non-pistol gun is 2 tall. Width is the gun's visible length at the old shared scale
  (9 model units per cell), rounded to the nearest cell. Pistols stay 1 tall and fill it.
- Changed footprints: M4A1 5x2 -> 4x2, SCAR-H 4x2 -> 5x2, RPK 6x2 -> 4x2, M1014, SKS and SPAS-12
  6x2 -> 5x2, M320 2x2 -> 3x2, M107 7x3 -> 6x2, minigun 6x3 -> 6x2. The rest are unchanged.
  A gun already in an inventory whose new footprint collides with a neighbour shows as 1x1
  until you move it, as with any footprint change.
- `DefaultGunFootprints.LENGTH` is removed; nothing reads it any more.

## What the build has to confirm

New calls (all checked against the 1.20.1 mappings / Forge 47 jar): `TextureTarget`,
`RenderTarget.enableStencil` (Forge), `NativeImage.downloadTexture`, `RenderSystem`
projection/model-view getters and setters, `GlStateManager._glBindFramebuffer` and the scissor
toggles, LWJGL `GL11`/`GL30` state queries. With `debugLogging = true` each gun logs its vertex
box vs visible box once.

# dayzhud 2.13.3 - the whole backpack on screen

**2 changed files** (plus version bump). On top of 2.13.2.

## Seven rows instead of four

2.13.2 made big bags reachable by scrolling a 4-row window. There was room for the whole bag
all along: the backpack column has space down to the divider at y 234, and 7 rows end at
y 226. `BACKPACK_VISIBLE_ROWS` is now 7, so every bag up to 63 slots is shown whole - no
scrolling. The slot count, grid region and shift-click range all follow the constant.

The dark backing behind the bag used to be a fixed 4 rows tall; it is now sized to the rows
the worn bag actually has, so a small bag doesn't sit in a big empty box.

Scrolling stays as the fallback for a bag bigger than 63 (only a 64th slot from another
mod can trigger it), and the 2.13.2 count fix is what lets 7 rows show at all.
# dayzhud 2.13.2 - backpacks bigger than 36 slots scroll

**2 changed files** (plus version bump).

## Big bags showed as 36 slots

The inventory shows the worn bag through a 4-row window (36 slots) that scrolls over the
bag - but the server counted the bag's usable slots only up to that window size before
syncing the count to the client. Every bag therefore reported at most 36 slots: the
scrollbar never appeared and anything past slot 36 was unreachable from this screen (it
was still there - opening the bag another way showed it).

The count now runs to the bag's real size, capped at the client's 64-slot mirror
(`BackCurioItemHandler.MIRROR_SIZE`, now package-visible for that). The window, the
scrollbar, the scroll packet and the visible-slot logic were all already built for this
and are unchanged; they simply never had more than four rows to scroll.

Worth a look in game: this is the first time the scrolling path runs. A multi-cell item
whose footprint runs past the bottom of the visible rows shows as 1x1 until you scroll so
it fits - the grid only reconciles the rows on screen, which is the existing degrade
behaviour, not data loss.
# dayzhud 2.13.1 - true-to-scale guns, self-centring renders, knives point left

**4 changed files** (plus version bump). Unzip over the repo root, on top of 2.13.0.

## Knives pointing down - fixed

LR melee weapons are held at their model origin, and the tip is the point farthest from it.
2.13.0 placed that origin at (0, 24, 0) - a guess about TACZ's coordinates that was never
checked in game. The knife pointing down pinned the real answer: TACZ's loader (which LR uses)
puts the origin at y = 24 *pixels*, and parts are drawn in *block* units (/16), so in game it's
(0, 1.5, 0). From (0, 24, 0) the "farthest point" of a knife was its bottom edge. The origin is
now derived from the same 16-or-1 unit factor the renderer already measures, so it holds
whichever unit LR uses. (The first-person hand markers were tried as the grip point and
rejected - on bats and karambits they sit off the item.)

## Sizes relative to each other - now true to scale

Checked first whether TACZ's models themselves are off: they aren't. Against published lengths
they're mostly within 7% of real proportions (Glock/AK 0.24 vs real 0.23, AWP/AK 1.39 vs 1.36,
M4/AK 0.97 vs 0.95; the Uzi's stock is modelled folded, the SPAS is 12% long).

The problem was the drawing: every gun was scaled to fill its footprint, so a gun that just
rounded up to an extra cell was drawn noticeably bigger than a near-identical one that didn't.
Now every non-pistol gun is drawn at one shared scale - 2 px per model unit, the same scale the
footprints were sized at - centred in its footprint. Relative sizes are the real ones; a gun
only shrinks when it genuinely can't fit. A fitted suppressor makes a gun longer, not smaller
(the unit conversion is snapped, not taken per gun). The loadout boxes use the same scale.
LR melee/consumables too.

Pistols still fill their 2x1, as you asked earlier - at true scale a Glock would use about 58%
of its box. Say if you'd rather have pistols to scale as well.

## Off-centre renders - corrected from the actual draw

2.12-2.13 centred on a separate measuring pass, and some guns still came out off-centre:
something TACZ draws differently at runtime than in that pass, which I couldn't reproduce
offline (the gun models themselves are ruled out). So centring no longer depends on knowing
the cause. Each real draw now goes through a pass-through buffer that forwards everything
unchanged and records where the vertices actually landed on screen. The next frame corrects by
the difference, accumulating, so it settles immediately and stays centred. Effects (render
types with under 8 quads - muzzle flash and the like) are ignored for this, as for sizing.

`debugLogging = true` logs each correction ("drawn N px off-centre ...") - if you see a gun
correct every single frame instead of once, that log is what I'd need.

## Known issue, unchanged

The Apocalyptic Arsenal katana is built from many separately angled thin segments and still
comes out diagonal and small.

## What the build has to confirm

Nothing new beyond 2.13.0's list: the pass-through uses only calls already used for the
measuring pass (the same buffer swap and VertexConsumer method set).
