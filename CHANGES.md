# dayzhud 2.4.1 - fixes the load crash from 2.4.0

**5 changed files.** Unzip over the repo root. Apply on top of your 2.3.0 tree - this replaces
2.4.0 entirely.

## The crash

    [12:28:22] [main/ERROR]: Mod Sorting failed.
    Detected Cycles: [[ModFileInfo@6b063695, ModFileInfo@464abed]]

Field Kit already declares `dayzhud` with `ordering="AFTER"`. In 2.4.0 I added `fieldkit` with
`ordering="AFTER"` on our side, so each was waiting for the other and Forge refused to sort the
mod list. Nothing loads, and the error names neither mod - just two ModFileInfo object hashes.

Ours is `ordering="NONE"` now, with a comment saying why it must stay that way. Nothing here
needed an order: the price data is a datapack resource and Field Kit's items are looked up by
id when the catalogue is built, long after every mod has registered. **`AFTER` was cargo-cult -
I copied it from the neighbouring entries without asking whether this integration needed it.**

I checked the other eight: `curios`, `ragdollifiedpc`, `firstaid`, `thirst`, `tarkovdayz`,
`tacz` and `taczmagazines` are all `AFTER`, and none of them declares anything about dayzhud,
so none of them cycles.

## A check for it

An ordering cycle is neither a compile error nor a runtime exception - it is a refusal to
start, with no stack trace and no mod names. So it now gets checked: our `AFTER` list is read
out of our mods.toml and cross-referenced against the mods.toml inside every jar you have sent
me, flagging any that declares an ordering against dayzhud in return.

Confirmed against the shipped 2.4.0 file:

    CYCLE: we declare fieldkit AFTER us, and it declares dayzhud AFTER us
           -> Forge cannot sort the mod list

Ten checks now.

## Everything else from 2.4.0 is unchanged

All 31 Field Kit items priced and stocked, and the magazine resolution fix with its logging.

## Verification

`RESULT: PASS (10 checks)` against the extracted zip.
