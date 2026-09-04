# dayzhud 2.1.0 - the backpack is searched too

**Complete.** 52 files. Unzip over the repo root.
**Delete `src/main/java/com/dayzhud/mod/market/RummageCompat.java`** if you have not already,
and remove Rummage from the pack.

## Answer to the question: it was not, and now it is

Until this build the corpse's worn backpack was one gate - hidden entirely, then the whole
pack popped open the moment the body was done. That skipped the search on the half of the
loot most worth finding.

Bag slots are now revealed one at a time like everything else, continuing straight on from
the body: pockets, then the pack.

## How, without a second progress store

Bag slots live in the **same BitSet** as the corpse's, at an offset past the container's size -
bag slot n is bit (containerSize + n). A BitSet has no fixed length, so this costs nothing,
keeps one record per corpse per player, and makes the ordering fall out of the numbering
instead of needing a separate gate.

Masking happens on the slot rather than in a wrapper, because the bag is an `IItemHandler`
and there is nothing for the menu to read through. `CorpseLootSlot.getItem` returns empty for
an unsearched slot, and `broadcastChanges` reads `getItem()` - so the item is never sent to
the client at all. `mayPickup` is overridden too, so a hidden slot cannot be taken from.

## One thing that needed care

The corpse loot list scrolls, so which visible slot maps to which bag slot changes without
anything being revealed. The hatch mask is now resent whenever it differs from the last one
sent - one packet per actual change, not one per tick, and scrolling is covered without a
special case.

## Verification

`RESULT: PASS (7 checks)` against the extracted zip.

The duplicate-block detector caught a real bug during this change: refactoring `sendMask` left
the old inline copy of the mask computation beside the new `maskFor`. That is the check added
after four CI failures from exactly this, doing its job on the first change since.
