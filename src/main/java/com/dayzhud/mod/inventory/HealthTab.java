package com.dayzhud.mod.inventory;

import com.dayzhud.mod.client.UiSounds;
import com.dayzhud.mod.compat.FirstAidCompat;
import com.dayzhud.mod.compat.FirstAidCompat.Limb;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The inventory's HEALTH tab (2.16.0), in the equipment card: First Aid's per-limb health as
 * a front-on body diagram coloured by condition, with each limb's numbers either side.
 *
 * Treating works like First Aid's own screen, from the inventory: pick up a bandage or
 * plaster and hold it on a limb (diagram or row) for the item's apply time. The server then
 * does what First Aid's packet does, with the cursor stack instead of the hand
 * (ApplyHealingPacket). A limb being healed shows a "+" and its progress.
 */
final class HealthTab {

    private static final int COLOR_GOOD = 0xFF5E8C3A, COLOR_HURT = 0xFFC9A227, COLOR_BAD = 0xFFB0302A,
            COLOR_GONE = 0xFF3A1A1A, COLOR_GONE_TEXT = 0xFF7A3A3A, OUTLINE = 0xFF0E0E0E,
            TEXT = 0xFFCCCCCC, HEADER = 0xFF9A9A9A, DIM = 0xFF6A6A6A, TRACK = 0xFF2A2A2A,
            ACCENT = StyledTheme.ACCENT, BLOOD = 0xFFE23A2E;

    /** Diagram origin (top of the head, centre line) in panel coordinates, and GUI px per skin px. */
    private static final float ORIGIN_X = 90, ORIGIN_Y = 52, K = 2.7f;

    /** x, y, w, h in skin pixels from the origin. Front view, so the right side is on the left. */
    private static final Map<String, int[]> SHAPES = Map.of(
            "HEAD", new int[]{-4, 0, 8, 8},
            "BODY", new int[]{-4, 8, 8, 12},
            "RIGHT_ARM", new int[]{-8, 8, 4, 12},
            "LEFT_ARM", new int[]{4, 8, 4, 12},
            "RIGHT_LEG", new int[]{-4, 20, 4, 9},
            "LEFT_LEG", new int[]{0, 20, 4, 9},
            "RIGHT_FOOT", new int[]{-4, 29, 4, 3},
            "LEFT_FOOT", new int[]{0, 29, 4, 3});

    private static final String[] LEFT_ROWS = {"HEAD", "RIGHT_ARM", "RIGHT_LEG", "RIGHT_FOOT"};
    private static final String[] RIGHT_ROWS = {"BODY", "LEFT_ARM", "LEFT_LEG", "LEFT_FOOT"};
    private static final int ROW_LEFT_X = 16, ROW_RIGHT_X = 164, ROW_Y = 54, ROW_SPACING = 21, ROW_W = 44;
    private static final int BAR_W = 30;

    private final TarkovInventoryScreen screen;

    /** The limb a healing item is being held on, or null. */
    private String holdLimb;
    private long holdStart;
    private int holdMs;

    HealthTab(TarkovInventoryScreen screen) {
        this.screen = screen;
    }

    boolean holding() {
        return holdLimb != null;
    }

    void cancel() {
        holdLimb = null;
    }

    // ---- drawing ----------------------------------------------------------------------------

    void render(GuiGraphics g, int mouseX, int mouseY) {
        Player player = Minecraft.getInstance().player;
        List<Limb> limbs = FirstAidCompat.limbs(player).orElse(null);
        if (limbs == null) return;
        String hovered = limbAt(mouseX, mouseY);
        ItemStack carried = screen.menu().getCarried();
        boolean treating = FirstAidCompat.isHealingItem(carried);
        float holdFraction = updateHold(hovered, carried);

        for (Limb limb : limbs) {
            int[] r = rect(limb.id());
            if (r == null) continue;
            g.fill(r[0], r[1], r[2], r[3], OUTLINE);
            g.fill(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, color(limb));
            if (limb.id().equals(hovered) && (treating || holdLimb == null)) {
                g.fill(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, 0x30FFFFFF);
            }
            if (limb.id().equals(holdLimb)) {
                int top = r[3] - 1 - Math.round((r[3] - r[1] - 2) * holdFraction);
                g.fill(r[0] + 1, top, r[2] - 1, r[3] - 1, 0x70FFFFFF);
            }
            if (limb.healProgress() >= 0) {
                int cx = (r[0] + r[2]) / 2, cy = (r[1] + r[3]) / 2;
                g.fill(cx - 2, cy, cx + 3, cy + 1, 0xFFFFFFFF);
                g.fill(cx, cy - 2, cx + 1, cy + 3, 0xFFFFFFFF);
            }
        }
        // Bleeding (dayzhud's own injuries) is body-wide, not per limb: a drop on the chest.
        if (com.dayzhud.mod.injury.ClientInjuries.light() + com.dayzhud.mod.injury.ClientInjuries.heavy() > 0) {
            int[] body = rect("BODY");
            if (body != null) {
                int cx = (body[0] + body[2]) / 2, cy = body[1] + (body[3] - body[1]) / 3;
                g.fill(cx - 1, cy, cx + 2, cy + 4, BLOOD);
                g.fill(cx, cy - 1, cx + 1, cy, BLOOD);
            }
        }

        for (int i = 0; i < LEFT_ROWS.length; i++) {
            drawRow(g, find(limbs, LEFT_ROWS[i]), screen.left() + ROW_LEFT_X,
                    screen.top() + ROW_Y + i * ROW_SPACING, false);
            drawRow(g, find(limbs, RIGHT_ROWS[i]), screen.left() + ROW_RIGHT_X,
                    screen.top() + ROW_Y + i * ROW_SPACING, true);
        }

        String hint;
        int hintColor;
        if (holdLimb != null) {
            hint = "APPLYING " + Math.round(holdFraction * 100) + "%";
            hintColor = ACCENT;
        } else if (treating) {
            hint = "HOLD ON A LIMB TO APPLY " + carried.getHoverName().getString().toUpperCase(Locale.ROOT);
            hintColor = ACCENT;
        } else {
            hint = "HOLD A BANDAGE OR PLASTER ON A LIMB TO TREAT IT";
            hintColor = DIM;
        }
        float hx = screen.left() + ORIGIN_X - screen.font().width(hint) / 4f;
        screen.caption(g, hint, Math.max(screen.left() + 12, hx), screen.top() + 141, hintColor);
    }

    private void drawRow(GuiGraphics g, Limb limb, int x, int y, boolean rightAligned) {
        if (limb == null) return;
        int color = color(limb);
        boolean full = limb.current() >= limb.max();
        String label = shortName(limb.id()) + (limb.healProgress() >= 0 ? "  +" : "");
        String value = number(limb.current()) + "/" + limb.max();
        int valueColor = full ? TEXT : limb.current() <= 0 ? COLOR_GONE_TEXT : color;
        int fill = Math.round(BAR_W * limb.health01());
        if (rightAligned) {
            screen.captionRight(g, label, x, y, HEADER);
            screen.captionRight(g, value, x, y + 5, valueColor);
            g.fill(x - BAR_W, y + 11, x, y + 12, TRACK);
            g.fill(x - fill, y + 11, x, y + 12, color);
        } else {
            screen.caption(g, label, x, y, HEADER);
            screen.caption(g, value, x, y + 5, valueColor);
            g.fill(x, y + 11, x + BAR_W, y + 12, TRACK);
            g.fill(x, y + 11, x + fill, y + 12, color);
        }
        if (limb.healProgress() >= 0) {
            // How far through its heals the active bandage/plaster is, under the health bar.
            int p = Math.round(BAR_W * limb.healProgress());
            if (rightAligned) g.fill(x - p, y + 13, x, y + 14, ACCENT);
            else g.fill(x, y + 13, x + p, y + 14, ACCENT);
        }
    }

    void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (holdLimb != null) return;
        String id = limbAt(mouseX, mouseY);
        if (id == null) return;
        Limb limb = FirstAidCompat.limbs(Minecraft.getInstance().player)
                .map(l -> find(l, id)).orElse(null);
        if (limb == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(longName(id)));
        lines.add(Component.literal("§7" + number(limb.current()) + " / " + limb.max() + " HP"));
        if (limb.healProgress() >= 0) {
            lines.add(Component.literal("§aHealing - " + Math.round(limb.healProgress() * 100) + "%"));
        }
        ItemStack carried = screen.menu().getCarried();
        if (FirstAidCompat.isHealingItem(carried)) {
            lines.add(Component.literal(limb.current() >= limb.max() ? "§8Not hurt"
                    : "§8Hold to apply " + carried.getHoverName().getString()));
        } else if (limb.current() < limb.max()) {
            lines.add(Component.literal("§8Hold a bandage or plaster here to treat it"));
        }
        g.renderComponentTooltip(screen.font(), lines, mouseX, mouseY);
    }

    // ---- input ------------------------------------------------------------------------------

    /** A press on a limb while carrying a healing item starts the hold. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        String id = limbAt(mouseX, mouseY);
        ItemStack carried = screen.menu().getCarried();
        if (id == null || !FirstAidCompat.isHealingItem(carried)) return false;
        Limb limb = FirstAidCompat.limbs(Minecraft.getInstance().player).map(l -> find(l, id)).orElse(null);
        if (limb == null || limb.current() >= limb.max()) return true; // nothing to treat; eat the click
        holdLimb = id;
        holdStart = Util.getMillis();
        holdMs = Math.max(0, FirstAidCompat.applyTimeMs(carried));
        return true;
    }

    boolean mouseReleased(int button) {
        if (button != 0 || holdLimb == null) return false;
        holdLimb = null;
        return true;
    }

    /** Advances a hold; sends the packet when it completes. Returns 0..1 through the hold. */
    private float updateHold(String hovered, ItemStack carried) {
        if (holdLimb == null) return 0f;
        if (!holdLimb.equals(hovered) || !FirstAidCompat.isHealingItem(carried)) {
            holdLimb = null;   // slid off the limb, or the item went
            return 0f;
        }
        long elapsed = Util.getMillis() - holdStart;
        if (elapsed >= holdMs) {
            NetworkHandler.CHANNEL.sendToServer(new ApplyHealingPacket(holdLimb));
            UiSounds.inventoryMove();
            holdLimb = null;
            return 1f;
        }
        return holdMs <= 0 ? 1f : elapsed / (float) holdMs;
    }

    // ---- geometry and names -----------------------------------------------------------------

    /** The limb under the cursor - on the diagram or on its row - or null. */
    private String limbAt(double mouseX, double mouseY) {
        for (var e : SHAPES.entrySet()) {
            int[] r = rect(e.getKey());
            if (mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3]) return e.getKey();
        }
        for (int i = 0; i < LEFT_ROWS.length; i++) {
            int y = screen.top() + ROW_Y + i * ROW_SPACING - 1;
            if (mouseY < y || mouseY >= y + ROW_SPACING - 2) continue;
            int lx = screen.left() + ROW_LEFT_X, rx = screen.left() + ROW_RIGHT_X;
            if (mouseX >= lx && mouseX < lx + ROW_W) return LEFT_ROWS[i];
            if (mouseX >= rx - ROW_W && mouseX < rx) return RIGHT_ROWS[i];
        }
        return null;
    }

    /** Screen rectangle {x0, y0, x1, y1} of a limb on the diagram. */
    private int[] rect(String id) {
        int[] s = SHAPES.get(id);
        if (s == null) return null;
        float ox = screen.left() + ORIGIN_X, oy = screen.top() + ORIGIN_Y;
        return new int[]{Math.round(ox + s[0] * K), Math.round(oy + s[1] * K),
                Math.round(ox + (s[0] + s[2]) * K), Math.round(oy + (s[1] + s[3]) * K)};
    }

    private static Limb find(List<Limb> limbs, String id) {
        for (Limb l : limbs) if (l.id().equals(id)) return l;
        return null;
    }

    private static int color(Limb limb) {
        float f = limb.health01();
        if (limb.current() <= 0) return COLOR_GONE;
        if (f < 0.35f) return COLOR_BAD;
        if (f < 0.7f) return COLOR_HURT;
        return COLOR_GOOD;
    }

    private static String number(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String shortName(String id) {
        return id.replace("LEFT_", "L ").replace("RIGHT_", "R ");
    }

    private static String longName(String id) {
        String s = id.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
