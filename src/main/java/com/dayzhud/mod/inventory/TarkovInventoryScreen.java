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

    /** Ghost icon drawn in an empty loadout slot, so an unrestricted-looking box doesn't
     *  read as "any item goes here" - see drawWeaponSlotDecor. */
    private static final int WEAPON_GHOST_COLOR = 0x40AFAFAF;
    private int lastMouseX, lastMouseY;

    /**
     * GUI depths for the flat-gun layer. Vanilla draws a slot's item at z 100 (renderSlot's
     * own push) + 150 (renderItem's) = ~250, so anything meant to cover it - the panel that
     * hides TACZ's little diagonal icon - must sit above that. 2.12.1 put the panel at 190,
     * UNDER the icon, which is why the icon still showed. Carried item (~382) and tooltips
     * (400) stay above all of this.
     */
    private static final float FLAT_PANEL_Z = 280, FLAT_GUN_Z = 330, FLAT_HOVER_Z = 345,
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
    private static final ResourceLocation ICON_HEART = rl("icon_heart_solid");
    private static final ResourceLocation ICON_FOOD = rl("icon_food_solid");
    private static final ResourceLocation ICON_WATER = rl("icon_droplet_solid");

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(DayzHudMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 360;
        // 2.12.5: 376 -> 348. At GUI scale 3 on a 1080p screen there are only 360 units, so
        // 376 centred to topPos -8 and cut off the top of the window.
        this.imageHeight = 348;
        if (menu.isCorpse()) {
            this.imageWidth = TarkovInventoryMenu.CORPSE_INV_X + 9 * 18 + 12;
            this.imageHeight = 348; // same as the player's own panel; the corpse bag ends at 342
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

        // Recessed zone backings so the panel reads as distinct regions.
        graphics.fill(x + 8, y + 26, x + 172, y + 138, SECTION_BG);     // paperdoll + armor
        graphics.fill(x + 8, y + 144, x + 172, y + 234, SECTION_BG);    // loadout cluster
        graphics.fill(x + 8, y + 236, x + 172, y + 294, SECTION_BG);    // gear grid
        graphics.fill(x + 8, y + 296, x + 172, y + 342, SECTION_BG);    // hotbar
        graphics.fill(x + 180, y + 20, x + 352, y + 84, SECTION_BG);    // inventory
        int bagSlots = menu.getActiveBackpackSlots();
        if (bagSlots > 0) {
            // Sized to the rows the worn bag actually has: the window is 7 rows now, and a
            // fixed backing that tall left a small bag sitting in a big empty box.
            int bagRows = Math.min(TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS, (bagSlots + 8) / 9);
            graphics.fill(x + 180, y + 94, x + 352, y + 100 + bagRows * 18 + 4, SECTION_BG); // backpack
        }

        if (menu.isCorpse()) {
            drawCorpseZones(graphics, x, y);
            drawCorpseFigure(graphics);
        } else if (menu.hasContainer()) {
            int cx = TarkovInventoryMenu.CONTAINER_X;
            int cy = TarkovInventoryMenu.CONTAINER_Y;
            int cw = TarkovInventoryMenu.CONTAINER_COLS * 18;
            int ch = menu.containerRows * 18;
            graphics.fill(x + 360, y + 16, x + 361, y + imageHeight - 16, PANEL_BORDER); // divider
            graphics.fill(x + cx - 6, y + cy - 6, x + cx + cw + 6, y + cy + ch + 6, SECTION_BG);
        }

        graphics.fill(x + 176, y + 16, x + 177, y + 262, PANEL_BORDER); // vertical divider
        graphics.fill(x + 8, y + 234, x + 352, y + 235, PANEL_BORDER);  // below the loadout cluster

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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        renderBackground(graphics);
        // Vanilla draws the carried item as its normal inventory icon - for a TACZ gun, the
        // small diagonal sprite - and that draw is private, so it can't be replaced. Instead
        // the carried stack is blanked for the length of vanilla's pass only (restored in
        // finally, before anything reads it again - tooltips included) and drawn flat below.
        ItemStack carried = menu.getCarried();
        boolean flatCarried = !carried.isEmpty() && ItemGrid.isMultiCell(carried)
                && TaczFlatGunRenderer.canRender(carried);
        if (flatCarried) menu.setCarried(ItemStack.EMPTY);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            if (flatCarried) menu.setCarried(carried);
        }

        drawSearchCover(graphics);
        drawPaperdoll(graphics);
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
        drawStatBar(graphics);
        drawGridIcons(graphics);
        drawGridPlacementPreview(graphics, mouseX, mouseY);
        if (flatCarried) drawCarriedFlatGun(graphics, carried, mouseX, mouseY);

        renderTooltip(graphics, mouseX, mouseY);
        drawCurioHoverTooltip(graphics, mouseX, mouseY);
        drawWeaponHoverTooltip(graphics, mouseX, mouseY);
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

        // Sits between the two equipment columns; feet land on the boots row, head on the
        // helmet row, so the flanking slots read as body-part aligned.
        int pdX = leftPos + 78;
        int pdY = topPos + 132;

        // Facing mostly forward but turned slightly toward the right of the screen.
        // TUNING NOTE: this helper turns the model by roughly (angleXComponent * 20)
        // degrees off front-facing, so small values give small turns. Flip the sign if it
        // leans the wrong way.
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, pdX, pdY, 50,
                -0.8f, 0.0f, localPlayer);
    }

    private void drawSectionHeaders(GuiGraphics graphics) {
        drawHeader(graphics, "EQUIPMENT", leftPos + 12, topPos + 8, 54);
        drawHeader(graphics, "GEAR", leftPos + 12, topPos + 238, 30);
        drawHeader(graphics, "INVENTORY", leftPos + 184, topPos + 8, 54);
        if (menu.isCorpse()) {
            String name = title.getString().toUpperCase(Locale.ROOT);
            int rule = Math.max(40, Math.round(font.width(name) * 0.8f));
            drawHeader(graphics, name, leftPos + TarkovInventoryMenu.CORPSE_ARMOR_X, topPos + 8, rule);
            drawHeader(graphics, "GEAR", leftPos + TarkovInventoryMenu.CORPSE_GEAR_X,
                    topPos + TarkovInventoryMenu.CORPSE_GEAR_Y - 14, 30);
            drawHeader(graphics, "INVENTORY", leftPos + TarkovInventoryMenu.CORPSE_INV_X,
                    topPos + TarkovInventoryMenu.CORPSE_INV_Y - 14, 54);
            drawHeader(graphics, "HOTBAR", leftPos + TarkovInventoryMenu.CORPSE_INV_X,
                    topPos + TarkovInventoryMenu.CORPSE_HOTBAR_Y - 14, 40);
            if (menu.corpseHasBackpack()) {
                drawHeader(graphics, "BACKPACK", leftPos + TarkovInventoryMenu.CORPSE_INV_X,
                        topPos + TarkovInventoryMenu.CORPSE_BAG_Y - 14, 50);
            }
            drawHeader(graphics, "INVENTORY", leftPos + TarkovInventoryMenu.CORPSE_INV_X,
                    topPos + TarkovInventoryMenu.CORPSE_INV_Y - 14, 54);

        } else if (menu.hasContainer()) {
            // The menu title carries the opened block's own display name (e.g. "Chest",
            // "Barrel", or a renamed container), sent from the server when it opened.
            String name = title.getString().toUpperCase(Locale.ROOT);
            int rule = Math.max(40, Math.round(font.width(name) * 0.8f));
            drawHeader(graphics, name, leftPos + TarkovInventoryMenu.CONTAINER_X,
                    topPos + TarkovInventoryMenu.CONTAINER_Y - 18, rule);
        }
        drawHeader(graphics, "HOTBAR", leftPos + 12, topPos + 298, 40);
        if (menu.getActiveBackpackSlots() > 0) {
            drawHeader(graphics, "BACKPACK", leftPos + 184, topPos + 86, 50);
        }
    }

    private void drawHeader(GuiGraphics graphics, String text, int x, int y, int ruleWidth) {
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
            } else if (TaczFlatGunRenderer.canRender(stack)) {
                // Equipped TACZ gun: its real model, side-on, filling the box - same as the
                // grid. The box outline/background above stays; the panel covers vanilla's
                // small icon in the centred 16x16 slot.
                drawFlatGunBox(graphics, stack, bx, by, bw, bh, 2, false, TaczFlatGunRenderer.gridScale(stack));
            }
            // Anything else equipped (a knife in SHEATH, a non-TACZ gun) shows vanilla's own
            // small icon, drawn underneath by the normal slot pass - no overlay.

            graphics.pose().pushPose();
            graphics.pose().translate(bx - 2, by - 8, 0);
            graphics.pose().scale(0.5f, 0.5f, 1f);
            graphics.drawString(font, type.label, 0, 0, LABEL_DIM, false);
            graphics.pose().popPose();

            // Bound-key badge - primary/secondary only, matching the reference (holster and
            // sheath don't show one there either, even though they're bound the same way).
            if (type == WeaponSlots.PRIMARY || type == WeaponSlots.SECONDARY) {
                graphics.pose().pushPose();
                // Above the flat-gun panel, which would otherwise hide the badge.
                graphics.pose().translate(bx + 2, by + 1, FLAT_HOVER_Z + 1);
                graphics.pose().scale(0.6f, 0.6f, 1f);
                graphics.drawString(font, type == WeaponSlots.PRIMARY ? "1" : "2", 0, 0, LABEL_DIM, false);
                graphics.pose().popPose();
            }
        }

        // The offhand is a real slot too (drawn by vanilla), so it just needs its label here.
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + menu.offhandX - 2, topPos + menu.offhandY + 18, 0);
        graphics.pose().scale(0.5f, 0.5f, 1f);
        graphics.drawString(font, "OFFHAND", 0, 0, LABEL_DIM, false);
        graphics.pose().popPose();
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
            if (!ItemGrid.isMultiCell(stack) || !menu.isGridSlot(slot.index)) continue;
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
     * The carried gun, flat, at its footprint size, with its top-left cell centred on the
     * cursor - the same cell the placement preview outlines and a click would anchor to.
     */
    private void drawCarriedFlatGun(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        Footprint fp = ItemGrid.footprintOf(stack);
        int x = mouseX - 9, y = mouseY - 9, w = fp.width() * 18, h = fp.height() * 18;
        int in = TaczFlatGunRenderer.GRID_INSET;
        TaczFlatGunRenderer.render(graphics, stack, x + in, y + in, w - 2 * in, h - 2 * in, CARRIED_Z,
                ItemGrid.isRotated(stack));
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
        if (swallowRelease) {
            swallowRelease = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (swallowRelease) return true;
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

        if (!TaczFlatGunRenderer.render(graphics, stack, x + inset, y + inset, w - inset * 2, h - inset * 2, FLAT_GUN_Z,
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

        if (TaczFlatGunRenderer.canRender(stack)
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

    private void drawStatBar(GuiGraphics graphics) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        // First Aid, when present, owns the real health - vanilla health is only a lossy
        // summary of its limb model and drifts out of step with it. See FirstAidCompat.
        float health01 = FirstAidCompat.getBodyHealth01(player)
                .orElseGet(() -> player.getHealth() / Math.max(1f, player.getMaxHealth()));
        float food01 = player.getFoodData().getFoodLevel() / 20f;
        float water01 = ThirstWasTakenCompat.getThirst01(player)
                .orElseGet(() -> player.getFoodData().getSaturationLevel() / 20f);

        int y = topPos + 298;
        int x = leftPos + 192;
        int spacing = 56;

        drawStatEntry(graphics, ICON_HEART, health01, x, y);
        drawStatEntry(graphics, ICON_FOOD, food01, x + spacing, y);
        drawStatEntry(graphics, ICON_WATER, water01, x + spacing * 2, y);
    }

    private void drawStatEntry(GuiGraphics graphics, ResourceLocation icon, float value01, int x, int y) {
        int size = 12;
        int color = severityColor(value01);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                1f);
        graphics.blit(icon, x, y, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        graphics.drawString(font, Math.round(value01 * 100) + "%", x + size + 4, y + 2, TEXT_COLOR, false);
    }

    private int severityColor(float value01) {
        if (value01 <= 0.25f) return 0xE23A2E;
        if (value01 <= 0.5f) return 0xE2A62E;
        return 0xE6E6E6;
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
        graphics.fill(x + 364, y + 16, x + 365, y + imageHeight - 16, PANEL_BORDER);
        graphics.fill(x + 372, y + 20, x + 548, y + 110, SECTION_BG);   // armor + figure
        graphics.fill(x + 372, y + 116, x + 548, y + 162, SECTION_BG);  // gear
        graphics.fill(x + 372, y + 172, x + 548, y + 236, SECTION_BG);  // inventory
        graphics.fill(x + 372, y + 244, x + 548, y + 272, SECTION_BG);  // hotbar
        if (menu.corpseHasBackpack()) {
            graphics.fill(x + 372, y + 282, x + 548, y + 348, SECTION_BG); // backpack
        }
    }

    // --- Crafting-table button, sits beside the INVENTORY header ---
    private static final int CRAFT_BTN_W = 16;
    private static final int CRAFT_BTN_H = 16;

    private int craftBtnX() { return leftPos + 330; }
    private int craftBtnY() { return topPos + 4; }

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

    private int skillsBtnX() { return leftPos + 308; }
    private int skillsBtnY() { return topPos + 4; }

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

        int trackX = leftPos + 344;
        int trackTop = topPos + 100;
        int trackHeight = TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS * 18;

        graphics.fill(trackX, trackTop, trackX + 4, trackTop + trackHeight, 0xFF1C1C1C);

        int totalRows = Math.max(1, view.totalRows());
        int thumbHeight = Math.max(8,
                trackHeight * TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS / totalRows);
        int maxScroll = Math.max(1, view.maxScrollRow());
        int thumbY = trackTop + (trackHeight - thumbHeight) * view.getScrollRow() / maxScroll;

        graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFF6A6A6A);
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
        int x1 = leftPos + 180, x2 = leftPos + 352;
        int y1 = topPos + 94;
        int y2 = y1 + TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS * 18 + 8;
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Replaced by drawSectionHeaders().
    }
}
