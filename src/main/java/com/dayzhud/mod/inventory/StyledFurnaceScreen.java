package com.dayzhud.mod.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;

/**
 * Furnace / blast furnace / smoker. Same panel styling as everything else, plus the two
 * indicators those screens need: a smelt-progress arrow between the input and output, and
 * a burn gauge for the fuel slot.
 *
 * Progress values come from the menu's synced data slots (the same ones vanilla's own
 * screen reads), so they stay correct on servers without any extra networking.
 */
public class StyledFurnaceScreen extends StyledContainerScreen<AbstractFurnaceMenu> {

    public StyledFurnaceScreen(AbstractFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);

        int x = leftPos, y = topPos;

        // Vanilla furnace slot layout: input (56,17), fuel (56,53), result (116,31).
        StyledTheme.burnGauge(graphics, x + 57, y + 37, 14, 14, menu.getLitProgress());
        StyledTheme.progressArrow(graphics, x + 79, y + 33, 24, 8, menu.getBurnProgress());
    }
}
