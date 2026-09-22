# dayzhud 2.5.0 - typed weapon loadout slots

**9 changed/new files.** Unzip over the repo root, on top of 2.4.2.

## What changed

PRIMARY / SECONDARY / HOLSTER / SHEATH used to be a read-only display: four squares that
just mirrored hotbar slots 1-4, with no restriction on what sat in them and no way to
interact with them directly.

They're now real, clickable, typed slots:

- **PRIMARY / SECONDARY** - any TACZ gun except a pistol (rifle, SMG, shotgun, sniper, MG,
  launcher).
- **HOLSTER** - pistols only.
- **SHEATH** - melee only, via the `dayzhud:sheath_weapons` item tag (ships with vanilla
  swords + trident; add your own knives/machetes to that tag from a datapack).

Still backed by hotbar slots 0-3, same as before - a weapon placed in PRIMARY is still what
you're holding when you press "1" in world. That didn't change; what changed is the slot is
now typed, and it's drawn in the WEAPONS row instead of the plain hotbar row. The hotbar row
underneath now shows 5 slots (4-8) instead of 9, left-aligned in the space that opens up.

An empty slot shows a dim silhouette of what it accepts (a rifle shape, a pistol shape, or a
blade) instead of a blank box, and hovering an empty slot explains the restriction. A full
slot behaves like any other item slot now - vanilla's own tooltip, drag, and shift-click all
work on it, and dropping the wrong category on it just bounces back, the same as trying to put
a sword in a helmet slot.

## New config: `config/dayzhud-weaponslots.toml`

- `primaryGunTypes` / `secondaryGunTypes` / `holsterGunTypes` - lists of TACZ type strings
  (`rifle`, `smg`, `shotgun`, `sniper`, `mg`, `rpg`, `pistol`). Retune without recompiling if
  you want SMGs holster-only, say, or want launchers excluded.
- `sheathTag` - which item tag the SHEATH slot checks. Default `dayzhud:sheath_weapons`.
- `enforce` - master off switch; false makes all four slots plain unrestricted slots again
  (still repositioned into the WEAPONS row).

## Not done here

This is slot restriction and redraw only. It doesn't touch the second thing you asked for -
occupying more than one grid cell - see the design notes below for why that's a separate,
much bigger piece of work and how I'd scope a first version of it.

## Verified

Every new/changed file passes the same `javac -Xmaxerrs 5000` filtered check the rest of this
project uses (real errors only; missing-Minecraft/Forge symbols are expected noise with no
MDK in this workspace - see `ragdollgore-local-verification-limits.md`'s notes on what that
does and doesn't prove). No error in the full-tree pass names anything from this change.

The slot-restriction logic itself (`WeaponSlots.accepts`) can't be unit-tested without the
real TACZ jar on the classpath, so it hasn't been exercised beyond the compile check. First
in-game test should specifically try: a rifle in HOLSTER (should bounce), a pistol in PRIMARY
(should bounce), a vanilla sword in SHEATH (should work), and pressing "1" with a rifle in
PRIMARY (should still fire it).
