package com.dayzhud.mod.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Drop-in restyle for any vanilla container whose UI is essentially "a grid of slots":
 * chests, barrels, shulker boxes, dispensers/droppers, hoppers, crafting tables and so on.
 *
 * It deliberately does NOT move any slots - slot coordinates come from the server-side
 * menu and must not be second-guessed - it only replaces the background art with this
 * mod's dark panel styling and sizes the panel from the slots that are actually there.
 * That's why one class can cover every chest size and most simple containers.
 *
 * Containers with extra moving parts (furnace progress arrows, anvil text fields,
 * enchanting buttons) need their own subclass or are left vanilla; see StyledScreens.
 */
public class StyledContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    private static final int PAD = 8;
    private static final int TITLE_H = 18;

    private final int playerInvSplitY;

    public StyledContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        // Size the panel around whatever slots this menu actually has.
        int maxX = 0, maxY = 0, minY = Integer.MAX_VALUE;
        for (Slot slot : menu.slots) {
            maxX = Math.max(maxX, slot.x + 16);
            maxY = Math.max(maxY, slot.y + 16);
            minY = Math.min(minY, slot.y);
        }
        if (minY == Integer.MAX_VALUE) minY = 0;

        this.imageWidth = maxX + PAD;
        this.imageHeight = maxY + PAD;

        // Vanilla always puts the player's 4 inventory rows at the bottom; find where that
        // block starts so we can draw a divider above it.
        int inventoryTop = Integer.MAX_VALUE;
        int slotCount = menu.slots.size();
        if (slotCount >= 36) {
            inventoryTop = menu.slots.get(slotCount - 36).y;
        }
        this.playerInvSplitY = inventoryTop;

        this.titleLabelY = 6;
        this.inventoryLabelY = -1000; // we draw our own captions
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        StyledTheme.panel(graphics, x, y, imageWidth, imageHeight);

        if (playerInvSplitY != Integer.MAX_VALUE) {
            int dividerY = y + playerInvSplitY - 6;
            graphics.fill(x + 6, dividerY, x + imageWidth - 6, dividerY + 1, StyledTheme.PANEL_BORDER);
        }

        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            StyledTheme.slot(graphics, x + slot.x, y + slot.y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        StyledTheme.header(graphics, font, title.getString().toUpperCase(java.util.Locale.ROOT),
                8, 8, Math.max(30, font.width(title.getString()) - 4));
        if (playerInvSplitY != Integer.MAX_VALUE) {
            StyledTheme.header(graphics, font, "INVENTORY", 8, playerInvSplitY - 16, 54);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
