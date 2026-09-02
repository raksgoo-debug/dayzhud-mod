# dayzhud 1.1.0 fix 2 - the trader was being replaced by the generic styled screen

**Three changed files.** Unzip over the repo root. Apply the creative-tab hotfix first if you
have not already; this is on top of it.

    src/main/java/com/dayzhud/mod/inventory/StyledScreens.java
    src/main/java/com/dayzhud/mod/market/MarketAccess.java
    src/main/java/com/dayzhud/mod/market/MarketScreen.java

## 1. StyledScreens was eating the trader (the screenshot)

`StyledScreens` restyles container screens by DEFAULT and only excludes a list of
known-complex menus. `MarketMenu` has 45 slots, so it cleared the `> 36` test, and
"MarketMenu" was not in `EXCLUDED_MENU_CLASSES` - so `ScreenEvent.Opening` threw away
`MarketScreen` and substituted a plain `StyledContainerScreen`. That screen draws the title
and the slots and nothing else, which is exactly what was on screen: TRADER, an empty panel,
the 3x3 sell tray in the corner, and no stock list, tabs, balance, SELL or WITHDRAW.

Fixed by adding `MarketScreen` to the "don't re-wrap our own screens" guard, next to
`StyledContainerScreen` and `TarkovInventoryScreen`, plus "MarketMenu" in
`EXCLUDED_MENU_CLASSES` as belt and braces. **Any future screen of ours that draws its own
widgets needs the same line** - this class is opt-out, not opt-in.

## 2. The laptop only worked pointed at open air

`PlayerInteractEvent.RightClickItem` does not fire when the crosshair is on a block -
`RightClickBlock` fires instead. So the laptop did nothing whenever the player was looking at
anything, which in play is most of the time. `onRightClickBlock` now falls through to a shared
`tryTerminalItem` when the block is not a terminal, so the laptop behaves identically either
way.

Note this is separate from the safe-zone rule, which is working as designed: the laptop is
portable, so `access.itemRequiresSafeZone` defaults to true and it needs
`/market zone add <name> <radius>` first. Set it to false in `config/dayzhud-common.toml` if
you would rather the laptop work anywhere.

## 3. Two rendering fixes in MarketScreen

- Removed a redundant `renderBackground` call; `AbstractContainerScreen.render` already makes
  one, so the world was being dimmed twice and the panel read as murky.
- Moved the "DRAG ITEMS HERE TO SELL" caption below the sell tray. The tray is three slots
  (54 px) wide from x 213, so the caption was drawing on top of its third column.

## Verification

Parse-checked against the merged tree. Minecraft and Forge are not on the classpath here, so
as always this proves the syntax and the mod's own cross-class references and nothing about
MC/Forge calls. CI is the real check.
