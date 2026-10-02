package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.search.ClientSearchState;
import com.dayzhud.mod.client.UiSounds;
import com.dayzhud.mod.compat.FirstAidCompat;
import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import com.dayzhud.mod.inventory.grid.Footprint;
import com.dayzhud.mod.inventory.grid.ItemGrid;
import com.dayzhud.mod.inventory.grid.RotateCarriedPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Extraction-shooter style inventory screen (Tarkov / Arena Breakout): a side-on player
 * paperdoll flanked by equipment slots aligned to the matching body parts, a dedicated
 * grid for the (often numerous) Curios slots other mods add, a labelled inventory grid, a
 * weapon-mirror row, and a stat strip reusing the same gauge icons as the in-world HUD.
 *
 * Curios slot names are shown as HOVER TOOLTIPS rather than inline text - with a dozen-plus
 * slots installed, inline labels overlap into unreadable mush.
 *
 * 2.16.0: laid out as cards with headers, vitals in the title bar, and a HEALTH tab over the
 * equipment card when First Aid is installed (HealthTab).
 */
public class TarkovInventoryScreen extends AbstractContainerScreen<TarkovInventoryMenu> {

    /** Cover drawn over a slot that has not been searched yet. */
    private static final int SEARCH_COVER_BG = 0xE0161616;
    private static final int SEARCH_COVER_LINE = 0x40707070;


    private static final int PANEL_BG = 0xF0121212;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int SECTION_BG = 0x40000000;
    private static final int SLOT_BG = 0xFF232323;
    private static final int SLOT_BORDER = 0xFF484848;
    private static final int HEADER_COLOR = 0xFF9A9A9A;
    private static final int HEADER_ACCENT = 0xFF4A4A4A;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int LABEL_DIM = 0xFF6A6A6A;
    // 2.16.0 cards.
    private static final int CARD_BG = 0xFF181818;
    private static final int CARD_BORDER = 0xFF2C2C2C;
    private static final int ACCENT = StyledTheme.ACCENT;
    private static final int BAR_TRACK = 0xFF2A2A2A;
    private static final int COLOR_LOW = 0xFFE2A62E, COLOR_CRITICAL = 0xFFE23A2E, COLOR_WARN = 0xFFE2D22E;

    /** Ghost icon drawn in an empty loadout slot, so an unrestricted-looking box doesn't
     *  read as "any item goes here" - see drawWeaponSlotDecor. */
    private static final int WEAPON_GHOST_COLOR = 0x40AFAFAF;
    private int lastMouseX, lastMouseY;

    /**
     * GUI depths for the flat-gun layer. Vanilla draws a slot's item at z 100 (renderSlot's
     * own push) + 150 (renderItem's) = ~250, so anything meant to cover it - the panel that
     * hides TACZ's little diagonal icon - must sit above that. 2.12.1 put the panel at 190,
     * UNDER the icon, which is why the icon still showed. Its count and durability bar go
     * higher still - renderItemDecorations adds 200, so ~300 - and with the panel at 280 a
     * stacked multi-cell item (magazines) showed vanilla's count and bar in its anchor cell as
     * well as ours at the bottom of the box: two numbers. The panel sits above that since
     * 2.13.10; our own decorations are at FLAT_DECOR_Z + 200 = 350, over the model. Carried
     * item (~382) and tooltips (400) stay above all of this.
     */
    private static final float FLAT_PANEL_Z = 310, FLAT_GUN_Z = 330, FLAT_HOVER_Z = 345,
            FLAT_DECOR_Z = 150, FLAT_PREVIEW_Z = 360;
    private static final int FLAT_HOVER_COLOR = 0x30FFFFFF;

    /**
     * Flat, top-down weapon icons - user-supplied art, sliced from one composite reference
     * image and alpha-extracted from luminance (same technique as the skill icons: the
     * source was a faint outline on a near-black background, so alpha comes from how bright
     * each pixel is, and colour comes entirely from the tint applied at draw time - see
     * drawFlatWeaponIcon). One icon per loadout CATEGORY, not per specific gun: PRIMARY and
     * SECONDARY both use the rifle icon regardless of which non-pistol type is actually
     * equipped (smg, shotgun, sniper, mg, rpg all show the same silhouette), since the
     * supplied art only covers one shape per category. Native pixel sizes are the resized
     * files' own dimensions, needed by drawFlatWeaponIcon's blit call.
     */
    private static final ResourceLocation WEAPON_ICON_RIFLE = rl("weapon_flat_rifle");
    private static final ResourceLocation WEAPON_ICON_PISTOL = rl("weapon_flat_pistol");
    private static final ResourceLocation WEAPON_ICON_KNIFE = rl("weapon_flat_knife");
    private static final int WEAPON_ICON_RIFLE_W = 128, WEAPON_ICON_RIFLE_H = 39;
    private static final int WEAPON_ICON_PISTOL_W = 128, WEAPON_ICON_PISTOL_H = 67;
    private static final int WEAPON_ICON_KNIFE_W = 128, WEAPON_ICON_KNIFE_H = 37;

    private static final ResourceLocation CORPSE_FIGURE = rl("corpse_figure");

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(DayzHudMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TarkovInventoryMenu.PLAYER_WIDTH;
        // 2.12.5: 376 -> 348. At GUI scale 3 on a 1080p screen there are only 360 units, so
        // 376 centred to topPos -8 and cut off the top of the window. 2.16.1: 352, still
        // inside 360.
        this.imageHeight = 352;
        if (menu.isCorpse()) {
            this.imageWidth = TarkovInventoryMenu.CORPSE_INV_X + 9 * 18 + 12;
        } else if (menu.hasContainer()) {
            // Grow rightwards to fit the container grid; the loadout side keeps its layout.
            this.imageWidth = TarkovInventoryMenu.CONTAINER_X
                    + TarkovInventoryMenu.CONTAINER_COLS * 18 + 12;
        }
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    /** Widest the window ever gets - used to anchor the layout so it never jumps. */
    private static final int FULL_LAYOUT_WIDTH =
            TarkovInventoryMenu.CONTAINER_X + TarkovInventoryMenu.CONTAINER_COLS * 18 + 12;
    private static final int CORPSE_LAYOUT_WIDTH =
            TarkovInventoryMenu.CORPSE_INV_X + 9 * 18 + 12;

    /**
     * init() runs again on every window resize, so the open sound is guarded - without this
     * you'd hear it each time the window changed size while the screen was up.
     */
    private boolean openSoundPlayed = false;

    @Override
    protected void init() {
        super.init();
        // Centre on the FULL layout width (inventory + container) even when no container is
        // open, so the loadout panel sits in exactly the same spot either way and the UI
        // doesn't jump sideways as you open and close chests.
        int anchorWidth = menu.isCorpse() ? CORPSE_LAYOUT_WIDTH : FULL_LAYOUT_WIDTH;
        this.leftPos = (this.width - anchorWidth) / 2;
        // Never let the top go off-screen on a short GUI: if the window can't fit, its bottom
        // edge is what gets clipped, not the headers.
        this.topPos = Math.max(2, this.topPos);

        if (!openSoundPlayed) {
            openSoundPlayed = true;
            UiSounds.inventoryOpen();
        }
    }


    @Override
    public void removed() {
        ClientSearchState.clear();
        super.removed();
        UiSounds.inventoryClose();
    }

    /**
     * Handling clicks by SLOT rather than by mouse means every route into moving an item is
     * covered at once - left/right click, shift-click, number-key swaps, and dropping with Q
     * or outside the window (which arrives here with a null slot).
     *
     * The state is sampled BEFORE super runs, because super is what empties the cursor or the
     * slot; checking afterwards would miss exactly the interactions we want to hear.
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        boolean movedSomething = !menu.getCarried().isEmpty() || (slot != null && slot.hasItem());
        super.slotClicked(slot, slotId, mouseButton, type);
        if (movedSomething) {
            UiSounds.inventoryMove();
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        graphics.renderOutline(x, y, imageWidth, imageHeight, PANEL_BORDER);

        // Title bar (2.16.0): name, vitals, buttons, then a rule the width of the player side.
        graphics.pose().pushPose();
        graphics.pose().translate(x + 12, y + 10, 0);   // 2.16.2: normal size, was 1.25x
        graphics.drawString(font, "LOADOUT", 0, 0, TEXT_COLOR, false);
        graphics.pose().popPose();
        graphics.fill(x + 8, y + 26, x + TarkovInventoryMenu.PLAYER_WIDTH - 8, y + 27, CARD_BORDER);

        // Cards. Their positions match TarkovInventoryMenu's slot constants.
        drawCard(graphics, 8, 30, 188, 150);     // equipment / health
        drawCard(graphics, 8, 154, 188, 276);    // weapons + hotbar 5-9
        drawCard(graphics, 8, 280, 188, 346);    // gear
        drawCard(graphics, 196, 30, 376, 108);   // pockets
        drawCard(graphics, 196, 112, 376, backpackCardBottom()); // backpack, sized to the bag
        drawCard(graphics, 196, 268, 376, 346);  // secure + conditions
        drawNoBagNote(graphics);

        if (menu.isCorpse()) {
            drawCorpseZones(graphics, x, y);
            drawCorpseFigure(graphics);
        } else if (menu.hasContainer()) {
            int cx = TarkovInventoryMenu.CONTAINER_X;
            int cy = TarkovInventoryMenu.CONTAINER_Y;
            int cw = TarkovInventoryMenu.CONTAINER_COLS * 18;
            int ch = menu.containerRows * 18;
            drawCard(graphics, cx - 9, 30, cx + cw + 9, cy + ch + 5);
        }

        for (var slot : menu.slots) {
            if (!slot.isActive()) continue; // inactive backpack slots shouldn't leave ghost squares
            if (slot instanceof TarkovInventoryMenu.WeaponSlot) continue; // drawn bigger, separately
            drawSlotBackdrop(graphics, x + slot.x, y + slot.y);
        }
    }

    private void drawSlotBackdrop(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
        graphics.renderOutline(x - 1, y - 1, 18, 18, SLOT_BORDER);
    }

    /** A section card, in panel coordinates. */
    private void drawCard(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(leftPos + x0, topPos + y0, leftPos + x1, topPos + y1, CARD_BG);
        graphics.renderOutline(leftPos + x0, topPos + y0, x1 - x0, y1 - y0, CARD_BORDER);
    }

    /**
     * The backpack card is as tall as the worn bag (2.16.1; it used to always fit the biggest
     * bag, with the missing rows drawn faint - which read as clutter). Panel y of its bottom.
     */
    private int backpackCardBottom() {
        int bagSlots = menu.getActiveBackpackSlots();
        if (bagSlots == 0) return TarkovInventoryMenu.BACKPACK_Y + 14;
        int rows = Math.min(TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS, (bagSlots + 8) / 9);
        return TarkovInventoryMenu.BACKPACK_Y + rows * 18 + 5;
    }

    private void drawNoBagNote(GuiGraphics graphics) {
        if (menu.getActiveBackpackSlots() > 0) return;
        caption(graphics, "NO BAG WORN", leftPos + TarkovInventoryMenu.BACKPACK_X,
                topPos + TarkovInventoryMenu.BACKPACK_Y + 2, LABEL_DIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        syncEquipmentTab();
        renderBackground(graphics);
        // Vanilla draws the carried item as its normal inventory icon - for a TACZ gun, the
        // small diagonal sprite - and that draw is private, so it can't be replaced. Instead
        // the carried stack is blanked for the length of vanilla's pass only (restored in
        // finally, before anything reads it again - tooltips included) and drawn below at its
        // footprint size: flat if it has a model render, otherwise its own icon scaled up the
        // way the grid draws it (CAPS armour used to stay a small 16x16 icon while carried).
        ItemStack carried = menu.getCarried();
        boolean bigCarried = !carried.isEmpty()
                && (ItemGrid.isMultiCell(carried) || FlatModelRenderer.canRender(carried));
        if (bigCarried) menu.setCarried(ItemStack.EMPTY);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            if (bigCarried) menu.setCarried(carried);
        }

        drawSearchCover(graphics);
        if (healthTabShown()) {
            health.render(graphics, mouseX, mouseY);
        } else {
            drawPaperdoll(graphics);
            drawEquipmentLabels(graphics);
        }
        drawSectionHeaders(graphics);
        drawWeaponSlotDecor(graphics, mouseX, mouseY);
        drawCraftTableButton(graphics, mouseX, mouseY);
        drawSkillsButton(graphics, mouseX, mouseY);
        if (isOverCraftButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal("Open 3x3 crafting"), mouseX, mouseY);
        }
        if (isOverSkillsButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal("Skills"), mouseX, mouseY);
        }
        drawBackpackScrollbar(graphics);
        drawCorpseScrollbar(graphics);
        drawVitals(graphics);
        drawConditions(graphics);
        drawGridIcons(graphics);
        drawGridPlacementPreview(graphics, mouseX, mouseY);
        if (bigCarried) drawCarriedBig(graphics, carried, mouseX, mouseY);

        renderTooltip(graphics, mouseX, mouseY);
        drawCurioHoverTooltip(graphics, mouseX, mouseY);
        drawWeaponHoverTooltip(graphics, mouseX, mouseY);
        if (healthTabShown()) health.renderTooltip(graphics, mouseX, mouseY);
    }

    // ---- Equipment / Health tabs (2.16.0) ----

    /** Remembered for the session, so the inventory reopens on the tab it was left on. */
    private static boolean healthTab = false;
    private final HealthTab health = new HealthTab(this);

    /** The HEALTH tab exists only with First Aid, whose per-limb model it shows. Updated each frame. */
    private boolean tabAvailable;

    boolean healthTabShown() {
        return healthTab && tabAvailable;
    }

    /** Keeps the menu's equipment slots in step with the tab. Runs at the top of each frame,
     *  before vanilla's pass, so hidden slots are neither drawn nor hovered. */
    private void syncEquipmentTab() {
        tabAvailable = minecraft != null
                && com.dayzhud.mod.compat.FirstAidCompat.limbs(minecraft.player).isPresent();
        menu.equipmentShown = !healthTabShown();
        if (!healthTabShown()) health.cancel();
    }

    private static final int TAB_EQUIP_X = 14, TAB_HEALTH_X = 66, TAB_Y = 35;

    /** Card headers and tabs (2.16.2: were full size, 1.0, and read too big). */
    private static final float HEADER_SCALE = 0.75f;

    private void drawScaled(GuiGraphics graphics, String text, float x, float y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** The equipment card's header: one title, or two tabs when First Aid is installed. */
    private void drawEquipmentHeader(GuiGraphics graphics) {
        if (!tabAvailable) {
            drawHeader(graphics, "EQUIPMENT", 14, TAB_Y, 168, null, 0);
            return;
        }
        boolean onHealth = healthTabShown();
        drawTab(graphics, "EQUIPMENT", TAB_EQUIP_X, !onHealth);
        drawTab(graphics, "HEALTH", TAB_HEALTH_X, onHealth);
    }

    /** A tab title: bright with the accent rule when active, dim (lighter on hover) when not. */
    private void drawTab(GuiGraphics graphics, String label, int x, boolean active) {
        int w = Math.round(font.width(label) * HEADER_SCALE);
        boolean hover = isOverTab(label, x, lastMouseX, lastMouseY);
        drawScaled(graphics, label, leftPos + x, topPos + TAB_Y + 2, HEADER_SCALE,
                active || hover ? HEADER_COLOR : LABEL_DIM);
        if (active) {
            graphics.fill(leftPos + x, topPos + TAB_Y + 10, leftPos + x + w + 4, topPos + TAB_Y + 11, ACCENT);
        }
    }

    private boolean isOverTab(String label, int x, double mouseX, double mouseY) {
        int tx = leftPos + x, ty = topPos + TAB_Y - 2;
        return mouseX >= tx && mouseX < tx + font.width(label) * HEADER_SCALE + 4
                && mouseY >= ty && mouseY < ty + 14;
    }

    /** Slot names beside the armour and side columns, and the paperdoll between them. */
    private void drawEquipmentLabels(GuiGraphics graphics) {
        String[] armour = {"HEAD", "BODY", "LEGS", "FEET"};
        for (int i = 0; i < armour.length; i++) {
            caption(graphics, armour[i], leftPos + TarkovInventoryMenu.EQUIP_COL_X + 18,
                    topPos + TarkovInventoryMenu.EQUIP_START_Y + i * TarkovInventoryMenu.EQUIP_SPACING + 6, LABEL_DIM);
        }
        int sx = leftPos + TarkovInventoryMenu.SIDE_COL_X - 2;
        for (var info : menu.curioSlotInfos) {
            if (info.x() != TarkovInventoryMenu.SIDE_COL_X) continue;
            String label = info.identifier().equalsIgnoreCase("mask") ? "FACE" : "BAG";
            captionRight(graphics, label, sx, topPos + info.y() + 6, LABEL_DIM);
        }
        captionRight(graphics, "OFF", sx, topPos + menu.offhandY + 6, LABEL_DIM);
    }

    /** Half-size text, the panel's small labels. */
    void caption(GuiGraphics graphics, String text, float x, float y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.5f, 0.5f, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    void captionRight(GuiGraphics graphics, String text, float rightX, float y, int color) {
        caption(graphics, text, rightX - font.width(text) / 2f, y, color);
    }

    net.minecraft.client.gui.Font font() {
        return font;
    }

    int left() {
        return leftPos;
    }

    int top() {
        return topPos;
    }

    TarkovInventoryMenu menu() {
        return menu;
    }

    /**
     * Hatches over every slot the player has not searched yet.
     *
     * The items themselves are already absent - SearchedContainer never sends them - so this
     * is purely a signal that the slot is unknown rather than empty. Without it a body being
     * searched looks like a body that is simply empty, and the two need to read differently.
     *
     * Indices come straight from the server against this same menu, so a slot's cover always
     * lands on the slot it belongs to. That was the whole failure of the previous approach.
     */
    private void drawSearchCover(GuiGraphics graphics) {
        if (!ClientSearchState.any()) return;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.isActive() || !ClientSearchState.isMasked(i)) continue;

            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.fill(x, y, x + 16, y + 16, SEARCH_COVER_BG);
            // Diagonal hatching, clipped to the slot. Drawn as short horizontal runs stepping
            // down one pixel at a time, which is cheaper than a texture and keeps the look
            // consistent with the rest of the flat-filled UI.
            for (int d = -16; d < 16; d += 4) {
                for (int step = 0; step < 16; step++) {
                    int px = x + d + step;
                    int py = y + step;
                    if (px < x || px >= x + 16) continue;
                    graphics.fill(px, py, px + 1, py + 1, SEARCH_COVER_LINE);
                }
            }
        }
    }

    private void drawPaperdoll(GuiGraphics graphics) {
        LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer == null) return;

        // Centred between the armour column and the side column, feet on the card's floor.
        int pdX = leftPos + 98;
        int pdY = topPos + 145;

        // Facing mostly forward but turned slightly toward the right of the screen.
        // TUNING NOTE: this helper turns the model by roughly (angleXComponent * 20)
        // degrees off front-facing, so small values give small turns. Flip the sign if it
        // leans the wrong way.
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, pdX, pdY, 44,
                -0.8f, 0.0f, localPlayer);
    }

    private void drawSectionHeaders(GuiGraphics graphics) {
        drawEquipmentHeader(graphics);
        drawHeader(graphics, "WEAPONS", 14, 159, 168, "HOTBAR 1-9", LABEL_DIM);
        drawHeader(graphics, "GEAR", 14, 285, 168, "CURIOS", LABEL_DIM);
        drawHeader(graphics, "POCKETS", 204, 35, 162, "9 x 3", LABEL_DIM);
        drawHeader(graphics, "BACKPACK", 204, 117, 162, backpackSummary(), LABEL_DIM);
        drawHeader(graphics, "SECURE", 204, 273, 162, "KEPT ON DEATH", ACCENT);
        for (int i = 0; i < 5; i++) {
            caption(graphics, String.valueOf(5 + i),
                    leftPos + TarkovInventoryMenu.HOTBAR_X + i * TarkovInventoryMenu.HOTBAR_SPACING,
                    topPos + TarkovInventoryMenu.HOTBAR_Y - 6, LABEL_DIM);
        }
        if (menu.isCorpse()) {
            String name = title.getString().toUpperCase(Locale.ROOT);
            int rule = Math.max(40, Math.round(font.width(name) * 0.8f));
            drawCorpseHeader(graphics, name, leftPos + TarkovInventoryMenu.CORPSE_ARMOR_X, topPos + 8, rule);
            drawCorpseHeader(graphics, "GEAR", leftPos + TarkovInventoryMenu.CORPSE_GEAR_X,
                    topPos + TarkovInventoryMenu.CORPSE_GEAR_Y - 14, 30);
            drawCorpseHeader(graphics, "INVENTORY", leftPos + TarkovInventoryMenu.CORPSE_INV_X,
                    topPos + TarkovInventoryMenu.CORPSE_INV_Y - 14, 54);
            drawCorpseHeader(graphics, "HOTBAR", leftPos + TarkovInventoryMenu.CORPSE_INV_X,
                    topPos + TarkovInventoryMenu.CORPSE_HOTBAR_Y - 14, 40);
            if (menu.corpseHasBackpack()) {
                drawCorpseHeader(graphics, "BACKPACK", leftPos + TarkovInventoryMenu.CORPSE_INV_X,
                        topPos + TarkovInventoryMenu.CORPSE_BAG_Y - 14, 50);
            }
        } else if (menu.hasContainer()) {
            // The menu title carries the opened block's own display name (e.g. "Chest",
            // "Barrel", or a renamed container), sent from the server when it opened.
            String name = title.getString().toUpperCase(Locale.ROOT);
            int size = menu.openedContainer.getContainerSize();
            drawHeader(graphics, name, TarkovInventoryMenu.CONTAINER_X - 1, 35,
                    TarkovInventoryMenu.CONTAINER_COLS * 18, size + " SLOTS", LABEL_DIM);
        }
    }

    /** "ASSAULT PACK  11/27": the worn bag's name and how many of its cells are taken. */
    private String backpackSummary() {
        int slots = menu.getActiveBackpackSlots();
        if (slots == 0) return null;
        int used = menu.usedBackpackCells();
        String name = menu.backpackHandler.getBagStack().getHoverName().getString().toUpperCase(Locale.ROOT);
        String count = used + "/" + slots;
        int room = 162 - Math.round(font.width("BACKPACK") * HEADER_SCALE) - 10
                - Math.round(font.width("  " + count) * 0.5f);
        while (name.length() > 3 && Math.round(font.width(name) * 0.5f) > room) {
            name = name.substring(0, name.length() - 1);
        }
        return name + "  " + count;
    }

    /**
     * A card header in panel coordinates: title with a short accent rule under it, and
     * optional small text right-aligned at {@code width}. (2.16.1: the rule no longer runs on
     * across the card - one line fewer per card.)
     */
    private void drawHeader(GuiGraphics graphics, String text, int px, int py, int width,
                            String right, int rightColor) {
        int x = leftPos + px, y = topPos + py;
        int w = Math.round(font.width(text) * HEADER_SCALE);
        drawScaled(graphics, text, x, y + 2, HEADER_SCALE, HEADER_COLOR);
        graphics.fill(x, y + 10, x + Math.min(width, w + 4), y + 11, ACCENT);
        // Caption-sized, sitting on the same line as the title's foot.
        if (right != null) captionRight(graphics, right, x + width, y + 4, rightColor);
    }

    /** The corpse side keeps its pre-2.16 look: small title over a plain rule. */
    private void drawCorpseHeader(GuiGraphics graphics, String text, int x, int y, int ruleWidth) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.8f, 0.8f, 1f);
        graphics.drawString(font, text, 0, 0, HEADER_COLOR, false);
        graphics.pose().popPose();
        graphics.fill(x, y + 9, x + ruleWidth, y + 10, HEADER_ACCENT);
    }

    /**
     * Names the hovered Curios slot. Only shown for EMPTY slots - a slot holding an item
     * already gets that item's own tooltip from vanilla, and stacking ours on top would
     * double up.
     */
    private void drawCurioHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.hasItem()) return;

        for (var info : menu.curioSlotInfos) {
            // Face and bag hide under the HEALTH tab.
            if (!menu.equipmentShown && info.x() == TarkovInventoryMenu.SIDE_COL_X) continue;
            int sx = leftPos + info.x();
            int sy = topPos + info.y();
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                graphics.renderTooltip(font, Component.literal(prettify(info.identifier())), mouseX, mouseY);
                return;
            }
        }
    }

    private String prettify(String identifier) {
        String cleaned = identifier.replace('_', ' ').trim();
        if (cleaned.isEmpty()) return identifier;
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    /**
     * Backgrounds, ghost icons for an empty slot, a bigger real icon for an occupied one,
     * and labels - for the four loadout boxes, each a different size (see
     * TarkovInventoryMenu.WEAPON_BOX_*), styled after the reference image rather than the
     * plain 16x16 grid cell these used to be.
     *
     * These are still real Slots (see TarkovInventoryMenu.WeaponSlot) with a real, normal
     * 16x16 clickable region - centred inside the bigger box, not resized, since vanilla
     * doesn't support a variable-size Slot. An empty slot gets the flat ghost icon; an
     * occupied one gets nothing extra at all - vanilla's own small icon, already drawn
     * underneath by the normal render pass, is left alone rather than overlaid.
     */
    private void drawWeaponSlotDecor(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < WeaponSlots.ORDER.length; i++) {
            int bx = leftPos + TarkovInventoryMenu.WEAPON_BOX_X[i];
            int by = topPos + TarkovInventoryMenu.WEAPON_BOX_Y[i];
            int bw = TarkovInventoryMenu.WEAPON_BOX_W[i];
            int bh = TarkovInventoryMenu.WEAPON_BOX_H[i];
            WeaponSlots type = WeaponSlots.ORDER[i];

            graphics.fill(bx, by, bx + bw, by + bh, SLOT_BG);
            graphics.renderOutline(bx, by, bw, bh, SLOT_BORDER);

            ItemStack stack = menu.weaponSlots[i].getItem();
            if (stack.isEmpty()) {
                int pad = 4;
                drawFlatWeaponIcon(graphics, type, bx + pad, by + pad, bw - pad * 2, bh - pad * 2,
                        WEAPON_GHOST_COLOR);
                // The whole box takes clicks (isHovering), so the whole box lights up.
                if (mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh) {
                    graphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, FLAT_HOVER_COLOR);
                }
            } else if (TaczFlatGunRenderer.canRender(stack)) {
                // Equipped TACZ gun: its real model, side-on, filling the box - same as the
                // grid. The box outline/background above stays; the panel covers vanilla's
                // small icon in the centred 16x16 slot.
                drawFlatGunBox(graphics, stack, bx, by, bw, bh, 2, false, TaczFlatGunRenderer.gridScale(stack));
            }
            // Anything else equipped (a knife in SHEATH, a non-TACZ gun) shows vanilla's own
            // small icon, drawn underneath by the normal slot pass - no overlay.

            // "1  PRIMARY" above the box: the hotbar key it's bound to, then the slot.
            caption(graphics, (i + 1) + "  " + type.name(), bx + 2, by - 6, LABEL_DIM);
        }
    }

    /**
     * The flat category icon (see WEAPON_ICON_RIFLE/PISTOL/KNIFE's doc), used for both the
     * empty ghost and the equipped state - only the tint colour differs between them.
     * Scaled to CONTAIN within ({@code availW}, {@code availH}) preserving the icon's own
     * aspect ratio (never stretched, unlike the grid's big-item icons - these are fixed art,
     * not an arbitrary item's model, so there's no reason to distort them) and centred in
     * that space.
     */
    private void drawFlatWeaponIcon(GuiGraphics graphics, WeaponSlots type,
                                     int availX, int availY, int availW, int availH, int tint) {
        ResourceLocation icon;
        int nativeW, nativeH;
        switch (type) {
            case PRIMARY, SECONDARY -> {
                icon = WEAPON_ICON_RIFLE;
                nativeW = WEAPON_ICON_RIFLE_W;
                nativeH = WEAPON_ICON_RIFLE_H;
            }
            case HOLSTER -> {
                icon = WEAPON_ICON_PISTOL;
                nativeW = WEAPON_ICON_PISTOL_W;
                nativeH = WEAPON_ICON_PISTOL_H;
            }
            default -> {
                icon = WEAPON_ICON_KNIFE;
                nativeW = WEAPON_ICON_KNIFE_W;
                nativeH = WEAPON_ICON_KNIFE_H;
            }
        }

        float scale = Math.min((float) availW / nativeW, (float) availH / nativeH);
        int drawW = Math.round(nativeW * scale);
        int drawH = Math.round(nativeH * scale);
        int x = availX + (availW - drawW) / 2;
        int y = availY + (availH - drawH) / 2;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(
                ((tint >> 16) & 0xFF) / 255f,
                ((tint >> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f,
                ((tint >> 24) & 0xFF) / 255f);
        graphics.blit(icon, x, y, 0, 0, drawW, drawH, drawW, drawH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Explains what an empty loadout slot accepts. Non-empty slots get vanilla's own
     *  item tooltip automatically, so this only has to handle the empty case. */
    private void drawWeaponHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < WeaponSlots.ORDER.length; i++) {
            int bx = leftPos + TarkovInventoryMenu.WEAPON_BOX_X[i];
            int by = topPos + TarkovInventoryMenu.WEAPON_BOX_Y[i];
            int bw = TarkovInventoryMenu.WEAPON_BOX_W[i];
            int bh = TarkovInventoryMenu.WEAPON_BOX_H[i];
            if (mouseX < bx || mouseX >= bx + bw || mouseY < by || mouseY >= by + bh) continue;
            if (!menu.weaponSlots[i].getItem().isEmpty()) return; // vanilla handles this one

            WeaponSlots type = WeaponSlots.ORDER[i];
            String accepted = switch (type) {
                case PRIMARY, SECONDARY -> "Rifles, SMGs, shotguns, snipers, MGs, launchers";
                case HOLSTER -> "Pistols";
                case SHEATH -> "Melee weapons";
            };
            graphics.renderTooltip(font, Component.literal(type.label + " \u00a77- " + accepted),
                    mouseX, mouseY);
            return;
        }
    }

    // ---- Multi-cell grid rendering/interaction ----

    /**
     * Draws every multi-cell item's icon big, spanning its footprint, over the top of
     * whatever vanilla's own render pass already drew at that slot's normal 16x16 position.
     *
     * There's deliberately no attempt to suppress vanilla's own small icon underneath. An
     * earlier version tried exactly that via {@code Slot.isActive()} - the only public hook
     * available, since the actual per-slot render method turned out to be private and
     * un-overridable - and that briefly worked visually but broke something more important:
     * {@code isActive() == false} also makes vanilla's own mouse hit-testing skip the slot
     * entirely, which is what let a placed multi-cell item render correctly while becoming
     * permanently unclickable. Simplest fix once that was understood: don't touch
     * isActive() at all. This method runs after super.render() every frame, so the bigger
     * icon it draws - same top-left origin, strictly larger - fully covers the smaller one
     * underneath on its own, with the slot staying completely normal (and clickable) as far
     * as vanilla is concerned. A shadow cell's marker item has a blank texture and a count of
     * 1, so vanilla draws literally nothing for it either way - nothing to cover there.
     */
    private void drawGridIcons(GuiGraphics graphics) {
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || ItemGrid.isReservation(stack)) continue;
            // 1x1 items too when they have a model render (pistol magazines) - their tilted
            // inventory icon is what that render replaces.
            if ((!ItemGrid.isMultiCell(stack) && !FlatModelRenderer.canRender(stack))
                    || !menu.isGridSlot(slot.index)) continue;
            Footprint fp = ItemGrid.footprintOf(stack);
            // A stack that lost its footprint contention (see GridStorage's class doc and
            // ItemGrid.hasReservedFootprint) - most commonly two same-type items that ended
            // up adjacent from before a footprint size change, rather than through this
            // screen's own placement check - renders as a plain 1x1 instead. Drawing it big
            // anyway would visually overlap whatever actually holds those cells.
            if (!menu.gridFootprintReserved(slot.index, fp)) continue;
            if (com.dayzhud.mod.inventory.grid.GridConfig.DEBUG_LOGGING.get()) {
                com.dayzhud.mod.DayzHudMod.LOGGER.info(
                        "grid draw: menu slot {} at screen ({},{}) footprint={} item={}",
                        slot.index, leftPos + slot.x, topPos + slot.y, fp, stack.getItem());
            }
            drawBigGridIcon(graphics, slot, fp);
        }
    }

    /**
     * Scales TACZ's normal 3D GUI render UNIFORMLY to fit the box, rather than the earlier
     * non-uniform stretch that caused the shearing this was originally built to fix. A gun
     * rendered at its native aspect ratio and just made bigger reads fine; forced
     * independently in width and height to fill a wide, short rectangle is what turned it
     * into a thin diagonal streak. The rotation is the other half of this mod's own doing -
     * see GridConfig.FLAT_ITEM_ANGLE_X - and defaults off (0) pending an in-game look.
     */
    private void renderTiltedItem(GuiGraphics graphics, ItemStack stack, int x, int y, int w, int h) {
        float angle = com.dayzhud.mod.inventory.grid.GridConfig.FLAT_ITEM_ANGLE_X.get().floatValue();
        float scale = Math.min(w / 16f, h / 16f);

        graphics.pose().pushPose();
        graphics.pose().translate(x + w / 2f, y + h / 2f, 0);
        if (angle != 0f) {
            graphics.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(angle));
        }
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(stack, -8, -8);
        graphics.pose().popPose();
    }

    /** Above everything in the grid layer and below tooltips (400) - vanilla's own carried item sits ~382. */
    private static final float CARRIED_Z = 385;

    /**
     * The carried item at its footprint size, with its top-left cell centred on the cursor -
     * the same cell the placement preview outlines and a click would anchor to. Its model
     * render if it has one; otherwise (CAPS armour, ...) its own icon scaled into the
     * footprint, as the grid draws it; then its count / durability bar.
     */
    private void drawCarriedBig(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        Footprint fp = ItemGrid.footprintOf(stack);
        int x = mouseX - 9, y = mouseY - 9, w = fp.width() * 18, h = fp.height() * 18;
        int in = TaczFlatGunRenderer.GRID_INSET;
        boolean drawn = FlatItems.canRender(stack) && FlatItems.render(graphics, stack, x + in, y + in,
                w - 2 * in, h - 2 * in, CARRIED_Z, ItemGrid.isRotated(stack), -1f);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, CARRIED_Z - 150);   // renderItem adds its own 150
        if (!drawn) renderTiltedItem(graphics, stack, x + 1, y + 1, w - 2, h - 2);
        graphics.renderItemDecorations(font, stack, x + w - 17, y + h - 17);
        graphics.pose().popPose();
    }

    /**
     * A loadout slot's real Slot is a 16x16 square in the middle of its big box (vanilla has no
     * variable-size Slot), so only that square used to take clicks. Vanilla routes every slot
     * hover and click test through this method with the slot's position and 16x16, so a match
     * on a weapon slot's position is answered for its whole box instead - click anywhere in it.
     */
    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
        if (w == 16 && h == 16) {
            for (int i = 0; i < menu.weaponSlots.length; i++) {
                Slot ws = menu.weaponSlots[i];
                if (ws != null && ws.x == x && ws.y == y) {
                    return super.isHovering(TarkovInventoryMenu.WEAPON_BOX_X[i], TarkovInventoryMenu.WEAPON_BOX_Y[i],
                            TarkovInventoryMenu.WEAPON_BOX_W[i], TarkovInventoryMenu.WEAPON_BOX_H[i], mouseX, mouseY);
                }
            }
        }
        return super.isHovering(x, y, w, h, mouseX, mouseY);
    }

    /** Set when a press was consumed as a placement click; swallows the matching release. */
    private boolean swallowRelease;

    /**
     * Carrying a multi-cell item and pressing over the grid: place it right away as a plain
     * click, rather than letting vanilla begin a drag-spread ("quick craft"), which placed a
     * gun without checking its footprint whenever the mouse moved between press and release.
     * The menu refuses that drag for multi-cell items too; this makes placement feel normal.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && tabAvailable) {
            boolean toEquipment = isOverTab("EQUIPMENT", TAB_EQUIP_X, mouseX, mouseY);
            if (toEquipment || isOverTab("HEALTH", TAB_HEALTH_X, mouseX, mouseY)) {
                if (healthTab == toEquipment) UiSounds.inventoryMove();
                healthTab = !toEquipment;
                syncEquipmentTab();
                return true;
            }
        }
        if (healthTabShown() && health.mouseClicked(mouseX, mouseY, button)) return true;
        if ((button == 0 || button == 1) && ItemGrid.isMultiCell(menu.getCarried())
                && hoveredSlot != null && menu.isGridSlot(hoveredSlot.index)) {
            slotClicked(hoveredSlot, hoveredSlot.index, button, ClickType.PICKUP);
            swallowRelease = true;
            return true;
        }
        return mouseClickedBase(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (health.mouseReleased(button)) return true;
        if (swallowRelease) {
            swallowRelease = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (swallowRelease || health.holding()) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * One panel over the box (x,y,w,h) - covering TACZ's small diagonal icon that vanilla
     * already drew in the slot, and the grid's internal cell borders - then the gun's real
     * model on it, side-on (TaczFlatGunRenderer), then decorations and a hover highlight
     * (vanilla's own highlight is under the panel now). Shared by the grid and the loadout
     * boxes. Returns false if the model draw failed, so the caller can fall back.
     */
    private boolean drawFlatGunBox(GuiGraphics graphics, ItemStack stack, int x, int y, int w, int h, int inset,
                                   boolean rotated, float maxScale) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, FLAT_PANEL_Z);
        graphics.fill(x, y, x + w, y + h, SLOT_BG);
        graphics.renderOutline(x, y, w, h, SLOT_BORDER);
        graphics.pose().popPose();

        if (!FlatItems.render(graphics, stack, x + inset, y + inset, w - inset * 2, h - inset * 2, FLAT_GUN_Z,
                rotated, maxScale)) {
            return false;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, FLAT_DECOR_Z);
        graphics.renderItemDecorations(font, stack, x + w - 17, y + h - 17);
        graphics.pose().popPose();

        if (lastMouseX >= x && lastMouseX < x + w && lastMouseY >= y && lastMouseY < y + h) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, FLAT_HOVER_Z);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, FLAT_HOVER_COLOR);
            graphics.pose().popPose();
        }
        return true;
    }

    /**
     * Always the real gun's own 3D render, at its real colours and texture - not TACZ's
     * flat grayscale HUD icon (tried in 2.11.0; reverted per feedback: original-looking guns
     * matter more here than avoiding the render entirely). Only the rotation and the
     * contain-vs-stretch fit are this mod's own doing - see renderTiltedItem.
     */
    private void drawBigGridIcon(GuiGraphics graphics, Slot slot, Footprint footprint) {
        ItemStack stack = slot.getItem();
        int x = leftPos + slot.x;
        int y = topPos + slot.y;
        int w = footprint.width() * 18 - 2;
        int h = footprint.height() * 18 - 2;

        if (FlatItems.canRender(stack)
                && drawFlatGunBox(graphics, stack, x - 1, y - 1, w + 2, h + 2, TaczFlatGunRenderer.GRID_INSET,
                        ItemGrid.isRotated(stack), -1f)) {
            return;
        }
        // Anything else multi-cell (CAPS armor, magazines, ...): same panel, then the item's
        // own inventory render scaled uniformly into the footprint. Before 2.12.6 this drew
        // at vanilla's depth with no panel, so the small vanilla icon sat on top of it.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, FLAT_PANEL_Z);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_BG);
        graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, SLOT_BORDER);
        graphics.pose().popPose();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, FLAT_GUN_Z - 150);   // renderItem adds its own 150
        renderTiltedItem(graphics, stack, x, y, w, h);
        graphics.pose().translate(0, 0, FLAT_DECOR_Z);
        graphics.renderItemDecorations(font, stack, x + w - 16, y + h - 16);
        graphics.pose().popPose();
    }

    /**
     * While carrying a multi-cell item and hovering a grid region, outlines where it would
     * land - green if {@link TarkovInventoryMenu#gridFits} agrees, red if it wouldn't fit
     * here. Purely a preview; clicked() re-checks the same fit server-side regardless.
     */
    private void drawGridPlacementPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || !ItemGrid.isMultiCell(carried)) return;
        if (hoveredSlot == null || !menu.isGridSlot(hoveredSlot.index)) return;

        Footprint fp = ItemGrid.footprintOf(carried);
        boolean fits = menu.gridFits(hoveredSlot.index, fp);

        int x = leftPos + hoveredSlot.x - 1;
        int y = topPos + hoveredSlot.y - 1;
        int w = fp.width() * 18;
        int h = fp.height() * 18;
        int color = fits ? 0xA000FF00 : 0xA0FF0000;
        // Above the flat-gun panels and models (FLAT_PANEL_Z / FLAT_GUN_Z), or a red "won't fit" outline
        // over an existing gun would be hidden behind it - exactly when it matters most.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, FLAT_PREVIEW_Z);
        graphics.fill(x, y, x + w, y + h, (color & 0x00FFFFFF) | 0x30000000);
        graphics.renderOutline(x, y, w, h, color);
        graphics.pose().popPose();
    }

    /**
     * "R" while carrying a multi-cell item rotates it. Not yet a rebindable KeyMapping -
     * hardcoded, matching the reference this was built from, which showed the same fixed key.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && ItemGrid.isMultiCell(carried)) {
                ItemGrid.setRotated(carried, !ItemGrid.isRotated(carried));
                NetworkHandler.CHANNEL.sendToServer(new RotateCarriedPacket());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Vitals in the title bar (2.16.0; were a column of icons beside the secure container):
     * health, hydration, energy (food) and carried weight, each a thin bar with its value.
     */
    private void drawVitals(GuiGraphics graphics) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        // First Aid, when present, owns the real health - vanilla health is only a lossy
        // summary of its limb model and drifts out of step with it. See FirstAidCompat.
        float health01 = FirstAidCompat.getBodyHealth01(player)
                .orElseGet(() -> player.getHealth() / Math.max(1f, player.getMaxHealth()));
        float food01 = player.getFoodData().getFoodLevel() / 20f;
        float water01 = ThirstWasTakenCompat.getThirst01(player)
                .orElseGet(() -> player.getFoodData().getSaturationLevel() / 20f);

        // One row (2.16.1; was two, squeezed): four columns between the title and the buttons.
        drawVital(graphics, VITAL_X[0], VITAL_Y, "HEALTH", health01, severityColor(health01), percent(health01));
        drawVital(graphics, VITAL_X[1], VITAL_Y, "HYDRATION", water01, severityColor(water01), percent(water01));
        drawVital(graphics, VITAL_X[2], VITAL_Y, "ENERGY", food01, severityColor(food01), percent(food01));

        if (!com.dayzhud.mod.weight.ClientWeight.known()) {
            drawVital(graphics, VITAL_X[3], VITAL_Y, "WEIGHT", 0f, LABEL_DIM, "-");
            return;
        }
        float kg = com.dayzhud.mod.weight.ClientWeight.kg();
        float over = com.dayzhud.mod.weight.ClientWeight.overweight();
        float heavy = com.dayzhud.mod.weight.ClientWeight.heavy();
        float critical = Math.max(1f, com.dayzhud.mod.weight.ClientWeight.critical());
        int level = com.dayzhud.mod.weight.ClientWeight.level();
        // Against the next limit up, the same number the HUD warns about.
        float limit = level == 0 ? over : level == 1 ? heavy : critical;
        int color = weightColor(level);
        drawVital(graphics, VITAL_X[3], VITAL_Y, "WEIGHT", kg / critical, color,
                String.format(Locale.ROOT, "%.1f/%.0f KG", kg, limit));
        // Where the overweight and heavy limits fall on the bar.
        for (float mark : new float[]{over / critical, heavy / critical}) {
            int mx = leftPos + VITAL_X[3] + Math.round(VITAL_W * Math.min(1f, mark));
            graphics.fill(mx, topPos + VITAL_Y + 7, mx + 1, topPos + VITAL_Y + 11, HEADER_COLOR);
        }
    }

    private static final int[] VITAL_X = {96, 156, 216, 276};
    private static final int VITAL_Y = 8, VITAL_W = 54;

    private void drawVital(GuiGraphics graphics, int px, int py, String label, float value01, int color,
                           String value) {
        int x = leftPos + px, y = topPos + py, width = VITAL_W;
        caption(graphics, label, x, y, LABEL_DIM);
        captionRight(graphics, value, x + width, y, color);
        graphics.fill(x, y + 8, x + width, y + 10, BAR_TRACK);
        graphics.fill(x, y + 8, x + Math.round(width * Math.max(0f, Math.min(1f, value01))), y + 10, color);
    }

    private static String percent(float value01) {
        return Math.round(value01 * 100) + "%";
    }

    private static int weightColor(int level) {
        return switch (level) {
            case 0 -> TEXT_COLOR;
            case 1 -> COLOR_WARN;
            case 2 -> COLOR_LOW;
            default -> COLOR_CRITICAL;
        };
    }

    private int severityColor(float value01) {
        if (value01 <= 0.25f) return COLOR_CRITICAL;
        if (value01 <= 0.5f) return COLOR_LOW;
        return TEXT_COLOR;
    }

    /**
     * CONDITION, right of the secure container: bleeding, pain, morphine and load - the same
     * things the HUD's condition icons show, spelled out.
     */
    private void drawConditions(GuiGraphics graphics) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;
        int x = leftPos + TarkovInventoryMenu.SECURE_X + 3 * 18 + 12, y = topPos + TarkovInventoryMenu.SECURE_Y;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.5f, 0.5f, 1f);
        graphics.drawString(font, "CONDITION", 0, 0, HEADER_COLOR, false);
        graphics.pose().popPose();

        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        int heavyWounds = com.dayzhud.mod.injury.ClientInjuries.heavy();
        int lightWounds = com.dayzhud.mod.injury.ClientInjuries.light();
        if (heavyWounds > 0) {
            labels.add("HEAVY BLEEDING" + (heavyWounds > 1 ? " x" + heavyWounds : ""));
            colors.add(0xFF9E0F0F);
        }
        if (lightWounds > 0) {
            labels.add("LIGHT BLEEDING" + (lightWounds > 1 ? " x" + lightWounds : ""));
            colors.add(COLOR_CRITICAL);
        }
        int pain = com.dayzhud.mod.injury.ClientInjuries.pain();
        int relief = com.dayzhud.mod.injury.ClientInjuries.reliefSeconds();
        if (pain >= com.dayzhud.mod.injury.InjurySystem.PAIN_MILD) {
            boolean severe = pain >= com.dayzhud.mod.injury.InjurySystem.PAIN_SEVERE;
            labels.add((severe ? "SEVERE PAIN" : "PAIN") + (relief > 0 ? " - " + relief + "S RELIEF" : ""));
            colors.add(relief > 0 ? 0xFF8FB08F : severe ? COLOR_CRITICAL : COLOR_LOW);
        }
        int morphine = FirstAidCompat.morphineTicks(player);
        if (morphine > 0) {
            labels.add("MORPHINE - " + (morphine + 19) / 20 + "S");
            colors.add(0xFF8FB08F);
        }
        int level = com.dayzhud.mod.weight.ClientWeight.known() ? com.dayzhud.mod.weight.ClientWeight.level() : 0;
        if (level > 0) {
            labels.add(level == 1 ? "OVERWEIGHT" : level == 2 ? "HEAVILY LOADED" : "CRITICAL LOAD");
            colors.add(weightColor(level));
        }
        if (labels.isEmpty()) {
            labels.add("NO CONDITIONS");
            colors.add(LABEL_DIM);
        }
        // Four rows fit the card.
        for (int i = 0; i < Math.min(4, labels.size()); i++) {
            int ry = y + 11 + i * 11;
            if (colors.get(i) != LABEL_DIM) graphics.fill(x, ry + 1, x + 4, ry + 5, colors.get(i));
            caption(graphics, labels.get(i), x + (colors.get(i) != LABEL_DIM ? 7 : 0), ry + 1, TEXT_COLOR);
        }
    }

    /**
     * Stand-in figure for the corpse.
     *
     * A live paperdoll isn't possible here: rendering one needs the actual corpse entity,
     * and this screen only receives its inventory container - the entity itself isn't
     * available client-side inside the menu. So we draw the outline figure instead, which
     * also keeps the panel readable regardless of what the corpse was wearing.
     */
    private void drawCorpseFigure(GuiGraphics graphics) {
        // Centred in the gap between the armor column and the side column, and vertically
        // centred on the four armor rows so it sits level with the gear it represents.
        int size = 84;
        int gapCentreX = TarkovInventoryMenu.CORPSE_ARMOR_X + 16
                + (TarkovInventoryMenu.CORPSE_SIDE_X - (TarkovInventoryMenu.CORPSE_ARMOR_X + 16)) / 2;
        int armorTop = TarkovInventoryMenu.CORPSE_EQUIP_START_Y;
        int armorBottom = armorTop + 3 * TarkovInventoryMenu.CORPSE_EQUIP_SPACING + 16;

        int cx = leftPos + gapCentreX - size / 2;
        int cy = topPos + armorTop + (armorBottom - armorTop) / 2 - size / 2;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.85f);
        graphics.blit(CORPSE_FIGURE, cx, cy, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Recessed zones for the corpse side, matching the player panel's structure. */
    private void drawCorpseZones(GuiGraphics graphics, int x, int y) {
        // Relative to the corpse columns, which start 20 px right of the player side.
        int l = x + TarkovInventoryMenu.CORPSE_ARMOR_X - 8, r = l + 176;
        graphics.fill(l - 8, y + 16, l - 7, y + imageHeight - 16, PANEL_BORDER);
        graphics.fill(l, y + 20, r, y + 110, SECTION_BG);   // armor + figure
        graphics.fill(l, y + 116, r, y + 162, SECTION_BG);  // gear
        graphics.fill(l, y + 172, r, y + 236, SECTION_BG);  // inventory
        graphics.fill(l, y + 244, r, y + 272, SECTION_BG);  // hotbar
        if (menu.corpseHasBackpack()) {
            graphics.fill(l, y + 282, r, y + 348, SECTION_BG); // backpack
        }
    }

    // --- Crafting-table button, sits beside the INVENTORY header ---
    private static final int CRAFT_BTN_W = 16;
    private static final int CRAFT_BTN_H = 16;

    private int craftBtnX() { return leftPos + 340; }
    private int craftBtnY() { return topPos + 5; }

    private boolean isOverCraftButton(double mouseX, double mouseY) {
        return mouseX >= craftBtnX() && mouseX <= craftBtnX() + CRAFT_BTN_W
                && mouseY >= craftBtnY() && mouseY <= craftBtnY() + CRAFT_BTN_H;
    }

    private void drawCraftTableButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = isOverCraftButton(mouseX, mouseY);
        int bx = craftBtnX(), by = craftBtnY();
        graphics.fill(bx, by, bx + CRAFT_BTN_W, by + CRAFT_BTN_H,
                hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        graphics.renderOutline(bx, by, CRAFT_BTN_W, CRAFT_BTN_H,
                hovered ? StyledTheme.ACCENT : SLOT_BORDER);

        // 3x3 grid glyph, centred. Visible extent is 3 cells of 2px plus 2 gutters of 1px
        // = 8px (the trailing gutter isn't drawn), so the origin is (16-8)/2 = 4, not 3.
        int cell = 3;
        int gridOrigin = 4;
        int glyphColor = hovered ? 0xFFFFFFFF : HEADER_COLOR;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int gx = bx + gridOrigin + col * cell;
                int gy = by + gridOrigin + row * cell;
                graphics.fill(gx, gy, gx + 2, gy + 2, glyphColor);
            }
        }
    }

    // --- Skills button, immediately right of the crafting button ---

    private int skillsBtnX() { return leftPos + 360; }
    private int skillsBtnY() { return topPos + 5; }

    private boolean isOverSkillsButton(double mouseX, double mouseY) {
        return mouseX >= skillsBtnX() && mouseX <= skillsBtnX() + CRAFT_BTN_W
                && mouseY >= skillsBtnY() && mouseY <= skillsBtnY() + CRAFT_BTN_H;
    }

    private void drawSkillsButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = isOverSkillsButton(mouseX, mouseY);
        int bx = skillsBtnX(), by = skillsBtnY();
        graphics.fill(bx, by, bx + CRAFT_BTN_W, by + CRAFT_BTN_H,
                hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        graphics.renderOutline(bx, by, CRAFT_BTN_W, CRAFT_BTN_H,
                hovered ? StyledTheme.ACCENT : SLOT_BORDER);

        // Three ascending bars - a "level up" glyph, distinct at a glance from the crafting
        // button's 3x3 grid even at small GUI scales.
        int glyphColor = hovered ? 0xFFFFFFFF : HEADER_COLOR;
        for (int i = 0; i < 3; i++) {
            int barHeight = 3 + i * 3;
            int bxi = bx + 4 + i * 3;
            graphics.fill(bxi, by + 12 - barHeight, bxi + 2, by + 12, glyphColor);
        }
    }

    private boolean mouseClickedBase(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverCraftButton(mouseX, mouseY)) {
            NetworkHandler.CHANNEL.sendToServer(new OpenCraftingPacket());
            return true;
        }
        if (button == 0 && isOverSkillsButton(mouseX, mouseY)) {
            // The skills screen has no slots, so it isn't a container screen. Close this
            // menu properly first - just swapping the screen would leave the server holding
            // an open container for a screen that no longer exists.
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.closeContainer();
                minecraft.setScreen(new com.dayzhud.mod.client.SkillsScreen());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Scrollbar for the corpse loot list. */
    private void drawCorpseScrollbar(GuiGraphics graphics) {
        if (!menu.isCorpse() || menu.corpseLootView == null || !menu.corpseLootView.isScrollable()) return;
        var view = menu.corpseLootView;

        int trackX = leftPos + TarkovInventoryMenu.CORPSE_INV_X + TarkovInventoryMenu.CORPSE_LOOT_COLS * 18 + 2;
        // Track runs the FULL height of the corpse column - level with the figure at the
        // top down to the bottom of the backpack rows - as in the reference. It still only
        // drives the backpack's scroll (that's the only part with hidden content), but it
        // reads as the column's scrollbar rather than one section's.
        int trackTop = topPos + TarkovInventoryMenu.CORPSE_BAG_Y;
        int trackBottom = trackTop + TarkovInventoryMenu.CORPSE_BAG_VISIBLE_ROWS * 18;
        int trackHeight = trackBottom - trackTop;

        graphics.fill(trackX, trackTop, trackX + 4, trackBottom, 0xFF1C1C1C);
        int totalRows = Math.max(1, view.totalRows());
        int thumbHeight = Math.max(10,
                trackHeight * TarkovInventoryMenu.CORPSE_BAG_VISIBLE_ROWS / totalRows);
        int maxScroll = Math.max(1, view.maxScrollRow());
        int thumbY = trackTop + (trackHeight - thumbHeight) * view.getScrollRow() / maxScroll;
        graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFF6A6A6A);
    }

    /** Thin scrollbar to the right of the backpack grid, only when the bag overflows. */
    private void drawBackpackScrollbar(GuiGraphics graphics) {
        var view = menu.backpackView;
        if (!view.isScrollable()) return;

        int trackX = leftPos + TarkovInventoryMenu.BACKPACK_X + 9 * 18 + 1;
        int trackTop = topPos + TarkovInventoryMenu.BACKPACK_Y - 1;
        int trackHeight = TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS * 18;

        graphics.fill(trackX, trackTop, trackX + 3, trackTop + trackHeight, 0xFF1C1C1C);

        int totalRows = Math.max(1, view.totalRows());
        int thumbHeight = Math.max(8,
                trackHeight * TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS / totalRows);
        int maxScroll = Math.max(1, view.maxScrollRow());
        int thumbY = trackTop + (trackHeight - thumbHeight) * view.getScrollRow() / maxScroll;

        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xFF6A6A6A);
    }

    /**
     * Anywhere over the corpse column counts, not just the backpack rows - the scrollbar
     * spans the whole column, so the wheel should work wherever the cursor is on that side.
     */
    private boolean isOverCorpseLoot(double mouseX, double mouseY) {
        int x1 = leftPos + TarkovInventoryMenu.CORPSE_ARMOR_X - 10;
        int x2 = leftPos + TarkovInventoryMenu.CORPSE_INV_X
                + TarkovInventoryMenu.CORPSE_LOOT_COLS * 18 + 10;
        int y1 = topPos + TarkovInventoryMenu.CORPSE_BAG_Y - 10;
        int y2 = topPos + TarkovInventoryMenu.CORPSE_BAG_Y
                + TarkovInventoryMenu.CORPSE_BAG_VISIBLE_ROWS * 18 + 10;
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (menu.isCorpse() && menu.corpseLootView != null
                && menu.corpseLootView.isScrollable() && isOverCorpseLoot(mouseX, mouseY)) {
            var cv = menu.corpseLootView;
            int target = cv.getScrollRow() - (int) Math.signum(delta);
            target = Math.max(0, Math.min(target, cv.maxScrollRow()));
            if (target != cv.getScrollRow()) {
                cv.setScrollRow(target);
                NetworkHandler.CHANNEL.sendToServer(new BackpackScrollPacket(target, true));
            }
            return true;
        }
        var view = menu.backpackView;
        if (view.isScrollable() && isOverBackpackArea(mouseX, mouseY)) {
            int target = view.getScrollRow() - (int) Math.signum(delta);
            target = Math.max(0, Math.min(target, view.maxScrollRow()));
            if (target != view.getScrollRow()) {
                // Applied locally for instant feedback AND sent to the server, because the
                // offset decides which real inventory index each slot maps to - if the two
                // sides disagreed, clicks would hit the wrong item.
                menu.setBackpackScroll(target);
                NetworkHandler.CHANNEL.sendToServer(new BackpackScrollPacket(target, false));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isOverBackpackArea(double mouseX, double mouseY) {
        int x1 = leftPos + 196, x2 = leftPos + 376;
        int y1 = topPos + 112, y2 = topPos + backpackCardBottom();   // the backpack card
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Replaced by drawSectionHeaders().
    }
}
