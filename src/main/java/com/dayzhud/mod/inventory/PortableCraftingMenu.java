package com.dayzhud.mod.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * The 3x3 crafting grid opened by the button on the inventory screen.
 *
 * WHY THIS CLASS EXISTS (it fixes a real bug - the button used to open a grid that could
 * never craft anything):
 *
 * CraftingMenu does all its work through its ContainerLevelAccess:
 *
 *   slotsChanged() -> access.execute(...) -> slotChangedCraftingGrid(...)   // fills the result
 *   removed()      -> access.execute(...) -> clearContainer(...)            // returns your items
 *
 * The button previously passed {@link ContainerLevelAccess#NULL}, whose evaluate() returns
 * Optional.empty() WITHOUT EVER CALLING the function handed to it. So neither of those ran:
 * the result slot stayed permanently empty (nothing was ever craftable, which is why a real
 * table was still needed), and anything left on the grid at close was silently voided rather
 * than given back.
 *
 * Passing a real access fixes both - but a real access also makes the inherited stillValid()
 * check for an actual crafting table block at that position, which would slam the screen shut
 * the instant it opened. Hence the override: this IS the portable table, so it stays valid.
 *
 * BALANCE NOTE: this is deliberately a portable crafting table - no block needed. To require
 * a real table instead, delete the button in TarkovInventoryScreen, OpenCraftingPacket and
 * this class; the in-panel 2x2 grid is independent and keeps working either way.
 *
 * SERVER-SIDE ONLY. The client builds a plain vanilla CraftingMenu from MenuType.CRAFTING
 * (which this still reports, since the super constructor sets it), and that's correct: the
 * client's copy never needs to compute a result, it receives it from the server.
 */
public class PortableCraftingMenu extends CraftingMenu {

    public PortableCraftingMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(containerId, playerInventory, access);
    }

    /**
     * Always valid: the player carries this grid rather than standing at a block. Without
     * this, the inherited check looks for a crafting table at the access position - the spot
     * the player was standing on - finds grass, and closes the menu immediately.
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
