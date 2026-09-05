package com.dayzhud.mod.market;

import com.dayzhud.mod.inventory.NetworkHandler;
import com.dayzhud.mod.inventory.StyledTheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The trader. BUY and SELL tabs, a category sidebar that expands into sections, a scrolling
 * stock list, and a details panel.
 *
 * THE PANEL IS NOT THE MENU. Slot x/y are final in 1.20.1, so a container screen that resizes
 * with the window cannot move its slots the way it moves its artwork. Everything here is
 * therefore drawn in ABSOLUTE screen coordinates from {@link #panelX}/{@link #panelY}, and
 * leftPos/topPos are set by hand in {@link #init()} to drop the menu's fixed 162x106 slot
 * block where the layout wants it. Anything that mixes the two systems up will look right at
 * one window size and wrong at every other, so: panel geometry absolute, slots via
 * leftPos/topPos, and never the two in one expression.
 *
 * Row counts are computed from the space available rather than fixed, which is what lets a
 * bigger window show more stock instead of the same five rows with more padding.
 */
public class MarketScreen extends AbstractContainerScreen<MarketMenu> {

    private static final int MARGIN = 6;
    private static final int MAX_W = 640;
    private static final int MAX_H = 400;

    private static final int ROW_H = 24;
    private static final int SIDEBAR_ROW_H = 14;
    private static final int SIDEBAR_W_MAX = 104;
    private static final int SIDEBAR_W_MIN = 62;
    private static final int DETAIL_W_MAX = 152;
    private static final int DETAIL_W_MIN = 94;
    private static final int LIST_W_MIN = 92;
    private static final int TAB_W = 78;
    private static final int TAB_H = 17;

    private static final int[] QUANTITIES = {1, 5, 16, 64};

    // ---- computed layout ---------------------------------------------------
    private int panelX, panelY, panelW, panelH;
    private int contentY, contentH, tabY;
    private int sidebarX, sidebarW, listX, listW, scrollbarX, detailX, detailW;
    private int visibleRows, sidebarRowsVisible;
    private int groupX, groupY;
    private boolean compact;

    // ---- state -------------------------------------------------------------
    private EditBox search;
    private boolean sellTab;
    private String category = "";
    private String sub = "";
    private int scroll;
    private int sidebarScroll;
    private int detailScroll;
    private int selected = -1;
    private int quantity = 1;
    private List<Integer> filtered = new ArrayList<>();
    private Confirm confirm;

    private record Confirm(String title, String line, String cost, Runnable onConfirm) {}

    /** A sidebar line: a category, or a section nested under the open one. */
    private record Row(String category, String sub, boolean nested) {}

    public MarketScreen(MarketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        layout();

        search = new EditBox(this.font, sidebarX + 4, contentY + 4, sidebarW - 8, 12,
                Component.translatable("gui.dayzhud.market.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(StyledTheme.TEXT_COLOR);
        search.setResponder(s -> {
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(search);
        menu.sellTabActive = sellTab;
        rebuild();
    }

    private void layout() {
        panelW = Math.min(this.width - MARGIN * 2, MAX_W);
        panelH = Math.min(this.height - MARGIN * 2, MAX_H);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        // The slot block is fixed size, so it is placed first and the content region takes
        // whatever is left above it.
        groupX = panelX + (panelW - MarketMenu.GROUP_W) / 2;
        groupY = panelY + panelH - 10 - MarketMenu.GROUP_H;
        this.leftPos = groupX - MarketMenu.INV_X;
        this.topPos = groupY - MarketMenu.TRAY_Y;
        this.imageWidth = panelW;
        this.imageHeight = panelH;

        // A short window gets a compressed header rather than losing its only list row to
        // decoration. Minecraft guarantees no less than 320x240 GUI units, and at that size
        // the full header leaves room for exactly one row.
        compact = panelH < 300;
        int headerH = compact ? 40 : 54;
        tabY = panelY + headerH - 19;
        contentY = panelY + headerH;
        // The tray row only exists on the SELL tab, so on BUY its band is dead space between
        // the stock list and the inventory - give it to the list instead of leaving a gap.
        int contentBottom = groupY - 16
                + (sellTab ? 0 : MarketMenu.INV_Y - MarketMenu.TRAY_Y);
        contentH = Math.max(ROW_H, contentBottom - contentY);

        // Columns are proportional with floors, then the side columns give ground to the list
        // if it is still too narrow. Fixed widths looked fine at 640 and collapsed the stock
        // list to six pixels at 320 - verified by walking the arithmetic across window sizes
        // rather than by eye, because a compiler cannot catch a negative column.
        sidebarW = clamp((int) (panelW * 0.20), SIDEBAR_W_MIN, SIDEBAR_W_MAX);
        detailW = clamp((int) (panelW * 0.28), DETAIL_W_MIN, DETAIL_W_MAX);
        for (int guard = 0; guard < 8 && listWidthFor(sidebarW, detailW) < LIST_W_MIN; guard++) {
            if (detailW > DETAIL_W_MIN) detailW -= Math.min(8, detailW - DETAIL_W_MIN);
            else if (sidebarW > SIDEBAR_W_MIN) sidebarW -= Math.min(8, sidebarW - SIDEBAR_W_MIN);
            else break;
        }

        sidebarX = panelX + 10;
        detailX = panelX + panelW - 10 - detailW;
        listX = sidebarX + sidebarW + 10;
        scrollbarX = detailX - 12;
        listW = Math.max(24, scrollbarX - 4 - listX);

        visibleRows = Math.max(1, contentH / ROW_H);
        contentH = visibleRows * ROW_H;          // no ragged remainder under the last row
        sidebarRowsVisible = Math.max(1, (contentH - 22) / SIDEBAR_ROW_H);
    }

    private int listWidthFor(int sidebar, int detail) {
        return (panelX + panelW - 10 - detail - 12) - 4 - (panelX + 10 + sidebar + 10);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        String query = search == null ? "" : search.getValue();
        super.resize(mc, w, h);
        if (search != null) search.setValue(query);
    }

    private void rebuild() {
        filtered = new ArrayList<>();
        List<MarketOffer> offers = ClientMarketState.offers();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < offers.size(); i++) {
            MarketOffer offer = offers.get(i);
            if (!category.isEmpty() && !category.equals(offer.category())) continue;
            if (!sub.isEmpty() && !sub.equals(offer.sub())) continue;
            if (!query.isEmpty()) {
                String name = offer.prototype().getHoverName().getString().toLowerCase(Locale.ROOT);
                if (!name.contains(query)) continue;
            }
            filtered.add(i);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - visibleRows)));
        if (!filtered.contains(selected)) selected = filtered.isEmpty() ? -1 : filtered.get(0);
        detailScroll = 0;
    }

    /**
     * Sidebar rows, with the selected category expanded into its sections.
     *
     * Nested in the column rather than added as a second strip along the top: a strip has a
     * fixed width and would hit exactly the overflow that hid FIREARMS twice, while a column
     * only gets longer and this one scrolls.
     */
    private List<Row> sidebarRows() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MarketOffer o : ClientMarketState.offers()) seen.add(o.category());

        List<Row> rows = new ArrayList<>();
        rows.add(new Row("", "", false));
        for (String cat : MarketCatalog.sortCategories(seen)) {
            rows.add(new Row(cat, "", false));
            if (!cat.equals(category)) continue;
            List<String> subs = MarketCatalog.subcategories(ClientMarketState.offers(), cat);
            // One section is not a section - "ARMOR > helmets" alone is just noise.
            if (subs.size() < 2) continue;
            rows.add(new Row(cat, "", true));
            for (String s : subs) rows.add(new Row(cat, s, true));
        }
        return rows;
    }

    // ---- background --------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        StyledTheme.panel(g, panelX, panelY, panelW, panelH);
        if (!sellTab) {
            StyledTheme.zone(g, sidebarX, contentY, sidebarX + sidebarW, contentY + contentH);
            StyledTheme.zone(g, listX, contentY, listX + listW, contentY + contentH);
        } else {
            StyledTheme.zone(g, sidebarX, contentY, scrollbarX - 4, contentY + contentH);
        }
        StyledTheme.zone(g, detailX, contentY, detailX + detailW, contentY + contentH);

        if (sellTab) {
            for (int i = 0; i < MarketMenu.SELL_SLOTS; i++) {
                StyledTheme.slot(g, leftPos + MarketMenu.TRAY_X + i * 18, topPos + MarketMenu.TRAY_Y);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                StyledTheme.slot(g, leftPos + MarketMenu.INV_X + col * 18,
                        topPos + MarketMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            StyledTheme.slot(g, leftPos + MarketMenu.INV_X + col * 18, topPos + MarketMenu.HOTBAR_Y);
        }
    }

    /** Labels are drawn absolutely in {@link #render}; this stays empty on purpose. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    /**
     * Clicking anywhere in the panel is inside the window, not a drop.
     *
     * Vanilla measures this from leftPos/topPos plus imageWidth/imageHeight, and leftPos here
     * points at the slot block rather than the panel corner - so without this, clicking the
     * stock list while holding an item would throw it on the floor.
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return mouseX < panelX || mouseY < panelY
                || mouseX >= panelX + panelW || mouseY >= panelY + panelH;
    }

    // ---- render ------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        menu.sellTabActive = sellTab;
        super.render(g, mouseX, mouseY, partialTick);

        drawHeader(g);
        drawTabs(g, mouseX, mouseY);
        if (sellTab) {
            drawSellList(g);
            drawSellDetails(g, mouseX, mouseY);
        } else {
            drawSidebar(g, mouseX, mouseY);
            drawOffers(g, mouseX, mouseY);
            drawBuyDetails(g, mouseX, mouseY);
        }
        drawSlotLabels(g);

        if (confirm == null) {
            renderTooltip(g, mouseX, mouseY);
            drawOfferTooltip(g, mouseX, mouseY);
        } else {
            drawConfirm(g, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphics g) {
        float titleScale = compact ? 1.0f : 1.6f;
        g.pose().pushPose();
        g.pose().translate(panelX + 12, panelY + (compact ? 8 : 10), 0);
        g.pose().scale(titleScale, titleScale, 1f);
        g.drawString(font, "MARKET", 0, 0, StyledTheme.TEXT_COLOR, false);
        g.pose().popPose();
        if (!compact) {
            StyledTheme.caption(g, font, "BUY, SELL AND TRADE EQUIPMENT", panelX + 13, panelY + 27);
        }

        String text = Money.withSymbol(ClientWallet.get());
        int right = panelX + panelW - 12;
        StyledTheme.caption(g, font, "BALANCE", right - 24, panelY + 8);
        g.pose().pushPose();
        g.pose().translate(right, panelY + (compact ? 14 : 17), 0);
        float moneyScale = compact ? 1.0f : 1.3f;
        g.pose().scale(moneyScale, moneyScale, 1f);
        g.drawString(font, text, -font.width(text), 0, 0xFFC9A227, false);
        g.pose().popPose();

        if (!sellTab && search.getValue().isEmpty() && !search.isFocused()) {
            StyledTheme.caption(g, font, "SEARCH ITEMS", sidebarX + 6, contentY + 8);
        }
    }

    private void drawSlotLabels(GuiGraphics g) {
        g.drawString(font, "INVENTORY", groupX, groupY + MarketMenu.INV_Y - 11,
                StyledTheme.HEADER_COLOR, false);
        if (sellTab) {
            StyledTheme.header(g, font, "SELL TRAY - DROP ITEMS HERE", groupX,
                    groupY + MarketMenu.TRAY_Y - 12, MarketMenu.GROUP_W);
        }
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        drawTab(g, mouseX, mouseY, 0, "BUY", !sellTab);
        drawTab(g, mouseX, mouseY, 1, "SELL", sellTab);
        int y = tabY + TAB_H;
        g.fill(panelX + 10, y, panelX + panelW - 10, y + 1, StyledTheme.HEADER_ACCENT);
    }


    private void drawTab(GuiGraphics g, int mouseX, int mouseY, int index, String label,
                         boolean active) {
        int x = tabX(index);
        int y = tabY;
        boolean hovered = inBox(mouseX, mouseY, x, y, TAB_W, TAB_H);
        g.fill(x, y, x + TAB_W, y + TAB_H,
                active || hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        if (active) g.fill(x, y + TAB_H - 1, x + TAB_W, y + TAB_H, StyledTheme.ACCENT);
        g.drawString(font, label, x + (TAB_W - font.width(label)) / 2, y + 5,
                active ? StyledTheme.TEXT_COLOR : StyledTheme.LABEL_DIM, false);
    }

    private int tabX(int index) {
        return panelX + 10 + index * (TAB_W + 3);
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
        List<Row> rows = sidebarRows();
        clampSidebar(rows.size());
        int y = contentY + 22;
        for (int i = sidebarScroll; i < Math.min(rows.size(), sidebarScroll + sidebarRowsVisible); i++) {
            Row row = rows.get(i);
            boolean active = isActiveRow(row);
            boolean hovered = inBox(mouseX, mouseY, sidebarX + 1, y, sidebarW - 2, SIDEBAR_ROW_H);
            if (active || hovered) {
                g.fill(sidebarX + 1, y, sidebarX + sidebarW - 1, y + SIDEBAR_ROW_H,
                        StyledTheme.BUTTON_BG_HOVER);
            }
            if (active) {
                g.fill(sidebarX + 1, y, sidebarX + 3, y + SIDEBAR_ROW_H, StyledTheme.ACCENT);
            }
            int indent = row.nested() ? 18 : 8;
            String label = labelFor(row);
            if (row.nested()) {
                // Sections are drawn small and indented, so the hierarchy reads without a
                // tree line or an arrow glyph.
                StyledTheme.caption(g, font, label, sidebarX + indent, y + 5);
            } else {
                g.drawString(font, trim(label, sidebarW - indent - 8), sidebarX + indent, y + 3,
                        active ? StyledTheme.TEXT_COLOR : StyledTheme.LABEL_DIM, false);
            }
            y += SIDEBAR_ROW_H;
        }
        if (rows.size() > sidebarRowsVisible) {
            int trackTop = contentY + 22;
            int trackH = sidebarRowsVisible * SIDEBAR_ROW_H;
            int thumb = Math.max(8, trackH * sidebarRowsVisible / rows.size());
            int ty = trackTop + (trackH - thumb) * sidebarScroll
                    / Math.max(1, rows.size() - sidebarRowsVisible);
            g.fill(sidebarX + sidebarW - 4, trackTop, sidebarX + sidebarW - 1, trackTop + trackH,
                    StyledTheme.SLOT_BG);
            g.fill(sidebarX + sidebarW - 4, ty, sidebarX + sidebarW - 1, ty + thumb,
                    StyledTheme.SLOT_BORDER);
        }
    }

    private void clampSidebar(int total) {
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, Math.max(0, total - sidebarRowsVisible)));
    }

    private boolean isActiveRow(Row row) {
        if (!row.category().equals(category)) return false;
        // A category header stays lit while one of its sections is selected, so it is still
        // obvious where you are after scrolling the header out of view.
        return !row.nested() || row.sub().equals(sub);
    }

    private String labelFor(Row row) {
        if (row.nested()) {
            // "ALL" directly under "ALL" reads as a duplicate; name the scope.
            return row.sub().isEmpty()
                    ? "ALL " + row.category().toUpperCase(Locale.ROOT)
                    : row.sub().toUpperCase(Locale.ROOT);
        }
        return row.category().isEmpty() ? "ALL" : row.category().toUpperCase(Locale.ROOT);
    }

    private void drawOffers(GuiGraphics g, int mouseX, int mouseY) {
        List<MarketOffer> offers = ClientMarketState.offers();
        g.enableScissor(listX, contentY, listX + listW, contentY + visibleRows * ROW_H);
        for (int row = 0; row < visibleRows; row++) {
            int idx = scroll + row;
            if (idx >= filtered.size()) break;
            int offerIndex = filtered.get(idx);
            MarketOffer offer = offers.get(offerIndex);
            int y = contentY + row * ROW_H;
            boolean hovered = inBox(mouseX, mouseY, listX, y, listW, ROW_H);
            if (offerIndex == selected) {
                g.fill(listX, y, listX + listW, y + ROW_H, StyledTheme.BUTTON_BG_HOVER);
                g.fill(listX, y, listX + 2, y + ROW_H, StyledTheme.ACCENT);
            } else if (hovered) {
                g.fill(listX, y, listX + listW, y + ROW_H, StyledTheme.BUTTON_BG);
            }
            g.fill(listX, y + ROW_H - 1, listX + listW, y + ROW_H, StyledTheme.HEADER_ACCENT);

            ItemStack stack = offer.prototype();
            g.renderItem(stack, listX + 6, y + 4);
            g.renderItemDecorations(font, stack, listX + 6, y + 4);

            String price = Money.withSymbol(offer.price());
            int priceW = font.width(price);
            g.drawString(font, trim(stack.getHoverName().getString(), listW - 34 - priceW - 10),
                    listX + 28, y + 5, StyledTheme.TEXT_COLOR, false);
            String tag = offer.sub().isEmpty() ? offer.category() : offer.sub();
            StyledTheme.caption(g, font, tag.toUpperCase(Locale.ROOT), listX + 28, y + 16);
            g.drawString(font, price, listX + listW - 6 - priceW, y + 9,
                    ClientWallet.get() >= offer.price() ? StyledTheme.ACCENT : 0xFFB04A3A, false);
        }
        g.disableScissor();

        if (filtered.isEmpty()) {
            String msg = ClientMarketState.offers().isEmpty() ? "NO STOCK" : "NOTHING MATCHES";
            g.drawString(font, msg, listX + 10, contentY + 10, StyledTheme.LABEL_DIM, false);
        }
        drawListScrollbar(g);
    }

    private void drawListScrollbar(GuiGraphics g) {
        int trackH = visibleRows * ROW_H;
        g.fill(scrollbarX, contentY, scrollbarX + 5, contentY + trackH, StyledTheme.SLOT_BG);
        int total = filtered.size();
        if (total <= visibleRows) return;
        int thumb = Math.max(12, trackH * visibleRows / total);
        int y = contentY + (trackH - thumb) * scroll / Math.max(1, total - visibleRows);
        g.fill(scrollbarX, y, scrollbarX + 5, y + thumb, StyledTheme.SLOT_BORDER);
    }

    // ---- details -----------------------------------------------------------

    private int qtyRowY() {
        return contentY + contentH - 38;
    }

    private int buyButtonY() {
        return contentY + contentH - 20;
    }

    private static final int STAT_ROW_H = 11;

    private int statTop() {
        return contentY + 74;
    }

    private int statRegionHeight() {
        return Math.max(STAT_ROW_H, (qtyRowY() - 16) - statTop());
    }

    /**
     * Small text, drawn at 0.75 scale.
     *
     * The stat block needs roughly twice the rows the panel has at full size, and shrinking
     * the text is cheaper than shrinking the panel. Values are right-aligned, so the scale
     * has to be applied before the width is measured or the alignment drifts.
     */
    private void small(GuiGraphics g, String text, int x, int y, int colour, boolean rightAlign) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, rightAlign ? -font.width(text) : 0, 0, colour, false);
        g.pose().popPose();
    }

    private void drawBuyDetails(GuiGraphics g, int mouseX, int mouseY) {
        StyledTheme.header(g, font, "ITEM DETAILS", detailX + 6, contentY + 5, detailW - 12);
        MarketOffer offer = selectedOffer();
        if (offer == null) {
            g.drawString(font, "SELECT AN ITEM", detailX + 8, contentY + 28,
                    StyledTheme.LABEL_DIM, false);
            return;
        }
        ItemStack stack = offer.prototype();

        // 2x rather than 3x: the preview was eating the room the stats need, and at 32px an
        // item icon is already perfectly readable.
        g.pose().pushPose();
        // Down from contentY+14: at 2x the icon is 32 tall and was running into the
        // ITEM DETAILS rule above it.
        g.pose().translate(detailX + detailW / 2f - 16, contentY + 20, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();

        int y = contentY + 56;
        // Drawn at 0.75 like the stats. Gear names in this pack run to "Ballistic Armor Co.
        // 'Bastion' Helmet (MultiCam)"; at full size that is three lines in a 152px column and
        // pushes everything below it into the price row.
        List<String> nameLines = wrap(stack.getHoverName().getString(),
                (int) ((detailW - 16) / 0.75f), 2);
        for (String line : nameLines) {
            small(g, line, detailX + 8, y, StyledTheme.TEXT_COLOR, false);
            y += 8;
        }
        // The category caption and the stats start BELOW however many lines the name took.
        // Anchoring them to a fixed offset put a two-line name straight through the caption,
        // which is what a long helmet name was doing.
        StyledTheme.caption(g, font, offer.sub().isEmpty()
                ? offer.category().toUpperCase(Locale.ROOT)
                : (offer.category() + " / " + offer.sub()).toUpperCase(Locale.ROOT),
                detailX + 8, y + 2);

        List<ItemStatCard.Stat> stats = ItemStatCard.forStack(stack);
        int regionTop = Math.max(statTop(), y + 12);
        // Real available height, not a clamped one. On a short window there is genuinely no
        // room between the name and the price row, and clamping to a minimum just drew the
        // stats on top of the price - better to show none than to show a mess.
        // Minus a line for the "n/m - SCROLL" hint, which was being drawn at the bottom of
        // the region and landing straight on the price.
        int regionH = (qtyRowY() - 28) - regionTop;
        int fits = regionH / STAT_ROW_H;
        if (fits < 1) stats = List.of();
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, stats.size() - fits)));

        if (!stats.isEmpty()) {
        g.enableScissor(detailX, regionTop, detailX + detailW, regionTop + regionH);
        int sy = regionTop;
        for (int i = detailScroll; i < Math.min(stats.size(), detailScroll + fits); i++) {
            ItemStatCard.Stat stat = stats.get(i);
            small(g, stat.label(), detailX + 8, sy + 1, StyledTheme.LABEL_DIM, false);
            small(g, stat.value(), detailX + detailW - 8, sy + 1, StyledTheme.TEXT_COLOR, true);
            if (stat.bar() >= 0f) {
                int bx = detailX + 8;
                int bw2 = detailW - 16;
                g.fill(bx, sy + 9, bx + bw2, sy + 10, StyledTheme.SLOT_BG);
                g.fill(bx, sy + 9, bx + (int) (bw2 * stat.bar()), sy + 10, StyledTheme.ACCENT);
            }
            sy += STAT_ROW_H;
        }
        g.disableScissor();
        }

        // A cut-off list with no indication it continues is what made the old panel look
        // broken, so say so.
        if (fits >= 1 && stats.size() > fits) {
            StyledTheme.caption(g, font,
                    (detailScroll + fits) + "/" + stats.size() + " - SCROLL",
                    detailX + detailW - 46, regionTop + regionH + 2);
        }

        long total = offer.price() * quantity;
        String price = Money.withSymbol(total);
        // Cleared behind first: the stat block is scissored to regionH, but a long price and
        // the last visible stat were still landing on the same pixels at small panel sizes.
        int priceY = qtyRowY() - 14;
        g.fill(detailX + 1, priceY - 2, detailX + detailW - 1, priceY + 11, StyledTheme.SECTION_BG);
        StyledTheme.caption(g, font, quantity > 1
                ? "UNIT " + Money.withSymbol(offer.price()) : "PRICE", detailX + 8, priceY + 4);
        g.drawString(font, price, detailX + detailW - 8 - font.width(price), priceY,
                ClientWallet.get() >= total ? StyledTheme.ACCENT : 0xFFB04A3A, false);

        int bw = (detailW - 16 - 3 * 3) / 4;
        for (int i = 0; i < QUANTITIES.length; i++) {
            int x = detailX + 8 + i * (bw + 3);
            boolean active = quantity == QUANTITIES[i];
            boolean hovered = inBox(mouseX, mouseY, x, qtyRowY(), bw, 13);
            g.fill(x, qtyRowY(), x + bw, qtyRowY() + 13,
                    active || hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
            if (active) g.renderOutline(x, qtyRowY(), bw, 13, StyledTheme.ACCENT);
            String label = "x" + QUANTITIES[i];
            g.drawString(font, label, x + (bw - font.width(label)) / 2, qtyRowY() + 3,
                    active ? StyledTheme.ACCENT : StyledTheme.LABEL_DIM, false);
        }

        drawWideButton(g, mouseX, mouseY, detailX + 8, buyButtonY(), detailW - 16, "BUY",
                ClientWallet.get() >= total);
    }

    private void drawSellList(GuiGraphics g) {
        StyledTheme.header(g, font, "TO SELL", sidebarX + 6, contentY + 5,
                scrollbarX - 12 - sidebarX);
        int y = contentY + 24;
        int shown = 0;
        for (int i = 0; i < menu.sellTray().getContainerSize(); i++) {
            ItemStack stack = menu.sellTray().getItem(i);
            if (stack.isEmpty()) continue;
            long value = MarketPrices.sellPrice(stack, stack.getCount());
            g.renderItem(stack, sidebarX + 6, y - 4);
            g.renderItemDecorations(font, stack, sidebarX + 6, y - 4);
            String price = value > 0 ? Money.withSymbol(value) : "NO VALUE";
            int priceW = font.width(price);
            int rowRight = scrollbarX - 8;
            g.drawString(font, trim(stack.getHoverName().getString(),
                            rowRight - sidebarX - 40 - priceW),
                    sidebarX + 28, y, StyledTheme.TEXT_COLOR, false);
            g.drawString(font, price, rowRight - priceW, y,
                    value > 0 ? StyledTheme.ACCENT : StyledTheme.LABEL_DIM, false);
            y += 20;
            shown++;
        }
        if (shown == 0) {
            g.drawString(font, "TRAY IS EMPTY", sidebarX + 8, contentY + 30,
                    StyledTheme.LABEL_DIM, false);
            StyledTheme.caption(g, font,
                    "SHIFT-CLICK ITEMS IN YOUR INVENTORY TO MOVE THEM INTO THE TRAY",
                    sidebarX + 8, contentY + 46);
        }
    }

    private void drawSellDetails(GuiGraphics g, int mouseX, int mouseY) {
        StyledTheme.header(g, font, "PAYOUT", detailX + 6, contentY + 5, detailW - 12);
        long total = menu.trayValue();
        g.drawString(font, "TOTAL", detailX + 8, contentY + 26, StyledTheme.LABEL_DIM, false);
        String text = Money.withSymbol(total);
        g.drawString(font, text, detailX + detailW - 8 - font.width(text), contentY + 26,
                total > 0 ? StyledTheme.ACCENT : StyledTheme.LABEL_DIM, false);
        StyledTheme.caption(g, font, "TRADERS PAY BELOW LIST PRICE", detailX + 8, contentY + 40);
        StyledTheme.caption(g, font, "WITHDRAW PAYS OUT UP TO " + Money.withSymbol(10_000),
                detailX + 8, contentY + 50);

        drawWideButton(g, mouseX, mouseY, detailX + 8, qtyRowY(), detailW - 16, "SELL ALL",
                total > 0);
        drawWideButton(g, mouseX, mouseY, detailX + 8, buyButtonY(), detailW - 16,
                "WITHDRAW CASH", ClientWallet.get() > 0);
    }

    private void drawWideButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w,
                                String label, boolean enabled) {
        boolean hovered = enabled && inBox(mouseX, mouseY, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        g.renderOutline(x, y, w, 16, enabled ? StyledTheme.SLOT_BORDER : StyledTheme.HEADER_ACCENT);
        int colour = enabled ? (hovered ? StyledTheme.ACCENT : StyledTheme.TEXT_COLOR)
                             : StyledTheme.LABEL_DIM;
        String shown = trim(label, w - 8);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + 4, colour, false);
    }

    // ---- confirmation ------------------------------------------------------

    private static final int CONFIRM_W = 230;
    private static final int CONFIRM_H = 84;

    private int confirmX() {
        return panelX + (panelW - CONFIRM_W) / 2;
    }

    private int confirmY() {
        return panelY + (panelH - CONFIRM_H) / 2;
    }

    private void drawConfirm(GuiGraphics g, int mouseX, int mouseY) {
        // Dim everything behind it, so it is obvious the rest of the screen is inert.
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC0000000);
        int x = confirmX();
        int y = confirmY();
        StyledTheme.panel(g, x, y, CONFIRM_W, CONFIRM_H);
        g.drawString(font, confirm.title(), x + 10, y + 10, StyledTheme.TEXT_COLOR, false);
        g.drawString(font, trim(confirm.line(), CONFIRM_W - 20), x + 10, y + 28,
                StyledTheme.HEADER_COLOR, false);
        g.drawString(font, confirm.cost(), x + 10, y + 42, StyledTheme.ACCENT, false);

        int bw = (CONFIRM_W - 28) / 2;
        drawConfirmButton(g, mouseX, mouseY, x + 10, y + CONFIRM_H - 24, bw, "CONFIRM", true);
        drawConfirmButton(g, mouseX, mouseY, x + 18 + bw, y + CONFIRM_H - 24, bw, "CANCEL", false);
    }

    private void drawConfirmButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w,
                                   String label, boolean primary) {
        boolean hovered = inBox(mouseX, mouseY, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        g.renderOutline(x, y, w, 16, primary ? StyledTheme.ACCENT : StyledTheme.SLOT_BORDER);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4,
                hovered ? StyledTheme.TEXT_COLOR : StyledTheme.HEADER_COLOR, false);
    }

    /** Every click is swallowed while the panel is up - that is the point of it. */
    private boolean confirmClick(double mouseX, double mouseY) {
        int x = confirmX();
        int y = confirmY();
        int bw = (CONFIRM_W - 28) / 2;
        if (inBox(mouseX, mouseY, x + 10, y + CONFIRM_H - 24, bw, 16)) {
            accept();
        } else if (inBox(mouseX, mouseY, x + 18 + bw, y + CONFIRM_H - 24, bw, 16)) {
            confirm = null;
        }
        return true;
    }

    private void accept() {
        Runnable action = confirm.onConfirm();
        confirm = null;
        action.run();
    }

    // ---- input -------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirm != null) return confirmClick(mouseX, mouseY);
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        for (int i = 0; i < 2; i++) {
            if (inBox(mouseX, mouseY, tabX(i), tabY, TAB_W, TAB_H)) {
                sellTab = i == 1;
                menu.sellTabActive = sellTab;
                layout();
                if (search != null) search.setPosition(sidebarX + 4, contentY + 4);
                return true;
            }
        }

        if (sellTab) {
            if (inBox(mouseX, mouseY, detailX + 8, qtyRowY(), detailW - 16, 16)) {
                long total = menu.trayValue();
                if (total > 0) {
                    confirm = new Confirm("SELL ITEMS?", countTrayItems() + " stack(s) in the tray",
                            "You receive " + Money.withSymbol(total),
                            () -> NetworkHandler.CHANNEL.sendToServer(new MarketPackets.Sell()));
                }
                return true;
            }
            if (inBox(mouseX, mouseY, detailX + 8, buyButtonY(), detailW - 16, 16)) {
                if (ClientWallet.get() > 0) {
                    confirm = new Confirm("WITHDRAW CASH?", "Paid out in notes",
                            "Up to " + Money.withSymbol(10_000),
                            () -> NetworkHandler.CHANNEL.sendToServer(
                                    new MarketPackets.Withdraw(10_000L)));
                }
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        List<Row> rows = sidebarRows();
        clampSidebar(rows.size());
        int sy = contentY + 22;
        for (int i = sidebarScroll; i < Math.min(rows.size(), sidebarScroll + sidebarRowsVisible); i++) {
            if (inBox(mouseX, mouseY, sidebarX + 1, sy, sidebarW - 2, SIDEBAR_ROW_H)) {
                Row row = rows.get(i);
                // Picking a category clears the section, so switching never leaves an
                // inherited filter behind that silently empties the list.
                category = row.category();
                sub = row.sub();
                scroll = 0;
                rebuild();
                return true;
            }
            sy += SIDEBAR_ROW_H;
        }

        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            selected = filtered.get(row);
            detailScroll = 0;
            return true;
        }

        int bw = (detailW - 16 - 3 * 3) / 4;
        for (int i = 0; i < QUANTITIES.length; i++) {
            if (inBox(mouseX, mouseY, detailX + 8 + i * (bw + 3), qtyRowY(), bw, 13)) {
                quantity = QUANTITIES[i];
                return true;
            }
        }

        if (inBox(mouseX, mouseY, detailX + 8, buyButtonY(), detailW - 16, 16)) {
            MarketOffer offer = selectedOffer();
            if (offer != null) {
                long cost = offer.price() * quantity;
                if (ClientWallet.get() >= cost) {
                    int index = selected;
                    int qty = quantity;
                    confirm = new Confirm("CONFIRM PURCHASE",
                            offer.prototype().getHoverName().getString() + " x" + qty,
                            "Costs " + Money.withSymbol(cost),
                            () -> NetworkHandler.CHANNEL.sendToServer(new MarketPackets.Buy(
                                    ClientMarketState.revision(), index, qty)));
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (confirm != null) return true;
        if (!sellTab && inBox(mouseX, mouseY, sidebarX, contentY, sidebarW, contentH)) {
            int max = Math.max(0, sidebarRows().size() - sidebarRowsVisible);
            sidebarScroll = Math.max(0, Math.min(max, sidebarScroll - (int) Math.signum(delta)));
            return true;
        }
        if (!sellTab && inBox(mouseX, mouseY, detailX, contentY, detailW, contentH)) {
            // The stat block is longer than the column for most guns, so the panel scrolls
            // rather than silently cutting stats off - which is what it did before.
            int rows = ItemStatCard.forStack(selectedOffer() == null
                    ? ItemStack.EMPTY : selectedOffer().prototype()).size();
            int fits = Math.max(1, statRegionHeight() / STAT_ROW_H);
            detailScroll = Math.max(0, Math.min(Math.max(0, rows - fits),
                    detailScroll - (int) Math.signum(delta)));
            return true;
        }
        if (!sellTab && inBox(mouseX, mouseY, listX, contentY, listW + 10, contentH)) {
            int max = Math.max(0, filtered.size() - visibleRows);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (confirm != null) {
            if (key == InputConstants.KEY_ESCAPE) {
                confirm = null;
            } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                accept();
            }
            return true;
        }
        // While the search box has focus, letters must reach it - otherwise typing "e" closes
        // the screen, which is the classic EditBox-in-a-container-screen bug.
        if (search != null && search.isFocused() && key != InputConstants.KEY_ESCAPE) {
            return search.keyPressed(key, scanCode, modifiers)
                    || search.canConsumeInput()
                    || super.keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        ClientMarketState.clear();
        super.onClose();
    }

    // ---- helpers -----------------------------------------------------------

    private MarketOffer selectedOffer() {
        List<MarketOffer> offers = ClientMarketState.offers();
        return selected >= 0 && selected < offers.size() ? offers.get(selected) : null;
    }

    private int countTrayItems() {
        int n = 0;
        for (int i = 0; i < menu.sellTray().getContainerSize(); i++) {
            if (!menu.sellTray().getItem(i).isEmpty()) n++;
        }
        return n;
    }

    /** Index into {@link #filtered}, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        if (sellTab) return -1;
        if (mouseX < listX || mouseX >= listX + listW) return -1;
        int rel = (int) (mouseY - contentY);
        if (rel < 0 || rel >= visibleRows * ROW_H) return -1;
        int idx = scroll + rel / ROW_H;
        return idx < filtered.size() ? idx : -1;
    }

    private void drawOfferTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int row = rowAt(mouseX, mouseY);
        if (row < 0) return;
        MarketOffer offer = ClientMarketState.offers().get(filtered.get(row));
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, offer.prototype()));
        lines.add(Component.literal(""));
        lines.add(Component.translatable("gui.dayzhud.market.price",
                Money.withSymbol(offer.price())));
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 8) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(4, maxWidth - 8)) + "...";
    }

    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                if (out.size() >= maxLines) return out;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (out.size() < maxLines && line.length() > 0) out.add(trim(line.toString(), maxWidth));
        return out;
    }
}
