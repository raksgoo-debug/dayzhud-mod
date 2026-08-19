package com.dayzhud.mod.inventory;

import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Dark, panel-based inventory screen in the style of extraction shooters (Tarkov / Arena
 * Breakout): a rotating player paperdoll on the left surrounded by equipment slots, a
 * "Pockets" grid on the right (the standard player inventory), and a bottom stat strip
 * reusing this mod's own health/thirst/food/stamina tracking so it matches the HUD.
 *
 * Primary/Secondary/Holster/Sheath are drawn as read-only previews of hotbar slots 0-3 -
 * per design, these mirror the hotbar rather than being separate functional slots. Change
 * WEAPON_MIRROR_HOTBAR_INDEX below if you'd rather map different hotbar slots to them.
 */
public class TarkovInventoryScreen extends AbstractContainerScreen<TarkovInventoryMenu> {

    private static final int PANEL_BG = 0xE0141414;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int SLOT_BG = 0xFF232323;
    private static final int SLOT_BORDER = 0xFF454545;
    private static final int SLOT_BORDER_INACTIVE = 0xFF2A2A2A;

    private static final int[] WEAPON_MIRROR_HOTBAR_INDEX = {0, 1, 2, 3}; // Primary, Secondary, Holster, Sheath
    private static final String[] WEAPON_MIRROR_LABEL = {"Primary", "Secondary", "Holster", "Sheath"};

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 320;
        this.imageHeight = 210;
        this.inventoryLabelY = -1000; // hide vanilla "Inventory" label - we draw our own panels
        this.titleLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        // Whole-screen backing panel
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        graphics.renderOutline(x, y, imageWidth, imageHeight, PANEL_BORDER);

        // Divider between the equipment/paperdoll side and the Pockets grid
        graphics.fill(x + 128, y + 4, x + 129, y + imageHeight - 30, PANEL_BORDER);

        // Slot backdrops for every real slot in the menu (armor/curios/pockets)
        for (var slot : menu.slots) {
            drawSlotBackdrop(graphics, x + slot.x, y + slot.y, isChestRigSlotAndInactive(slot));
        }

        // Weapon mirror boxes (non-interactive, drawn directly - not real slots)
        int mirrorY = y + imageHeight - 26;
        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            int mx = x + 6 + i * 30;
            drawSlotBackdrop(graphics, mx, mirrorY, false);
        }
    }

    private boolean isChestRigSlotAndInactive(net.minecraft.world.inventory.Slot slot) {
        return slot == menu.chestRigSlot && (menu.chestArmorSlot == null || !menu.chestArmorSlot.hasItem());
    }

    private void drawSlotBackdrop(GuiGraphics graphics, int x, int y, boolean inactive) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
        graphics.renderOutline(x - 1, y - 1, 18, 18, inactive ? SLOT_BORDER_INACTIVE : SLOT_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        // Player paperdoll, roughly centered over the armor/curios cluster.
        // Exact 1.20.1 signature (confirmed from the compiler's own error output):
        //   (GuiGraphics, int x, int y, int scale, float mouseX, float mouseY, LivingEntity)
        // No angleXComponent parameter here, and the mouse coords are floats.
        LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer != null) {
            int pdX = leftPos + 107, pdY = topPos + 140;
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, pdX, pdY, 45,
                    (float) mouseX, (float) mouseY, localPlayer);
        }

        drawWeaponMirrors(graphics);
        drawStatBar(graphics);

        renderTooltip(graphics, mouseX, mouseY);
    }

    private void drawWeaponMirrors(GuiGraphics graphics) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        int mirrorY = topPos + imageHeight - 26;
        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            int mx = leftPos + 6 + i * 30;
            ItemStack stack = player.getInventory().items.get(WEAPON_MIRROR_HOTBAR_INDEX[i]);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, mx, mirrorY);
                graphics.renderItemDecorations(font, stack, mx, mirrorY);
            }
        }
    }

    private void drawStatBar(GuiGraphics graphics) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        float health01 = player.getHealth() / Math.max(1f, player.getMaxHealth());
        float food01 = player.getFoodData().getFoodLevel() / 20f;
        float water01 = ThirstWasTakenCompat.getThirst01(player)
                .orElseGet(() -> player.getFoodData().getSaturationLevel() / 20f);

        int barY = topPos + imageHeight - 12;
        String stats = "HP " + Math.round(health01 * 100) + "%   "
                + "Food " + Math.round(food01 * 100) + "%   "
                + "Water " + Math.round(water01 * 100) + "%";
        graphics.drawString(font, stats, leftPos + 132, barY, 0xCCCCCC, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Intentionally empty - vanilla's default "Inventory"/title label rendering is
        // replaced by our own panel drawing in renderBg/render.
    }
}
