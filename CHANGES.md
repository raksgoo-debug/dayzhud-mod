# dayzhud 1.10.2 - the diagnostic now captures the right menu

**Complete.** 46 files. Unzip over the repo root.

## The diagnostic was unusable and that was my mistake

Your log shows it working exactly as written and telling us nothing:

    [rummage] menu InventoryMenu, 46 slots
    [rummage] NO slots resolve a target

`InventoryMenu`, not the corpse. Typing `/market rummage` opens chat, which closes the
container, so by the time the command ran `player.containerMenu` was already back to the
player's own inventory. Both runs at 14:00:32 and 14:01:12 were before the corpse even
opened at 14:01:41.

The snapshot is now taken **at redirect time**, in the same place the merged menu is opened,
and stashed per player. `/market rummage` prints that snapshot instead of reading the live
menu. It also logs itself once per distinct menu shape, so `latest.log` will have it whether
or not you run the command.

**What to do:** open a corpse, then either run `/market rummage` or just send me
`latest.log` - the `[rummage]` lines will now describe the corpse menu.

## Two other things your log settled

**Magazines: no verdict yet, because you never opened the shop.** There is no "Market
catalogue rebuilt" line in 12,845 lines, and that line is written every time the catalogue is
built. The catalogue is built lazily on first market open, so it never ran. What the log does
show is 56 `Discovered magazine family` entries, so TaCZ Magazines is populating its registry
fine and the 1.9.1 fallback should stock them. Open the trader once and the rebuild line will
say how many magazines went in.

**The corpse redirect is firing.** `Opened the merged corpse view for Rakarts (43 slots, 2
curios, from com.raiiiden.ragdollifiedpc.menu.CorpseMenu)`. So the merge itself is working and
the question really is only about Rummage's targeting.

## Verification

Six checks against the extracted zip, all clean.
