# dayzhud 1.5.1 - build fix, and a packaging fix so this cannot recur

**This is the COMPLETE market feature, not a patch on top of earlier zips.** 38 files: 28 new,
9 changed, plus gradle.properties. Unzip over the repo root and you have everything from 1.1.0
through 1.5.0 in one consistent state. If you have applied earlier zips, this simply overwrites
them.

## What broke the build

`MarketPrices.java` referenced `LrTacticalCompat`, a class that exists in my working tree and
that **I never put in a zip**. Every pack since 1.2.0 was assembled from a hand-written list of
filenames, so a class added later was silently left out while the file that calls it shipped.
My local compile passed because it ran against the working tree, where the class is present -
the check and the artifact were not the same thing, which is the whole failure.

Two fixes:

1. **`LrTacticalCompat` is deleted rather than shipped.** It produced byte-identical keys to
   `NbtVariants` (`lrtactical:consumable/lrtactical:ai2` from both), so it was pure
   duplication - and two mechanisms deciding the same thing drift apart the moment one is
   edited. NbtVariants is the one that stays: it is config-driven and covers the next mod
   built this way. `market.lrDefaultPrice` is gone with it.
2. **This zip was built by diffing the merged tree against the pristine repo**, not from a
   filename list. Every file that differs from your last upload is in here by construction, so
   a new class cannot be forgotten again. Future zips will be built the same way.

## Everything included, in one place

- Rouble wallet, absorbed from notes on pickup, survives death; `/money`, `/money pay`.
- Trader on a terminal block (tarkovdayz PC/safes) or a laptop inside a safe zone;
  `/market zone add`.
- Full-window responsive market UI with BUY/SELL tabs, category sidebar expanding into
  sections, scrolling stock, details panel, and confirmation on buy/sell/withdraw.
- 161 price rows; TACZ guns and per-calibre ammo priced from their own ballistics; unlisted
  armour priced AND stocked from its own stats (all 238 caps_awim pieces); lrtactical's 20
  NBT-variant items.
- `/market debug` for when the shop looks empty.
- Bottled water item, model and texture.

Version bumped to 1.5.1.

## Config note

Delete `config/dayzhud-common.toml` - there are keys from every version in this range and Forge
only writes defaults into a file that does not exist yet.

## Verification

Parse-checked over the merged tree with a targeted scan for unresolved FIRST-PARTY symbols,
which is exactly the class of error CI caught here and my earlier filtering hid among the
expected missing-Minecraft noise. Minecraft and Forge are still not on the classpath, so
MC/Forge calls remain unverified. CI is the real check.
