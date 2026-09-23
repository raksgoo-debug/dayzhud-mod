# dayzhud 2.8.2 - flat-tilt off by default, plus diagnostics

**5 changed files.** Unzip over the repo root, on top of 2.8.1.

## About the report: big icons only showing at normal size, and appearing to move around

I can't be fully certain of the cause from screenshots alone, so this drop does two things
rather than one guessed fix: reverts the part I'm actually suspicious of, and adds logging so
the next test gives facts instead of another guess. That's deliberate, not a shrug - three
guessed fixes in a row already went wrong this week (a private method, a rotation class, a
boxing rule), and this project's own notes elsewhere say it plainly: instrument the chain
before theorising about it.

**What I'm suspicious of, and reverted:** the "lay flat" tilt shipped in 2.8.0 defaulted to
55 degrees. That rotation is applied, then the icon is scaled *non-uniformly* to stretch into
a wide, short rectangle (a 4-wide, 2-tall footprint isn't square). Tilting something in 3D and
then squashing it unevenly in 2D is exactly the kind of combination that can render as a thin,
barely-visible sliver rather than a flattened gun - which would look precisely like "the
visual only occupied 1 slot": not because the big render didn't happen, but because it
happened and came out nearly invisible, leaving only vanilla's own untouched small icon
underneath actually visible.

`flatItemAngleX` now defaults to **0** (off). With it off, the render is exactly the plain
non-uniform stretch from 2.7.0 - reasoned-but-unverified in its own way, but not the newest,
least-tested change. If the problem persists with it at 0, that theory was wrong and it's
something else; if it goes away, turn the angle back up gradually (it reloads live, no
restart) to find where it starts breaking down.

**What I can't explain from the screenshots, and am not guessing at:** the icon appearing to
"move" - to the top of the inventory after placing something in the backpack, or near the
helmet slot after equipping a weapon. My best guess is that this is actually vanilla's own
"item follows your cursor while carried" rendering (documented as unstyled/normal-size back in
2.6.0), showing up wherever the mouse happened to be at the moment of the screenshot, rather
than anything tied to where the item is actually stored - none of this mod's code draws
anything at the cursor's position, only at a slot's own fixed position, so a big icon "moving"
on its own isn't something the placement/reservation code could do. But that's a guess too,
and the logging below will show definitively whether an item ever actually gets written to a
slot it shouldn't be in.

## New: `grid.debugLogging` in `dayzhud-grid.toml`

Off by default. Turn it on, do the repro (place a rifle, equip one, move one to the backpack),
then turn it back off - it logs a line for every multi-cell pickup, every placement, and every
big-icon draw (menu slot index, screen position, footprint), and that last one repeats every
frame something is on screen, so a few seconds is plenty. The log lines are worth reading even
if the flat-tilt revert turns out to be the whole fix, just to confirm the reservation math is
landing where it should.

## Verified

Pulled the same full deduplicated error-message list as the last two drops and diffed it
directly against the last known-good one - identical, meaning nothing in this change
introduced a new error class. **Still nothing here has run in game**; this drop especially is
a diagnostic step as much as a fix, and its own honesty depends on what the log says next.
