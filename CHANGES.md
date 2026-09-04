# dayzhud 2.2.0 - loot order, empty slots, one sound, panel overlaps

**Complete.** 53 files. Unzip over the repo root; see `DELETE.txt`.

## Search

**Order is now equipment, inventory, hotbar, backpack.** The corpse container numbers its
slots hotbar-first (0-8), then main (9-35), then armour (36-39) - so walking it by index
searched the belt before the body armour. `searchOrder()` returns the order a person would
actually go through a body; a plain chest has no such expectation and keeps its natural order.

**Empty slots are never hatched.** They were being masked until the walk happened to reach
them, which on the empty rows at the end of a corpse looked like a cover that never lifted.
There is nothing in an empty slot to find, so it is never covered.

**One sound per body, not per slot.** Your clip plays once when you open a corpse that still
has something to find, and nothing after that. Converted to mono Ogg Vorbis at 44.1 kHz,
loudness-normalised to -16 LUFS - Minecraft decodes only Ogg, and a stereo file plays
non-positionally, so a stereo search sound would have followed the listener around instead of
coming from the body.

The per-slot beep is gone. Twelve ticks apart and identical every time, it was a metronome.

## Market details panel

**The name no longer runs through the category line.** The caption and the stat block now
start below however many lines the name took, instead of at a fixed offset - a two-line
helmet name was landing straight on top of it.

**The price row has its own band**, cleared behind, so a long price and the last visible stat
cannot share pixels.

**And when there is genuinely no room, the stats are dropped rather than squeezed.** Walking
the arithmetic across window sizes showed the previous clamp-to-a-minimum drew the stat block
straight over the price at 320x240 and on a two-line name at 640x360. Showing none is honest;
showing them on top of the price is not. Checked at five window sizes and both name lengths.

## Verification

`RESULT: PASS (9 checks)` against the extracted zip.
