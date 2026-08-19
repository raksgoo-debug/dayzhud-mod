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
        this.imageHeight = 252;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        graphics.renderOutline(x, y, imageWidth, imageHeight, PANEL_BORDER);

        // Recessed zone backings so the panel reads as distinct regions.
        graphics.fill(x + 8, y + 26, x + 172, y + 152, SECTION_BG);    // paperdoll + armor
        graphics.fill(x + 8, y + 158, x + 172, y + 206, SECTION_BG);    // curios grid
        graphics.fill(x + 180, y + 26, x + 352, y + 106, SECTION_BG);   // inventory
        graphics.fill(x + 180, y + 112, x + 352, y + 136, SECTION_BG);  // hotbar
        graphics.fill(x + 8, y + 218, x + 172, y + 246, SECTION_BG);    // weapons

        graphics.fill(x + 176, y + 22, x + 177, y + 208, PANEL_BORDER); // vertical divider
        graphics.fill(x + 8, y + 212, x + 352, y + 213, PANEL_BORDER);  // horizontal divider

        for (var slot : menu.slots) {
            drawSlotBackdrop(graphics, x + slot.x, y + slot.y);
        }

        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            drawSlotBackdrop(graphics, x + 16 + i * 30, y + 224);
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
        int pdX = leftPos + 88;
        int pdY = topPos + 146;

        // Right-facing profile. TUNING NOTE: this helper turns the model by roughly
        // (angleXComponent * 20) degrees off front-facing, so +/-4.5 is about a quarter
        // turn. If it faces the wrong way, flip the sign; if it's under-rotated, increase
        // the magnitude.
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, pdX, pdY, 42,
                4.5f, 0.0f, localPlayer);
    }

    private void drawSectionHeaders(GuiGraphics graphics) {
        drawHeader(graphics, "EQUIPMENT", leftPos + 12, topPos + 14, 54);
        drawHeader(graphics, "GEAR", leftPos + 12, topPos + 146, 30);
        drawHeader(graphics, "INVENTORY", leftPos + 184, topPos + 14, 54);
        drawHeader(graphics, "HOTBAR", leftPos + 184, topPos + 100, 40);
        drawHeader(graphics, "WEAPONS", leftPos + 12, topPos + 206, 46);
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

        int mirrorY = topPos + 224;
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
    }

    private void drawWeaponHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        int mirrorY = topPos + 224;
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

        int y = topPos + 226;
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

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Replaced by drawSectionHeaders().
    }
}
