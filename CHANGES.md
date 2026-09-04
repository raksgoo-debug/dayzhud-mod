# dayzhud 1.9.0 - the corpse mask bug, found and fixed

**Complete.** 46 files. Unzip over the repo root.

## What was actually wrong

You were right that this was fixable, and the reason it only broke on corpses is the whole
answer.

Rummage recomputes its mask on `PlayerContainerEvent.Open` and sends it as a BitSet of **menu
slot indices**. But `EventHandler.onContainerOpen` **skips the packet entirely when that set
comes out empty** - there is an `isEmpty()` guard at offset 216 with no "send a clear" branch.

This mod replaces the menu inside that same event. So for a corpse the sequence was:

1. The addon's corpse menu opens. Rummage masks it - its slots 0..40 are the corpse.
2. We swap in the merged menu and re-fire the open event.
3. Rummage recomputes, gets an empty set, and sends nothing.
4. The client is still holding the bitset from step 1 and paints it over the new menu, where
   indices 0..5 are **your armour and curios**.

Chests never showed it because a merged chest still resolves Rummage targets, so the
recomputed set is non-empty and correctly overwrites the stale one. That difference is what
told me where to look.

`RummageCompat.clearClientMask()` now clears the client's mask when the merged screen opens.
That is safe: the server sends its fresh state after the open packet, so a real mask
re-applies a moment later, and an empty one correctly stays empty.

## Merging is the default again

`access.respectRummage` is **false** by default now. Searching works inside the merged view -
a plain `Slot` on a rummageable container resolves a Rummage target whatever menu it sits in,
which the corpse column already uses for its armour, curios, inventory and hotbar. The setting
stays as a fallback if some container misbehaves.

## Corrections to what I told you last round

- "The mask is positional over menu.slots, so the Tarkov layout can never work" - **wrong**.
  Server and client both index by menu slot; they agree. I asserted that from a screenshot
  instead of reading `SlotMixin`, which does exactly what I later found it does.
- "It might be a client/server slot-count mismatch in our menu" - also wrong, and your one
  sentence about chests working ruled it out immediately. A shared offset bug would have hit
  both.

## Known remaining gap

The corpse's **backpack** section still will not mask or search: those slots are
`CorpseLootSlot extends SlotItemHandler` over this mod's `ScrollingBackpackView`, and
Rummage's `getWrappedTarget` only unwraps Forge's `InvWrapper` and `SidedInvWrapper`. The
corpse's own armour, curios, inventory and hotbar are plain Slots and are unaffected. Fixing
the bag means giving that section a wrapper Rummage can see through - say the word.

## Verification

Four checks against the extracted zip, all clean. The Rummage calls are reflective, so a
rename upstream shows as a logged warning at runtime rather than a compile error.
