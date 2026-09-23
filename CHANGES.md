# dayzhud 2.9.0 - the actual bug, found from the log

**5 changed files.** Unzip over the repo root, on top of 2.8.2.

## What the log actually showed

Thank you for the log - it found the real bug in one pass, which is exactly the point of
adding it instead of guessing again.

`grid draw` fired for **three** different menu slots (13, 14, 24) at the same time, all
`modern_kinetic_gun` (that's TACZ's one shared item for every gun - the specific weapon is in
its NBT, not the item type, so three different rifles all log identically), all footprint
4x2. Slots 13 and 14 are literally the next cell over from each other - two 4-wide items
sitting one cell apart cannot both have their claimed space, and the log's own pickup/place
history confirms neither 14 nor 24 was ever placed there through this screen's own mechanism
(no matching "grid place" line for either) - they were just already sitting there, most
likely from before `smg`/`rifle` became 4x2 in the config, when 1-cell spacing was completely
fine.

**The actual bug:** once the footprint size changed, `reconcile()` correctly worked out that
only one gun in a contested cluster gets to keep its reserved shadow cells - but the render
code never checked which one that was. It drew *every* multi-cell stack big regardless of
whether it actually won that contest, so a "losing" gun still tried to render at 4x2 and
visually collided with its neighbour. That's what "the visual only occupied 1 slot" actually
was: not a failure to render big, but a big render that lost a fight for the same pixels and
looked like nothing happened.

## The fix

`ItemGrid.hasReservedFootprint()` - new - checks whether a stack's declared footprint
rectangle is actually, currently, all reserved in its own name (not just declared). The
render loop now calls this before drawing anything big: a stack that doesn't hold its
footprint renders as a plain, ordinary 1x1 instead, same as any item without a footprint
would - the graceful degradation `reconcile()`'s own doc always promised on the data side,
now actually kept on the render side too.

This should also explain the earlier "appears at the top inv" / "appears near the helmet"
report from the previous drop, at least partly: with multiple overlapping big renders
fighting for the same pixels, moving one of them away would make whichever one had been
losing (or winning) suddenly look like it "moved," when really it had been sitting there
the whole time, just visually buried under the other one.

**Worth checking once this is in**: any inventory that had guns sitting close together
before 2.8.0's footprint change may still have some of that pre-existing crowding. This fix
makes it render sanely (one big, the rest as plain 1x1 until moved apart), but the actual fix
for the crowding itself is just to drag the affected guns apart once - reconcile() doesn't
retroactively rearrange anything, it only decides who wins a contest that already exists.

## Verified

Same full deduplicated error-message diff as every drop since 2.8.0 - identical to the known-
good baseline, nothing new introduced. This one has actual log evidence behind it rather than
a screenshot-only guess, which is the most confidence I've had in a fix this whole thread -
but the fix itself still hasn't been watched render in game, so the usual caveat still applies.
