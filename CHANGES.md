# dayzhud 1.8.1 - corpses stand down for Rummage too

**Complete.** 45 files. Unzip over the repo root.

## What your screenshot shows

Rummage was masking your EQUIPMENT and GEAR slots while leaving the corpse column untouched -
the mask landed on menu slots 0-5, which in the merged view are your own armour and curios.

## What I found in Rummage's code, and what I did NOT find

`CommonContainerUtil.getTarget(Slot, menu)` resolves in three steps:

1. `getTarget(slot.container, slot.getContainerSlot())` - correct for a plain Slot on a
   rummageable container.
2. If the slot is a `SlotItemHandler`, `getWrappedTarget(handler, index)` - which only
   understands Forge's `InvWrapper` and `SidedInvWrapper`.
3. Otherwise `getMcrTarget(menu, index)`, which looks for a `boundBlockEntity` field and
   returns null for our menus.

So the corpse column - `CorpseLootSlot extends SlotItemHandler` over this mod's own
`CorpseLootHandler`, which is neither wrapper type - falls through all three and resolves to
no target at all. That explains the corpse column being unmasked.

**It does not explain the equipment slots being masked**, and I have not pinned that down. The
most likely candidate is that Rummage's client screen mixin paints the mask positionally over
`menu.slots` from the synced state, in which case the rummageable container's slots have to
occupy menu indices 0..N - which the Tarkov layout will never do, since equipment comes first.
If that is what is happening, "make it work in the merged view" is not fixable from this side
without either reordering the whole menu (which breaks every index in `quickMoveStack`) or an
upstream change to target by container rather than position.

I am not going to guess at it a third time. What would settle it in one in-game run is a
diagnostic that dumps, for every slot in the open menu, its index, container class, container
slot and whether Rummage resolves a target. Say the word and I will add it.

## What this build actually changes

`CorpseOpenRedirect` now stands down for an unsearched corpse, the way `ContainerOpenRedirect`
already did for containers. So:

- Right-click a fresh corpse: Rummage's own screen, searched correctly, slot by slot.
- Right-click it again once searched: the merged Tarkov view, everything visible.

That also gives you the inventory-then-backpack order you asked for, by accident of how
Rummage already works - the corpse's own container is what gets searched, and its backpack is
a separate container reached afterwards. Doing that ordering *inside* the merged view is
blocked on the same unresolved question above: the client cannot answer "is this fully
rummaged" for a corpse, because the container it holds is a synced copy, not the rummageable
original.

`access.respectRummage` (default on) covers both redirects. Turn it off and you get the merged
view immediately - and the mis-targeted mask from your screenshot back with it.

## Verification

Four checks against the extracted zip, all clean. The Rummage integration is reflective, so a
wrong method name there fails at runtime with a logged warning rather than at compile time.
