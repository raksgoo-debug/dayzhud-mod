package com.dayzhud.mod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Replaces the vanilla hotbar with a dark, flat-panel hotbar matching the inventory
 * screen's styling (same slot background, border, and selection colours), so the two read
 * as one consistent UI. Vanilla's own hotbar overlay is cancelled in OverlayCanceller.
 *
 * Because we're replacing the vanilla widget wholesale, everything it drew has to be
 * redrawn here: item icons, stack counts, durability bars, the selection highlight, and
 * the offhand slot. Colours intentionally mirror TarkovInventoryScreen's constants.
 */
public class DayzHotbarOverlay implements IGuiOverlay {

    private static final int SLOT_SIZE = 20;
    private static final int SLOT_COUNT = 9;
    private static final int BOTTOM_MARGIN = 4;

    private static final int PANEL_BG = 0xF0121212;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int SLOT_BG = 0xFF232323;
    private static final int SLOT_BORDER = 0xFF484848;
    private static final int SELECTED_BORDER = 0xFFE6E6E6;
    private static final int SELECTED_BG = 0xFF303030;

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics,
                       float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) return;

        int totalWidth = SLOT_COUNT * SLOT_SIZE;
        int startX = screenWidth / 2 - totalWidth / 2;
        int y = screenHeight - BOTTOM_MARGIN - SLOT_SIZE;

        // Backing panel behind the whole row
        graphics.fill(startX - 2, y - 2, startX + totalWidth + 2, y + SLOT_SIZE + 2, PANEL_BG);
        graphics.renderOutline(startX - 2, y - 2, totalWidth + 4, SLOT_SIZE + 4, PANEL_BORDER);

        int selected = player.getInventory().selected;

        for (int i = 0; i < SLOT_COUNT; i++) {
            int sx = startX + i * SLOT_SIZE;
            boolean isSelected = i == selected;

            graphics.fill(sx, y, sx + SLOT_SIZE, y + SLOT_SIZE, isSelected ? SELECTED_BG : SLOT_BG);
            graphics.renderOutline(sx, y, SLOT_SIZE, SLOT_SIZE, isSelected ? SELECTED_BORDER : SLOT_BORDER);

            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty()) {
                int itemX = sx + 2;
                int itemY = y + 2;
                graphics.renderItem(stack, itemX, itemY);
                graphics.renderItemDecorations(mc.font, stack, itemX, itemY);
            }
        }

        // Offhand, drawn just left of the row when something is held there.
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            int ox = startX - SLOT_SIZE - 6;
            graphics.fill(ox, y, ox + SLOT_SIZE, y + SLOT_SIZE, SLOT_BG);
            graphics.renderOutline(ox, y, SLOT_SIZE, SLOT_SIZE, SLOT_BORDER);
            graphics.renderItem(offhand, ox + 2, y + 2);
            graphics.renderItemDecorations(mc.font, offhand, ox + 2, y + 2);
        }
    }
}
