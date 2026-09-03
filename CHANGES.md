# dayzhud 1.4.0 - sections inside categories, HUD balance off

**Cumulative.** Replaces every earlier zip except the original 1.1.0 update; apply on top of
that. Sixteen files.

## Sections inside each category

Offers now carry a section as well as a category, and the sidebar expands the selected
category into its sections, indented and drawn small underneath it. ARMOR opens into HELMETS,
BODY ARMOR, LEGS, BOOTS; FIREARMS into PISTOLS, SMGS, RIFLES, MARKSMAN, SHOTGUNS, MACHINE
GUNS, LAUNCHERS; GEAR into BACKPACKS, RIGS, MASKS, EYEWEAR, HEADWEAR, UNIFORMS, GLOVES; MEDS
into KITS, PILLS, INJECTORS, BANDAGES; TACTICAL into GRENADES, EXPLOSIVES, SHIELDS.

Nested in the sidebar rather than a second strip along the top of the list. A horizontal strip
has a fixed width and would hit exactly the overflow that hid FIREARMS twice already; a column
just gets longer, and the column already scrolls.

Armour and firearm sections are **derived**, not authored - from the item's equipment slot and
from TACZ's own gun type - so all 238 caps_awim pieces file themselves, and so does whatever
gear or gun pack you add next. Hand-written rows can set a section with an optional `"sub"`
field in prices.json.

Two details worth knowing:

- A category with only one section does not expand. "ARMOR > helmets" on its own is noise.
- Picking a category clears the section, so switching category never leaves an inherited
  filter behind that silently shows an empty list.
- The list rows now show the section under the item name instead of repeating the category
  you are already standing in.

## Balance off the HUD

`market.showBalanceOnHud`, now **false** by default - the terminal shows it and that is
enough. The code stayed rather than being deleted, so it is one config flip if you ever want
it back.

## Config note

New keys: `market.showBalanceOnHud`. Forge only writes defaults into a config file that does
not exist yet - delete `config/dayzhud-common.toml`.

## A note on how this one went

Patching MarketScreen left a stale copy of the old sidebar loop behind a working one - two
`drawSidebar` bodies, one reading a list of category strings and one reading the new row
model. It parsed as a duplicate-method error rather than doing something subtle, which is the
good outcome, but it is the second time editing this file in place has produced a half-applied
change. Both stale blocks are removed and the merged tree parses clean.

## Verification

Parse-checked over the merged tree, 78 source files, nothing outside the expected
missing-Minecraft noise. That proves syntax and this mod's own cross-class references and
nothing about MC/Forge calls. CI is the real check.
