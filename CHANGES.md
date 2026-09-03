# dayzhud 1.6.0 - build fix, plus attachments in the shop

**Complete, not incremental.** 39 files: 28 new, 10 changed, plus gradle.properties. Unzip
over the repo root and the whole market feature is in one consistent state.

## What broke the 1.5.1 build

`MarketConfig` declared `STOCK_ARMOR` and never assigned it. A `static final` with no
assignment on every path is a compile error, and it is the second time this exact trap has
bitten a config class in this project.

**My local compile could not see it, and that is the point worth recording.** ForgeConfigSpec
is not on the classpath here, so every field resolved to an error type and javac skipped
definite-assignment analysis entirely - the file looked clean. The fix is a ~25-line stub
ForgeConfigSpec that exists only so the compiler will actually run flow analysis on that one
file. Run against the shipped 1.5.1 it reproduces CI's error exactly; run against this build
it is silent.

I also now **verify the extracted zip over a pristine copy of the repo**, not my working tree.
Both of the last two failures were the artifact and the thing I checked being different trees.
That loop is closed: build zip, extract, check, and only then send.

## Attachments are now stocked

`tacz.stockAttachments` and `tacz.attachmentPrice` existed in the config but nothing read
them - the config promised a feature that could never appear. TACZ attachments are now a real
**ATTACHMENTS** category, priced from their own index, with sections for OPTICS, MUZZLE,
GRIPS, STOCKS and EXTENDED mags. `tacz:attachment/<id>` overrides the derived price.

Two lrtactical config keys orphaned by removing `LrTacticalCompat` are gone with it. Nothing
in the config now describes behaviour that does not exist.

## Verification

Four checks, run against the extracted zip:

1. Config definite-assignment, via the ForgeConfigSpec stub.
2. No references to removed classes.
3. No unresolved first-party symbols (the 1.5.0 failure).
4. No errors outside the expected missing-Minecraft noise.

Minecraft and Forge still are not on the classpath, so MC/Forge calls remain unverified. CI is
the real check - but these three specific failure modes cannot reach it again.

## Config note

Delete `config/dayzhud-common.toml`. There are keys from every version in this range, and two
removed ones.
