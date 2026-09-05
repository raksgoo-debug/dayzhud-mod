# dayzhud 2.2.2 - the covers never drew, and two cells that could never uncover

**Complete.** 53 files. Unzip over the repo root; see `DELETE.txt`.

## Why nothing was hatched on a fresh body

`maskedBodyMenuSlots` matched slots with `slot.container != searchedContainer`, and
`searchedContainer` was set to the **delegate** - the raw corpse container - while the menu's
slots are built on the **`SearchedContainer` wrapper**. Different objects, so nothing ever
matched, and every mask came out empty. Items hidden, nothing drawn over them: exactly your
first screenshot.

Yesterday's fix replaced an offset assumption with a slot walk, which was right, and then
compared against the wrong one of the two container references. The menu keeps both now, with
a comment saying which is for what: the **wrapper** for slot identity, the **delegate** for
progress and for asking whether a slot actually holds anything.

That last part matters and is easy to get backwards - occupancy has to be read from the
delegate, because the wrapper reports a hidden slot as empty, and "empty slots are never
masked" would then unmask everything the moment it was masked.

## The two stuck cells at the bottom of the bag

The scrolling bag view can show more cells than the bag actually has. `isBagSlotHidden` was
covering those too, and the search walk only ever visits real slots - so nothing existed that
could ever lift them. They now report unhidden when the index is past the real bag size, and
also when the slot is empty, matching the rule the body already follows.

## Verification

`RESULT: PASS (9 checks)` against the extracted zip. Nothing in your log points at anything
else; the only "search" lines in it belong to other mods.
