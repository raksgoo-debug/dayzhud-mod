# dayzhud 2.2.1 - the mask was on the wrong slots

**Complete.** 53 files. Unzip over the repo root; see `DELETE.txt`.

## The hotbar/backpack bug - your description was the whole diagnosis

"The masked slots were in the hotbar but the actual items were in the backpack" is exactly
what was happening, and it names the cause.

The mask was built as `menuIndex = menuOffset + containerIndex`. That assumes the corpse's
slots appear in the menu in the container's own order. They do not: the corpse column is laid
out **armour head-down, then curios, then inventory, then hotbar**, while the container numbers
itself **hotbar 0-8, main 9-35, armour 36-39**. So a cover computed for container slot 3 (a
hotbar item) was painted onto whatever the menu happened to have third - and the item it was
meant to be hiding stayed invisible somewhere else until its own slot got revealed.

Covers on empty hotbar slots and an item appearing in the bag when one of them "opened" are
both that same off-by-a-whole-layout error.

It now walks the menu's actual slots and masks the ones whose container is the corpse and whose
item is present. No offset, no assumption about ordering. **I deleted `maskedSlots` and
`searchedMenuOffset` outright** rather than leaving them for the next caller to trip over -
they only ever encoded the wrong assumption.

## Details panel

- **The scroll hint was the thing on the price**, not the stats - it was drawn at the bottom of
  the stat region, which ran right up to the price band. The region is a line shorter now and
  the hint sits in it.
- **The preview moved down 6px.** At 2x an icon is 32 tall and was touching the ITEM DETAILS
  rule above it.
- **Item names are drawn at 0.75**, matching the stats. Names in this pack run to "Ballistic
  Armor Co. 'Bastion' Helmet (MultiCam)", which at full size is three lines in a 152px column.

Re-walked the whole column at five window sizes and both name lengths: preview clear of the
header, name clear of the stats, stats and hint clear of the price, everywhere.

## Verification

`RESULT: PASS (9 checks)` against the extracted zip.
