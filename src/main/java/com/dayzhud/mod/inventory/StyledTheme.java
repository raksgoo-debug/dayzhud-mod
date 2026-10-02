package com.dayzhud.mod.inventory;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Single source of truth for the extraction-shooter UI look. Every screen this mod
 * restyles pulls its colours and primitives from here, so changing the palette in one
 * place updates the whole game's UI rather than needing edits in a dozen screen classes.
 *
 * 2.17.0: Arena Breakout style. No panel behind a screen, just a see-through dark backdrop
 * ({@link #backdrop}) over the world, and every section is a translucent dark box with a
 * small grey label strip on top. Orange marks
 * what's active; green / amber / blue / red are reserved for values.
 */
public final class StyledTheme {

    /** Opaque backing, only for pop-up dialogs that must hide what's under them. */
    public static final int PANEL_BG = 0xF0141516;
    public static final int PANEL_BORDER = 0xFF34383B;
    /** A section box: translucent, so the world shows through faintly. */
    public static final int SECTION_BG = 0xB0121314;
    public static final int SLOT_BG = 0xE6131415;
    /** SLOT_BG at full opacity: for panels that must hide what vanilla drew underneath. */
    public static final int SLOT_COVER = 0xFF131415;
    public static final int SLOT_BORDER = 0xFF2A2D30;
    public static final int HEADER_COLOR = 0xFFA7ACB0;
    /** Rules and dividers. */
    public static final int HEADER_ACCENT = 0xFF34383B;
    public static final int TEXT_COLOR = 0xFFD6D9DB;
    public static final int LABEL_DIM = 0xFF7D8286;
    /** Active tab text, selection marks, highlights. */
    public static final int ACCENT = 0xFFE8784A;
    public static final int BUTTON_BG = 0xC81E2123;
    public static final int BUTTON_BG_HOVER = 0xD82C3034;

    /** The label bar on top of a section or slot box. */
    public static final int STRIP_BG = 0xE025282B;
    /** Active tab: dark rust fill and edge, ACCENT text. */
    public static final int ACTIVE_BG = 0xE04A271D;
    public static final int ACTIVE_LINE = 0xFF6E3524;
    public static final int GOOD = 0xFF7CC46A;
    public static final int WARN = 0xFFE6C84A;
    public static final int INFO = 0xFF5CA6E6;
    public static final int BAD = 0xFFE0503C;
    public static final int HOVER = 0x30FFFFFF;
    /** Height of a label strip. */
    public static final int STRIP_H = 9;

    private StyledTheme() {}

    /** Backdrop dimming: darker toward the bottom, like the reference, but see-through. */
    public static final int BACKDROP_TOP = 0x88101112, BACKDROP_BOTTOM = 0xB0080909;

    /**
     * Dims the whole screen behind a UI: dark, but the world stays visible through it
     * (2.17.1; 2.17.0 had none at all, and vanilla's own backdrop is near-opaque).
     */
    public static void backdrop(GuiGraphics g, int screenWidth, int screenHeight) {
        g.fillGradient(0, 0, screenWidth, screenHeight, BACKDROP_TOP, BACKDROP_BOTTOM);
    }

    /**
     * A screen's outer frame. Deliberately draws nothing: the style has no panel, the
     * sections float over the dimmed world. Kept so every screen still marks where its frame is.
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
    }

    /** Opaque box for a pop-up (confirmations) that has to cover the screen under it. */
    public static void dialog(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.renderOutline(x, y, w, h, PANEL_BORDER);
    }

    /** A section box. */
    public static void zone(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, SECTION_BG);
        g.renderOutline(x1, y1, x2 - x1, y2 - y1, SLOT_BORDER);
    }

    /** Standard 16x16 slot backdrop (call with the slot's own x/y). */
    public static void slot(GuiGraphics g, int x, int y) {
        cell(g, x - 1, y - 1, 18, 18);
    }

    /** A dark cell or box of any size. */
    public static void cell(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_BG);
        g.renderOutline(x, y, w, h, SLOT_BORDER);
    }

    /**
     * The label strip: a grey bar {@code w} wide with the label in small caps, and optional
     * small text right-aligned on it.
     */
    public static void strip(GuiGraphics g, Font font, String label, int x, int y, int w,
                             String right, int rightColor) {
        g.fill(x, y, x + w, y + STRIP_H, STRIP_BG);
        small(g, font, label, x + 2, y + 2.5f, HEADER_COLOR);
        if (right != null) small(g, font, right, x + w - 2 - font.width(right) * 0.5f, y + 2.5f, rightColor);
    }

    /** Section header, as a strip sitting on the line a header used to. */
    public static void header(GuiGraphics g, Font font, String text, int x, int y, int ruleWidth) {
        int w = Math.max(ruleWidth, Math.round(font.width(text) * 0.5f) + 8);
        strip(g, font, text, x - 2, y - 1, w + 2, null, 0);
    }

    /**
     * A tab: ACTIVE_BG with an ACCENT label when active, a plain button otherwise. Label
     * centred at 0.75 size.
     */
    public static void tab(GuiGraphics g, Font font, String label, int x, int y, int w, int h,
                           boolean active, boolean hovered) {
        if (active) {
            g.fill(x, y, x + w, y + h, ACTIVE_BG);
            g.renderOutline(x, y, w, h, ACTIVE_LINE);
        } else {
            g.fill(x, y, x + w, y + h, hovered ? BUTTON_BG_HOVER : BUTTON_BG);
        }
        float tw = font.width(label) * 0.75f;
        scaled(g, font, label, x + (w - tw) / 2f, y + (h - 6) / 2f, 0.75f,
                active ? ACCENT : hovered ? TEXT_COLOR : HEADER_COLOR);
    }

    /** Tiny dim caption, e.g. under a slot. */
    public static void caption(GuiGraphics g, Font font, String text, int x, int y) {
        small(g, font, text, x, y, LABEL_DIM);
    }

    /** Half-size text. */
    public static void small(GuiGraphics g, Font font, String text, float x, float y, int color) {
        scaled(g, font, text, x, y, 0.5f, color);
    }

    public static void scaled(GuiGraphics g, Font font, String text, float x, float y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Progress arrow used by furnace-style screens. 0..1 fill, left to right. */
    public static void progressArrow(GuiGraphics g, int x, int y, int width, int height, float progress) {
        cell(g, x, y, width, height);
        int filled = Math.round((width - 2) * Math.max(0f, Math.min(1f, progress)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, ACCENT);
        }
    }

    /** Vertical fuel/burn gauge. 0..1 fill, bottom to top. */
    public static void burnGauge(GuiGraphics g, int x, int y, int width, int height, float progress) {
        cell(g, x, y, width, height);
        int filled = Math.round((height - 2) * Math.max(0f, Math.min(1f, progress)));
        if (filled > 0) {
            g.fill(x + 1, y + height - 1 - filled, x + width - 1, y + height - 1, 0xFFC98A3A);
        }
    }
}
