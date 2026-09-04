# dayzhud 1.11.1 - build fix

**Complete.** 46 files. Unzip over the repo root. Same contents as 1.11.0 with the compile
error fixed: the conditional mask clear is what you want to test.

## What broke

`rummageTargets` was declared twice in both redirects - my patch applied its block twice
again. Fourth CI failure this session from that one cause.

## Why the detector I added last time did not catch it

The block detector needed six identical consecutive lines. After stripping comments and short
lines like `}`, the duplicated fragment was three qualifying lines. I set the threshold to
silence false positives without checking it still caught the thing it was built for - which is
the same mistake as shipping a fix without testing it.

So there is a second check now, and it looks for the **error** rather than the shape of the
edit: a local variable redeclared while an earlier one of the same name is still in scope,
which is exactly what javac reports and what it cannot tell me locally (a method body touching
a missing Minecraft type stops attribution before that check runs).

It keeps a real scope stack, so sibling blocks reusing a name are not flagged - a flat
per-method version reported twelve false positives on legal code. **I ran it against the
broken 1.11.0 file and confirmed it reports `'rummageTargets' redeclared at line 169 while the
one from line 157 is in scope`.** A check that has not been shown to fail on a known-bad input
is not a check.

## Verification

Seven checks against the extracted zip, all clean.
