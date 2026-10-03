package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.search.ClientSearchState;
import com.dayzhud.mod.client.UiSounds;
import com.dayzhud.mod.compat.FirstAidCompat;
import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import com.dayzhud.mod.inventory.grid.Footprint;
import com.dayzhud.mod.inventory.grid.ItemGrid;
import com.dayzhud.mod.inventory.grid.RotateCarriedPacket;
import com.dayzhud.mod.market.TaczMarketCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Extraction-shooter style inventory screen, laid out after Arena Breakout (2.17.0):
 *
 *   column A  EQUIPMENT / HEALTH tabs; big labelled gear boxes either side of the player's
 *             paperdoll; the four weapon boxes; vitals underneath
 *   column B  pockets, gear (Curios), backpack (the bag's own box beside its grid), quick
 *             bar (hotbar 5-9) and the secure container
 *   column C  whatever was opened - a chest, the stash, or a corpse
 *
 * A thin rule along the top with the balance above it, and a nav bar along the bottom
 * (INVENTORY / CRAFTING / SKILLS). There is no panel: the world shows through between the
 * boxes. Slot positions live in TarkovInventoryMenu; the boxed slots (equipment, weapons,
 * the bag) take clicks anywhere in their box.
 *
 * Curios slot names are shown as HOVER TOOLTIPS rather than inline text - with a dozen-plus
 * slots installed, inline labels overlap into unreadable mush.
 */
public class TarkovInventoryScreen extends AbstractContainerScreen<TarkovInventoryMenu> {

    /** Cover drawn over a slot that has not been searched yet. */
    private static final int SEARCH_COVER_BG = 0xE0161616;
    private static final int SEARCH_COVER_LINE = 0x40707070;

    private static final int SLOT_COVER = StyledTheme.SLOT_COVER;
    private static final int SLOT_BORDER = StyledTheme.SLOT_BORDER;
    private static final int HEADER_COLOR = StyledTheme.HEADER_COLOR;
    private static final int TEXT_COLOR = StyledTheme.TEXT_COLOR;
    private static final int LABEL_DIM = StyledTheme.LABEL_DIM;
    private static final int RULE = StyledTheme.HEADER_ACCENT;
    private static final int COLOR_LOW = 0xFFE2A62E;

    /** Ghost icon drawn in an empty loadout slot, so an unrestricted-looking box doesn't
     *  read as "any item goes here" - see drawSlotBoxes. */
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
            FLAT_DECOR_Z = 150, FLAT_PREVIEW_Z = 360, TEXT_Z = 350;
    private static final int FLAT_HOVER_COLOR = StyledTheme.HOVER;

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
    private static final ResourceLocation ICON_STAMINA = rl("icon_sprinting");
    private static final ResourceLocation ICON_TEMPERATURE = rl("icon_thermometer_solid");
    private static final ResourceLocation ICON_PAIN = rl("icon_bolt");

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(DayzHudMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Always the full three-column width, so the layout sits in the same place whether or
        // not something is open (2.17.2; it used to centre just columns A and B, and jumped
        // left when a chest opened). Column C is simply empty with nothing open.
        this.imageWidth = TarkovInventoryMenu.FULL_WIDTH;
        // 356: inside the 360 GUI units a 1080p screen has at GUI scale 3.
        this.imageHeight = TarkovInventoryMenu.LAYOUT_HEIGHT;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    /**
     * init() runs again on every window resize, so the open sound is guarded - without this
     * you'd hear it each time the window changed size while the screen was up.
     */
    private boolean openSoundPlayed = false;

    @Override
    protected void init() {
        super.init();
        // Never let the top go off-screen on a short GUI: if the layout can't fit, its bottom
        // edge is what gets clipped, not the tabs.
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

    // ---- Background: rules, strips, boxes, cells ----------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // No panel and no darkened backdrop: the boxes float over the world.
        graphics.fill(x, y + 16, x + imageWidth, y + 17, RULE);
        graphics.fill(x, y + 338, x + imageWidth, y + 339, RULE);
        drawBalance(graphics);

        // Column B strips.
        strip(graphics, "POCKETS", 206, 22, 62, null, 0);
        strip(graphics, "GEAR", 206, 92, 62, "CURIOS", LABEL_DIM);
        strip(graphics, "BACKPACK", 206, 126, 62, null, 0);
        String summary = backpackSummary();
        if (summary != null) tag(graphics, summary, 408, 126, LABEL_DIM);
        strip(graphics, "QUICK BAR", 206, 270, 62, null, 0);
        strip(graphics, "SECURE", 336, 270, 72, "KEPT ON DEATH", StyledTheme.GOOD);
        for (int i = 0; i < 5; i++) {
            int sx = TarkovInventoryMenu.HOTBAR_X + i * TarkovInventoryMenu.HOTBAR_SPACING;
            small(graphics, String.valueOf(5 + i), leftPos + sx + 16 - font.width("9") / 2f,
                    topPos + TarkovInventoryMenu.HOTBAR_Y, LABEL_DIM, 0);
        }

        // Column C.
        if (menu.isCorpse()) {
            drawCorpseStrips(graphics);
            drawCorpseFigure(graphics);
        } else if (menu.hasContainer()) {
            String name = title.getString().toUpperCase(Locale.ROOT);
            strip(graphics, name, TarkovInventoryMenu.COL_C_X, 22, stripWidth(name), null, 0);
            tag(graphics, menu.openedContainer.getContainerSize() + " SLOTS",
                    TarkovInventoryMenu.CONTAINER_X + TarkovInventoryMenu.CONTAINER_COLS * 18 - 1, 22, LABEL_DIM);
        }

        // Boxed slots (equipment, weapons, the bag): strip on top, empty box.
        for (var e : menu.slotBoxes.entrySet()) {
            Slot slot = menu.slots.get(e.getKey());
            if (!slot.isActive()) continue;
            var b = e.getValue();
            if (!b.label().isEmpty()) strip(graphics, b.label(), b.x(), b.y() - StyledTheme.STRIP_H, b.w(), null, 0);
            StyledTheme.cell(graphics, x + b.x(), y + b.y(), b.w(), b.h());
        }

        // Every other active slot is a grid cell.
        for (var slot : menu.slots) {
            if (!slot.isActive()) continue; // inactive backpack slots shouldn't leave ghost squares
            if (menu.slotBoxes.containsKey(slot.index)) continue;
            StyledTheme.slot(graphics, x + slot.x, y + slot.y);
        }
    }

    /** A label strip in layout coordinates. */
    private void strip(GuiGraphics graphics, String label, int px, int py, int w, String right, int rightColor) {
        StyledTheme.strip(graphics, font, label, leftPos + px, topPos + py, w, right, rightColor);
    }

    private int stripWidth(String label) {
        return Math.max(62, Math.round(font.width(label) * 0.5f) + 8);
    }

    /** Small right-aligned text on its own strip, ending at {@code rightX} (layout coordinates). */
    private void tag(GuiGraphics graphics, String text, int rightX, int py, int color) {
        int w = Math.round(font.width(text) * 0.5f) + 6;
        int x = leftPos + rightX - w, y = topPos + py;
        graphics.fill(x, y, x + w, y + StyledTheme.STRIP_H, StyledTheme.STRIP_BG);
        small(graphics, text, x + 3, y + 2.5f, color, 0);
    }

    /** Rouble balance, top right above the rule, as the reference shows its currency. */
    private void drawBalance(GuiGraphics graphics) {
        if (!com.dayzhud.mod.market.MarketConfig.ENABLED.get()) return;
        String text = com.dayzhud.mod.market.Money.withSymbol(com.dayzhud.mod.market.ClientWallet.get());
        graphics.drawString(font, text, leftPos + imageWidth - font.width(text), topPos + 5, TEXT_COLOR, true);
    }

    /** "GREEN HIKING BAG  14/36": the worn bag's name and how many of its cells are taken. */
    private String backpackSummary() {
        int slots = menu.getActiveBackpackSlots();
        if (slots == 0) return "NO BAG WORN";
        String name = menu.backpackHandler.getBagStack().getHoverName().getString().toUpperCase(Locale.ROOT);
        return fit(name, 96) + "  " + menu.usedBackpackCells() + "/" + slots;
    }

    // ---- Frame --------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        syncEquipmentTab();
        // See-through dark backdrop instead of vanilla's near-opaque one.
        StyledTheme.backdrop(graphics, width, height);

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
        drawTabs(graphics, mouseX, mouseY);
        if (healthTabShown()) {
            health.render(graphics, mouseX, mouseY);
        } else {
            drawPaperdoll(graphics);
        }
        drawSlotBoxes(graphics, mouseX, mouseY);
        drawVitals(graphics);
        drawNav(graphics, mouseX, mouseY);
        drawBackpackScrollbar(graphics);
        drawCorpseScrollbar(graphics);
        drawGridIcons(graphics);
        drawGridPlacementPreview(graphics, mouseX, mouseY);
        if (bigCarried) drawCarriedBig(graphics, carried, mouseX, mouseY);

        renderTooltip(graphics, mouseX, mouseY);
        drawCurioHoverTooltip(graphics);
        drawWeaponHoverTooltip(graphics, mouseX, mouseY);
        if (healthTabShown()) health.renderTooltip(graphics, mouseX, mouseY);
        drawVitalTooltip(graphics, mouseX, mouseY);
    }

    // ---- Equipment / Health tabs (2.16.0) -----------------------------------------------------

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
        tabAvailable = minecraft != null && FirstAidCompat.limbs(minecraft.player).isPresent();
        menu.equipmentShown = !healthTabShown();
        if (!healthTabShown()) health.cancel();
    }

    private static final int TAB_Y = 22, TAB_W = 94, TAB_H = 12, TAB_HEALTH_X = 98;

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean onHealth = healthTabShown();
        StyledTheme.tab(graphics, font, "EQUIPMENT", leftPos, topPos + TAB_Y, TAB_W, TAB_H, !onHealth,
                isOver(mouseX, mouseY, 0, TAB_Y, TAB_W, TAB_H));
        if (tabAvailable) {
            StyledTheme.tab(graphics, font, "HEALTH", leftPos + TAB_HEALTH_X, topPos + TAB_Y, TAB_W, TAB_H, onHealth,
                    isOver(mouseX, mouseY, TAB_HEALTH_X, TAB_Y, TAB_W, TAB_H));
        }
    }

    /** Whether the mouse is over a rectangle given in layout coordinates. */
    private boolean isOver(double mouseX, double mouseY, int px, int py, int w, int h) {
        double x = mouseX - leftPos, y = mouseY - topPos;
        return x >= px && x < px + w && y >= py && y < py + h;
    }

    /** Half-size text, the layout's small labels. */
    void caption(GuiGraphics graphics, String text, float x, float y, int color) {
        StyledTheme.small(graphics, font, text, x, y, color);
    }

    void captionRight(GuiGraphics graphics, String text, float rightX, float y, int color) {
        caption(graphics, text, rightX - font.width(text) / 2f, y, color);
    }

    /** Half-size text at depth {@code z} (over item renders when z is TEXT_Z). */
    private void small(GuiGraphics graphics, String text, float x, float y, int color, float z) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, z);
        graphics.pose().scale(0.5f, 0.5f, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** {@code text} cut down until it fits {@code maxWidth} GUI units at half size. */
    private String fit(String text, int maxWidth) {
        String s = text;
        while (s.length() > 1 && font.width(s) * 0.5f > maxWidth) s = s.substring(0, s.length() - 1);
        return s;
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

    /** The player, between the two columns of gear boxes, feet just above the weapon strips. */
    private void drawPaperdoll(GuiGraphics graphics) {
        LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer == null) return;
        // TUNING NOTE: this helper turns the model by roughly (angleXComponent * 20)
        // degrees off front-facing, so small values give small turns. Flip the sign if it
        // leans the wrong way.
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, leftPos + 96, topPos + 190, 70,
                -0.8f, 0.0f, localPlayer);
    }

    // ---- Boxed slots: equipment, weapons, the bag ---------------------------------------------

    /**
     * Each box: the item rendered big (its model for guns, bags and magazines; its own icon
     * scaled up otherwise) on an opaque panel that hides vanilla's small icon, its name
     * top-left, and ammo / durability / count bottom-right. Weapon boxes add the gun's
     * calibre under the name and their key in the top-right corner; an empty weapon box shows
     * the ghost of what goes there.
     */
    private void drawSlotBoxes(GuiGraphics graphics, int mouseX, int mouseY) {
        for (var e : menu.slotBoxes.entrySet()) {
            Slot slot = menu.slots.get(e.getKey());
            if (!slot.isActive()) continue;
            var b = e.getValue();
            int bx = leftPos + b.x(), by = topPos + b.y(), bw = b.w(), bh = b.h();
            boolean weapon = b.key() > 0;
            ItemStack stack = slot.getItem();

            if (stack.isEmpty()) {
                if (weapon) {
                    drawFlatWeaponIcon(graphics, WeaponSlots.ORDER[b.key() - 1], bx + 6, by + 6, bw - 12, bh - 12,
                            WEAPON_GHOST_COLOR);
                }
            } else {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, FLAT_PANEL_Z);
                graphics.fill(bx, by, bx + bw, by + bh, SLOT_COVER);
                graphics.renderOutline(bx, by, bw, bh, SLOT_BORDER);
                graphics.pose().popPose();

                // Room for the name line above and the value line below.
                int ix = bx + 3, iy = by + 7, iw = bw - 6, ih = bh - 13;
                float maxScale = weapon && TaczFlatGunRenderer.canRender(stack) ? TaczFlatGunRenderer.gridScale(stack) : -1f;
                boolean drawn = FlatItems.canRender(stack)
                        && FlatItems.render(graphics, stack, ix, iy, iw, ih, FLAT_GUN_Z, false, maxScale);
                if (!drawn) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0, 0, FLAT_GUN_Z - 150);   // renderItem adds its own 150
                    renderTiltedItem(graphics, stack, ix, iy, iw, ih);
                    graphics.pose().popPose();
                }

                small(graphics, fit(stack.getHoverName().getString().toUpperCase(Locale.ROOT), bw - 4),
                        bx + 2, by + 2, StyledTheme.TEXT_COLOR, TEXT_Z);
                var stats = gunStats(stack);
                if (stats != null && stats.ammoId() != null) {
                    small(graphics, calibre(stats.ammoId()), bx + 2, by + 7, LABEL_DIM, TEXT_Z);
                }
                String value = valueText(stack, stats);
                if (value != null) {
                    small(graphics, value, bx + bw - 2 - font.width(value) * 0.5f, by + bh - 6,
                            StyledTheme.TEXT_COLOR, TEXT_Z);
                }
            }

            if (weapon) {
                int kx = bx + bw - 8, ky = by + 1;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, TEXT_Z);
                graphics.renderOutline(kx, ky, 7, 7, 0xFF4A4E52);
                graphics.pose().popPose();
                small(graphics, String.valueOf(b.key()), kx + 2, ky + 1.5f, HEADER_COLOR, TEXT_Z);
            } else if (stack.isEmpty() && b.label().isEmpty()) {
                small(graphics, "NO BAG", bx + 2, by + 2, LABEL_DIM, 0);
            }

            // The whole box takes clicks (isHovering), so the whole box lights up.
            if (mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, FLAT_HOVER_Z);
                graphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, FLAT_HOVER_COLOR);
                graphics.pose().popPose();
            }
        }
    }

    /** Gun stats are looked up through every TACZ gun, so they're kept per gun id. */
    private static final Map<ResourceLocation, TaczMarketCompat.GunStats> GUN_STATS = new HashMap<>();

    private static TaczMarketCompat.GunStats gunStats(ItemStack stack) {
        ResourceLocation id = TaczMarketCompat.gunIdOf(stack).orElse(null);
        if (id == null) return null;
        return GUN_STATS.computeIfAbsent(id, k -> TaczMarketCompat.statsOf(stack));
    }

    /** "tacz:556x45" -> "556X45". */
    private static String calibre(String ammoId) {
        int colon = ammoId.indexOf(':');
        return (colon < 0 ? ammoId : ammoId.substring(colon + 1)).replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    /**
     * Bottom-right figure: a gun's rounds loaded out of its magazine size, durability left out
     * of the maximum, or the stack size - whichever applies.
     */
    private static String valueText(ItemStack stack, TaczMarketCompat.GunStats stats) {
        if (stats != null) {
            CompoundTag tag = stack.getTag();
            int ammo = tag == null ? 0 : tag.getInt("GunCurrentAmmoCount");
            return stats.magazine() > 0 ? ammo + "/" + stats.magazine() : String.valueOf(ammo);
        }
        if (stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            return (max - stack.getDamageValue()) + "/" + max;
        }
        return stack.getCount() > 1 ? String.valueOf(stack.getCount()) : null;
    }

    /**
     * The flat category icon (see WEAPON_ICON_RIFLE/PISTOL/KNIFE's doc), used for the empty
     * ghost. Scaled to CONTAIN within ({@code availW}, {@code availH}) preserving the icon's
     * own aspect ratio and centred in that space.
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
        blitTinted(graphics, icon, x, y, drawW, drawH, tint);
    }

    private static void blitTinted(GuiGraphics graphics, ResourceLocation icon, int x, int y, int w, int h, int tint) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(
                ((tint >> 16) & 0xFF) / 255f,
                ((tint >> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f,
                ((tint >> 24) & 0xFF) / 255f);
        graphics.blit(icon, x, y, 0, 0, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /**
     * Names the hovered Curios slot. Only shown for EMPTY slots - a slot holding an item
     * already gets that item's own tooltip from vanilla, and stacking ours on top would
     * double up.
     */
    private void drawCurioHoverTooltip(GuiGraphics graphics) {
        if (hoveredSlot == null || hoveredSlot.hasItem()) return;
        for (var info : menu.curioSlotInfos) {
            if (info.x() == hoveredSlot.x && info.y() == hoveredSlot.y) {
                graphics.renderTooltip(font, Component.literal(prettify(info.identifier())), lastMouseX, lastMouseY);
                return;
            }
        }
    }

    private String prettify(String identifier) {
        String cleaned = identifier.replace('_', ' ').trim();
        if (cleaned.isEmpty()) return identifier;
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    /** Explains what an empty loadout slot accepts. Non-empty slots get vanilla's own
     *  item tooltip automatically, so this only has to handle the empty case. */
    private void drawWeaponHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot == null || hoveredSlot.hasItem()) return;
        for (int i = 0; i < WeaponSlots.ORDER.length; i++) {
            if (menu.weaponSlots[i] != hoveredSlot) continue;
            WeaponSlots type = WeaponSlots.ORDER[i];
            String accepted = switch (type) {
                case PRIMARY, SECONDARY -> "Rifles, SMGs, shotguns, snipers, MGs, launchers";
                case HOLSTER -> "Pistols";
                case SHEATH -> "Melee weapons";
            };
            graphics.renderTooltip(font, Component.literal(type.label + " \u00a77- " + accepted), mouseX, mouseY);
            return;
        }
    }

    // ---- Vitals, under the weapons ------------------------------------------------------------

    /**
     * Two rows of icon + value, coloured like the reference: health (green), energy (amber),
     * hydration (blue), carried weight; then stamina, temperature, bleeding and pain. A value
     * turns red when it's low.
     */
    private void drawVitals(GuiGraphics graphics) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;
        int x = leftPos, y = topPos + 304, y2 = topPos + 318;
        graphics.fill(x, topPos + 298, x + 192, topPos + 299, RULE);

        // First Aid, when present, owns the real health - vanilla health is only a lossy
        // summary of its limb model and drifts out of step with it. See FirstAidCompat.
        String health;
        float health01;
        var limbs = FirstAidCompat.limbs(player).orElse(null);
        if (limbs != null && !limbs.isEmpty()) {
            float cur = 0, max = 0;
            for (var l : limbs) {
                cur += Math.max(0f, l.current());
                max += l.max();
            }
            health = Math.round(cur) + "/" + Math.round(max);
            health01 = max <= 0 ? 0 : cur / max;
        } else {
            health = Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth());
            health01 = player.getHealth() / Math.max(1f, player.getMaxHealth());
        }
        int food = player.getFoodData().getFoodLevel();
        float water01 = ThirstWasTakenCompat.getThirst01(player)
                .orElseGet(() -> player.getFoodData().getSaturationLevel() / 20f);

        // Each icon keeps its own colour even when the value is "NONE", so the eight read as
        // eight distinct things (2.17.2: the pain bolt went the same dim grey as its "NONE"
        // and vanished, and bleeding borrowed the water drop). Hover any of them for its name.
        stat(graphics, 0, ICON_HEART, x, y, health, low(health01, StyledTheme.GOOD), StyledTheme.GOOD,
                "Health", FirstAidCompat.isModLoaded() ? "All limbs together (First Aid)" : null);
        stat(graphics, 1, ICON_FOOD, x + 48, y, food + "/20", low(food / 20f, StyledTheme.WARN), StyledTheme.WARN,
                "Energy", "Food level");
        stat(graphics, 2, ICON_WATER, x + 96, y, Math.round(water01 * 20) + "/20", low(water01, StyledTheme.INFO),
                StyledTheme.INFO, "Hydration", null);
        if (com.dayzhud.mod.weight.ClientWeight.known()) {
            int level = com.dayzhud.mod.weight.ClientWeight.level();
            int color = level == 0 ? TEXT_COLOR : level == 1 ? StyledTheme.WARN : level == 2 ? COLOR_LOW : StyledTheme.BAD;
            stat(graphics, 3, ICON_WEIGHT, x + 144, y,
                    String.format(Locale.ROOT, "%.1fKG", com.dayzhud.mod.weight.ClientWeight.kg()), color, color,
                    "Carried weight", String.format(Locale.ROOT, "Slower over %.0f kg, no sprinting over %.0f kg, crawling over %.0f kg",
                            com.dayzhud.mod.weight.ClientWeight.overweight(), com.dayzhud.mod.weight.ClientWeight.heavy(),
                            com.dayzhud.mod.weight.ClientWeight.critical()));
        }

        float stamina = com.dayzhud.mod.client.VitalsTracker.getStamina01();
        stat(graphics, 4, ICON_STAMINA, x, y2, Math.round(stamina * 100) + "%", low(stamina, TEXT_COLOR), TEXT_COLOR,
                "Stamina", null);

        float t = com.dayzhud.mod.client.VitalsTracker.getTemperature01();
        // The HUD's own Celsius reading - short enough for the column ("HEATSTROKE" ran into
        // the bleeding value next to it).
        String temp = com.dayzhud.mod.client.DayzHudOverlay.tempCelsius(t) + DEGREES_C;
        int tempColor = t < 0.25f ? StyledTheme.INFO : t < 0.4f ? 0xFF9CC8EE : t <= 0.6f ? TEXT_COLOR
                : t <= 0.75f ? COLOR_LOW : StyledTheme.BAD;
        String tempState = t < 0.25f ? "Freezing" : t < 0.4f ? "Cold" : t <= 0.6f ? "Normal" : t <= 0.75f ? "Hot" : "Heatstroke";
        stat(graphics, 5, ICON_TEMPERATURE, x + 48, y2, temp, tempColor, tempColor, "Body temperature", tempState);

        int heavyWounds = com.dayzhud.mod.injury.ClientInjuries.heavy();
        int lightWounds = com.dayzhud.mod.injury.ClientInjuries.light();
        String bleed = heavyWounds > 0 ? "HEAVY" + (heavyWounds > 1 ? " x" + heavyWounds : "")
                : lightWounds > 0 ? "LIGHT" + (lightWounds > 1 ? " x" + lightWounds : "") : "NONE";
        stat(graphics, 6, ICON_BLOOD, x + 96, y2, bleed, bleed.equals("NONE") ? LABEL_DIM
                        : heavyWounds > 0 ? 0xFFB01E1E : StyledTheme.BAD, StyledTheme.BAD,
                "Bleeding", "Light wounds clot by themselves; heavy ones need a first aid kit");

        int pain = com.dayzhud.mod.injury.ClientInjuries.pain();
        int relief = com.dayzhud.mod.injury.ClientInjuries.reliefSeconds();
        int morphine = FirstAidCompat.morphineTicks(player);
        String painText;
        int painColor;
        if (morphine > 0) {
            painText = "MORPHINE";
            painColor = StyledTheme.GOOD;
        } else if (pain >= com.dayzhud.mod.injury.InjurySystem.PAIN_MILD) {
            boolean severe = pain >= com.dayzhud.mod.injury.InjurySystem.PAIN_SEVERE;
            painText = relief > 0 ? relief + "S" : severe ? "SEVERE" : "PAIN";
            painColor = relief > 0 ? StyledTheme.GOOD : severe ? StyledTheme.BAD : COLOR_LOW;
        } else {
            painText = "NONE";
            painColor = LABEL_DIM;
        }
        stat(graphics, 7, ICON_PAIN, x + 144, y2, painText, painColor, COLOR_LOW, "Pain",
                relief > 0 ? "Painkiller working: " + relief + "s left" : "Slows stamina recovery; painkillers relieve it");
    }

    /** "\u00b0C", built from the code point so the source file's encoding can't mangle it. */
    private static final String DEGREES_C = (char) 0xB0 + "C";
    private static final ResourceLocation ICON_WEIGHT = rl("icon_weight");
    private static final ResourceLocation ICON_BLOOD = rl("icon_blood");

    /** Name and detail of each vital, as last drawn, for the hover tooltips. */
    private final String[][] vitalTips = new String[8][];

    private static int low(float value01, int normal) {
        return value01 <= 0.25f ? StyledTheme.BAD : normal;
    }

    private void stat(GuiGraphics graphics, int index, ResourceLocation icon, int x, int y, String value, int color,
                      int iconColor, String name, String detail) {
        blitTinted(graphics, icon, x, y, 8, 8, iconColor | 0xFF000000);
        value(graphics, value, x + 11, y, color);
        vitalTips[index] = new String[]{name, detail};
    }

    /** Names the vital under the cursor: two rows of four 48-wide cells under the weapons. */
    private void drawVitalTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int col = (mouseX - leftPos) / 48, row = mouseY - topPos < 316 ? 0 : 1;
        if (mouseX < leftPos || col > 3 || mouseY < topPos + 302 || mouseY >= topPos + 330) return;
        String[] tip = vitalTips[row * 4 + col];
        if (tip == null) return;
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.literal(tip[0]));
        if (tip[1] != null) lines.add(Component.literal("\u00a77" + tip[1]));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** A vital's value: 0.75 size, shadowed so it reads over the world. */
    private void value(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + 1, 0);
        graphics.pose().scale(0.75f, 0.75f, 1f);
        graphics.drawString(font, text, 0, 0, color, true);
        graphics.pose().popPose();
    }

    // ---- Bottom nav: INVENTORY / CRAFTING / SKILLS ----------------------------------------------

    private static final String[] NAV = {"INVENTORY", "CRAFTING", "SKILLS"};
    private static final int NAV_W = 64, NAV_H = 15, NAV_Y = 340;

    private int navX(int i) {
        return (imageWidth - NAV.length * NAV_W) / 2 + i * NAV_W;
    }

    private void drawNav(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < NAV.length; i++) {
            boolean active = i == 0;
            boolean hovered = isOver(mouseX, mouseY, navX(i), NAV_Y, NAV_W - 2, NAV_H);
            int x = leftPos + navX(i), y = topPos + NAV_Y;
            if (active) {
                graphics.fill(x, y, x + NAV_W - 2, y + NAV_H, 0xC82C3034);
                graphics.renderOutline(x, y, NAV_W - 2, NAV_H, 0xFF464B50);
            } else if (hovered) {
                graphics.fill(x, y, x + NAV_W - 2, y + NAV_H, StyledTheme.BUTTON_BG);
            }
            float tw = font.width(NAV[i]) * 0.75f;
            StyledTheme.scaled(graphics, font, NAV[i], x + (NAV_W - 2 - tw) / 2f, y + 5, 0.75f,
                    active ? 0xFFFFFFFF : hovered ? TEXT_COLOR : HEADER_COLOR);
        }
    }

    /** Nav clicks; true when one was handled. */
    private boolean navClicked(double mouseX, double mouseY) {
        if (isOver(mouseX, mouseY, navX(1), NAV_Y, NAV_W - 2, NAV_H)) {
            NetworkHandler.CHANNEL.sendToServer(new OpenCraftingPacket());
            return true;
        }
        if (isOver(mouseX, mouseY, navX(2), NAV_Y, NAV_W - 2, NAV_H)) {
            // The skills screen has no slots, so it isn't a container screen. Close this
            // menu properly first - just swapping the screen would leave the server holding
            // an open container for a screen that no longer exists.
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.closeContainer();
                minecraft.setScreen(new com.dayzhud.mod.client.SkillsScreen());
            }
            return true;
        }
        return isOver(mouseX, mouseY, navX(0), NAV_Y, NAV_W - 2, NAV_H); // already here
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
     * A boxed slot's real Slot is a 16x16 square in the middle of its big box (vanilla has no
     * variable-size Slot). Vanilla routes every slot hover and click test through this method
     * with the slot's position and 16x16, so a match on a boxed slot's position is answered for
     * its whole box instead - click anywhere in it.
     */
    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
        if (w == 16 && h == 16) {
            for (var e : menu.slotBoxes.entrySet()) {
                Slot s = menu.slots.get(e.getKey());
                if (s.x == x && s.y == y) {
                    var b = e.getValue();
                    return super.isHovering(b.x(), b.y(), b.w(), b.h(), mouseX, mouseY);
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
        if (button == 0) {
            boolean toEquipment = isOver(mouseX, mouseY, 0, TAB_Y, TAB_W, TAB_H);
            boolean toHealth = tabAvailable && isOver(mouseX, mouseY, TAB_HEALTH_X, TAB_Y, TAB_W, TAB_H);
            if (toEquipment || toHealth) {
                if (healthTab != toHealth) UiSounds.inventoryMove();
                healthTab = toHealth;
                syncEquipmentTab();
                return true;
            }
            if (navClicked(mouseX, mouseY)) return true;
        }
        if (healthTabShown() && health.mouseClicked(mouseX, mouseY, button)) return true;
        if ((button == 0 || button == 1) && ItemGrid.isMultiCell(menu.getCarried())
                && hoveredSlot != null && menu.isGridSlot(hoveredSlot.index)) {
            slotClicked(hoveredSlot, hoveredSlot.index, button, ClickType.PICKUP);
            swallowRelease = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
     * (vanilla's own highlight is under the panel now). Returns false if the model draw
     * failed, so the caller can fall back.
     */
    private boolean drawFlatGunBox(GuiGraphics graphics, ItemStack stack, int x, int y, int w, int h, int inset,
                                   boolean rotated, float maxScale) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, FLAT_PANEL_Z);
        graphics.fill(x, y, x + w, y + h, SLOT_COVER);
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
     * contain-vs-stretch fit are this mod's own doing - see renderTiltedItem. Items two or
     * more cells wide get their name top-left, and guns their rounds bottom-right (2.17.0).
     */
    private void drawBigGridIcon(GuiGraphics graphics, Slot slot, Footprint footprint) {
        ItemStack stack = slot.getItem();
        int x = leftPos + slot.x;
        int y = topPos + slot.y;
        int w = footprint.width() * 18 - 2;
        int h = footprint.height() * 18 - 2;

        boolean flat = FlatItems.canRender(stack)
                && drawFlatGunBox(graphics, stack, x - 1, y - 1, w + 2, h + 2, TaczFlatGunRenderer.GRID_INSET,
                        ItemGrid.isRotated(stack), -1f);
        if (!flat) {
            // Anything else multi-cell (CAPS armor, magazines, ...): same panel, then the item's
            // own inventory render scaled uniformly into the footprint. Before 2.12.6 this drew
            // at vanilla's depth with no panel, so the small vanilla icon sat on top of it.
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, FLAT_PANEL_Z);
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_COVER);
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, SLOT_BORDER);
            graphics.pose().popPose();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, FLAT_GUN_Z - 150);   // renderItem adds its own 150
            renderTiltedItem(graphics, stack, x, y, w, h);
            graphics.pose().translate(0, 0, FLAT_DECOR_Z);
            graphics.renderItemDecorations(font, stack, x + w - 16, y + h - 16);
            graphics.pose().popPose();
        }

        if (w >= 34) {
            small(graphics, fit(stack.getHoverName().getString().toUpperCase(Locale.ROOT), w - 2),
                    x + 1, y + 1, StyledTheme.TEXT_COLOR, TEXT_Z);
        }
        var stats = gunStats(stack);
        if (stats != null) {
            String value = valueText(stack, stats);
            small(graphics, value, x + w - 1 - font.width(value) * 0.5f, y + h - 5, StyledTheme.TEXT_COLOR, TEXT_Z);
        }
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

    // ---- Corpse (column C) ----------------------------------------------------------------------

    private void drawCorpseStrips(GuiGraphics graphics) {
        int x = TarkovInventoryMenu.CORPSE_ARMOR_X - 1;
        String name = title.getString().toUpperCase(Locale.ROOT);
        strip(graphics, name, TarkovInventoryMenu.COL_C_X, 22, stripWidth(name), null, 0);
        strip(graphics, "GEAR", x, TarkovInventoryMenu.CORPSE_GEAR_Y - 11, 62, null, 0);
        strip(graphics, "INVENTORY", x, TarkovInventoryMenu.CORPSE_INV_Y - 11, 62, null, 0);
        strip(graphics, "HOTBAR", x, TarkovInventoryMenu.CORPSE_HOTBAR_Y - 11, 62, null, 0);
        if (menu.corpseHasBackpack()) {
            strip(graphics, "BACKPACK", x, TarkovInventoryMenu.CORPSE_BAG_Y - 11, 62, null, 0);
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

    /** Scrollbar for the corpse loot list. */
    private void drawCorpseScrollbar(GuiGraphics graphics) {
        if (!menu.isCorpse() || menu.corpseLootView == null || !menu.corpseLootView.isScrollable()) return;
        var view = menu.corpseLootView;

        int trackX = leftPos + TarkovInventoryMenu.CORPSE_INV_X + TarkovInventoryMenu.CORPSE_LOOT_COLS * 18 + 2;
        int trackTop = topPos + TarkovInventoryMenu.CORPSE_BAG_Y;
        int trackBottom = trackTop + TarkovInventoryMenu.CORPSE_BAG_VISIBLE_ROWS * 18;
        int trackHeight = trackBottom - trackTop;

        graphics.fill(trackX, trackTop, trackX + 3, trackBottom, StyledTheme.SLOT_BG);
        int totalRows = Math.max(1, view.totalRows());
        int thumbHeight = Math.max(10,
                trackHeight * TarkovInventoryMenu.CORPSE_BAG_VISIBLE_ROWS / totalRows);
        int maxScroll = Math.max(1, view.maxScrollRow());
        int thumbY = trackTop + (trackHeight - thumbHeight) * view.getScrollRow() / maxScroll;
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, HEADER_COLOR);
    }

    /** Thin scrollbar between the bag's box and its grid, only when the bag overflows. */
    private void drawBackpackScrollbar(GuiGraphics graphics) {
        var view = menu.backpackView;
        if (!view.isScrollable()) return;

        int trackX = leftPos + TarkovInventoryMenu.BACKPACK_X - 4;
        int trackTop = topPos + TarkovInventoryMenu.BACKPACK_Y - 1;
        int trackHeight = TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS * 18;

        graphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, StyledTheme.SLOT_BG);

        int totalRows = Math.max(1, view.totalRows());
        int thumbHeight = Math.max(8,
                trackHeight * TarkovInventoryMenu.BACKPACK_VISIBLE_ROWS / totalRows);
        int maxScroll = Math.max(1, view.maxScrollRow());
        int thumbY = trackTop + (trackHeight - thumbHeight) * view.getScrollRow() / maxScroll;

        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, HEADER_COLOR);
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
        if (view.isScrollable() && isOver(mouseX, mouseY, 206, 126, 202, 140)) {
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

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Strips and labels are drawn in renderBg / render.
    }
}
