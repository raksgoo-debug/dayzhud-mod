# dayzhud 1.9.1 - magazines actually appear, corpse bag gate confirmed

**Complete.** 46 files. Unzip over the repo root.

## Why you could not find the magazines

Not a section you missed - they were being dropped from the catalogue silently, by my code.

`MagazineCompat.priceOf` read the magazine's capacity out of its NBT and returned 0 if that
came back 0. `MarketCatalog` then had `if (unit <= 0) continue;`, so every magazine whose
capacity tag was missing vanished without a word. Capacity is written from the mod's family
data, and `MagazineRegistrar` fills its family list on `OnDatapackSync` - so anything read
before or outside that has no size.

A magazine with an unknown size is still a magazine. It now falls back to
`magazines.basePrice` instead of disappearing, and the catalogue logs a warning if the mod is
active but reports no families at all. The rebuild line in latest.log also counts magazines,
so `/market debug` and the log will tell you exactly what got stocked.

## The corpse backpack

Checking this properly, the gate was **already in the tree and already shipped in 1.9.0** -
`broadcastChanges` zeroes `corpseBagSlots` while `RummageCompat.gates(corpse)` is true and the
player has not finished searching, so the bag section stays hidden until the body is done. I
described it as a "known remaining gap" last round; that was wrong, and I found it only by
going to add a second copy of the same mechanism. Both would have worked, which is the
dangerous kind of duplication - the second one is reverted.

To be precise about what is and is not true: Rummage cannot **mask** those slots, because
`CorpseLootSlot` is a `SlotItemHandler` over this mod's own view and `getWrappedTarget` only
unwraps Forge's `InvWrapper` and `SidedInvWrapper`. Gating the whole section behind the body's
own search is the equivalent, and it gives the pockets-then-pack order you asked for.

## Also

My verification script only scanned for unresolved first-party **classes**, so a call to a
method that does not exist would have slipped through - which is how the duplicate above
nearly shipped. It checks methods too now.

## Verification

Five checks against the extracted zip, all clean.
