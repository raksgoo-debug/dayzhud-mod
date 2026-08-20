package com.dayzhud.mod.inventory;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Single source of truth for the extraction-shooter UI look. Every screen this mod
 * restyles pulls its colours and primitives from here, so changing the palette in one
 * place updates the whole game's UI rather than needing edits in a dozen screen classes.
 */
public final class StyledTheme {

    public static final int PANEL_BG = 0xF0121212;
    public static final int PANEL_BORDER = 0xFF3A3A3A;
    public static final int SECTION_BG = 0x40000000;
    public static final int SLOT_BG = 0xFF232323;
    public static final int SLOT_BORDER = 0xFF484848;
    public static final int HEADER_COLOR = 0xFF9A9A9A;
    public static final int HEADER_ACCENT = 0xFF4A4A4A;
    public static final int TEXT_COLOR = 0xFFCCCCCC;
    public static final int LABEL_DIM = 0xFF6A6A6A;
    public static final int ACCENT = 0xFF7FA650;
    public static final int BUTTON_BG = 0xFF272727;
    public static final int BUTTON_BG_HOVER = 0xFF383838;

    private StyledTheme() {}

    /** Full background panel with border. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.renderOutline(x, y, w, h, PANEL_BORDER);
    }

    /** Recessed backing for a functional region inside a panel. */
    public static void zone(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, SECTION_BG);
    }

    /** Standard 16x16 slot backdrop (call with the slot's own x/y). */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
        g.renderOutline(x - 1, y - 1, 18, 18, SLOT_BORDER);
    }

    /** Small uppercase section header with an accent rule under it. */
    public static void header(GuiGraphics g, Font font, String text, int x, int y, int ruleWidth) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, text, 0, 0, HEADER_COLOR, false);
        g.pose().popPose();
        g.fill(x, y + 9, x + ruleWidth, y + 10, HEADER_ACCENT);
    }

    /** Tiny dim caption, e.g. under a slot. */
    public static void caption(GuiGraphics g, Font font, String text, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        g.drawString(font, text, 0, 0, LABEL_DIM, false);
        g.pose().popPose();
    }

    /** Progress arrow used by furnace-style screens. 0..1 fill, left to right. */
    public static void progressArrow(GuiGraphics g, int x, int y, int width, int height, float progress) {
        g.fill(x, y, x + width, y + height, SLOT_BG);
        g.renderOutline(x, y, width, height, SLOT_BORDER);
        int filled = Math.round((width - 2) * Math.max(0f, Math.min(1f, progress)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, ACCENT);
        }
    }

    /** Vertical fuel/burn gauge. 0..1 fill, bottom to top. */
    public static void burnGauge(GuiGraphics g, int x, int y, int width, int height, float progress) {
        g.fill(x, y, x + width, y + height, SLOT_BG);
        g.renderOutline(x, y, width, height, SLOT_BORDER);
        int filled = Math.round((height - 2) * Math.max(0f, Math.min(1f, progress)));
        if (filled > 0) {
            g.fill(x + 1, y + height - 1 - filled, x + width - 1, y + height - 1, 0xFFC98A3A);
        }
    }
}
