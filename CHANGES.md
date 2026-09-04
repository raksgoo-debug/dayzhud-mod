# dayzhud 1.12.0 - the corpse mask, fixed properly

**Complete.** 46 files. Unzip over the repo root.

## The log settled it

    [rummage]        menu TarkovInventoryMenu, 160 slots   (server)
    [rummage]        menu indices Rummage will consider: 90,91,...,132
    [rummage-client] menu has 160 slots, masked set = {0, 1, 2, 3, 5, 6, 34, 35}

**160 slots on both sides.** So the client and server build the identical menu and the indices
agree - my earlier "client/server slot-count mismatch" theory was wrong, and this rules it out
rather than arguing about it.

The client is simply holding the bitset from the corpse menu we replaced. Those eight bits are
the corpse's own occupied slots in *its* menu's numbering, painted onto ours, where 0-6 are
your armour and gear.

And 1.11.0's conditional clear made it permanent. I reasoned: targets exist, therefore Rummage
will send a replacement, therefore do not clear. The first step is true and the second is not -
it recomputes on container open but never sends one for the replacement menu. So the condition
protected a packet that never arrives.

## The fix

We send the mask ourselves. `RummageCompat.computeMaskIndices` walks the merged menu the same
way Rummage does - resolve each slot's target, look up that container's per-player
`getRummageProgressByUUID` bitset, and include the slot if its local index is not yet marked
searched - and the result is applied to the client's `MASKED_MENU_SLOTS` directly.

Only the initial state needed this. Rummage's later updates, once you actually start searching
inside our screen, are computed against `player.containerMenu`, which is our menu by then, and
were always correct. That is why the backpack appeared at the right moment while the initial
mask was nonsense.

## What you should see

Corpse slots hatched until searched. Your own equipment and gear untouched. Backpack still
gated behind finishing the body.

The `[rummage]` and `[rummage-client]` log lines stay in - they cost nothing and if this is
still wrong, the two numbers side by side will say why.

## Verification

`RESULT: PASS (6 checks)` against the extracted zip.
