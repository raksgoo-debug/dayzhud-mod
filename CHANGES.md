# dayzhud 1.11.0 - the clear packet was deleting the correct mask

**Complete.** 46 files. Unzip over the repo root.

## The diagnostic answered it

From your log, on the merged corpse menu:

    [rummage] menu TarkovInventoryMenu, 158 slots
    [rummage]   menuSlot 90 -> RummageCorpseContainer[39] target local=39
    ...
    [rummage] menu indices Rummage will consider: 90,91,...,130

**Target resolution is correct.** Rummage resolves every corpse slot in the merged menu,
against a `RummageCorpseContainer`, at the right container indices. Nothing is wrong with the
Tarkov layout, and my earlier "this may be unfixable" was wrong on the facts.

Which means Rummage's recomputed bitset is NOT empty - it has bits 90-130 - so it *does* send
a state packet that replaces the stale one from the corpse menu we swapped out.

And then, one line later, we sent `ClearRummageMask` and wiped it.

The clear I added in 1.10.0 was unconditional. It was written for the case where Rummage sends
nothing, and in that case it is right; here Rummage sends the correct answer and we deleted
it immediately afterwards. A fix for one branch, applied to both.

## The fix

The clear is now conditional on the merged menu resolving **no** Rummage targets:

- **Targets exist** (your corpses): Rummage sends a correct bitset that overwrites the stale
  one. We send nothing and stay out of the way.
- **No targets** (a container Rummage cannot see through, e.g. the corpse's item-handler
  backpack section): Rummage skips its packet, the stale mask would survive, so we clear it.

`RummageCompat.capture()` already walks every slot for the snapshot, so this costs one boolean
off work that was being done anyway.

## What you should see

Corpse slots hatched until searched; your own equipment and gear untouched. The backpack
section stays gated behind the body's search, as before.

If gear is still masked after this, the remaining suspect is the ordering of Rummage's packet
against ours during the nested container-open, and the next step is a client-side dump of
`ClientRummageManager.MASKED_MENU_SLOTS` to compare against the 90-130 the server computed.

## Verification

Six checks against the extracted zip, all clean.
