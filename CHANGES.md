# dayzhud 1.2.0 - market UI rebuild, confirmations, and two more mods

**Cumulative.** This replaces both earlier fix zips - if you have not applied them, this one
is enough on top of the original 1.1.0 update. Eleven files, all replacements.

## New UI (MarketScreen, rebuilt)

BUY and SELL tabs across the top; balance top right; a scrolling stock list (not pages) with
icon, name, category and price; an ITEM DETAILS panel on the right with a large preview, an
x1 / x5 / x16 quantity selector and a BUY button. SELL shows the tray, a per-item payout
breakdown and a TOTAL, with SELL ALL and WITHDRAW beneath it.

**Categories moved from a horizontal strip to a vertical sidebar, and that is a bug fix, not
decoration.** The strip laid tabs left to right and stopped when it ran out of panel width -
so with your modpack installed WEAPONS, SUPPLIES and VALUABLES were unreachable. The stock
was there; the way to reach it was not. That is why the old screen looked ammo-only.

The panel is 384x246 now, up from 336x250, to fit three columns.

## Confirmations

Buying, selling and withdrawing all raise a confirm panel naming the item and the exact
amount. It dims the screen behind it and swallows every click until CONFIRM or CANCEL - Enter
and Escape work too. Buying is irreversible at a 55% buyback and the tray can hold a rifle,
so a misclick was expensive.

## The sell tray now hides itself on the BUY tab

`Slot.x`/`y` are final in 1.20.1, so the tray cannot be moved off-screen. It uses a
`SellSlot.isActive()` override instead, which is what AbstractContainerScreen already gates
rendering and hit-testing on. The flag is only ever written by the screen, so the server side
stays active and quick-move keeps working.

## Pricing: derived, not hand-written

`caps_awim_tactical_gear_rework` ships **238 armour pieces**. A row each in prices.json does
not survive contact with a real modpack, so `DerivedPrices` now prices any unlisted armour
from its own defence, toughness, knockback resistance and durability, and any unlisted food
from nutrition and saturation. Damaged gear is worth less, which also stops a trader paying
full price for a helmet that ate a rifle round. Explicit entries always win. Toggles and a
scale live in the new `[derived]` config block.

Added by hand where stats cannot say anything useful: **20 lrtactical entries** (medkits,
M67 / RGN / flash / smoke / molotov / C4, karambit and dagger, condensed milk) and **54 caps
entries** for the parts that are curios rather than armour - backpacks, balaclavas, shemaghs,
uniforms, glasses, gas mask, NVG module, rations. 160 entries total.

**TACZ ammo is no longer one flat price.** Every calibre cost 1 350 because TACZ's ammo index
carries no ballistics - damage lives on the *gun's* bullet data. `priceOfAmmo` now finds the
hardest-hitting gun that chambers each round and prices from that, with pierce and
armour-ignore premiums, so 12 gauge and .338 Lapua no longer cost the same.

## Also fixed

The TOTAL readout was drawing on top of the "drag items here" caption (visible in your
screenshot). Both moved in the rebuild.

## Verification

Parse-checked over the merged tree, 76 source files, no errors outside the expected
missing-Minecraft noise. As always that proves the syntax and this mod's own cross-class
references and nothing about MC/Forge calls - CI is the real check.

## Config note

`[derived]` is a new config section. Forge only writes defaults into a config file that does
not exist yet, so delete `config/dayzhud-common.toml` or hand-add the block to pick it up.
