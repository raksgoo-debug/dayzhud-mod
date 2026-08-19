package com.dayzhud.mod.client;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Draws a DayZ-style status row in the bottom-right corner (Temperature, Food, Water,
 * Health) plus a thin horizontal stamina bar and movement-state icon in the bottom-left.
 *
 * Each status icon is drawn as two layers: a thin outline (always fully visible) and a
 * solid fill clipped to the bottom N% of the icon based on the stat's value - a liquid-
 * gauge effect so the icon itself visually communicates the level, not just the percentage
 * text next to it. Icon position is fixed per column regardless of the text's digit count,
 * so it never visibly shifts as a value crosses 100/99 etc.
 */
public class DayzHudOverlay implements IGuiOverlay {

    private static final int ICON_SIZE = 12;
    private static final int COLUMN_WIDTH = 36;
    private static final int TEXT_GAP = 3;
    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 6;

    private static final int STAMINA_BAR_WIDTH = 130;
    private static final int STAMINA_BAR_HEIGHT = 3;
    private static final int STAMINA_BAR_GAP_FROM_OFFHAND = 10; // clearance from the offhand slot
    private static final int STAMINA_BAR_MIN_LEFT = 70;          // never sit closer to the left edge than this (clears other mods' bottom-left UI)

    private static final int MOVEMENT_ICON_SIZE = 12;
    private static final int MOVEMENT_ICON_GAP = 6;

    private static final int COLOR_NEUTRAL = 0xE6E6E6;
    private static final int COLOR_LOW = 0xE2A62E;
    private static final int COLOR_CRITICAL = 0xE23A2E;
    private static final int COLOR_COLD = 0x4DA6FF;
    private static final int COLOR_HOT = 0xFF5C33;
    private static final int COLOR_OUTLINE = 0x9A9A9A;

    private static final ResourceLocation ICON_HEART_OUTLINE = rl("icon_heart_outline");
    private static final ResourceLocation ICON_HEART_SOLID = rl("icon_heart_solid");
    private static final ResourceLocation ICON_DROPLET_OUTLINE = rl("icon_droplet_outline");
    private static final ResourceLocation ICON_DROPLET_SOLID = rl("icon_droplet_solid");
    private static final ResourceLocation ICON_FOOD_OUTLINE = rl("icon_food_outline");
    private static final ResourceLocation ICON_FOOD_SOLID = rl("icon_food_solid");
    private static final ResourceLocation ICON_THERMOMETER_OUTLINE = rl("icon_thermometer_outline");
    private static final ResourceLocation ICON_THERMOMETER_SOLID = rl("icon_thermometer_solid");

    private static final ResourceLocation ICON_WALKING = rl("icon_walking");
    private static final ResourceLocation ICON_SPRINTING = rl("icon_sprinting");
    private static final ResourceLocation ICON_CROUCHING = rl("icon_crouching");
    private static final ResourceLocation ICON_CRAWLING = rl("icon_crawling");
    private static final ResourceLocation ICON_MOUNTED = rl("icon_mounted");
    private static final ResourceLocation ICON_STANDING = rl("icon_standing");

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

        int rowY = screenHeight - MARGIN_Y - ICON_SIZE;
        int rightX = screenWidth - MARGIN_X;

        // Icon position is purely a function of column index - NOT the width of any label
        // (worst-case or current). This keeps every gap between icons visually identical
        // regardless of how long each stat's text happens to be, and also keeps each icon
        // perfectly still as its own number changes digit count.
        int col3Icon = rightX - COLUMN_WIDTH * 1; // health
        int col2Icon = rightX - COLUMN_WIDTH * 2; // water
        int col1Icon = rightX - COLUMN_WIDTH * 3; // food
        int col0Icon = rightX - COLUMN_WIDTH * 4; // temperature

        drawGaugeStat(graphics, col0Icon, rowY, ICON_THERMOMETER_OUTLINE, ICON_THERMOMETER_SOLID, temperature01, tempColor(temperature01), tempCelsius(temperature01) + "\u00B0C");
        drawGaugeStat(graphics, col1Icon, rowY, ICON_FOOD_OUTLINE, ICON_FOOD_SOLID, food01, severityColor(food01), Math.round(food01 * 100) + "%");
        drawGaugeStat(graphics, col2Icon, rowY, ICON_DROPLET_OUTLINE, ICON_DROPLET_SOLID, water01, severityColor(water01), Math.round(water01 * 100) + "%");
        drawGaugeStat(graphics, col3Icon, rowY, ICON_HEART_OUTLINE, ICON_HEART_SOLID, health01, severityColor(health01), Math.round(health01 * 100) + "%");

        drawStaminaBar(graphics, stamina01, screenWidth, screenHeight);
        drawMovementIcon(graphics, player, screenWidth, screenHeight);
    }

    private void drawStaminaBar(GuiGraphics graphics, float stamina01, int screenWidth, int screenHeight) {
        int barBottom = screenHeight - MARGIN_Y;
        int barTop = barBottom - STAMINA_BAR_HEIGHT;

        // Anchored relative to the hotbar/offhand slot so it never overlaps them regardless
        // of resolution or GUI scale, instead of a fixed left-edge offset.
        // Matches DayzHotbarOverlay's geometry (9 slots x 20px, offhand 26px to its left),
        // not vanilla's - keep these in sync if either changes.
        int hotbarLeft = screenWidth / 2 - 90;
        int offhandLeft = hotbarLeft - 26;
        int barRight = offhandLeft - STAMINA_BAR_GAP_FROM_OFFHAND;
        int barLeft = Math.max(STAMINA_BAR_MIN_LEFT, barRight - STAMINA_BAR_WIDTH);

        // Lighter translucent grey backing plate instead of near-black, so the empty
        // portion reads as a soft panel rather than a harsh dark block.
        graphics.fill(barLeft, barTop, barRight, barBottom, 0x90707070);

        int filledWidth = Math.round((barRight - barLeft) * Math.max(0f, Math.min(1f, stamina01)));
        int fillColor = 0xFF000000 | severityColor(stamina01);
        graphics.fill(barLeft, barTop, barLeft + filledWidth, barBottom, fillColor);
    }

    private void drawMovementIcon(GuiGraphics graphics, LocalPlayer player, int screenWidth, int screenHeight) {
        ResourceLocation icon = movementIconFor(player);

        // Matches DayzHotbarOverlay's geometry (9 slots x 20px, offhand 26px to its left),
        // not vanilla's - keep these in sync if either changes.
        int hotbarLeft = screenWidth / 2 - 90;
        int offhandLeft = hotbarLeft - 26;
        int barRight = offhandLeft - STAMINA_BAR_GAP_FROM_OFFHAND;
        int barLeft = Math.max(STAMINA_BAR_MIN_LEFT, barRight - STAMINA_BAR_WIDTH);

        int barBottom = screenHeight - MARGIN_Y;
        int y = barBottom - MOVEMENT_ICON_SIZE;
        int x = barLeft - MOVEMENT_ICON_GAP - MOVEMENT_ICON_SIZE;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(icon, x, y, 0, 0, MOVEMENT_ICON_SIZE, MOVEMENT_ICON_SIZE, MOVEMENT_ICON_SIZE, MOVEMENT_ICON_SIZE);
        RenderSystem.disableBlend();
    }

    private ResourceLocation movementIconFor(LocalPlayer player) {
        if (player.isPassenger()) return ICON_MOUNTED;

        boolean crawling = player.getPose() == Pose.SWIMMING && !player.isInWater() && !player.isFallFlying();
        if (crawling) return ICON_CRAWLING;
        if (player.isCrouching()) return ICON_CROUCHING;
        if (player.isSprinting()) return ICON_SPRINTING;

        double speedSq = player.getDeltaMovement().horizontalDistanceSqr();
        if (speedSq > 1.0E-4) return ICON_WALKING;

        return ICON_STANDING;
    }

    private void drawGaugeStat(GuiGraphics graphics, int startX, int y, ResourceLocation outline, ResourceLocation solid, float value01, int color, String text) {
        float textScale = 0.65f;

        RenderSystem.enableBlend();

        setTint(COLOR_OUTLINE);
        graphics.blit(outline, startX, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        int fillHeight = Math.round(ICON_SIZE * Math.max(0f, Math.min(1f, value01)));
        if (fillHeight > 0) {
            int clipTop = y + (ICON_SIZE - fillHeight);
            graphics.enableScissor(startX, clipTop, startX + ICON_SIZE, y + ICON_SIZE);
            setTint(color);
            graphics.blit(solid, startX, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            graphics.disableScissor();
        }

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

    private void setTint(int color) {
        RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                1f
        );
    }

    private int severityColor(float value01) {
        if (value01 <= 0.25f) return COLOR_CRITICAL;
        if (value01 <= 0.5f) return COLOR_LOW;
        return COLOR_NEUTRAL;
    }

    private int tempColor(float t) {
        if (t < 0.3f) return COLOR_COLD;
        if (t > 0.7f) return COLOR_HOT;
        return COLOR_NEUTRAL;
    }

    /** Maps the internal 0-1 gauge to a plausible Celsius reading for display (0 = -10C, 0.5 = 15C, 1 = 40C). */
    private int tempCelsius(float t) {
        return Math.round(-10f + t * 50f);
    }
}
