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

import java.util.Locale;

/**
 * Extraction-shooter style inventory screen (Tarkov / Arena Breakout): a side-on player
 * paperdoll flanked by equipment slots aligned to the matching body parts, a dedicated
 * grid for the (often numerous) Curios slots other mods add, a labelled inventory grid, a
 * weapon-mirror row, and a stat strip reusing the same gauge icons as the in-world HUD.
 *
 * Curios slot names are shown as HOVER TOOLTIPS rather than inline text - with a dozen-plus
 * slots installed, inline labels overlap into unreadable mush.
 */
public class TarkovInventoryScreen extends AbstractContainerScreen<TarkovInventoryMenu> {

    private static final int PANEL_BG = 0xF0121212;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int SECTION_BG = 0x40000000;
    private static final int SLOT_BG = 0xFF232323;
    private static final int SLOT_BORDER = 0xFF484848;
    private static final int HEADER_COLOR = 0xFF9A9A9A;
    private static final int HEADER_ACCENT = 0xFF4A4A4A;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int LABEL_DIM = 0xFF6A6A6A;

    private static final int[] WEAPON_MIRROR_HOTBAR_INDEX = {0, 1, 2, 3};
    private static final String[] WEAPON_MIRROR_LABEL = {"PRIMARY", "SECONDARY", "HOLSTER", "SHEATH"};

    private static final ResourceLocation ICON_HEART = rl("icon_heart_solid");
    private static final ResourceLocation ICON_FOOD = rl("icon_food_solid");
    private static final ResourceLocation ICON_WATER = rl("icon_droplet_solid");

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(DayzHudMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 360;
        this.imageHeight = 322;
        if (menu.hasContainer()) {
            // Grow rightwards to fit the container grid; the loadout side keeps its layout.
            this.imageWidth = TarkovInventoryMenu.CONTAINER_X
                    + TarkovInventoryMenu.CONTAINER_COLS * 18 + 12;
        }
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        graphics.renderOutline(x, y, imageWidth, imageHeight, PANEL_BORDER);

        // Recessed zone backings so the panel reads as distinct regions.
        graphics.fill(x + 8, y + 26, x + 172, y + 150, SECTION_BG);     // paperdoll + armor
        graphics.fill(x + 8, y + 164, x + 172, y + 214, SECTION_BG);    // gear grid
        graphics.fill(x + 8, y + 228, x + 172, y + 274, SECTION_BG);    // crafting
        graphics.fill(x + 180, y + 20, x + 352, y + 84, SECTION_BG);    // inventory
        graphics.fill(x + 180, y + 94, x + 352, y + 122, SECTION_BG);   // hotbar
        if (menu.getActiveBackpackSlots() > 0) {
            graphics.fill(x + 180, y + 132, x + 352, y + 214, SECTION_BG); // backpack
        }
        graphics.fill(x + 8, y + 288, x + 352, y + 316, SECTION_BG);    // weapons + offhand + stats

        if (menu.hasContainer()) {
            int cx = TarkovInventoryMenu.CONTAINER_X;
            int cy = TarkovInventoryMenu.CONTAINER_Y;
            int cw = TarkovInventoryMenu.CONTAINER_COLS * 18;
            int ch = menu.containerRows * 18;
            graphics.fill(x + 360, y + 16, x + 361, y + imageHeight - 16, PANEL_BORDER); // divider
            graphics.fill(x + cx - 6, y + cy - 6, x + cx + cw + 6, y + cy + ch + 6, SECTION_BG);
        }

        graphics.fill(x + 176, y + 16, x + 177, y + 274, PANEL_BORDER); // vertical divider
        graphics.fill(x + 8, y + 280, x + 352, y + 281, PANEL_BORDER);  // horizontal divider

        for (var slot : menu.slots) {
            if (!slot.isActive()) continue; // inactive backpack slots shouldn't leave ghost squares
            drawSlotBackdrop(graphics, x + slot.x, y + slot.y);
        }

        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            drawSlotBackdrop(graphics, x + 16 + i * 30, y + 296);
        }
    }

    private void drawSlotBackdrop(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
        graphics.renderOutline(x - 1, y - 1, 18, 18, SLOT_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        drawPaperdoll(graphics);
        drawSectionHeaders(graphics);
        drawWeaponMirrors(graphics, mouseX, mouseY);
        drawCraftingArrow(graphics);
        drawCraftTableButton(graphics, mouseX, mouseY);
        drawBackpackScrollbar(graphics);
        drawStatBar(graphics);

        renderTooltip(graphics, mouseX, mouseY);
        drawCurioHoverTooltip(graphics, mouseX, mouseY);
        drawWeaponHoverTooltip(graphics, mouseX, mouseY);
    }

    private void drawPaperdoll(GuiGraphics graphics) {
        LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer == null) return;

        // Sits between the two equipment columns; feet land on the boots row, head on the
        // helmet row, so the flanking slots read as body-part aligned.
        int pdX = leftPos + 78;
        int pdY = topPos + 144;

        // Facing mostly forward but turned slightly toward the right of the screen.
        // TUNING NOTE: this helper turns the model by roughly (angleXComponent * 20)
        // degrees off front-facing, so small values give small turns. Flip the sign if it
        // leans the wrong way.
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, pdX, pdY, 50,
                -0.8f, 0.0f, localPlayer);
    }

    private void drawSectionHeaders(GuiGraphics graphics) {
        drawHeader(graphics, "EQUIPMENT", leftPos + 12, topPos + 8, 54);
        drawHeader(graphics, "GEAR", leftPos + 12, topPos + 156, 30);
        drawHeader(graphics, "INVENTORY", leftPos + 184, topPos + 8, 54);
        drawHeader(graphics, "HOTBAR", leftPos + 184, topPos + 86, 40);
        if (menu.hasContainer()) {
            drawHeader(graphics, "CONTAINER", leftPos + TarkovInventoryMenu.CONTAINER_X,
                    topPos + TarkovInventoryMenu.CONTAINER_Y - 18, 54);
        }
        drawHeader(graphics, "CRAFTING", leftPos + 12, topPos + 220, 48);
        drawHeader(graphics, "WEAPONS", leftPos + 12, topPos + 282, 46);
        if (menu.getActiveBackpackSlots() > 0) {
            drawHeader(graphics, "BACKPACK", leftPos + 184, topPos + 124, 50);
        }
    }

    private void drawHeader(GuiGraphics graphics, String text, int x, int y, int ruleWidth) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.8f, 0.8f, 1f);
        graphics.drawString(font, text, 0, 0, HEADER_COLOR, false);
        graphics.pose().popPose();
        graphics.fill(x, y + 9, x + ruleWidth, y + 10, HEADER_ACCENT);
    }

    /**
     * Names the hovered Curios slot. Only shown for EMPTY slots - a slot holding an item
     * already gets that item's own tooltip from vanilla, and stacking ours on top would
     * double up.
     */
    private void drawCurioHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.hasItem()) return;

        for (var info : menu.curioSlotInfos) {
            int sx = leftPos + info.x();
            int sy = topPos + info.y();
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                graphics.renderTooltip(font, Component.literal(prettify(info.identifier())), mouseX, mouseY);
                return;
            }
        }
    }

    private String prettify(String identifier) {
        String cleaned = identifier.replace('_', ' ').trim();
        if (cleaned.isEmpty()) return identifier;
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    private void drawWeaponMirrors(GuiGraphics graphics, int mouseX, int mouseY) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        int mirrorY = topPos + 296;
        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            int mx = leftPos + 16 + i * 30;
            ItemStack stack = player.getInventory().items.get(WEAPON_MIRROR_HOTBAR_INDEX[i]);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, mx, mirrorY);
                graphics.renderItemDecorations(font, stack, mx, mirrorY);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(mx - 2, mirrorY + 18, 0);
            graphics.pose().scale(0.5f, 0.5f, 1f);
            graphics.drawString(font, WEAPON_MIRROR_LABEL[i], 0, 0, LABEL_DIM, false);
            graphics.pose().popPose();
        }

        // The offhand is a real slot (drawn by vanilla), so it just needs its label here.
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + menu.offhandX - 2, topPos + menu.offhandY + 18, 0);
        graphics.pose().scale(0.5f, 0.5f, 1f);
        graphics.drawString(font, "OFFHAND", 0, 0, LABEL_DIM, false);
        graphics.pose().popPose();
    }

    private void drawWeaponHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        int mirrorY = topPos + 296;
        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            int mx = leftPos + 16 + i * 30;
            if (mouseX >= mx && mouseX < mx + 16 && mouseY >= mirrorY && mouseY < mirrorY + 16) {
                ItemStack stack = player.getInventory().items.get(WEAPON_MIRROR_HOTBAR_INDEX[i]);
                if (stack.isEmpty()) {
                    graphics.renderTooltip(font, Component.literal(WEAPON_MIRROR_LABEL[i]
                            + " - mirrors hotbar " + (WEAPON_MIRROR_HOTBAR_INDEX[i] + 1)), mouseX, mouseY);
                } else {
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                }
                return;
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

        int y = topPos + 298;
        int x = leftPos + 192;
        int spacing = 56;

        drawStatEntry(graphics, ICON_HEART, health01, x, y);
        drawStatEntry(graphics, ICON_FOOD, food01, x + spacing, y);
        drawStatEntry(graphics, ICON_WATER, water01, x + spacing * 2, y);
    }

    private void drawStatEntry(GuiGraphics graphics, ResourceLocation icon, float value01, int x, int y) {
        int size = 12;
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

        graphics.drawString(font, Math.round(value01 * 100) + "%", x + size + 4, y + 2, TEXT_COLOR, false);
    }

    private int severityColor(float value01) {
        if (value01 <= 0.25f) return 0xE23A2E;
        if (value01 <= 0.5f) return 0xE2A62E;
        return 0xE6E6E6;
    }

    /** Arrow between the 2x2 grid and its result slot. */
    private void drawCraftingArrow(GuiGraphics graphics) {
        int ax = leftPos + 68;
        int ay = topPos + TarkovInventoryMenu.CRAFT_RESULT_Y + 4;
        graphics.fill(ax, ay, ax + 16, ay + 2, HEADER_ACCENT);
        graphics.fill(ax + 12, ay - 3, ax + 14, ay + 5, HEADER_ACCENT);
        graphics.fill(ax + 14, ay - 1, ax + 16, ay + 3, HEADER_ACCENT);
    }

    // --- Crafting-table button, sits beside the INVENTORY header ---
    private static final int CRAFT_BTN_W = 44;
    private static final int CRAFT_BTN_H = 12;

    private int craftBtnX() { return leftPos + 300; }
    private int craftBtnY() { return topPos + 6; }

    private boolean isOverCraftButton(double mouseX, double mouseY) {
        return mouseX >= craftBtnX() && mouseX <= craftBtnX() + CRAFT_BTN_W
                && mouseY >= craftBtnY() && mouseY <= craftBtnY() + CRAFT_BTN_H;
    }

    private void drawCraftTableButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = isOverCraftButton(mouseX, mouseY);
        int bx = craftBtnX(), by = craftBtnY();
        graphics.fill(bx, by, bx + CRAFT_BTN_W, by + CRAFT_BTN_H,
                hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        graphics.renderOutline(bx, by, CRAFT_BTN_W, CRAFT_BTN_H,
                hovered ? StyledTheme.ACCENT : SLOT_BORDER);

        graphics.pose().pushPose();
        graphics.pose().translate(bx + 6, by + 3, 0);
        graphics.pose().scale(0.7f, 0.7f, 1f);
        graphics.drawString(font, "CRAFT 3x3", 0, 0, hovered ? 0xFFFFFFFF : HEADER_COLOR, false);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverCraftButton(mouseX, mouseY)) {
            NetworkHandler.CHANNEL.sendToServer(new OpenCraftingPacket());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Thin scrollbar to the right of the backpack grid, only when the bag overflows. */
    private void drawBackpackScrollbar(GuiGraphics graphics) {
        var view = menu.backpackView;
        if (!view.isScrollable()) return;

        int trackX = leftPos + 344;
        int trackTop = topPos + 138;
        int trackHeight = TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS * 18;

        graphics.fill(trackX, trackTop, trackX + 4, trackTop + trackHeight, 0xFF1C1C1C);

        int totalRows = Math.max(1, view.totalRows());
        int thumbHeight = Math.max(8,
                trackHeight * TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS / totalRows);
        int maxScroll = Math.max(1, view.maxScrollRow());
        int thumbY = trackTop + (trackHeight - thumbHeight) * view.getScrollRow() / maxScroll;

        graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFF6A6A6A);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        var view = menu.backpackView;
        if (view.isScrollable() && isOverBackpackArea(mouseX, mouseY)) {
            int target = view.getScrollRow() - (int) Math.signum(delta);
            target = Math.max(0, Math.min(target, view.maxScrollRow()));
            if (target != view.getScrollRow()) {
                // Applied locally for instant feedback AND sent to the server, because the
                // offset decides which real inventory index each slot maps to - if the two
                // sides disagreed, clicks would hit the wrong item.
                menu.setBackpackScroll(target);
                NetworkHandler.CHANNEL.sendToServer(new BackpackScrollPacket(target));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isOverBackpackArea(double mouseX, double mouseY) {
        int x1 = leftPos + 180, x2 = leftPos + 352;
        int y1 = topPos + 132;
        int y2 = y1 + TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS * 18 + 8;
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Replaced by drawSectionHeaders().
    }
}
