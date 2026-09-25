# dayzhud 2.13.2 - backpacks bigger than 36 slots scroll

**2 changed files** (plus version bump).

## Big bags showed as 36 slots

The inventory shows the worn bag through a 4-row window (36 slots) that scrolls over the
bag - but the server counted the bag's usable slots only up to that window size before
syncing the count to the client. Every bag therefore reported at most 36 slots: the
scrollbar never appeared and anything past slot 36 was unreachable from this screen (it
was still there - opening the bag another way showed it).

The count now runs to the bag's real size, capped at the client's 64-slot mirror
(`BackCurioItemHandler.MIRROR_SIZE`, now package-visible for that). The window, the
scrollbar, the scroll packet and the visible-slot logic were all already built for this
and are unchanged; they simply never had more than four rows to scroll.

Worth a look in game: this is the first time the scrolling path runs. A multi-cell item
whose footprint runs past the bottom of the visible rows shows as 1x1 until you scroll so
it fits - the grid only reconciles the rows on screen, which is the existing degrade
behaviour, not data loss.
# dayzhud 2.13.1 - true-to-scale guns, self-centring renders, knives point left

**4 changed files** (plus version bump). Unzip over the repo root, on top of 2.13.0.

## Knives pointing down - fixed

LR melee weapons are held at their model origin, and the tip is the point farthest from it.
2.13.0 placed that origin at (0, 24, 0) - a guess about TACZ's coordinates that was never
checked in game. The knife pointing down pinned the real answer: TACZ's loader (which LR uses)
puts the origin at y = 24 *pixels*, and parts are drawn in *block* units (/16), so in game it's
(0, 1.5, 0). From (0, 24, 0) the "farthest point" of a knife was its bottom edge. The origin is
now derived from the same 16-or-1 unit factor the renderer already measures, so it holds
whichever unit LR uses. (The first-person hand markers were tried as the grip point and
rejected - on bats and karambits they sit off the item.)

## Sizes relative to each other - now true to scale

Checked first whether TACZ's models themselves are off: they aren't. Against published lengths
they're mostly within 7% of real proportions (Glock/AK 0.24 vs real 0.23, AWP/AK 1.39 vs 1.36,
M4/AK 0.97 vs 0.95; the Uzi's stock is modelled folded, the SPAS is 12% long).

The problem was the drawing: every gun was scaled to fill its footprint, so a gun that just
rounded up to an extra cell was drawn noticeably bigger than a near-identical one that didn't.
Now every non-pistol gun is drawn at one shared scale - 2 px per model unit, the same scale the
footprints were sized at - centred in its footprint. Relative sizes are the real ones; a gun
only shrinks when it genuinely can't fit. A fitted suppressor makes a gun longer, not smaller
(the unit conversion is snapped, not taken per gun). The loadout boxes use the same scale.
LR melee/consumables too.

Pistols still fill their 2x1, as you asked earlier - at true scale a Glock would use about 58%
of its box. Say if you'd rather have pistols to scale as well.

## Off-centre renders - corrected from the actual draw

2.12-2.13 centred on a separate measuring pass, and some guns still came out off-centre:
something TACZ draws differently at runtime than in that pass, which I couldn't reproduce
offline (the gun models themselves are ruled out). So centring no longer depends on knowing
the cause. Each real draw now goes through a pass-through buffer that forwards everything
unchanged and records where the vertices actually landed on screen. The next frame corrects by
the difference, accumulating, so it settles immediately and stays centred. Effects (render
types with under 8 quads - muzzle flash and the like) are ignored for this, as for sizing.

`debugLogging = true` logs each correction ("drawn N px off-centre ...") - if you see a gun
correct every single frame instead of once, that log is what I'd need.

## Known issue, unchanged

The Apocalyptic Arsenal katana is built from many separately angled thin segments and still
comes out diagonal and small.

## What the build has to confirm

Nothing new beyond 2.13.0's list: the pass-through uses only calls already used for the
measuring pass (the same buffer swap and VertexConsumer method set).
