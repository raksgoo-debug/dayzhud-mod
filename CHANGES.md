# dayzhud 1.10.0 - deterministic mask clear, and a diagnostic instead of another guess

**Complete.** 46 files. Unzip over the repo root.

## What changed

**1. The mask clear is now a packet, not a screen hook.** 1.9.0 cleared Rummage's client mask
in `TarkovInventoryScreen.init()`, which runs the moment the open-screen packet arrives - and
Rummage's own state packet may land either side of that. A race I did not notice. The clear is
now an explicit S2C packet sent immediately after the redirect opens the merged menu. A packet
sent later on the same connection cannot arrive earlier, so the ordering is fixed rather than
hopeful.

**2. `/market rummage` (op).** With the corpse screen open, run it. For every slot in the menu
it prints the container class, the container-relative index, and whether Rummage resolves a
target - ending with the exact list of menu indices Rummage will mask.

That one line settles which of two very different bugs this is:

- **If it lists the corpse's slot indices** (high numbers) and your equipment is still what
  gets hatched, the resolution is right and the client is drawing a stale or mis-indexed set -
  a client-state problem.
- **If it lists 0-5, or nothing at all**, then either the wrong slots resolve targets or none
  do, and the corpse column can never be masked as things stand - a resolution problem.

I have now been wrong twice about which of those it is, both times from reading a screenshot
instead of the running state. The output also goes to `latest.log` under `[rummage]`, so you
can paste it rather than retype it.

## Being straight about this one

I have not fixed your bug in this build. The packet ordering was a real defect and worth
fixing on its own, but I do not know that it was *the* defect, and shipping a third
speculative fix would waste another one of your test cycles. The diagnostic costs you one
command and tells me exactly where to look.

## Verification

Five checks against the extracted zip, all clean, including the new unresolved-method scan.
