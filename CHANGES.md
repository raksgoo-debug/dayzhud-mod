# dayzhud 2.4.0 - Field Kit items, and why the magazines went quiet

**5 changed files, nothing new.** Unzip over the repo root. Built by diffing against the tree
you just uploaded, so it is exactly your 2.3.0 plus these changes - nothing to delete.

## Field Kit

All **31** items priced and stocked. Cross-checked both ways against the jar's own lang file:
nothing in the mod is unpriced, and nothing priced is missing from the mod.

- **PROVISIONS / food** - rations, the four tinned meals, condensed milk, noodles, crackers,
  jerky, chocolate, nut bar, pickles, sugar.
- **PROVISIONS / drink** - bottled water, juice, energy drink, coffee, vodka.
- **MEDS** - iodine under PILLS; adrenaline, propital, eTG-c and SJ6 under INJECTORS.
- **MISC** - lighter, cigarette.

The **opened** tin variants and the lit lighter are priced but **not stocked**: they are states
of an item rather than stock, so a trader will buy one off you for scrap value but will never
sell you a can that is already open.

Unlike LesRaisins, every Field Kit item is separately registered, so these are plain item ids -
no NBT-variant handling needed.

Its `water_bottle` also covers the item you deleted, which I assume is why you deleted it.

## Magazines

Deleting the bottled water did not break them - that commit only removed `item/` and
`registry/`, and nothing in the magazine path referenced either. The stocking code is intact.

What I did find is a way for them to switch off permanently and stay off:
`MagazineCompat.resolve()` latched on **any** failure. `MagazineRegistrar.MAGAZINE` is a
`RegistryObject`, and `get()` throws until registration has run - so one early call, say a
sell-price lookup during load, would cache "unavailable" for the entire session behind a
single warning nobody would connect to an empty shop tab.

It now only latches when the API is genuinely absent - `ClassNotFoundException`,
`NoSuchMethodException`, `NoSuchFieldException`. Anything else leaves it unresolved to retry.
I verified every name it reflects on against the jar; all four match.

**If they are still missing, the log now says why.** On every catalogue build you get one of:

    Magazines not stocked: taczmagazines loaded=false, api resolved=false
    TaCZ Magazines resolved but reported no magazine families ...
    Market catalogue rebuilt: ... N magazines ...

That distinguishes "mod absent", "API moved", "built too early" and "working", which the
missing tab alone never could.

## Verification

`RESULT: PASS (9 checks)` against the extracted zip.
