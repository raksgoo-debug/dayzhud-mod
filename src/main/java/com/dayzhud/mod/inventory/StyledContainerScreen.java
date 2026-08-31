package com.dayzhud.mod.inventory;

import com.dayzhud.mod.client.UiSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/**
 * Restyle for containers whose UI is essentially a grid of slots: chests, barrels,
 * shulkers, dispensers, hoppers, crafting tables, and most modded storage.
 *
 * Slot coordinates come from the server-side menu and are never moved - only the
 * background art and labels are replaced, and the panel is sized around whatever slots
 * exist. That's why one class covers every chest size and most modded containers.
 *
 * Simple storage containers never reach this class - ContainerOpenRedirect turns those into
 * the merged inventory+container view instead. This handles the rest (crafting tables and
 * any modded grid GUIs).
 */
public class StyledContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    private static final int PAD = 10;

    private final int firstSlotY;
    private final int playerInvTop;

    public StyledContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        int maxX = 0, maxY = 0, minY = Integer.MAX_VALUE;
        for (Slot slot : menu.slots) {
            maxX = Math.max(maxX, slot.x + 16);
            maxY = Math.max(maxY, slot.y + 16);
            minY = Math.min(minY, slot.y);
        }
        this.firstSlotY = (minY == Integer.MAX_VALUE) ? 18 : minY;

        // Vanilla always places the player's 4 inventory rows last.
        int invTop = Integer.MAX_VALUE;
        int count = menu.slots.size();
        if (count >= 36) invTop = menu.slots.get(count - 36).y;
        this.playerInvTop = invTop;

        this.imageWidth = maxX + PAD;
        this.imageHeight = maxY + PAD;
        this.titleLabelY = -1000;      // headers are drawn manually
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        StyledTheme.panel(graphics, x, y, imageWidth, imageHeight);

        // Zone behind the container's own slots (everything above the player inventory).
        if (playerInvTop != Integer.MAX_VALUE && playerInvTop > firstSlotY) {
            StyledTheme.zone(graphics, x + 6, y + firstSlotY - 6, x + imageWidth - 6, y + playerInvTop - 14);
            StyledTheme.zone(graphics, x + 6, y + playerInvTop - 6, x + imageWidth - 6, y + imageHeight - 6);
        }

        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            StyledTheme.slot(graphics, x + slot.x, y + slot.y);
        }

        // Crafting tables get an arrow so the gap between grid and result reads deliberately.
        if (menu instanceof CraftingMenu) {
            int ax = x + 97, ay = y + 39;
            graphics.fill(ax, ay, ax + 16, ay + 2, StyledTheme.HEADER_ACCENT);
            graphics.fill(ax + 12, ay - 3, ax + 14, ay + 5, StyledTheme.HEADER_ACCENT);
            graphics.fill(ax + 14, ay - 1, ax + 16, ay + 3, StyledTheme.HEADER_ACCENT);
        }
    }

    /** Guarded because init() re-runs on every window resize. */
    private boolean openSoundPlayed = false;

    @Override
    protected void init() {
        super.init();
        if (!openSoundPlayed) {
            openSoundPlayed = true;
            UiSounds.inventoryOpen();
        }
    }

    @Override
    public void removed() {
        super.removed();
        UiSounds.inventoryClose();
    }

    /** Sampled before super, which is what clears the cursor or the slot. */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        boolean movedSomething = !menu.getCarried().isEmpty() || (slot != null && slot.hasItem());
        super.slotClicked(slot, slotId, mouseButton, type);
        if (movedSomething) {
            UiSounds.inventoryMove();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Title sits in the top padding, above the first slot row - never overlapping it.
        String heading = title.getString().toUpperCase(Locale.ROOT);
        int ruleWidth = Math.max(34, Math.round(font.width(heading) * 0.8f));
        StyledTheme.header(graphics, font, heading, 8, Math.max(6, firstSlotY - 16), ruleWidth);

        if (playerInvTop != Integer.MAX_VALUE) {
            // Rule tucked just above the inventory rows so it can't clash with the grid above.
            StyledTheme.header(graphics, font, "INVENTORY", 8, playerInvTop - 13, 54);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        renderTooltip(graphics, mouseX, mouseY);
    }
}
