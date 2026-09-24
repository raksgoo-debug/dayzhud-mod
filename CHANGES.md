# dayzhud 2.13.0 - LR Tactical / Apocalyptic Arsenal / CAPS / magazines; layout and centring

**8 changed/new files** (plus version bump). Unzip over the repo root, on top of 2.12.5.
No config edits needed - the new keys are added to your existing config automatically.

## 1. Gap between INVENTORY and BACKPACK - closed

The backpack sat where the old hotbar row used to be. It now starts right under the
inventory (38 units higher); its scrollbar and scroll area moved with it.

## 2. Guns not centred (SCAR and others) - fixed in the one place left to look, and logged

What's already ruled out: the gun models themselves. Checked all 54: nothing reaches past the
SCAR's real muzzle device, and no gun has invisible (fully transparent) cubes that shift its
box. What's left is what TACZ draws *around* the model at runtime - every gun carries a
muzzle-flash renderer at its muzzle, and a quad from it would stretch the measured box forward
and push the gun toward its stock, exactly the SCAR's shift.

The measurement now records vertices per render type and ignores any type with fewer than 32
vertices (8 quads). Effects are a few-quad billboard; stocks, scopes and bodies are dozens of
cubes. Filtering by type *name* doesn't work: TACZ draws attachments with the same translucent
type as its muzzle flash (checked in bytecode).

If any gun is still off-centre: set `debugLogging = true`, open the inventory with it, and send
the log. Each gun now logs every render type it drew - vertex count, kept or skipped, and its
extent - so whatever is stretching it will be named.

## 3. Loadout boxes keep the gun's grid size

A gun in PRIMARY / SECONDARY / HOLSTER / SHEATH is now drawn at the same scale as in your
inventory, instead of being blown up or shrunk to fill the box. The one exception: guns 6-7
cells long (snipers, M249, FAL, SPR-15...) are longer at that size than the 100-px box, so
they still shrink there (about 10-25%). Fixing that needs a wider left column - say if you want it.

## 4. LR Tactical + Apocalyptic Arsenal: sheath, grid, flat render

Apocalyptic Arsenal is a content pack for LR Tactical, and LR Tactical works like TACZ: a few
generic items (melee / consumable / throwable) whose variant is in NBT, drawn with TACZ's own
model classes. So:

- **Sheath:** every LR melee weapon (it's all one item, `lrtactical:melee`) now fits the SHEATH
  slot - dagger, karambit, bats, fire axe, katana. Added as an optional tag entry, so nothing
  breaks without LR Tactical installed.
- **Grid sizes**, from each variant's own model at the guns' scale (melee at least 2 wide):
  dagger / karambit 2x1, bats 4x1, fire axe 4x2, katana 5x2; blood pack, SURV12, molotov 2x1;
  other consumables and grenades 1x1. Built in, keyed by the variant id, same on both sides.
- **Flat render:** LR items draw as their real model in the grid and the sheath box, like guns.
  Their models share no orientation convention (measured across all 22), so it's read from
  each model: the longest side lies horizontal, you look at the flat side, tip to the left. For
  melee the length runs from the grip (every LR melee model is held at its origin) to the
  farthest point. Checked on all 22 models - lr-items-orientation.png /
  lr-melee-orientation.png. **Known issue:** the Apocalyptic Arsenal katana is built from many
  thin separately-angled segments and still comes out diagonal and small.

## 5. CAPS AWIM armor and TaCZ magazines

- **CAPS:** chestplates 3x3, helmets 2x2 - by armor type at runtime (61 CAPS items are real
  armor; slot read with the same calls CAPS itself uses), so no item list to maintain.
  Configurable: `armorFootprintMods`, `armorTypeFootprints`. CAPS's other 55 items
  (balaclavas, uniforms, belts, bandages, 7 backpacks) keep 1x1 - say if you want sizes.
- **Magazines:** `taczmagazines:magazine` 2x1, `magazine_small` (pistol) 1x1.
- **Render:** these aren't TACZ-model items, so in the grid they draw their normal inventory
  render, scaled evenly into the footprint on a proper panel. Before this, a multi-cell
  non-gun item drew under vanilla's small icon with no panel.

## What the build has to confirm

New vanilla/library calls, none checkable here: JOML `Quaternionf.setFromNormalized(Matrix3f)`
and the `Matrix3f` 9-float constructor (LR orientation), `ArmorItem.getType().getSlot()`
(proven in CAPS's bytecode), `EquipmentSlot` constants. Any mistake fails at compile time.
