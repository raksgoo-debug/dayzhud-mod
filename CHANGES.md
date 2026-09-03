# dayzhud 1.3.0 - lrtactical, armour on the shelves, firearms tab, UI pass

**Cumulative.** Replaces every earlier fix zip; apply straight onto the original 1.1.0
update. Fourteen files.

## Why nothing from lrtactical showed up

It is NBT-driven like TACZ, and I priced it as if it were not. There is no `lrtactical:ai2`
item registered - there are five base items, and every medkit is `lrtactical:consumable`
carrying `ConsumableId: lrtactical:ai2`. Throwables use `ThrowableId`, melee uses
`MeleeWeaponId`. So all twenty rows matched nothing and the mod was silently absent.

`NbtVariants` now handles that shape generally, driven by `market.nbtVariantItems` in config
rather than hardcoded, so the next mod built this way needs a config line and not a code
change. Keys are `<item id>/<variant id>`, e.g. `lrtactical:consumable/lrtactical:ai2`. All 20
items are priced: medkits under MEDS; M67 / RGN / flash / smoke / molotov / flash shield /
detonator under TACTICAL; karambit / dagger / bat under WEAPONS; condensed milk under
PROVISIONS.

## Why FIREARMS was missing, and it was not what it looked like

The guns were almost certainly in the catalogue the whole time. The category sidebar drew
seven rows and stopped - the same overflow bug as the old horizontal tab strip, turned ninety
degrees. Alphabetical order filled those seven slots with AMMO, GEAR, MATERIALS, MEDS, MISC,
PROVISIONS and pushed weapons off the bottom.

Fixed twice over: the sidebar scrolls (mouse wheel over it), and categories sort by a shared
display order - FIREARMS, AMMO, ARMOR, GEAR, TACTICAL, MEDS, PROVISIONS, SUPPLIES, MATERIALS,
WEAPONS, ELECTRONICS, VALUABLES, MISC - so what you shop for is at the top and anything a pack
invents falls to the end instead of vanishing. TACZ guns moved from "weapons" into their own
**FIREARMS** category; "weapons" is melee now.

**`/market debug` (op) is new** and reports offers per category, price-row count, and TACZ
status including how many guns priced successfully. The catalogue also logs a summary line on
every rebuild. "The shop is missing X" and "the shop has X and the UI is hiding it" look
identical from outside, and that ambiguity has now cost two rounds.

## All the caps_awim armour is on the shelves

238 pieces, stocked automatically under **ARMOR**, priced from their own defence, toughness,
knockback resistance and durability. The catalogue walks the item registry for any ArmorItem
with no explicit price row, so it also picks up Fracture Point and vanilla armour, and will
pick up whatever gear mod you add next. `derived.stockArmor` and `derived.maxListings` (600)
control it; explicit rows still win.

## UI pass

- Panel 400x256, up from 384x246, laid out on a grid.
- Title and subtitle share one line instead of the subtitle colliding with the tab row.
- Balance gets a BALANCE caption above it rather than floating in the corner.
- Search moved to the TOP of the sidebar - it sat under the last category and read as one.
- Detail panel re-spaced: preview, name, then a right-aligned total that turns red when you
  cannot afford it, with a UNIT caption under it when quantity is above one.
- Quantity buttons outlined when selected and sized so all three fit the column.
- WITHDRAW reads "WITHDRAW CASH" with the amount as a caption; the old label was being
  truncated mid-number.
- Bigger tabs, list rows tightened to 21px.

## Config note

New: `market.nbtVariantItems`, `derived.stockArmor`, `derived.maxListings`. Forge only writes
defaults into a config file that does not exist yet - delete `config/dayzhud-common.toml`.

## Verification

Parse-checked over the merged tree. Minecraft and Forge are not on the classpath here, so this
proves syntax and this mod's own cross-class references and nothing about MC/Forge calls. CI
is the real check.
