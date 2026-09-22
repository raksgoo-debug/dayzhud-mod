# dayzhud 2.4.2 - search only covers slots that hold something

**5 changed files.** Unzip over the repo root, on top of 2.4.1.

## What changed

Searching used to cover every slot of a container and reveal them one at a time, empty or not -
a corpse is 40-odd slots, so the sweep spent most of its time uncovering nothing.

Now only slots with an item in them are covered, and only those take a step to reveal. Empty
slots look empty from the moment the screen opens, and the sweep goes straight from one item to
the next. This includes the corpse's backpack.

The code for this was already there as `maskEmptySlots`; it was just off the default. The
default is now `false`.

## Read this before testing

Forge only writes defaults into a config file that does not exist yet. If
`config/dayzhud-search.toml` already exists it still says `maskEmptySlots = true` and nothing
will appear to change. Set it to `false`, or delete the file.

## Also

- The search sound now also counts the bag. With empty slots uncovered, a corpse with bare
  pockets and a full pack has nothing to find in the body, so the old body-only check would have
  made it open in silence.
- Reworded the `ticksPerSlot` and `maskEmptySlots` config comments, which described the old
  default. `ticksPerSlot` is unchanged at 5, but it is now the pace per ITEM, so a sweep is far
  shorter than before - raise it if it feels rushed.

## Trade-off

Covering only occupied slots means the hatching itself shows where the loot is. That is what was
asked for; `maskEmptySlots = true` puts the old behaviour back.

## Verified

The real `SearchProgress` was compiled against stubs and run in both modes: a container with three
items and one bag item takes 14 steps with `maskEmptySlots = true` and 4 with it off, in order
[1, 4, 8, bag 2]; an all-empty container takes none; a bare body with a full pack goes straight to
the pack. That covers the sweep logic only. Nothing that touches Minecraft or Forge has been
compiled - CI is the check for that, and the in-game look of the cover is untested.
