# dayzhud 1.8.0 - readable stat cards, magazines, Rummage compat

**Complete, not incremental.** 45 files. Unzip over the repo root.

## The details panel was broken, not just cramped

Your screenshot showed two stats and a clipped third. Two separate faults:

1. **Values were drawn at full font size** while labels were captions, so each row ate 11-13px
   of a column that only had about 60px to give. Values are 0.75 scale now, matching the
   labels, at a fixed 11px row.
2. **There was no way to see past the cut.** A gun has eleven stats and the column fits six or
   seven. The panel scrolls with the wheel now, and shows `7/11 - SCROLL` under the block so
   it is obvious there is more. Silently truncating is what made it look broken.

The preview also dropped from 3x to 2x. It was eating the room the stats needed, and at 32px
an item icon is already perfectly readable.

## Magazines from TaCZ: Magazines

Empty magazines are stocked under a **MAGAZINES** category, in SMALL / STANDARD / EXTENDED
MAGS / DRUMS sections by capacity.

Priced from capacity (`magazines.basePrice` 1200 + `pricePerRound` 90), because a magazine is
a container - a 100-round drum should not cost what a 7-round pistol mag does.

Like TACZ guns and lrtactical consumables, every magazine is one registered item with a family
string, so it gets its own price key (`taczmagazines:magazine/<family>`). It is handled in its
own compat rather than through `nbtVariantItems` because the family sits behind
`MagazineItem.getMagazineFamilyId` rather than in an NBT tag a config line could name, and
because the family list comes from the mod's own registry.

## Rummage compat

While a container still needs searching for that player, the container merging stands down and
Rummage's own screen is left alone. Once searched, opening it again gives the merged view.

Worth being precise about why: Rummage hides contents by masking `Slot.getItem`, and that
masking follows our slots fine - the items would stay hidden either way. What merging removes
is the *searching interaction*, which lives on Rummage's screen. Reimplementing that badly
would be worse than not merging, so the redirect defers instead. `access.respectRummage`,
default on. The check fails open: any doubt and it merges, because a false negative costs one
container's merged view while a false positive would hide a container entirely.

## Verification

Four checks against the extracted zip: config definite-assignment via the ForgeConfigSpec
stub, no references to removed classes, no unresolved first-party symbols, no other errors.
The magazine, Rummage and TACZ integrations are all reflective, so a wrong method name there
fails at runtime with a logged warning rather than at compile time - CI cannot catch those
either. New MC surface: `EditBox.setPosition`, `ArmorItem.getEquipmentSlot`.
