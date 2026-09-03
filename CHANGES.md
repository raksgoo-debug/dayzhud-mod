# dayzhud 1.7.0 - safe zones that are safe, working ammo boxes, gun stat cards

**Complete, not incremental.** 42 files. Unzip over the repo root.

## 1. Safes are no longer terminals

`access.terminalBlocks` defaults to `tarkovdayz:pc` only. Existing configs keep whatever
they already say - delete `config/dayzhud-common.toml` to pick up the new default.

## 2. Ammo boxes open

Right-click a tarkovdayz ammo box and it becomes TACZ rounds. The mapping is config
(`ammoBoxes.boxes`, `itemid=<tacz ammo id>,<count>`) because ammo ids belong to whatever gun
pack you run; defaults target TACZ's own `9mm`, `12g`, `545x39`, `556x45`, `762x39`, `308`.

Both halves of your ask are covered, because they are the same rule: **a box whose ammo id
does not resolve is dropped from the shop as well as being unopenable.** Selling something
that does nothing is worse than not selling it - the player pays, clicks, and gets silence.
The handler also hooks RightClickBlock, since RightClickItem does not fire when the crosshair
is on a block - the same trap the laptop hit.

## 3. Item details, properly

The details panel now shows a real stat block. Guns get DAMAGE (with the pellet breakdown for
shotguns), FIRE RATE, DPS, PENETRATION, ARMOUR IGNORE, MUZZLE VELOCITY, HEADSHOT multiplier,
MAGAZINE, CALIBRE, FIRE MODE and WEIGHT. Armour gets defence, toughness, knockback resistance,
slot and durability. Food gets nutrition and saturation; tools get attack and tier; anything
damageable shows condition.

All of it read client-side through the same reflective TACZ compat that prices weapons, so it
follows the installed gun pack with no extra packet.

One judgement worth stating: **bars are only drawn where there is an honest ceiling.** Damage,
rate, penetration and velocity get bars; weight, calibre and magazine size get numbers. A bar
against an invented maximum looks informative and is not.

## 4. Safe zones are actually safe

Players take no damage inside a registered zone. Cancelled at `LivingAttackEvent`, not
`LivingHurtEvent` - attack fires first, so cancelling there also kills the hurt animation, the
sound and the knockback. Cancel only the damage and you get a player visibly flinching under
fire while taking nothing, which reads as a bug. Priority HIGHEST, because the point of a safe
zone is that nothing else gets a say.

You also get a message on entering and leaving, checked twice a second rather than every tick.
A zone edge is invisible and the alternative way to learn you have crossed it is dying.
Both toggles: `access.safeZoneProtection`, `access.safeZoneMessages`.

## 5. Layout

The dead band under the stock list is gone. It was the sell tray's row, which only exists on
the SELL tab - on BUY that space is now more list rows, and the list tiles the region exactly
instead of leaving a ragged remainder. The nested "ALL" under an open category now reads
"ALL FIREARMS" rather than sitting under a second "ALL".

## Verification

Four checks against the extracted zip: config definite-assignment via the ForgeConfigSpec
stub, no references to removed classes, no unresolved first-party symbols, no errors outside
the expected missing-Minecraft noise. MC/Forge calls remain unverified here - CI is the real
check. New MC surface this round: `LivingAttackEvent`, `ServerTickEvent`,
`TieredItem.getTier()`, `FoodProperties.getEffects()`, `EditBox.setPosition`.
