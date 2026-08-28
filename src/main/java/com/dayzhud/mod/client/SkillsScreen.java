package com.dayzhud.mod.client;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.NetworkHandler;
import com.dayzhud.mod.inventory.StyledTheme;
import com.dayzhud.mod.skill.ClientSkillState;
import com.dayzhud.mod.skill.Skill;
import com.dayzhud.mod.skill.SpendSkillPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * The SKILLS screen, opened by the button beside the 3x3 crafting button.
 *
 * A plain Screen, not a container screen: there are no item slots here, so there's nothing
 * to sync and no menu to register. Everything drawn comes from {@link ClientSkillState},
 * which the server refreshes several times a second, so the moment a purchase goes through
 * the row updates itself with no client-side prediction to get out of step.
 *
 * Buttons are hand-drawn and handled in mouseClicked rather than being Button widgets, to
 * match how TarkovInventoryScreen draws its crafting button - one visual language, and the
 * rows stay a single block of layout maths instead of widget bookkeeping.
 */
@OnlyIn(Dist.CLIENT)
public class SkillsScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int HEADER_H = 46;
    private static final int ROW_H = 44;
    private static final int PAD = 14;

    private static final int PIP_W = 6;
    private static final int PIP_GAP = 2;
    private static final int PIP_H = 5;

    private static final int BUY_SIZE = 16;

    /**
     * The row's highlighted zone, relative to rowY. EVERY element in a row is positioned from
     * the centre of this box rather than from the row's top edge - which is what the first
     * version did, with each offset eyeballed separately, so the icon, the text block, the
     * cost and the + button all ended up centred on four slightly different lines.
     */
    private static final int ZONE_INSET = 2;              // zone starts this far above rowY
    private static final int ZONE_H = ROW_H - 6;

    /**
     * Icon column. The textures are 64x64 but are blitted with the texture dimensions given as
     * the DRAWN size - that's this codebase's convention for "scale the whole image down to
     * this box" (see DayzHudOverlay), and it's what keeps them crisp at any GUI scale.
     */
    private static final int ICON_SIZE = 26;

    /**
     * Gap on BOTH sides of the icon. Equal gutters are the point: the icon then sits centred
     * in a column of its own between the zone's left edge and the text, rather than being
     * tucked against one side of it. Widen or narrow this single number to move the icon and
     * the text together and keep that centring intact.
     */
    private static final int ICON_GUTTER = 16;

    /** Indexed by Skill.ordinal(), so the enum order and this array must stay in step. */
    private static final ResourceLocation[] ICONS = {
            icon("skill_vitality"), icon("skill_endurance"), icon("skill_metabolism"),
            icon("skill_toughness"), icon("skill_acclimation")
    };

    private static ResourceLocation icon(String name) {
        return new ResourceLocation(DayzHudMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    private int leftPos;
    private int topPos;
    private int panelH;

    public SkillsScreen() {
        super(Component.literal("Skills"));
    }

    @Override
    protected void init() {
        panelH = HEADER_H + Skill.values().length * ROW_H + PAD;
        leftPos = (width - PANEL_W) / 2;
        topPos = Math.max(10, (height - panelH) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int rowY(int index) {
        return topPos + HEADER_H + index * ROW_H;
    }

    private int zoneLeft() {
        return leftPos + PAD - 4;
    }

    private int zoneRight() {
        return leftPos + PANEL_W - PAD + 4;
    }

    /** Left edge of the icon - one gutter in from the zone. */
    private int iconX() {
        return zoneLeft() + ICON_GUTTER;
    }

    /** Left edge of the text block - one gutter past the icon, so both gutters match. */
    private int textX() {
        return iconX() + ICON_SIZE + ICON_GUTTER;
    }

    private int zoneTop(int index) {
        return rowY(index) - ZONE_INSET;
    }

    /** The single line everything in a row is centred on. */
    private int zoneCenterY(int index) {
        return zoneTop(index) + ZONE_H / 2;
    }

    /**
     * Top edge to pass to drawString so a single line of text sits centred on {@code centerY}.
     * Minecraft draws text downward from the given y, so this backs off half a line.
     */
    private int centeredTextY(int centerY) {
        return centerY - font.lineHeight / 2;
    }

    private int buyX() {
        return leftPos + PANEL_W - PAD - BUY_SIZE;
    }

    private int buyY(int index) {
        return zoneCenterY(index) - BUY_SIZE / 2;
    }

    /** XP levels the player has to spend right now. */
    private int availableLevels() {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        return player == null ? 0 : player.experienceLevel;
    }

    private boolean canAfford(Skill skill) {
        int level = ClientSkillState.level(skill);
        if (level >= skill.maxLevel()) return false;
        return availableLevels() >= skill.costFor(level + 1);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        StyledTheme.panel(graphics, leftPos, topPos, PANEL_W, panelH);
        StyledTheme.header(graphics, font, "SKILLS", leftPos + PAD, topPos + 12, 40);

        // XP is the currency, so it's shown where a shop shows your balance: top right,
        // and coloured by whether you can currently afford anything at all.
        String balance = availableLevels() + " XP LEVELS";
        int balanceX = leftPos + PANEL_W - PAD - font.width(balance);
        graphics.drawString(font, balance, balanceX, topPos + 12,
                availableLevels() > 0 ? StyledTheme.ACCENT : StyledTheme.LABEL_DIM, false);

        Skill[] skills = Skill.values();
        for (int i = 0; i < skills.length; i++) {
            renderRow(graphics, skills[i], i, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        renderTooltips(graphics, mouseX, mouseY);
    }

    /** Name + effect + pips stacked as one block: line heights and the gaps between them. */
    private static final int TEXT_LINE_H = 8;
    private static final int TEXT_GAP = 4;
    private static final int PIP_GAP_ABOVE = 5;
    private static final int TEXT_BLOCK_H =
            TEXT_LINE_H + TEXT_GAP + TEXT_LINE_H + PIP_GAP_ABOVE + PIP_H;

    private void renderRow(GuiGraphics graphics, Skill skill, int index, int mouseX, int mouseY) {
        int level = ClientSkillState.level(skill);
        boolean maxed = level >= skill.maxLevel();

        int top = zoneTop(index);
        int centerY = zoneCenterY(index);

        StyledTheme.zone(graphics, zoneLeft(), top, zoneRight(), top + ZONE_H);

        renderIcon(graphics, skill, centerY);

        // The three stacked lines are centred as ONE block, so the row reads as a unit rather
        // than as text that happens to sit near an icon.
        int textX = textX();
        int blockTop = centerY - TEXT_BLOCK_H / 2;

        graphics.drawString(font, skill.displayName().toUpperCase(Locale.ROOT),
                textX, blockTop, StyledTheme.TEXT_COLOR, false);

        // Current effect, so the number you're buying is always in front of you.
        String effect = level > 0 ? skill.describe(level) : "no bonus yet";
        graphics.drawString(font, effect, textX, blockTop + TEXT_LINE_H + TEXT_GAP,
                level > 0 ? StyledTheme.ACCENT : StyledTheme.LABEL_DIM, false);

        renderPips(graphics, skill, level, textX,
                blockTop + TEXT_BLOCK_H - PIP_H);

        if (maxed) {
            String maxLabel = "MAX";
            // Centred on the same column the + button occupies, so a maxed row and a buyable
            // row have their right-hand element in exactly the same place.
            graphics.drawString(font, maxLabel,
                    buyX() + (BUY_SIZE - font.width(maxLabel)) / 2, centeredTextY(centerY),
                    StyledTheme.LABEL_DIM, false);
            return;
        }

        int cost = skill.costFor(level + 1);
        boolean affordable = canAfford(skill);
        String costLabel = cost + " XP";
        graphics.drawString(font, costLabel,
                buyX() - 6 - font.width(costLabel), centeredTextY(centerY),
                affordable ? StyledTheme.TEXT_COLOR : StyledTheme.LABEL_DIM, false);

        renderBuyButton(graphics, index, affordable, mouseX, mouseY);
    }

    /**
     * The skill's glyph, dimmed until the first level is bought so an untouched row reads as
     * inactive at a glance rather than needing the text to be read.
     */
    private void renderIcon(GuiGraphics graphics, Skill skill, int centerY) {
        ResourceLocation texture = ICONS[skill.ordinal()];
        int x = iconX();
        int y = centerY - ICON_SIZE / 2;

        boolean owned = ClientSkillState.level(skill) > 0;
        float shade = owned ? 1f : 0.45f;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(shade, shade, shade, 1f);
        graphics.blit(texture, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        // Reset, or every later element on this screen inherits the tint.
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Ten pips per skill - the cap is visible at a glance, not something you discover. */
    private void renderPips(GuiGraphics graphics, Skill skill, int level, int x, int y) {
        for (int i = 0; i < skill.maxLevel(); i++) {
            int px = x + i * (PIP_W + PIP_GAP);
            graphics.fill(px, y, px + PIP_W, y + PIP_H,
                    i < level ? StyledTheme.ACCENT : StyledTheme.SLOT_BG);
        }
    }

    private void renderBuyButton(GuiGraphics graphics, int index, boolean affordable,
                                 int mouseX, int mouseY) {
        int bx = buyX();
        int by = buyY(index);
        boolean hovered = isOverBuy(index, mouseX, mouseY);

        graphics.fill(bx, by, bx + BUY_SIZE, by + BUY_SIZE,
                hovered && affordable ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        graphics.renderOutline(bx, by, BUY_SIZE, BUY_SIZE,
                affordable ? (hovered ? StyledTheme.ACCENT : StyledTheme.SLOT_BORDER)
                           : StyledTheme.HEADER_ACCENT);

        // A plus sign, drawn as two bars so it stays crisp at any GUI scale.
        int colour = affordable ? StyledTheme.TEXT_COLOR : StyledTheme.HEADER_ACCENT;
        graphics.fill(bx + 4, by + 7, bx + 12, by + 9, colour);
        graphics.fill(bx + 7, by + 4, bx + 9, by + 12, colour);
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        Skill[] skills = Skill.values();
        for (int i = 0; i < skills.length; i++) {
            int top = zoneTop(i);
            boolean overRow = mouseX >= leftPos + PAD && mouseX <= leftPos + PANEL_W - PAD
                    && mouseY >= top && mouseY <= top + ZONE_H;
            if (!overRow) continue;

            Skill skill = skills[i];
            int level = ClientSkillState.level(skill);
            String line = skill.description();
            if (level < skill.maxLevel()) {
                line += "  (next: " + skill.describe(level + 1) + ")";
            }
            graphics.renderTooltip(font, Component.literal(line), mouseX, mouseY);
            return;
        }
    }

    private boolean isOverBuy(int index, double mouseX, double mouseY) {
        int bx = buyX();
        int by = buyY(index);
        return mouseX >= bx && mouseX <= bx + BUY_SIZE
                && mouseY >= by && mouseY <= by + BUY_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Skill[] skills = Skill.values();
            for (int i = 0; i < skills.length; i++) {
                if (!isOverBuy(i, mouseX, mouseY)) continue;
                if (!canAfford(skills[i])) return true;   // swallow the click, no feedback loop

                // Fire and forget: the server validates the purchase and the resulting
                // state sync is what updates this screen. Nothing is predicted locally, so
                // a rejected purchase simply leaves the row as it was.
                NetworkHandler.CHANNEL.sendToServer(new SpendSkillPacket(skills[i]));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
