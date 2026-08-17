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
 * Draws a DayZ-style horizontal status row in the bottom-right corner: an icon plus a
 * numeric readout per column, colored by severity. Icons are smooth anti-aliased PNG
 * textures (src/main/resources/assets/dayzhud/textures/gui/) rather than blocky pixel art.
 */
public class DayzHudOverlay implements IGuiOverlay {

    private static final int ICON_SIZE = 9;        // on-screen icon size in pixels
    private static final int COLUMN_WIDTH = 30;      // horizontal spacing between stat columns
    private static final int TEXT_GAP = 2;
    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 30;          // clears the hotbar + XP bar + any hotbar-adjacent icons

    private static final ResourceLocation ICON_HEART = rl("icon_heart");
    private static final ResourceLocation ICON_DROPLET = rl("icon_droplet");
    private static final ResourceLocation ICON_FOOD = rl("icon_food");
    private static final ResourceLocation ICON_BOLT = rl("icon_bolt");
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
                .orElseGet(() -> player.getFoodData().getSaturationLevel() / 20f); // fallback if TWT absent
        float stamina01 = VitalsTracker.getStamina01();
        float temperature01 = VitalsTracker.getTemperature01();

        // Five columns, right-aligned, growing leftward from the bottom-right corner.
        // Order left-to-right: temperature, stamina, food, water, health (health closest to the corner).
        int rowY = screenHeight - MARGIN_Y - ICON_SIZE;
        int rightX = screenWidth - MARGIN_X;

        int col4 = rightX - COLUMN_WIDTH * 0; // health (rightmost)
        int col3 = rightX - COLUMN_WIDTH * 1; // water
        int col2 = rightX - COLUMN_WIDTH * 2; // food
        int col1 = rightX - COLUMN_WIDTH * 3; // stamina
        int col0 = rightX - COLUMN_WIDTH * 4; // temperature (leftmost)

        drawStat(graphics, col0, rowY, ICON_THERMOMETER, tempColor(temperature01), tempLabel(temperature01), true);
        drawStat(graphics, col1, rowY, ICON_BOLT, severityColor(stamina01, false), Math.round(stamina01 * 100) + "%", false);
        drawStat(graphics, col2, rowY, ICON_FOOD, severityColor(food01, false), Math.round(food01 * 100) + "%", false);
        drawStat(graphics, col3, rowY, ICON_DROPLET, severityColor(water01, false), Math.round(water01 * 100) + "%", false);
        drawStat(graphics, col4, rowY, ICON_HEART, severityColor(health01, true), Math.round(health01 * 100) + "%", false);
    }

    /** x is the column's right edge; icon+text are right-aligned within the column so columns don't collide. */
    private void drawStat(GuiGraphics graphics, int columnRightX, int y, ResourceLocation icon, int color, String text, boolean isWordLabel) {
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
        int textY = y + ICON_SIZE / 2 - 4;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0);
        graphics.pose().scale(textScale, textScale, 1f);
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    private int severityColor(float value01, boolean isHealth) {
        if (value01 <= 0.25f) return 0xE23A2E;      // red - critical
        if (value01 <= 0.5f) return 0xE2A62E;        // amber - low
        return isHealth ? 0xFFFFFF : 0xCFCFCF;       // white/light grey - fine
    }

    private int tempColor(float t) {
        if (t < 0.3f) return 0x4DA6FF;  // cold - blue
        if (t > 0.7f) return 0xFF5C33;  // hot - orange/red
        return 0xFFFFFF;                // neutral - white
    }

    private String tempLabel(float t) {
        if (t < 0.15f) return "Freezing";
        if (t < 0.35f) return "Cold";
        if (t < 0.65f) return "Normal";
        if (t < 0.85f) return "Hot";
        return "Overheating";
    }
}
