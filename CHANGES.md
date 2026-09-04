# dayzhud 1.11.3 - build fix

**Complete.** 46 files. Unzip over the repo root. Same contents as 1.11.2: the
`[rummage-client]` logging is what to test.

## What broke

My patch inserted the new `rummageLogTicks` field between the existing `@Override` and
`init()`, so the annotation landed on a field.

## The part worth recording

**My checker caught this and I did not read its output.** It printed

    src/main/java/.../TarkovInventoryScreen.java:83: error: annotation interface not
    applicable to this kind of declaration

and I ran it as `check.sh | tail -6`, which showed only the last two sections. The failure was
four lines above the cut.

Five of the six CI failures this session were things the local checks could not see. This one
they could, and I threw it away at the terminal.

So the script no longer relies on me reading all of it: every check now reports through one
accumulator and the **last line is a verdict** - `RESULT: PASS (6 checks)` or `RESULT: FAIL
(n check(s))` with the offending output above it. Tailing it is now safe by construction.

Confirmed against the broken 1.11.2 tree: `RESULT: FAIL (1 check(s))`. Against this one:
`RESULT: PASS (6 checks)`.

## Still waiting on

Open a corpse with this build, wait a second, send `latest.log`. The `[rummage-client]` line
says whether the client's menu has the same 158 slots the server saw, and which bits it is
actually masking.
