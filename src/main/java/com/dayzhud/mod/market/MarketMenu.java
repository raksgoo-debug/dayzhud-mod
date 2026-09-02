package com.dayzhud.mod.market;

import com.dayzhud.mod.inventory.TarkovMenuTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Trader screen container. The only real container here is the SELL tray - the shop side is
 * a list, not slots, so it needs no server-side inventory at all.
 *
 * Selling is deliberately a tray you fill and then confirm, rather than a click-to-sell on
 * the inventory grid: vanilla's slot-click path already means "move this item", and
 * overloading it with "destroy this item for money" is how people sell their gun by
 * shift-clicking. Filling a tray is also the interaction Tarkov and Arena Breakout use.
 */
public class MarketMenu extends AbstractContainerMenu {

    public static final int SELL_SLOTS = 9;

    /** Slot origins, in screen-relative pixels. The screen reads these so the two agree. */
    public static final int TRAY_X = 30;
    public static final int TRAY_Y = 62;
    public static final int INV_X = 111;
    public static final int INV_Y = 162;
    public static final int HOTBAR_Y = 220;

    /**
     * Client-side: whether the SELL tab is showing. The tray slots hide themselves when it is
     * not, because AbstractContainerScreen renders and hit-tests only ACTIVE slots - and a
     * Slot's x/y are final in 1.20.1, so moving them off-screen is not an option. The server
     * never touches this, so it stays true there and quick-move keeps working.
     */
    public boolean sellTabActive = true;

    private final Container sellTray;
    private final Player player;

    /** Client-side constructor: the catalogue arrives separately, by packet. */
    public MarketMenu(int windowId, Inventory inventory) {
        this(windowId, inventory, new SimpleContainer(SELL_SLOTS));
    }

    public MarketMenu(int windowId, Inventory inventory, Container sellTray) {
        super(TarkovMenuTypes.MARKET.get(), windowId);
        this.sellTray = sellTray;
        this.player = inventory.player;
        checkContainerSize(sellTray, SELL_SLOTS);

        for (int i = 0; i < SELL_SLOTS; i++) {
            addSlot(new SellSlot(sellTray, i, TRAY_X + (i % 3) * 18, TRAY_Y + (i / 3) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public Container sellTray() {
        return sellTray;
    }

    /** What the trader would pay for everything currently in the tray. */
    public long trayValue() {
        long total = 0;
        for (int i = 0; i < sellTray.getContainerSize(); i++) {
            ItemStack stack = sellTray.getItem(i);
            total += MarketPrices.sellPrice(stack, stack.getCount());
        }
        return total;
    }

    /**
     * Pays out for every sellable stack in the tray and consumes it. Worthless items are
     * left where they are rather than being eaten - a player who dropped the wrong thing in
     * gets it back instead of losing it for nothing.
     *
     * @return roubles paid.
     */
    public long sellTrayContents() {
        long total = 0;
        for (int i = 0; i < sellTray.getContainerSize(); i++) {
            ItemStack stack = sellTray.getItem(i);
            if (stack.isEmpty()) continue;
            long paid = MarketPrices.sellPrice(stack, stack.getCount());
            if (paid <= 0) continue;
            total += paid;
            sellTray.setItem(i, ItemStack.EMPTY);
        }
        if (total > 0) sellTray.setChanged();
        return total;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int invStart = SELL_SLOTS;
        int invEnd = this.slots.size();

        if (index < invStart) {
            // tray -> inventory
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            // inventory -> tray. Only worth moving something the trader will actually take;
            // otherwise a shift-click on junk silently fills the tray with unsellables.
            if (MarketPrices.sellPrice(stack) <= 0) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, 0, invStart, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // The tray is a scratch container that lives only as long as the screen, so anything
        // left in it has to come back to the player or it is destroyed on close.
        clearContainer(player, sellTray);
    }

    @Override
    public boolean stillValid(Player player) {
        // Range is enforced when the screen is opened (see MarketAccess) rather than every
        // tick: the terminal may be a block from another mod, and re-resolving it here would
        // couple this menu to whatever opened it.
        return player.isAlive();
    }

    public Component title() {
        return Component.translatable("gui.dayzhud.market.title");
    }

    public Player player() {
        return player;
    }

    /** A tray slot, hidden while the BUY tab is showing. */
    private class SellSlot extends Slot {
        SellSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return sellTabActive;
        }
    }
}
