# dayzhud 1.10.1 - build fix

**Complete.** 46 files. Unzip over the repo root. Same contents as 1.10.0 with the compile
error fixed - the deterministic mask-clear packet and `/market rummage` are both in here.

## What broke

`RummageCompat.resolve()` had its last seven lines twice, so `util` and `target` were declared
twice in one method. My patch matched and applied to a block that already contained the change.

## Why my check did not catch it, again

Same shape as the `STOCK_ARMOR` failure. `getTargetForSlot = util.getMethod("getTarget",
Slot.class, AbstractContainerMenu.class)` names two Minecraft types that are not on the
classpath here, so javac abandoned attribution of that method body before it ever reached the
duplicate-declaration check. The file compiled clean locally and failed in CI on a plain
scoping error.

That is now **three** CI failures in this session from one root cause: a text patch applied
twice, hidden by missing Minecraft types. The duplicated `drawSidebar` body, the duplicated
`isFullyRummagedForPlayer` field, and this.

So the check no longer relies on the compiler noticing. It scans for the *shape* of the
mistake instead: any run of six or more identical consecutive statements appearing twice in
one file. Three lines was too noisy - `super(view, index, x, y);` and friends recur honestly -
but at six it reports zero across the tree and would have caught all three of these. It runs
against the extracted zip alongside the other checks.

## Verification

Six checks against the extracted zip, all clean: config definite-assignment via stub, removed
classes, unresolved first-party classes, unresolved first-party methods, other errors, and
duplicated blocks.
