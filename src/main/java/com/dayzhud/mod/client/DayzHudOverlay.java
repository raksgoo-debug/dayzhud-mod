package com.dayzhud.mod.client;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Draws a DayZ-style status row in the bottom-right corner (Temperature, Food, Water,
 * Health - each a thin outline icon + percentage) plus a DayZ-style vertical stamina
 * bar in the bottom-left corner. Icons are outline PNG textures, normalized to a
 * consistent visual center so they line up cleanly against the percentage text
 * regardless of each icon's own silhouette shape.
 */
public class DayzHudOverlay implements IGuiOverlay {

    private static final int ICON_SIZE = 12;
    private static final int COLUMN_WIDTH = 36;
    private static final int TEXT_GAP = 3;
    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 6;           // sits right against the hotbar

    private static final int STAMINA_BAR_WIDTH = 6;
    private static final int STAMINA_BAR_HEIGHT = 54;
    private static final int STAMINA_MARGIN_X = 10;

    private static final ResourceLocation ICON_HEART = rl("icon_heart");
    private static final ResourceLocation ICON_DROPLET = rl("icon_droplet");
    private static final ResourceLocation ICON_FOOD = rl("icon_food");
    private static final ResourceLocation ICON_THERMOMETER = rl("icon_thermometer");

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(DayzHudMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;

        float health01 = player.getHealth() / Math.max(1f, player.getMaxHealth());
        float food01 = player.getFoodData().getFoodLevel() / 20f;
        float water01 = ThirstWasTakenCompat.getThirst01(player)
                .orElseGet(() -> player.getFoodData().getSaturationLevel() / 20f);
        float stamina01 = VitalsTracker.getStamina01();
        float temperature01 = VitalsTracker.getTemperature01();

        // Right-side row: temperature, food, water, health - health closest to the corner.
        int rowY = screenHeight - MARGIN_Y - ICON_SIZE;
        int rightX = screenWidth - MARGIN_X;

        int col3 = rightX - COLUMN_WIDTH * 0; // health
        int col2 = rightX - COLUMN_WIDTH * 1; // water
        int col1 = rightX - COLUMN_WIDTH * 2; // food
        int col0 = rightX - COLUMN_WIDTH * 3; // temperature

        drawStat(graphics, col0, rowY, ICON_THERMOMETER, tempColor(temperature01), tempLabel(temperature01));
        drawStat(graphics, col1, rowY, ICON_FOOD, severityColor(food01, false), Math.round(food01 * 100) + "%");
        drawStat(graphics, col2, rowY, ICON_DROPLET, severityColor(water01, false), Math.round(water01 * 100) + "%");
        drawStat(graphics, col3, rowY, ICON_HEART, severityColor(health01, true), Math.round(health01 * 100) + "%");

        drawStaminaBar(graphics, screenWidth, screenHeight, stamina01);
    }

    /** DayZ-style vertical stamina bar, bottom-left corner. */
    private void drawStaminaBar(GuiGraphics graphics, int screenWidth, int screenHeight, float stamina01) {
        int barBottom = screenHeight - MARGIN_Y;
        int barTop = barBottom - STAMINA_BAR_HEIGHT;
        int barLeft = STAMINA_MARGIN_X;
        int barRight = barLeft + STAMINA_BAR_WIDTH;

        // Background track (semi-transparent dark).
        graphics.fill(barLeft, barTop, barRight, barBottom, 0x80000000);

        // Filled portion, growing upward from the bottom - matches DayZ's stamina gauge.
        int filledHeight = Math.round(STAMINA_BAR_HEIGHT * Math.max(0f, Math.min(1f, stamina01)));
        int fillTop = barBottom - filledHeight;
        int fillColor = 0xFF000000 | severityColor(stamina01, false);
        graphics.fill(barLeft, fillTop, barRight, barBottom, fillColor);

        // 1px outline for contrast against bright/dark backgrounds alike.
        graphics.renderOutline(barLeft, barTop, STAMINA_BAR_WIDTH, STAMINA_BAR_HEIGHT, 0xFFFFFFFF);
    }

    private void drawStat(GuiGraphics graphics, int columnRightX, int y, ResourceLocation icon, int color, String text) {
        float textScale = 0.8f;
        int scaledTextWidth = Math.round(Minecraft.getInstance().font.width(text) * textScale);
        int totalWidth = ICON_SIZE + TEXT_GAP + scaledTextWidth;
        int startX = columnRightX - totalWidth;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                1f
        );
        graphics.blit(icon, startX, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        int textX = startX + ICON_SIZE + TEXT_GAP;
        float scaledFontHeight = Minecraft.getInstance().font.lineHeight * textScale;
        int textY = y + Math.round((ICON_SIZE - scaledFontHeight) / 2f);
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0);
        graphics.pose().scale(textScale, textScale, 1f);
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    private int severityColor(float value01, boolean isHealth) {
        if (value01 <= 0.25f) return 0xE23A2E;
        if (value01 <= 0.5f) return 0xE2A62E;
        return isHealth ? 0xFFFFFF : 0xCFCFCF;
    }

    private int tempColor(float t) {
        if (t < 0.3f) return 0x4DA6FF;
        if (t > 0.7f) return 0xFF5C33;
        return 0xFFFFFF;
    }

    private String tempLabel(float t) {
        if (t < 0.15f) return "Freezing";
        if (t < 0.35f) return "Cold";
        if (t < 0.65f) return "Normal";
        if (t < 0.85f) return "Hot";
        return "Overheating";
    }
}
