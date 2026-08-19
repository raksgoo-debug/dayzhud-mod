package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Dark, panel-based inventory screen in the style of extraction shooters (Tarkov / Arena
 * Breakout): a player paperdoll on the left surrounded by equipment slots, a labelled
 * "Pockets" grid on the right, a weapon-mirror row along the bottom, and a stat strip
 * that reuses the same gauge icons as the in-world HUD so the two match visually.
 *
 * Primary/Secondary/Holster/Sheath are read-only previews of hotbar slots 0-3 - per
 * design they mirror the hotbar rather than being separate storage. Change
 * WEAPON_MIRROR_HOTBAR_INDEX below to remap which hotbar slots they show.
 */
public class TarkovInventoryScreen extends AbstractContainerScreen<TarkovInventoryMenu> {

    private static final int PANEL_BG = 0xE0141414;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int SLOT_BG = 0xFF232323;
    private static final int SLOT_BORDER = 0xFF454545;
    private static final int SLOT_BORDER_INACTIVE = 0xFF2A2A2A;
    private static final int HEADER_COLOR = 0xFF8A8A8A;
    private static final int HEADER_ACCENT = 0xFF5A5A5A;
    private static final int TEXT_COLOR = 0xFFCCCCCC;

    private static final int[] WEAPON_MIRROR_HOTBAR_INDEX = {0, 1, 2, 3};
    private static final String[] WEAPON_MIRROR_LABEL = {"PRIMARY", "SECONDARY", "HOLSTER", "SHEATH"};

    // Reuse the HUD's own gauge icons so the two UIs read as one consistent set.
    private static final ResourceLocation ICON_HEART = rl("icon_heart_solid");
    private static final ResourceLocation ICON_FOOD = rl("icon_food_solid");
    private static final ResourceLocation ICON_WATER = rl("icon_droplet_solid");

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(DayzHudMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 310;
        this.imageHeight = 222;
        this.inventoryLabelY = -1000; // hide vanilla labels - we draw our own headers
        this.titleLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        graphics.renderOutline(x, y, imageWidth, imageHeight, PANEL_BORDER);

        // Vertical divider between the equipment side and the pockets side
        graphics.fill(x + 128, y + 6, x + 129, y + imageHeight - 40, PANEL_BORDER);
        // Horizontal divider above the weapon row
        graphics.fill(x + 6, y + imageHeight - 40, x + imageWidth - 6, y + imageHeight - 39, PANEL_BORDER);

        for (var slot : menu.slots) {
            drawSlotBackdrop(graphics, x + slot.x, y + slot.y, isChestRigSlotAndInactive(slot));
        }

        int mirrorY = y + imageHeight - 26;
        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            drawSlotBackdrop(graphics, x + 10 + i * 28, mirrorY, false);
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

        LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer != null) {
            int pdX = leftPos + 96, pdY = topPos + 128;
            // Fixed pose instead of mouse-following: we pass a constant "virtual mouse"
            // position offset to the right of and slightly above the model, so the player
            // holds a steady three-quarter view looking slightly right rather than
            // swivelling around as the cursor moves.
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, pdX, pdY, 42,
                    (float) (pdX + 40), (float) (pdY - 70), localPlayer);
        }

        drawSectionHeaders(graphics);
        drawWeaponMirrors(graphics, mouseX, mouseY);
        drawStatBar(graphics);

        renderTooltip(graphics, mouseX, mouseY);
    }

    /** Small uppercase section headers with a short accent rule underneath. */
    private void drawSectionHeaders(GuiGraphics graphics) {
        drawHeader(graphics, "EQUIPMENT", leftPos + 10, topPos + 8, 52);
        drawHeader(graphics, "POCKETS", leftPos + 140, topPos + 8, 46);
        drawHeader(graphics, "HOTBAR", leftPos + 140, topPos + 108, 40);
        drawHeader(graphics, "WEAPONS", leftPos + 10, topPos + imageHeight - 38, 46);
    }

    private void drawHeader(GuiGraphics graphics, String text, int x, int y, int ruleWidth) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.8f, 0.8f, 1f);
        graphics.drawString(font, text, 0, 0, HEADER_COLOR, false);
        graphics.pose().popPose();
        graphics.fill(x, y + 9, x + ruleWidth, y + 10, HEADER_ACCENT);
    }

    private void drawWeaponMirrors(GuiGraphics graphics, int mouseX, int mouseY) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        int mirrorY = topPos + imageHeight - 26;
        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            int mx = leftPos + 10 + i * 28;
            ItemStack stack = player.getInventory().items.get(WEAPON_MIRROR_HOTBAR_INDEX[i]);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, mx, mirrorY);
                graphics.renderItemDecorations(font, stack, mx, mirrorY);
            }
            // Tiny label under each mirror box so it's obvious what the row represents.
            graphics.pose().pushPose();
            graphics.pose().translate(mx - 1, mirrorY + 18, 0);
            graphics.pose().scale(0.5f, 0.5f, 1f);
            graphics.drawString(font, WEAPON_MIRROR_LABEL[i], 0, 0, HEADER_ACCENT, false);
            graphics.pose().popPose();

            // Hover tooltip naming the slot, since these boxes aren't real slots and so
            // don't get vanilla's own tooltip handling.
            if (mouseX >= mx && mouseX < mx + 16 && mouseY >= mirrorY && mouseY < mirrorY + 16) {
                if (stack.isEmpty()) {
                    graphics.renderTooltip(font, Component.literal(WEAPON_MIRROR_LABEL[i]
                            + " (hotbar slot " + (WEAPON_MIRROR_HOTBAR_INDEX[i] + 1) + ")"), mouseX, mouseY);
                }
            }
        }
    }

    /** Bottom-right stat strip: each value gets the same icon the in-world HUD uses. */
    private void drawStatBar(GuiGraphics graphics) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        float health01 = player.getHealth() / Math.max(1f, player.getMaxHealth());
        float food01 = player.getFoodData().getFoodLevel() / 20f;
        float water01 = ThirstWasTakenCompat.getThirst01(player)
                .orElseGet(() -> player.getFoodData().getSaturationLevel() / 20f);

        int y = topPos + imageHeight - 24;
        int x = leftPos + 148;
        int spacing = 58;

        drawStatEntry(graphics, ICON_HEART, health01, x, y);
        drawStatEntry(graphics, ICON_FOOD, food01, x + spacing, y);
        drawStatEntry(graphics, ICON_WATER, water01, x + spacing * 2, y);
    }

    private void drawStatEntry(GuiGraphics graphics, ResourceLocation icon, float value01, int x, int y) {
        int size = 11;
        int color = severityColor(value01);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                1f);
        graphics.blit(icon, x, y, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        String text = Math.round(value01 * 100) + "%";
        graphics.drawString(font, text, x + size + 4, y + 2, TEXT_COLOR, false);
    }

    private int severityColor(float value01) {
        if (value01 <= 0.25f) return 0xE23A2E;
        if (value01 <= 0.5f) return 0xE2A62E;
        return 0xE6E6E6;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Intentionally empty - replaced by drawSectionHeaders().
    }
}
