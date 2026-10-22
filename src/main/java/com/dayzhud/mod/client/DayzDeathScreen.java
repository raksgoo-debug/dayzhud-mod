package com.dayzhud.mod.client;

import com.dayzhud.mod.inventory.StyledTheme;
import com.dayzhud.mod.recap.ClientDeathRecap;
import com.dayzhud.mod.recap.DeathRecapPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * The death screen (2.19.0), replacing vanilla's in the mod's own style:
 *
 * <pre>
 *                 YOU ARE DEAD
 *        Raksgoo was shot by Bandit_Mike
 *   ------------------------------------------
 *   | KILLED BY     |  | DETAILS              |
 *   |               |  | Weapon    AK-47      |
 *   |   (killer's   |  | Distance  87m HEAD.. |
 *   |  3D model in  |  | DAMAGE TAKEN         |
 *   |   their gear) |  | ...                  |
 *   |  Bandit_Mike  |  | THIS LIFE            |
 *   |    AK-47      |  | Survived  1h 12m     |
 *   ------------------------------------------
 *          [ RESPAWN ]   [ TITLE SCREEN ]
 * </pre>
 *
 * It IS a DeathScreen (a subclass), so every vanilla and modded check for "is the death
 * screen up" still holds - respawning closes it exactly as before. Vanilla's own widgets are
 * never created (init is replaced); the two buttons are hand-drawn like the rest of the mod's
 * screens, and do exactly what vanilla's do, including the one-second delay before they work.
 *
 * The killer is drawn from KillerModelCache: the real entity, captured the moment you died
 * and kept even after the client unloads it, or a faithful rebuild when it was never loaded.
 */
public class DayzDeathScreen extends DeathScreen {

    private static final int MAX_WIDTH = 460;
    private static final int CARD_WIDTH = 130;
    /** Below this content width there's no room for the killer card, so it's left out. */
    private static final int CARD_MIN_CONTENT = 300;
    private static final int GAP = 8;
    private static final int MAX_BODY_HEIGHT = 180;
    private static final int LINE = 11;
    private static final int VALUE_X = 58;
    private static final int BUTTON_W = 120;
    private static final int BUTTON_H = 20;
    /** Vanilla's delay before the buttons work, so a click meant for the fight doesn't respawn you. */
    private static final int BUTTON_DELAY_TICKS = 20;

    private static final int BACKDROP_TOP = 0xD8160606;
    private static final int BACKDROP_BOTTOM = 0xF0060303;

    private final boolean hardcore;
    private int ticks;
    private boolean respawning;

    public DayzDeathScreen(boolean hardcore) {
        super(null, hardcore);
        this.hardcore = hardcore;
    }

    /** No vanilla widgets - see the class comment. */
    @Override
    protected void init() {
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
    }

    private boolean buttonsActive() {
        return ticks >= BUTTON_DELAY_TICKS && !respawning;
    }

    // ---- layout -----------------------------------------------------------------------------

    private int contentWidth() {
        return Math.min(width - 24, MAX_WIDTH);
    }

    /**
     * Where everything goes: title, rule, the two boxes, then the buttons right under them,
     * with the whole block centred vertically. The boxes shrink first on a short screen.
     */
    private record Layout(float titleScale, int titleY, int ruleY, int bodyTop, int bodyBottom, int buttonsY) {}

    private Layout layout() {
        float titleScale = height >= 300 ? 3f : 2f;
        DeathRecapPacket r = ClientDeathRecap.get();
        boolean message = r != null && r.deathMessage != null;
        int titleH = Math.round(font.lineHeight * titleScale) + 4;
        int header = titleH + (message ? font.lineHeight + 4 : 0) + 1 + GAP;
        int below = 10 + BUTTON_H;
        int body = Math.max(0, Math.min(MAX_BODY_HEIGHT, height - 16 - header - below));
        int top = Math.max(8, (height - header - body - below) / 2);
        int ruleY = top + titleH + (message ? font.lineHeight + 4 : 0);
        int bodyTop = ruleY + 1 + GAP;
        int bodyBottom = bodyTop + body;
        return new Layout(titleScale, top, ruleY, bodyTop, bodyBottom, bodyBottom + 10);
    }

    private int buttonsY() {
        return layout().buttonsY();
    }

    private int respawnX() {
        return width / 2 - BUTTON_W - GAP / 2;
    }

    private int titleButtonX() {
        return width / 2 + GAP / 2;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- rendering --------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, BACKDROP_TOP, BACKDROP_BOTTOM);
        DeathRecapPacket r = ClientDeathRecap.get();
        Layout l = layout();

        // Title.
        float titleScale = l.titleScale();
        String title = I18n.get(hardcore ? "deathscreen.dayzhud.title.hardcore" : "deathscreen.dayzhud.title");
        int y = l.titleY();
        StyledTheme.scaled(g, font, title, (width - font.width(title) * titleScale) / 2f, y, titleScale, StyledTheme.BAD);
        y += Math.round(font.lineHeight * titleScale) + 4;

        // Vanilla's death message under it.
        if (r != null && r.deathMessage != null) {
            List<FormattedCharSequence> msg = font.split(r.deathMessage, width - 40);
            if (!msg.isEmpty()) {
                FormattedCharSequence line = msg.get(0);
                g.drawString(font, line, (width - font.width(line)) / 2, y, StyledTheme.HEADER_COLOR, false);
            }
        }

        int contentW = contentWidth();
        int left = (width - contentW) / 2;
        g.fill(left, l.ruleY(), left + contentW, l.ruleY() + 1, StyledTheme.HEADER_ACCENT);
        y = l.bodyTop();

        int bodyBottom = l.bodyBottom();
        if (bodyBottom - y > 40) {
            int detailsX = left, detailsW = contentW;
            if (contentW >= CARD_MIN_CONTENT) {
                drawKillerCard(g, r, left, y, CARD_WIDTH, bodyBottom, mouseX, mouseY);
                detailsX = left + CARD_WIDTH + GAP;
                detailsW = contentW - CARD_WIDTH - GAP;
            }
            drawDetails(g, r, detailsX, y, detailsW, bodyBottom);
        }

        drawButton(g, respawnX(), l.buttonsY(),
                I18n.get(hardcore ? "deathScreen.spectate" : "deathScreen.respawn"), true, mouseX, mouseY);
        drawButton(g, titleButtonX(), l.buttonsY(), I18n.get("deathScreen.titleScreen"), false, mouseX, mouseY);
    }

    /** Left: whoever killed you, in 3D, with their name and weapon - or how you died. */
    private void drawKillerCard(GuiGraphics g, @Nullable DeathRecapPacket r, int x, int y, int w, int bottom,
                                int mouseX, int mouseY) {
        StyledTheme.zone(g, x, y, x + w, bottom);
        boolean hasKiller = r != null && r.killer != null;
        String header = I18n.get(hasKiller ? "recap.dayzhud.killed_by" : "recap.dayzhud.cause_of_death");
        StyledTheme.strip(g, font, header.toUpperCase(Locale.ROOT), x + 1, y + 1, w - 2, null, 0);

        if (!hasKiller) {
            Component cause = r != null && r.cause != null ? r.cause : Component.translatable("recap.dayzhud.unknown");
            String text = cause.getString().toUpperCase(Locale.ROOT);
            float scale = font.width(text) * 1.5f <= w - 12 ? 1.5f : 1f;
            StyledTheme.scaled(g, font, text, x + (w - font.width(text) * scale) / 2f,
                    (y + bottom) / 2f - font.lineHeight * scale / 2f, scale, StyledTheme.WARN);
            return;
        }

        int cx = x + w / 2;
        int nameY = bottom - (r.weapon != null ? 24 : 14);
        centered(g, r.killer, cx, nameY, w - 8, r.killerIsPlayer ? StyledTheme.BAD : StyledTheme.WARN);
        if (r.weapon != null) centered(g, r.weapon, cx, nameY + LINE, w - 8, StyledTheme.LABEL_DIM);

        LivingEntity model = KillerModelCache.get(r);
        if (model == null) return;
        int areaTop = y + StyledTheme.STRIP_H + 6;
        int feet = nameY - 6;
        float tall = Math.max(0.6f, model.getBbHeight());
        int scale = (int) Math.min(70f, (feet - areaTop) / tall);
        if (scale < 8) return;
        // Turns to follow the mouse, like the inventory preview.
        float lookX = (float) Math.atan((cx - mouseX) / 40f);
        float lookY = (float) Math.atan((feet - scale * tall * 0.75f - mouseY) / 40f);
        // Drawn standing and unflinching even if they've since been hurt or killed (a teammate
        // avenging you would otherwise show them toppling over).
        int deathTime = model.deathTime, hurtTime = model.hurtTime;
        model.deathTime = 0;
        model.hurtTime = 0;
        g.enableScissor(x + 1, areaTop - 2, x + w - 1, feet + 2);
        try {
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, cx, feet, scale, lookX, lookY, model);
        } finally {
            g.disableScissor();
            model.deathTime = deathTime;
            model.hurtTime = hurtTime;
        }
    }

    /** Right: the numbers. Clipped to the box on short screens. */
    private void drawDetails(GuiGraphics g, @Nullable DeathRecapPacket r, int x, int y, int w, int bottom) {
        StyledTheme.zone(g, x, y, x + w, bottom);
        g.enableScissor(x + 1, y + 1, x + w - 1, bottom - 1);
        int tx = x + 6, tw = w - 12;
        int cy = y + 1;

        if (r == null) {
            StyledTheme.strip(g, font, upper("recap.dayzhud.details"), x + 1, cy, w - 2, null, 0);
            g.drawString(font, Component.translatable("recap.dayzhud.none"), tx, cy + StyledTheme.STRIP_H + 4,
                    StyledTheme.LABEL_DIM, false);
            g.disableScissor();
            return;
        }

        // Details.
        StyledTheme.strip(g, font, upper("recap.dayzhud.details"), x + 1, cy, w - 2, null, 0);
        cy += StyledTheme.STRIP_H + 4;
        int before = cy;
        if (r.killer != null && r.cause != null) {
            cy = pair(g, "recap.dayzhud.cause", r.cause, StyledTheme.TEXT_COLOR, tx, cy, tw);
        }
        if (r.weapon != null) cy = pair(g, "recap.dayzhud.weapon", r.weapon, StyledTheme.TEXT_COLOR, tx, cy, tw);
        if (r.distance >= 0) {
            var dist = Component.literal(Math.round(r.distance) + "m");
            if (r.headshot) {
                dist.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("recap.dayzhud.headshot").withStyle(ChatFormatting.RED));
            }
            cy = pair(g, "recap.dayzhud.distance", dist, StyledTheme.TEXT_COLOR, tx, cy, tw);
        }
        if (r.lightWounds + r.heavyWounds > 0) {
            cy = pair(g, "recap.dayzhud.wounds",
                    Component.translatable("recap.dayzhud.wounds.value", r.lightWounds, r.heavyWounds),
                    StyledTheme.BAD, tx, cy, tw);
        }
        if (cy == before && r.cause != null) {
            cy = pair(g, "recap.dayzhud.cause", r.cause, StyledTheme.TEXT_COLOR, tx, cy, tw);
        }
        cy += 3;

        // Damage taken.
        String damageHeader = I18n.get("recap.dayzhud.damage", r.windowSeconds).toUpperCase(Locale.ROOT);
        StyledTheme.strip(g, font, damageHeader, x + 1, cy, w - 2, formatDamage(r.totalDamage), StyledTheme.HEADER_COLOR);
        cy += StyledTheme.STRIP_H + 4;
        if (r.lines.isEmpty()) {
            g.drawString(font, Component.translatable("recap.dayzhud.damage.none"), tx, cy, StyledTheme.LABEL_DIM, false);
            cy += LINE;
        }
        for (DeathRecapPacket.Line line : r.lines) {
            String right = (line.hits() > 1 ? "×" + line.hits() + "  " : "") + formatDamage(line.damage());
            g.drawString(font, right, tx + tw - font.width(right), cy, StyledTheme.TEXT_COLOR, false);
            var who = line.who().copy();
            if (line.weapon() != null) {
                who.append(Component.literal(" · ").append(line.weapon()).withStyle(ChatFormatting.GRAY));
            }
            trimmed(g, who, tx, cy, tw - font.width(right) - 6, StyledTheme.TEXT_COLOR);
            cy += LINE;
        }
        cy += 3;

        // This life.
        StyledTheme.strip(g, font, upper("recap.dayzhud.this_life"), x + 1, cy, w - 2, null, 0);
        cy += StyledTheme.STRIP_H + 4;
        cy = pair(g, "recap.dayzhud.survived", Component.literal(formatDuration(r.survivedSeconds)),
                StyledTheme.TEXT_COLOR, tx, cy, tw);
        cy = pair(g, "recap.dayzhud.kills",
                Component.translatable("recap.dayzhud.kills.value", r.playerKills, r.mobKills),
                StyledTheme.TEXT_COLOR, tx, cy, tw);
        if (minecraft != null && minecraft.player != null) {
            pair(g, "recap.dayzhud.score", Component.literal(String.valueOf(minecraft.player.getScore())),
                    StyledTheme.WARN, tx, cy, tw);
        }
        g.disableScissor();
    }

    /** Hand-drawn button in the mod's style; the primary one uses the rust "active" look. */
    private void drawButton(GuiGraphics g, int x, int y, String label, boolean primary, int mouseX, int mouseY) {
        boolean active = buttonsActive();
        boolean hovered = active && over(mouseX, mouseY, x, y, BUTTON_W, BUTTON_H);
        if (primary && active) {
            g.fill(x, y, x + BUTTON_W, y + BUTTON_H, StyledTheme.ACTIVE_BG);
            g.renderOutline(x, y, BUTTON_W, BUTTON_H, hovered ? StyledTheme.ACCENT : StyledTheme.ACTIVE_LINE);
        } else {
            g.fill(x, y, x + BUTTON_W, y + BUTTON_H, hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
            g.renderOutline(x, y, BUTTON_W, BUTTON_H, hovered ? StyledTheme.ACCENT : StyledTheme.SLOT_BORDER);
        }
        String text = label.toUpperCase(Locale.ROOT);
        int color = !active ? StyledTheme.HEADER_ACCENT
                : primary ? StyledTheme.ACCENT : hovered ? StyledTheme.TEXT_COLOR : StyledTheme.HEADER_COLOR;
        g.drawString(font, text, x + (BUTTON_W - font.width(text)) / 2, y + (BUTTON_H - 8) / 2, color, false);
    }

    private int pair(GuiGraphics g, String labelKey, Component value, int color, int x, int y, int w) {
        g.drawString(font, Component.translatable(labelKey), x, y, StyledTheme.LABEL_DIM, false);
        trimmed(g, value, x + VALUE_X, y, w - VALUE_X, color);
        return y + LINE;
    }

    private void trimmed(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        List<FormattedCharSequence> split = font.split(text, maxWidth);
        if (!split.isEmpty()) g.drawString(font, split.get(0), x, y, color, false);
    }

    private void centered(GuiGraphics g, Component text, int cx, int y, int maxWidth, int color) {
        List<FormattedCharSequence> split = font.split(text, maxWidth);
        if (split.isEmpty()) return;
        FormattedCharSequence line = split.get(0);
        g.drawString(font, line, cx - font.width(line) / 2, y, color, false);
    }

    private static String upper(String key) {
        return I18n.get(key).toUpperCase(Locale.ROOT);
    }

    static String formatDamage(float amount) {
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

    // ---- input ------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && buttonsActive() && minecraft != null) {
            if (over(mouseX, mouseY, respawnX(), buttonsY(), BUTTON_W, BUTTON_H)) {
                playClick();
                respawning = true;
                if (minecraft.player != null) minecraft.player.respawn();
                return true;
            }
            if (over(mouseX, mouseY, titleButtonX(), buttonsY(), BUTTON_W, BUTTON_H)) {
                playClick();
                minecraft.getReportingContext().draftReportHandled(minecraft, this, this::handleExitToTitle, true);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    /** Vanilla's, which is private: straight out in hardcore, else ask first. */
    private void handleExitToTitle() {
        if (minecraft == null) return;
        if (hardcore) {
            exitToTitleScreen();
            return;
        }
        ConfirmScreen confirm = new ConfirmScreen(quit -> {
            if (quit) {
                exitToTitleScreen();
            } else {
                if (minecraft.player != null) minecraft.player.respawn();
                minecraft.setScreen(null);
            }
        }, Component.translatable("deathScreen.quit.confirm"), CommonComponents.EMPTY,
                Component.translatable("deathScreen.titleScreen"), Component.translatable("deathScreen.respawn"));
        minecraft.setScreen(confirm);
        confirm.setDelay(20);
    }

    private void exitToTitleScreen() {
        if (minecraft == null) return;
        if (minecraft.level != null) minecraft.level.disconnect();
        minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        minecraft.setScreen(new TitleScreen());
    }
}
