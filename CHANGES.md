# dayzhud 2.0.1 - the search overlay, and the check that should have caught it

**Complete.** 52 files. Unzip over the repo root. **Rummage should be removed from the pack.**

## 2.0.0 would not have compiled

`TarkovInventoryScreen.render` called `drawSearchCover(graphics)` and that method did not
exist. The interrupted turn had added the call site and not the method; I shipped it.

**Nothing local could see it, for a reason worth writing down.** `TarkovInventoryScreen`
extends `AbstractContainerScreen`, which is a missing symbol here - so javac treats the
supertype as an error type and *suppresses* unresolved-member errors on the whole class. It
cannot know what the missing parent provides, so a call to a method nobody wrote looks
exactly like a call to an inherited one. Almost every class in this mod extends a Minecraft
type, so this blind spot covers most of the code.

Two fixes:

1. **`drawSearchCover` is now written.** Unsearched slots get a dark hatched cover instead of
   reading as plain empty - the items are genuinely absent (SearchedContainer never sends
   them), so without a cover a body mid-search looks like a body that was already looted. The
   indices come from the server against this same menu, so a cover always lands on the slot it
   belongs to.

2. **A textual check for calls to methods that were never written.** It collects
   statement-position calls, drops anything declared anywhere in the tree and a denylist of
   inherited Minecraft methods, and reports the rest. Confirmed against the 2.0.0 tree: it
   flags `drawSearchCover`. Seven checks now, and the verdict line is still last.

I also restored the unresolved-METHOD scan I dropped when consolidating the script two builds
ago. That one would not have caught this either, but losing it silently was its own mistake.

## Everything from 2.0.0

Our own container search: `SearchedContainer` masks at the container, so the server never
sends unsearched items and there is no index to translate between mods. Timed reveal with a
sound, per player per container, backpack gated behind finishing the body. Settings in
`config/dayzhud-search.toml`. Rummage compat, its packet, config key, `/market rummage` and
its logging are all gone.

## Verification

`RESULT: PASS (7 checks)` against the extracted zip.
