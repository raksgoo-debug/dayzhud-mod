# dayzhud 1.5.0 - the market fills the window

**Cumulative.** Replaces every earlier zip except the original 1.1.0 update. Sixteen files.

## The panel is responsive now

It sizes itself to the window - up to 640x400 GUI units, which is the whole screen at GUI
scale 3 on 1080p - instead of sitting in a fixed 400x256 box. Bigger window means more stock
rows and more categories visible, not the same five rows with more padding around them.

**Why this needed a rewrite rather than a bigger number.** Slot x/y are final in 1.20.1, so a
container screen cannot move its slots the way it moves its artwork. The whole layout is now
drawn in absolute screen coordinates, and `leftPos`/`topPos` are set by hand to drop the
menu's fixed 162x106 slot block wherever the layout wants it. The rule for anyone touching
this file later is in the class javadoc: panel geometry absolute, slots via leftPos/topPos,
never both in one expression.

Two consequences worth knowing:

- **The sell tray is one row of nine directly above the inventory**, not a 3x3 off to the
  side. Its offset from the inventory is frozen at construction, so the only placement that
  stays correct at every panel size is one defined relative to the inventory itself.
- **`hasClickedOutside` is overridden.** Vanilla measures "did they click outside the window"
  from leftPos plus imageWidth, and leftPos now points at the slot block rather than the panel
  corner - without the override, clicking the stock list while holding an item would have
  thrown it on the floor.

## Sizes that were checked rather than eyeballed

Fixed column widths looked fine at 640 and collapsed the stock list to **six pixels** at
320x240, which is the floor Minecraft guarantees. Columns are proportional with floors now,
and the side columns give ground to the list when it is still too narrow. A short window also
gets a compressed header instead of spending its only list row on a title.

Walked the arithmetic across six window sizes before shipping - a compiler cannot catch a
negative column width, and this is the third layout bug in this screen that only existed at
one size.

Also bigger, now that there is room: 3x item preview in the details panel, an x64 quantity
button alongside x1/x5/x16, a scaled-up title and balance, and 24px list rows.

## Verification

Parse-checked over the merged tree; layout maths checked numerically at 320x240, 427x240,
640x360, 960x540, 1280x720 and 1920x1080. Minecraft and Forge are not on the classpath here,
so this proves syntax and the mod's own cross-class references and nothing about MC/Forge
calls. CI is the real check.
