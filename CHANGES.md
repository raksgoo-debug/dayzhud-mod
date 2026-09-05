# dayzhud 2.3.0 - every slot is covered, not just the ones with loot

**Complete.** 53 files. Unzip over the repo root; see `DELETE.txt`.
**Delete `config/dayzhud-search.toml`** so the new key and the retuned default land.

## The change

Covering only the occupied slots drew a map of the loot. The hatching told you which slots
were worth waiting for before you had searched anything, which leaves the search with nothing
left to find out - it becomes a delay, not a mechanic.

Every slot in a searchable container is now covered until it is searched, empty ones included.
`search.maskEmptySlots`, on by default.

## Two details that matter

**An empty slot costs a step.** The obvious shortcut is to skip empties for free so the sweep
finishes quicker - but then it visibly pauses only where the loot is, which gives the position
away exactly as plainly as not covering them did. Uniform timing is the whole point.

**Out-of-range bag cells are still never covered.** The scrolling bag view can show more cells
than the bag has, and the search only visits real slots - a cover out there would have nothing
that could ever lift it. That exclusion is about reachability, not emptiness, so it stays.

## Retuned timing

`ticksPerSlot` drops from 12 to 5, because there are now forty-odd slots on a corpse rather
than the handful that held something:

    corpse (43 slots)          11.2s
    corpse + 27-slot backpack  18.0s
    single chest                7.2s
    double chest               14.0s

At the old 12 a corpse with a full pack took 42 seconds. Raise `ticksPerSlot` if you want
searching to be a bigger commitment; the numbers above scale linearly.

## Verification

`RESULT: PASS (9 checks)` against the extracted zip.
