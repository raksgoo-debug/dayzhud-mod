package com.dayzhud.mod.client;

import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Draws a DayZ-style vertical status stack in the bottom-left corner: an icon plus a
 * numeric readout per row, colored by severity. Icons are drawn procedurally (small pixel
 * grids) rather than using texture files, so there's nothing to import or attribute.
 *
 * Layout/scale/position are all plain constants below - tweak freely.
 */
public class DayzHudOverlay implements IGuiOverlay {

    private static final int ICON_SCALE = 2;      // each icon pixel is drawn at 2x2 screen pixels
    private static final int ICON_SIZE = 8 * ICON_SCALE;
    private static final int ROW_HEIGHT = ICON_SIZE + 4;
    private static final int MARGIN_X = 8;
    private static final int MARGIN_Y = 8;
    private static final int TEXT_GAP = 6;

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;

        // Values, all normalized 0..1 unless noted.
        float health01 = player.getHealth() / Math.max(1f, player.getMaxHealth());
        float food01 = player.getFoodData().getFoodLevel() / 20f;
        float water01 = ThirstWasTakenCompat.getThirst01(player)
                .orElseGet(() -> player.getFoodData().getSaturationLevel() / 20f); // fallback if TWT absent
        float stamina01 = VitalsTracker.getStamina01();
        float temperature01 = VitalsTracker.getTemperature01();

        int baseX = MARGIN_X;
        int baseY = screenHeight - MARGIN_Y - ROW_HEIGHT;

        // Bottom-to-top stack, DayZ order: temperature, stamina, food, water, health (health nearest hotbar).
        drawRow(graphics, baseX, baseY - ROW_HEIGHT * 4, Icons.THERMOMETER, tempColor(temperature01), tempLabel(temperature01));
        drawRow(graphics, baseX, baseY - ROW_HEIGHT * 3, Icons.BOLT, severityColor(stamina01, false), Math.round(stamina01 * 100) + "%");
        drawRow(graphics, baseX, baseY - ROW_HEIGHT * 2, Icons.APPLE, severityColor(food01, false), Math.round(food01 * 100) + "%");
        drawRow(graphics, baseX, baseY - ROW_HEIGHT, Icons.DROP, severityColor(water01, false), Math.round(water01 * 100) + "%");
        drawRow(graphics, baseX, baseY, Icons.HEART, severityColor(health01, true), Math.round(health01 * 100) + "%");
    }

    private void drawRow(GuiGraphics graphics, int x, int y, boolean[][] icon, int color, String text) {
        drawIcon(graphics, icon, x, y, color);
        graphics.drawString(Minecraft.getInstance().font, text, x + ICON_SIZE + TEXT_GAP, y + ICON_SIZE / 2 - 4, 0xFFFFFF, true);
    }

    private void drawIcon(GuiGraphics graphics, boolean[][] grid, int x, int y, int color) {
        RenderSystem.enableBlend();
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                if (grid[row][col]) {
                    int px = x + col * ICON_SCALE;
                    int py = y + row * ICON_SCALE;
                    graphics.fill(px, py, px + ICON_SCALE, py + ICON_SCALE, color);
                }
            }
        }
        RenderSystem.disableBlend();
    }

    /** Red when critical, orange mid, white/green when healthy - DayZ-ish severity coloring. */
    private int severityColor(float value01, boolean isHealth) {
        int alpha = 0xFF000000;
        if (value01 <= 0.25f) return alpha | 0xE23A2E;      // red - critical
        if (value01 <= 0.5f) return alpha | 0xE2A62E;       // amber - low
        return alpha | (isHealth ? 0xFFFFFF : 0xCFCFCF);    // white/light grey - fine
    }

    private int tempColor(float t) {
        int alpha = 0xFF000000;
        if (t < 0.3f) return alpha | 0x4DA6FF;  // cold - blue
        if (t > 0.7f) return alpha | 0xFF5C33;  // hot - orange/red
        return alpha | 0xFFFFFF;                // neutral - white
    }

    private String tempLabel(float t) {
        if (t < 0.15f) return "Freezing";
        if (t < 0.35f) return "Cold";
        if (t < 0.65f) return "Normal";
        if (t < 0.85f) return "Hot";
        return "Overheating";
    }

    /** Procedural 8x8 pixel icons - no texture files needed. */
    private static final class Icons {
        static final boolean[][] HEART = bools(
                "01100110",
                "11111111",
                "11111111",
                "11111111",
                "01111100",
                "00111000",
                "00010000",
                "00000000"
        );
        static final boolean[][] DROP = bools(
                "00010000",
                "00111000",
                "01111100",
                "11111110",
                "11011110",
                "11111110",
                "01111100",
                "00111000"
        );
        static final boolean[][] APPLE = bools(
                "00101000",
                "00010000",
                "01111100",
                "11111110",
                "11111110",
                "11111110",
                "01111100",
                "00111000"
        );
        static final boolean[][] BOLT = bools(
                "00011000",
                "00110000",
                "01111100",
                "00111000",
                "00011000",
                "00110000",
                "01100000",
                "00000000"
        );
        static final boolean[][] THERMOMETER = bools(
                "00110000",
                "00110000",
                "00110000",
                "00110000",
                "00110000",
                "01111000",
                "11111100",
                "11111100"
        );

        static boolean[][] bools(String... rows) {
            boolean[][] out = new boolean[rows.length][];
            for (int i = 0; i < rows.length; i++) {
                String row = rows[i];
                boolean[] r = new boolean[row.length()];
                for (int j = 0; j < row.length(); j++) r[j] = row.charAt(j) == '1';
                out[i] = r;
            }
            return out;
        }
    }
}
