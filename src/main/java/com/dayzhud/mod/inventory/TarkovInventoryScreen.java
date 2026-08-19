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
 * paperdoll flanked by equipment slots that line up with the matching body parts, a
 * labelled inventory grid, a weapon-mirror row, and a stat strip reusing the same gauge
 * icons as the in-world HUD.
 *
 * Primary/Secondary/Holster/Sheath mirror hotbar slots 0-3 read-only, per design.
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
        this.imageWidth = 330;
        this.imageHeight = 236;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        graphics.renderOutline(x, y, imageWidth, imageHeight, PANEL_BORDER);

        // Subtle recessed backing behind each functional region, so the panel reads as
        // distinct zones rather than one flat slab.
        graphics.fill(x + 8, y + 22, x + 140, y + 180, SECTION_BG);        // equipment zone
        graphics.fill(x + 149, y + 22, x + 322, y + 82, SECTION_BG);       // inventory zone
        graphics.fill(x + 149, y + 104, x + 322, y + 126, SECTION_BG);     // hotbar zone
        graphics.fill(x + 8, y + 196, x + 140, y + 228, SECTION_BG);       // weapons zone

        graphics.fill(x + 145, y + 18, x + 146, y + 190, PANEL_BORDER);    // vertical divider
        graphics.fill(x + 8, y + 188, x + 322, y + 189, PANEL_BORDER);     // horizontal divider

        for (var slot : menu.slots) {
            drawSlotBackdrop(graphics, x + slot.x, y + slot.y);
        }

        int mirrorY = y + 206;
        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            drawSlotBackdrop(graphics, x + 14 + i * 30, mirrorY);
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
        drawCurioLabels(graphics);
        drawWeaponMirrors(graphics, mouseX, mouseY);
        drawStatBar(graphics);

        renderTooltip(graphics, mouseX, mouseY);
    }

    private void drawPaperdoll(GuiGraphics graphics) {
        LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer == null) return;

        // Positioned so the model's feet land on the boots-slot row and its head on the
        // helmet row, making the flanking equipment columns read as body-part aligned.
        int pdX = leftPos + 62;
        int pdY = topPos + 148;

        // Straight-right profile view rather than mouse-following.
        // NOTE ON TUNING: this vanilla helper turns the model by roughly
        // (angleXComponent * 20) degrees, so ~4.5 gives about a 90-degree turn. If the
        // model ends up facing the wrong way, flip the sign; if it's not turned far
        // enough, raise the magnitude.
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, pdX, pdY, 44,
                -4.5f, 0.0f, localPlayer);
    }

    private void drawSectionHeaders(GuiGraphics graphics) {
        drawHeader(graphics, "EQUIPMENT", leftPos + 10, topPos + 10, 54);
        drawHeader(graphics, "INVENTORY", leftPos + 151, topPos + 10, 54);
        drawHeader(graphics, "HOTBAR", leftPos + 151, topPos + 92, 40);
        drawHeader(graphics, "WEAPONS", leftPos + 10, topPos + 194, 46);
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
     * Tiny label beside each Curios slot naming what it is (mask, backpack, uniform...),
     * pulled straight from the slot identifier Curios reports - so slots added by other
     * mods get named automatically without this mod hardcoding anything.
     */
    private void drawCurioLabels(GuiGraphics graphics) {
        for (var info : menu.curioSlotInfos) {
            String label = prettify(info.identifier());
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + info.x() + 19, topPos + info.y() + 5, 0);
            graphics.pose().scale(0.5f, 0.5f, 1f);
            graphics.drawString(font, label, 0, 0, LABEL_DIM, false);
            graphics.pose().popPose();
        }
    }

    private String prettify(String identifier) {
        String cleaned = identifier.replace('_', ' ');
        return cleaned.toUpperCase(Locale.ROOT);
    }

    private void drawWeaponMirrors(GuiGraphics graphics, int mouseX, int mouseY) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        int mirrorY = topPos + 206;
        for (int i = 0; i < WEAPON_MIRROR_HOTBAR_INDEX.length; i++) {
            int mx = leftPos + 14 + i * 30;
            ItemStack stack = player.getInventory().items.get(WEAPON_MIRROR_HOTBAR_INDEX[i]);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, mx, mirrorY);
                graphics.renderItemDecorations(font, stack, mx, mirrorY);
            }

            graphics.pose().pushPose();
            graphics.pose().translate(mx - 1, mirrorY + 18, 0);
            graphics.pose().scale(0.5f, 0.5f, 1f);
            graphics.drawString(font, WEAPON_MIRROR_LABEL[i], 0, 0, LABEL_DIM, false);
            graphics.pose().popPose();

            if (mouseX >= mx && mouseX < mx + 16 && mouseY >= mirrorY && mouseY < mirrorY + 16) {
                graphics.renderTooltip(font, Component.literal(WEAPON_MIRROR_LABEL[i]
                        + " - mirrors hotbar slot " + (WEAPON_MIRROR_HOTBAR_INDEX[i] + 1)), mouseX, mouseY);
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

        int y = topPos + 206;
        int x = leftPos + 162;
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
