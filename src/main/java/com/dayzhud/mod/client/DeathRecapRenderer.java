package com.dayzhud.mod.client;

import com.dayzhud.mod.inventory.StyledTheme;
import com.dayzhud.mod.recap.ClientDeathRecap;
import com.dayzhud.mod.recap.DeathRecapPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Death recap panel (2.19.0), drawn over vanilla's death screen rather than replacing it, so
 * its buttons (and anything other mods add to it) keep working untouched.
 *
 * Placement: in the empty column left of the buttons when the screen is wide enough, else
 * centred under the buttons, clipped to the screen if it's very short.
 */
@Mod.EventBusSubscriber(modid = "dayzhud", value = Dist.CLIENT)
public final class DeathRecapRenderer {

    private static final int PANEL_WIDTH = 200;
    private static final int PAD = 6;
    private static final int LINE = 11;
    private static final int VALUE_X = 60;
    /** Vanilla's death screen buttons are 200 wide and centred. */
    private static final int BUTTONS_WIDTH = 200;

    private interface RowDrawer {
        void draw(GuiGraphics g, Font font, int x, int y, int width);
    }

    private record Row(int height, RowDrawer drawer) {}

    private DeathRecapRenderer() {}

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof DeathScreen screen)) return;
        DeathRecapPacket recap = ClientDeathRecap.get();
        if (recap == null) return;
        Font font = Minecraft.getInstance().font;
        List<Row> rows = rows(recap);
        int height = PAD * 2;
        for (Row r : rows) height += r.height;

        int w = screen.width, h = screen.height;
        int sideRoom = (w - BUTTONS_WIDTH) / 2 - 16;
        int x, y;
        if (sideRoom >= PANEL_WIDTH) {
            x = Math.max(8, (sideRoom - PANEL_WIDTH) / 2 + 8);
            y = Math.max(8, (h - height) / 2);
        } else {
            // Under the buttons (the lower one ends at height / 4 + 116).
            x = (w - PANEL_WIDTH) / 2;
            y = h / 4 + 124;
        }
        int bottom = Math.min(y + height, h - 4);
        if (bottom - y < LINE * 3) return; // no room for anything useful

        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200); // above the screen's own widgets
        g.fill(x, y, x + PANEL_WIDTH, bottom, StyledTheme.SECTION_BG);
        g.renderOutline(x, y, PANEL_WIDTH, bottom - y, StyledTheme.PANEL_BORDER);
        g.enableScissor(x, y, x + PANEL_WIDTH, bottom);
        int cy = y + PAD;
        for (Row r : rows) {
            r.drawer.draw(g, font, x + PAD, cy, PANEL_WIDTH - PAD * 2);
            cy += r.height;
        }
        g.disableScissor();
        g.pose().popPose();
    }

    private static List<Row> rows(DeathRecapPacket r) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(LINE + 3, (g, f, x, y, w) -> {
            g.drawString(f, Component.translatable("recap.dayzhud.title"), x, y, StyledTheme.ACCENT, false);
            g.fill(x, y + LINE, x + w, y + LINE + 1, StyledTheme.HEADER_ACCENT);
        }));

        if (r.killer != null) {
            rows.add(pair("recap.dayzhud.killed_by", r.killer, r.killerIsPlayer ? StyledTheme.BAD : StyledTheme.WARN));
        }
        if (r.cause != null) rows.add(pair("recap.dayzhud.cause", r.cause, StyledTheme.TEXT_COLOR));
        if (r.weapon != null) rows.add(pair("recap.dayzhud.weapon", r.weapon, StyledTheme.TEXT_COLOR));
        if (r.distance >= 0) {
            var dist = Component.literal(Math.round(r.distance) + "m");
            if (r.headshot) {
                dist.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("recap.dayzhud.headshot").withStyle(ChatFormatting.RED));
            }
            rows.add(pair("recap.dayzhud.distance", dist, StyledTheme.TEXT_COLOR));
        }
        if (r.lightWounds + r.heavyWounds > 0) {
            rows.add(pair("recap.dayzhud.wounds",
                    Component.translatable("recap.dayzhud.wounds.value", r.lightWounds, r.heavyWounds),
                    StyledTheme.BAD));
        }

        // Damage taken.
        rows.add(new Row(LINE + 5, (g, f, x, y, w) -> {
            int top = y + 3;
            g.drawString(f, Component.translatable("recap.dayzhud.damage", r.windowSeconds), x, top,
                    StyledTheme.HEADER_COLOR, false);
            String total = formatDamage(r.totalDamage);
            g.drawString(f, total, x + w - f.width(total), top, StyledTheme.HEADER_COLOR, false);
        }));
        if (r.lines.isEmpty()) {
            rows.add(new Row(LINE, (g, f, x, y, w) ->
                    g.drawString(f, Component.translatable("recap.dayzhud.damage.none"), x, y, StyledTheme.LABEL_DIM, false)));
        }
        for (DeathRecapPacket.Line line : r.lines) {
            rows.add(new Row(LINE, (g, f, x, y, w) -> {
                String right = (line.hits() > 1 ? "×" + line.hits() + "  " : "") + formatDamage(line.damage());
                g.drawString(f, right, x + w - f.width(right), y, StyledTheme.TEXT_COLOR, false);
                var left = line.who().copy();
                if (line.weapon() != null) {
                    left.append(Component.literal(" · ").append(line.weapon()).withStyle(ChatFormatting.GRAY));
                }
                drawTrimmed(g, f, left, x, y, w - f.width(right) - 6, StyledTheme.TEXT_COLOR);
            }));
        }

        // This life.
        rows.add(new Row(5, (g, f, x, y, w) -> g.fill(x, y + 2, x + w, y + 3, StyledTheme.HEADER_ACCENT)));
        rows.add(pair("recap.dayzhud.survived", Component.literal(formatDuration(r.survivedSeconds)), StyledTheme.TEXT_COLOR));
        rows.add(pair("recap.dayzhud.kills",
                Component.translatable("recap.dayzhud.kills.value", r.playerKills, r.mobKills), StyledTheme.TEXT_COLOR));
        return rows;
    }

    /** A "Label   value" row; the value is cut to fit. */
    private static Row pair(String labelKey, Component value, int valueColor) {
        return new Row(LINE, (g, f, x, y, w) -> {
            g.drawString(f, Component.translatable(labelKey), x, y, StyledTheme.LABEL_DIM, false);
            drawTrimmed(g, f, value, x + VALUE_X, y, w - VALUE_X, valueColor);
        });
    }

    private static void drawTrimmed(GuiGraphics g, Font f, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        List<FormattedCharSequence> split = f.split(text, maxWidth);
        if (!split.isEmpty()) g.drawString(f, split.get(0), x, y, color, false);
    }

    private static String formatDamage(float amount) {
        return String.format(Locale.ROOT, "%.1f", amount);
    }

    /** 35s, 4m 05s, 1h 12m, 3d 4h. */
    static String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "s";
        int minutes = seconds / 60;
        if (minutes < 60) return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds % 60);
        int hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        return (hours / 24) + "d " + (hours % 24) + "h";
    }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        ClientDeathRecap.clear();
    }
}
