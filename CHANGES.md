# dayzhud 1.11.2 - client-side mask logging

**Complete.** 46 files. Unzip over the repo root.

## What your screenshots narrowed down

Six slots masked, on your side, at the very start of the menu. The corpse had exactly six
occupied slots - four gear, two hotbar. Rummage only sets a bit for a slot that still needs
searching, so **six bits is the right number** and they are landing at the wrong indices.

The server log already proved the server computes them correctly: every corpse slot resolves a
`RummageCorpseContainer` target, at menu indices 90-130. The client is masking 0-5. So the
client is holding a bitset that is not the one the server produced for this menu, and there
are only two ways that happens - the bits arrived from a different menu, or the client's menu
has a different slot list from the server's.

Both are visible in one line, so this build prints it:

    [rummage-client] menu has N slots, masked set = {...}

Sampled one second after the corpse screen opens, not in `init()` - the packets from the menu
swap have not all landed at that point, which is the same timing mistake I made with the
clear.

Compare against the server's `menu TarkovInventoryMenu, 158 slots`:

- **Same slot count, bits at 0-5** - the bits came from the corpse menu we replaced, and the
  conditional clear is not firing when it should.
- **Different slot count** - the client is building a different menu from the server, and the
  indices can never line up until that is fixed.

## Also good news in your screenshots

The searching sounds play and the backpack appears after searching. That is the corpse gate
working exactly as intended - pockets first, then the pack.

## What I need

Open a corpse, wait a second, then send `latest.log`. One `[rummage-client]` line settles it.

## Verification

Seven checks against the extracted zip, all clean.
