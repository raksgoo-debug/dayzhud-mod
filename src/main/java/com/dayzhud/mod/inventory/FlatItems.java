package com.dayzhud.mod.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** The grid's "real model, fitted and centred" draw, whichever renderer the item needs:
 *  TACZ guns (and LR items) or backpacks. */
public final class FlatItems {

    private FlatItems() {}

    public static boolean canRender(ItemStack stack) {
        return TaczFlatGunRenderer.canRender(stack) || FlatBackpackRenderer.canRender(stack);
    }

    /** Draws the item in the box; false (nothing drawn) if it can't. */
    public static boolean render(GuiGraphics graphics, ItemStack stack, int x, int y, int w, int h, float z,
                                 boolean rotated, float maxScale) {
        if (TaczFlatGunRenderer.canRender(stack)) {
            return TaczFlatGunRenderer.render(graphics, stack, x, y, w, h, z, rotated, maxScale);
        }
        return FlatBackpackRenderer.render(graphics, stack, x, y, w, h, z, rotated);
    }
}
