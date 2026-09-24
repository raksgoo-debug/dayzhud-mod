# dayzhud 2.10.2 - the actual glow, not just the box

**5 changed files.** Unzip over the repo root, on top of 2.10.1 - same three PNGs, no Java.

## What 2.10.1 fixed, and what it didn't

2.10.1 fixed a real bug: a wrong background-brightness floor left a faint but uniform
rectangular wash across the *entire* crop. That's gone.

What was still there, and what you were seeing: the reference image itself is a soft,
glowing render, not a crisp line drawing - checked directly this time rather than assumed.
A histogram of the rifle crop shows why: about 30% of the crop's pixels sit somewhere
between pure background and the gun's brightest edges, and that population is *smooth*, not
clustered - there's no clean brightness cutoff separating "glow" from "gun." My extraction
curve (`alpha ** 0.6`) was tuned for a crisp source with a thin anti-aliased edge; on a
source that's actually soft-shaded, that curve *boosts* the mid-brightness glow into
visibly opaque territory instead of suppressing it. That's the haze around the silhouette
you were still seeing - real gradient content in the source image, not a leftover
background-floor bug.

## The fix

Steeper curve: `alpha ** 2.5` instead of `0.6`. This crushes the wide, soft mid-brightness
population toward transparent while keeping the brighter, tighter core - the gun's actual
edges and detail lines - clearly visible. Tried 1.0/1.8/2.5/3.5 side by side before picking
this one: 3.5 kills the glow completely but starts eating real detail (the gun reads as a
thin wireframe rather than a filled shape); 2.5 is the point where the glow is gone and the
shape still reads as solid.

Checked at three sizes this time, not one: the raw 660x200-scale extraction, the actual
saved 128px-wide asset, and a composite scaled down to roughly the real in-game render size
(the loadout box's actual ~52x22px interior) - no visible surrounding artifact at any of the
three, on all three icons.

## Verified

Binary asset change again - no code, no compiler to check it against. Verified the only way
that actually matters here: composited onto the real slot background and looked at it, at
multiple sizes this time specifically because "looked fine once" wasn't enough last time.
