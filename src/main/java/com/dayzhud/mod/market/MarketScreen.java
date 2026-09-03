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
 * The trader: BUY and SELL tabs, a category sidebar, a scrolling stock list, and a details
 * panel for whatever is selected.
 *
 * Scrolling rather than paging, deliberately. A page control costs a click per screenful and
 * makes a shop feel like a website; the list is virtualised to five visible rows either way,
 * so paging would buy nothing.
 *
 * Categories are a VERTICAL sidebar rather than a tab strip, and that is a bug fix. The strip
 * laid tabs out left to right and stopped when it ran out of panel width, so with a full
 * modpack installed WEAPONS, SUPPLIES and VALUABLES were simply unreachable - the stock was
 * there, the way to it was not. A column cannot run out of room the same way and scales to
 * however many categories a pack's price data defines.
 *
 * Every purchase and every sale goes through a confirmation panel. Buying is irreversible at
 * a 55% buyback, and the sell tray can hold a rifle, so a misclick is expensive.
 */
public class MarketScreen extends AbstractContainerScreen<MarketMenu> {

    // ---- layout ------------------------------------------------------------
    private static final int CONTENT_Y = 56;
    private static final int CONTENT_H = 100;

    private static final int SIDEBAR_X = 8;
    private static final int SIDEBAR_W = 74;
    private static final int SIDEBAR_ROW_H = 12;

    private static final int LIST_X = 86;
    private static final int LIST_W = 182;
    private static final int ROW_H = 20;
    private static final int VISIBLE_ROWS = 5;
    private static final int SCROLLBAR_X = 270;

    private static final int DETAIL_X = 278;
    private static final int DETAIL_W = 104;

    private static final int TAB_Y = 20;
    private static final int TAB_W = 56;
    private static final int TAB_H = 14;

    private static final int BUY_BUTTON_Y = CONTENT_Y + CONTENT_H - 16;
    private static final int QTY_ROW_Y = CONTENT_Y + CONTENT_H - 32;
    private static final int SELL_ALL_Y = CONTENT_Y + CONTENT_H - 34;
    private static final int WITHDRAW_Y = CONTENT_Y + CONTENT_H - 16;

    private static final int[] QUANTITIES = {1, 5, 16};

    private static final int SEARCH_Y = 40;
    private static final int SEARCH_H = 14;

    // ---- state -------------------------------------------------------------
    private EditBox search;
    private boolean sellTab;
    private String category = "";
    private String sub = "";
    private int scroll;
    private int sidebarScroll;
    private int selected = -1;          // index into ClientMarketState.offers()
    private int quantity = 1;
    private List<Integer> filtered = new ArrayList<>();

    /** Non-null while a confirmation panel is up. Blocks every other click. */
    private Confirm confirm;

    private record Confirm(String title, String line, String cost, Runnable onConfirm) {}

    public MarketScreen(MarketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 390;
        this.imageHeight = 252;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 10;
        this.titleLabelY = 9;
        this.inventoryLabelX = MarketMenu.INV_X;
        this.inventoryLabelY = MarketMenu.INV_Y - 11;

        search = new EditBox(this.font, leftPos + LIST_X + 5, topPos + SEARCH_Y + 3,
                LIST_W - 10, 10, Component.translatable("gui.dayzhud.market.search"));
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
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - VISIBLE_ROWS)));
        if (!filtered.contains(selected)) selected = filtered.isEmpty() ? -1 : filtered.get(0);
    }

    /** A sidebar line: either a category, or a section nested under the open one. */
    private record Row(String category, String sub, boolean nested) {}

    /**
     * The sidebar, with the selected category expanded into its own sections.
     *
     * Nesting rather than a second horizontal strip along the top of the list: a strip has a
     * fixed width and would hit the same overflow that hid FIREARMS twice already, whereas a
     * column just gets longer and the column already scrolls.
     */
    private List<Row> sidebarRows() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MarketOffer o : ClientMarketState.offers()) seen.add(o.category());

        List<Row> rows = new ArrayList<>();
        rows.add(new Row("", "", false));               // ALL
        for (String cat : MarketCatalog.sortCategories(seen)) {
            rows.add(new Row(cat, "", false));
            if (!cat.equals(category)) continue;
            List<String> subs = MarketCatalog.subcategories(ClientMarketState.offers(), cat);
            // One section is not a section - showing "ARMOR > helmets" alone is just noise.
            if (subs.size() < 2) continue;
            rows.add(new Row(cat, "", true));           // "ALL" within the category
            for (String s : subs) rows.add(new Row(cat, s, true));
        }
        return rows;
    }

    // ---- background --------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        StyledTheme.panel(g, leftPos, topPos, imageWidth, imageHeight);
        StyledTheme.zone(g, leftPos + SIDEBAR_X, topPos + CONTENT_Y,
                leftPos + SIDEBAR_X + SIDEBAR_W, topPos + CONTENT_Y + CONTENT_H);
        StyledTheme.zone(g, leftPos + DETAIL_X, topPos + CONTENT_Y,
                leftPos + DETAIL_X + DETAIL_W, topPos + CONTENT_Y + CONTENT_H);
        if (!sellTab) {
            StyledTheme.zone(g, leftPos + LIST_X, topPos + CONTENT_Y,
                    leftPos + LIST_X + LIST_W, topPos + CONTENT_Y + CONTENT_H);
        }

        if (sellTab) {
            for (int i = 0; i < MarketMenu.SELL_SLOTS; i++) {
                StyledTheme.slot(g, leftPos + MarketMenu.TRAY_X + (i % 3) * 18,
                        topPos + MarketMenu.TRAY_Y + (i / 3) * 18);
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

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "MARKET", titleLabelX, titleLabelY, StyledTheme.TEXT_COLOR, false);
        g.drawString(font, "INVENTORY", inventoryLabelX, inventoryLabelY,
                StyledTheme.HEADER_COLOR, false);
        if (sellTab) {
            StyledTheme.header(g, font, "SELL TRAY", MarketMenu.TRAY_X - 2,
                    MarketMenu.TRAY_Y - 14, 58);
        }
    }

    // ---- render ------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        menu.sellTabActive = sellTab;
        search.visible = !sellTab;
        super.render(g, mouseX, mouseY, partialTick);

        drawBalance(g);
        drawTabs(g, mouseX, mouseY);
        if (sellTab) {
            drawSellList(g);
            drawSellDetails(g, mouseX, mouseY);
        } else {
            drawSearch(g);
            drawSidebar(g, mouseX, mouseY);
            drawOffers(g, mouseX, mouseY);
            drawBuyDetails(g, mouseX, mouseY);
        }

        if (confirm == null) {
            renderTooltip(g, mouseX, mouseY);
            drawOfferTooltip(g, mouseX, mouseY);
        } else {
            drawConfirm(g, mouseX, mouseY);
        }
    }

    private void drawBalance(GuiGraphics g) {
        String text = Money.withSymbol(ClientWallet.get());
        int x = leftPos + imageWidth - 8 - font.width(text);
        StyledTheme.caption(g, font, "BALANCE", x, topPos + 4);
        g.drawString(font, text, x, topPos + 11, 0xFFC9A227, false);
    }

    private void drawSearch(GuiGraphics g) {
        int x = leftPos + LIST_X;
        int y = topPos + SEARCH_Y;
        g.fill(x, y, x + LIST_W, y + SEARCH_H, StyledTheme.SLOT_BG);
        g.renderOutline(x, y, LIST_W, SEARCH_H,
                search.isFocused() ? StyledTheme.ACCENT : StyledTheme.SLOT_BORDER);
        if (search.getValue().isEmpty() && !search.isFocused()) {
            StyledTheme.caption(g, font, "SEARCH STOCK", x + 5, y + 5);
        }
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        drawTab(g, mouseX, mouseY, 0, "BUY", !sellTab);
        drawTab(g, mouseX, mouseY, 1, "SELL", sellTab);
        g.fill(leftPos + 8, topPos + TAB_Y + TAB_H, leftPos + imageWidth - 8,
                topPos + TAB_Y + TAB_H + 1, StyledTheme.HEADER_ACCENT);
    }

    private void drawTab(GuiGraphics g, int mouseX, int mouseY, int index, String label,
                         boolean active) {
        int x = leftPos + 8 + index * (TAB_W + 2);
        boolean hovered = inBox(mouseX, mouseY, x, topPos + TAB_Y, TAB_W, TAB_H);
        g.fill(x, topPos + TAB_Y, x + TAB_W, topPos + TAB_Y + TAB_H,
                active || hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        if (active) {
            g.fill(x, topPos + TAB_Y + TAB_H - 1, x + TAB_W, topPos + TAB_Y + TAB_H,
                    StyledTheme.ACCENT);
        }
        g.drawString(font, label, x + (TAB_W - font.width(label)) / 2, topPos + TAB_Y + 3,
                active ? StyledTheme.TEXT_COLOR : StyledTheme.LABEL_DIM, false);
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
        List<Row> rows = sidebarRows();
        clampSidebar(rows.size());
        int y = topPos + SIDEBAR_LIST_Y;
        for (int i = sidebarScroll; i < Math.min(rows.size(), sidebarScroll + SIDEBAR_ROWS); i++) {
            Row row = rows.get(i);
            boolean active = isActiveRow(row);
            boolean hovered = inBox(mouseX, mouseY, leftPos + SIDEBAR_X + 1, y,
                    SIDEBAR_W - 2, SIDEBAR_ROW_H);
            if (active || hovered) {
                g.fill(leftPos + SIDEBAR_X + 1, y, leftPos + SIDEBAR_X + SIDEBAR_W - 1,
                        y + SIDEBAR_ROW_H, StyledTheme.BUTTON_BG_HOVER);
            }
            if (active) {
                g.fill(leftPos + SIDEBAR_X + 1, y, leftPos + SIDEBAR_X + 3, y + SIDEBAR_ROW_H,
                        StyledTheme.ACCENT);
            }
            int indent = row.nested() ? 15 : 7;
            String label = labelFor(row);
            if (row.nested()) {
                // Sections are drawn small and indented so the eye reads the hierarchy
                // without needing a tree line or an arrow glyph.
                StyledTheme.caption(g, font, label, leftPos + SIDEBAR_X + indent, y + 5);
            } else {
                g.drawString(font, trim(label, SIDEBAR_W - indent - 7),
                        leftPos + SIDEBAR_X + indent, y + 3,
                        active ? StyledTheme.TEXT_COLOR : StyledTheme.LABEL_DIM, false);
            }
            y += SIDEBAR_ROW_H;
        }
        // A column holds seven rows; a modpack defines more than seven categories, and with a
        // category expanded into sections there are more still. The first version just stopped
        // drawing, which is how FIREARMS became unreachable twice.
        if (rows.size() > SIDEBAR_ROWS) {
            int trackTop = topPos + SIDEBAR_LIST_Y;
            int trackH = SIDEBAR_ROWS * SIDEBAR_ROW_H;
            int thumb = Math.max(8, trackH * SIDEBAR_ROWS / rows.size());
            int ty = trackTop + (trackH - thumb) * sidebarScroll
                    / Math.max(1, rows.size() - SIDEBAR_ROWS);
            g.fill(leftPos + SIDEBAR_X + SIDEBAR_W - 4, trackTop,
                    leftPos + SIDEBAR_X + SIDEBAR_W - 1, trackTop + trackH, StyledTheme.SLOT_BG);
            g.fill(leftPos + SIDEBAR_X + SIDEBAR_W - 4, ty,
                    leftPos + SIDEBAR_X + SIDEBAR_W - 1, ty + thumb, StyledTheme.SLOT_BORDER);
        }
    }

    private void clampSidebar(int total) {
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, Math.max(0, total - SIDEBAR_ROWS)));
    }

    private boolean isActiveRow(Row row) {
        if (!row.category().equals(category)) return false;
        if (row.nested()) return row.sub().equals(sub);
        // A category header stays lit while one of its sections is selected, so it is still
        // obvious where you are after scrolling the header out of view and back.
        return true;
    }

    private String labelFor(Row row) {
        if (row.nested()) return row.sub().isEmpty() ? "ALL" : row.sub().toUpperCase(Locale.ROOT);
        return row.category().isEmpty() ? "ALL" : row.category().toUpperCase(Locale.ROOT);
    }

    private void drawOffers(GuiGraphics g, int mouseX, int mouseY) {
        List<MarketOffer> offers = ClientMarketState.offers();
        int top = topPos + CONTENT_Y + 2;
        g.enableScissor(leftPos + LIST_X, top, leftPos + LIST_X + LIST_W, top + ROW_H * VISIBLE_ROWS);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scroll + row;
            if (idx >= filtered.size()) break;
            int offerIndex = filtered.get(idx);
            MarketOffer offer = offers.get(offerIndex);
            int y = top + row * ROW_H;
            boolean hovered = inBox(mouseX, mouseY, leftPos + LIST_X, y, LIST_W, ROW_H);
            if (offerIndex == selected) {
                g.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_W, y + ROW_H,
                        StyledTheme.BUTTON_BG_HOVER);
                g.fill(leftPos + LIST_X, y, leftPos + LIST_X + 2, y + ROW_H, StyledTheme.ACCENT);
            } else if (hovered) {
                g.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_W, y + ROW_H,
                        StyledTheme.BUTTON_BG);
            }
            g.fill(leftPos + LIST_X, y + ROW_H - 1, leftPos + LIST_X + LIST_W, y + ROW_H,
                    StyledTheme.HEADER_ACCENT);

            ItemStack stack = offer.prototype();
            g.renderItem(stack, leftPos + LIST_X + 5, y + 3);
            g.renderItemDecorations(font, stack, leftPos + LIST_X + 5, y + 3);

            String price = Money.withSymbol(offer.price());
            int priceW = font.width(price);
            int nameW = LIST_W - 28 - priceW - 8;
            g.drawString(font, trim(stack.getHoverName().getString(), nameW),
                    leftPos + LIST_X + 26, y + 4, StyledTheme.TEXT_COLOR, false);
            String tag = offer.sub().isEmpty() ? offer.category() : offer.sub();
            StyledTheme.caption(g, font, tag.toUpperCase(Locale.ROOT),
                    leftPos + LIST_X + 26, y + 14);
            g.drawString(font, price, leftPos + LIST_X + LIST_W - 5 - priceW, y + 8,
                    ClientWallet.get() >= offer.price() ? StyledTheme.ACCENT : 0xFFB04A3A, false);
        }
        g.disableScissor();

        if (filtered.isEmpty()) {
            String msg = ClientMarketState.offers().isEmpty() ? "NO STOCK" : "NOTHING MATCHES";
            g.drawString(font, msg, leftPos + LIST_X + 8, top + 8, StyledTheme.LABEL_DIM, false);
        }
        drawScrollbar(g, filtered.size());
    }

    private void drawScrollbar(GuiGraphics g, int total) {
        int top = topPos + CONTENT_Y + 2;
        int trackH = ROW_H * VISIBLE_ROWS;
        g.fill(leftPos + SCROLLBAR_X, top, leftPos + SCROLLBAR_X + 4, top + trackH,
                StyledTheme.SLOT_BG);
        if (total <= VISIBLE_ROWS) return;
        int thumb = Math.max(10, trackH * VISIBLE_ROWS / total);
        int y = top + (trackH - thumb) * scroll / Math.max(1, total - VISIBLE_ROWS);
        g.fill(leftPos + SCROLLBAR_X, y, leftPos + SCROLLBAR_X + 4, y + thumb,
                StyledTheme.SLOT_BORDER);
    }

    // ---- details panels ----------------------------------------------------

    private void drawBuyDetails(GuiGraphics g, int mouseX, int mouseY) {
        StyledTheme.header(g, font, "ITEM DETAILS", leftPos + DETAIL_X + 4,
                topPos + CONTENT_Y + 4, DETAIL_W - 8);
        MarketOffer offer = selectedOffer();
        if (offer == null) {
            g.drawString(font, "SELECT AN ITEM", leftPos + DETAIL_X + 6, topPos + CONTENT_Y + 24,
                    StyledTheme.LABEL_DIM, false);
            return;
        }
        ItemStack stack = offer.prototype();

        g.pose().pushPose();
        g.pose().translate(leftPos + DETAIL_X + DETAIL_W / 2f - 16, topPos + CONTENT_Y + 14, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();

        int y = topPos + CONTENT_Y + 50;
        for (String line : wrap(stack.getHoverName().getString(), DETAIL_W - 10, 2)) {
            g.drawString(font, line, leftPos + DETAIL_X + 5, y, StyledTheme.TEXT_COLOR, false);
            y += 10;
        }
        long total = offer.price() * quantity;
        String price = Money.withSymbol(total);
        g.drawString(font, price, leftPos + DETAIL_X + DETAIL_W - 5 - font.width(price), y + 3,
                ClientWallet.get() >= total ? StyledTheme.ACCENT : 0xFFB04A3A, false);
        StyledTheme.caption(g, font, quantity > 1 ? ("UNIT " + Money.withSymbol(offer.price())) : "PRICE",
                leftPos + DETAIL_X + 5, y + 6);

        for (int i = 0; i < QUANTITIES.length; i++) {
            int x = leftPos + DETAIL_X + 5 + i * 31;
            boolean active = quantity == QUANTITIES[i];
            boolean hovered = inBox(mouseX, mouseY, x, topPos + QTY_ROW_Y, 29, 12);
            g.fill(x, topPos + QTY_ROW_Y, x + 29, topPos + QTY_ROW_Y + 12,
                    active || hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
            if (active) g.renderOutline(x, topPos + QTY_ROW_Y, 29, 12, StyledTheme.ACCENT);
            String label = "x" + QUANTITIES[i];
            g.drawString(font, label, x + (29 - font.width(label)) / 2, topPos + QTY_ROW_Y + 2,
                    active ? StyledTheme.ACCENT : StyledTheme.LABEL_DIM, false);
        }

        boolean affordable = ClientWallet.get() >= offer.price() * (long) quantity;
        drawWideButton(g, mouseX, mouseY, DETAIL_X + 4, BUY_BUTTON_Y, DETAIL_W - 8, "BUY", affordable);
    }

    private void drawSellList(GuiGraphics g) {
        StyledTheme.header(g, font, "TO SELL", leftPos + LIST_X + 4, topPos + CONTENT_Y + 4,
                LIST_W - 8);
        int y = topPos + CONTENT_Y + 20;
        int shown = 0;
        for (int i = 0; i < menu.sellTray().getContainerSize(); i++) {
            ItemStack stack = menu.sellTray().getItem(i);
            if (stack.isEmpty()) continue;
            long value = MarketPrices.sellPrice(stack, stack.getCount());
            g.renderItem(stack, leftPos + LIST_X + 4, y - 4);
            g.renderItemDecorations(font, stack, leftPos + LIST_X + 4, y - 4);
            String price = value > 0 ? Money.withSymbol(value) : "NO VALUE";
            int priceW = font.width(price);
            g.drawString(font, trim(stack.getHoverName().getString(), LIST_W - 32 - priceW),
                    leftPos + LIST_X + 24, y, StyledTheme.TEXT_COLOR, false);
            g.drawString(font, price, leftPos + LIST_X + LIST_W - 4 - priceW, y,
                    value > 0 ? StyledTheme.ACCENT : StyledTheme.LABEL_DIM, false);
            y += 18;
            shown++;
        }
        if (shown == 0) {
            g.drawString(font, "TRAY IS EMPTY", leftPos + LIST_X + 6, topPos + CONTENT_Y + 24,
                    StyledTheme.LABEL_DIM, false);
            StyledTheme.caption(g, font, "SHIFT-CLICK ITEMS TO MOVE THEM IN",
                    leftPos + LIST_X + 6, topPos + CONTENT_Y + 40);
        }
    }

    private void drawSellDetails(GuiGraphics g, int mouseX, int mouseY) {
        StyledTheme.header(g, font, "PAYOUT", leftPos + DETAIL_X + 4, topPos + CONTENT_Y + 4,
                DETAIL_W - 8);
        long total = menu.trayValue();
        g.drawString(font, "TOTAL", leftPos + DETAIL_X + 5, topPos + CONTENT_Y + 22,
                StyledTheme.LABEL_DIM, false);
        String text = Money.withSymbol(total);
        g.drawString(font, text, leftPos + DETAIL_X + DETAIL_W - 5 - font.width(text),
                topPos + CONTENT_Y + 22,
                total > 0 ? StyledTheme.ACCENT : StyledTheme.LABEL_DIM, false);
        StyledTheme.caption(g, font, "TRADERS PAY BELOW LIST PRICE",
                leftPos + DETAIL_X + 5, topPos + CONTENT_Y + 34);
        StyledTheme.caption(g, font, "WITHDRAW PAYS OUT UP TO " + Money.withSymbol(10_000),
                leftPos + DETAIL_X + 5, topPos + CONTENT_Y + 44);

        drawWideButton(g, mouseX, mouseY, DETAIL_X + 4, SELL_ALL_Y, DETAIL_W - 8, "SELL ALL",
                total > 0);
        drawWideButton(g, mouseX, mouseY, DETAIL_X + 4, WITHDRAW_Y, DETAIL_W - 8,
                "WITHDRAW CASH", ClientWallet.get() > 0);
    }

    private void drawWideButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w,
                                String label, boolean enabled) {
        int px = leftPos + x;
        int py = topPos + y;
        boolean hovered = enabled && inBox(mouseX, mouseY, px, py, w, 14);
        g.fill(px, py, px + w, py + 14,
                hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        g.renderOutline(px, py, w, 14, enabled ? StyledTheme.SLOT_BORDER : StyledTheme.HEADER_ACCENT);
        int colour = enabled ? (hovered ? StyledTheme.ACCENT : StyledTheme.TEXT_COLOR)
                             : StyledTheme.LABEL_DIM;
        String shown = trim(label, w - 6);
        g.drawString(font, shown, px + (w - font.width(shown)) / 2, py + 3, colour, false);
    }

    // ---- confirmation ------------------------------------------------------

    private static final int CONFIRM_W = 210;
    private static final int CONFIRM_H = 76;

    private void drawConfirm(GuiGraphics g, int mouseX, int mouseY) {
        // Dim everything behind it, so it is obvious the rest of the screen is inert.
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0000000);

        int x = leftPos + (imageWidth - CONFIRM_W) / 2;
        int y = topPos + (imageHeight - CONFIRM_H) / 2;
        StyledTheme.panel(g, x, y, CONFIRM_W, CONFIRM_H);
        g.drawString(font, confirm.title(), x + 8, y + 8, StyledTheme.TEXT_COLOR, false);
        g.drawString(font, trim(confirm.line(), CONFIRM_W - 16), x + 8, y + 24,
                StyledTheme.HEADER_COLOR, false);
        g.drawString(font, confirm.cost(), x + 8, y + 38, StyledTheme.ACCENT, false);

        int bw = (CONFIRM_W - 24) / 2;
        drawConfirmButton(g, mouseX, mouseY, x + 8, y + CONFIRM_H - 22, bw, "CONFIRM", true);
        drawConfirmButton(g, mouseX, mouseY, x + 16 + bw, y + CONFIRM_H - 22, bw, "CANCEL", false);
    }

    private void drawConfirmButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w,
                                   String label, boolean primary) {
        boolean hovered = inBox(mouseX, mouseY, x, y, w, 14);
        g.fill(x, y, x + w, y + 14, hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        g.renderOutline(x, y, w, 14, primary ? StyledTheme.ACCENT : StyledTheme.SLOT_BORDER);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3,
                hovered ? StyledTheme.TEXT_COLOR : StyledTheme.HEADER_COLOR, false);
    }

    /** Every click is swallowed while the panel is up - that is the point of it. */
    private boolean confirmClick(double mouseX, double mouseY) {
        int x = leftPos + (imageWidth - CONFIRM_W) / 2;
        int y = topPos + (imageHeight - CONFIRM_H) / 2;
        int bw = (CONFIRM_W - 24) / 2;
        if (inBox(mouseX, mouseY, x + 8, y + CONFIRM_H - 22, bw, 14)) {
            accept();
        } else if (inBox(mouseX, mouseY, x + 16 + bw, y + CONFIRM_H - 22, bw, 14)) {
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
            int x = leftPos + 8 + i * (TAB_W + 2);
            if (inBox(mouseX, mouseY, x, topPos + TAB_Y, TAB_W, TAB_H)) {
                sellTab = i == 1;
                menu.sellTabActive = sellTab;
                return true;
            }
        }

        if (sellTab) {
            if (inBox(mouseX, mouseY, leftPos + DETAIL_X + 4, topPos + SELL_ALL_Y,
                    DETAIL_W - 8, 14)) {
                long total = menu.trayValue();
                if (total > 0) {
                    confirm = new Confirm("SELL ITEMS?",
                            countTrayItems() + " stack(s) in the tray",
                            "You receive " + Money.withSymbol(total),
                            () -> NetworkHandler.CHANNEL.sendToServer(new MarketPackets.Sell()));
                }
                return true;
            }
            if (inBox(mouseX, mouseY, leftPos + DETAIL_X + 4, topPos + WITHDRAW_Y,
                    DETAIL_W - 8, 14)) {
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
        int sy = topPos + SIDEBAR_LIST_Y;
        for (int i = sidebarScroll; i < Math.min(rows.size(), sidebarScroll + SIDEBAR_ROWS); i++) {
            if (inBox(mouseX, mouseY, leftPos + SIDEBAR_X + 1, sy, SIDEBAR_W - 2, SIDEBAR_ROW_H)) {
                Row row = rows.get(i);
                // Picking a category clears the section, so switching category never leaves
                // an inherited filter behind that silently empties the list.
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
            return true;
        }

        for (int i = 0; i < QUANTITIES.length; i++) {
            int x = leftPos + DETAIL_X + 5 + i * 31;
            if (inBox(mouseX, mouseY, x, topPos + QTY_ROW_Y, 29, 12)) {
                quantity = QUANTITIES[i];
                return true;
            }
        }

        if (inBox(mouseX, mouseY, leftPos + DETAIL_X + 4, topPos + BUY_BUTTON_Y, DETAIL_W - 8, 14)) {
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
        if (!sellTab && inBox(mouseX, mouseY, leftPos + SIDEBAR_X, topPos + SIDEBAR_LIST_Y,
                SIDEBAR_W, SIDEBAR_ROWS * SIDEBAR_ROW_H)) {
            int max = Math.max(0, sidebarRows().size() - SIDEBAR_ROWS);
            sidebarScroll = Math.max(0, Math.min(max, sidebarScroll - (int) Math.signum(delta)));
            return true;
        }
        if (!sellTab && inBox(mouseX, mouseY, leftPos + LIST_X, topPos + CONTENT_Y,
                LIST_W + 8, CONTENT_H)) {
            int max = Math.max(0, filtered.size() - VISIBLE_ROWS);
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
        if (mouseX < leftPos + LIST_X || mouseX >= leftPos + LIST_X + LIST_W) return -1;
        int rel = (int) (mouseY - (topPos + CONTENT_Y + 2));
        if (rel < 0 || rel >= ROW_H * VISIBLE_ROWS) return -1;
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
