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
import java.util.List;
import java.util.Locale;

/**
 * The trader screen: a filtered, scrollable buy list on the left and a sell tray on the
 * right, over the player's inventory.
 *
 * Styled entirely from {@link StyledTheme} so it matches the rest of this mod's UI - the
 * whole point of that class is that the palette lives in one place.
 */
public class MarketScreen extends AbstractContainerScreen<MarketMenu> {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 50;
    private static final int LIST_W = 188;
    private static final int VISIBLE_ROWS = 5;
    private static final int LIST_H = ROW_HEIGHT * VISIBLE_ROWS;
    private static final int SCROLLBAR_X = LIST_X + LIST_W + 2;

    private static final int TABS_Y = 34;
    private static final int SELL_BUTTON_Y = 122;
    private static final int WITHDRAW_BUTTON_Y = 142;
    private static final int PANEL_X = 204;
    private static final int PANEL_W = 124;

    private EditBox search;
    private String category = "";
    private int scroll;
    private List<Integer> filtered = new ArrayList<>();

    public MarketScreen(MarketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 336;
        this.imageHeight = 250;
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = MarketMenu.INV_Y - 11;
        this.titleLabelX = 8;
        this.titleLabelY = 8;

        search = new EditBox(this.font, leftPos + LIST_X + 1, topPos + 18, LIST_W - 2, 12,
                Component.translatable("gui.dayzhud.market.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(StyledTheme.TEXT_COLOR);
        search.setResponder(s -> {
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(search);
        rebuild();
    }

    /** Recomputes the visible offer indices from the current tab and search text. */
    private void rebuild() {
        filtered = new ArrayList<>();
        List<MarketOffer> offers = ClientMarketState.offers();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < offers.size(); i++) {
            MarketOffer offer = offers.get(i);
            if (!category.isEmpty() && !category.equals(offer.category())) continue;
            if (!query.isEmpty()) {
                String name = offer.prototype().getHoverName().getString().toLowerCase(Locale.ROOT);
                if (!name.contains(query)) continue;
            }
            filtered.add(i);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - VISIBLE_ROWS)));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        StyledTheme.panel(g, leftPos, topPos, imageWidth, imageHeight);
        StyledTheme.zone(g, leftPos + LIST_X, topPos + 16, leftPos + LIST_X + LIST_W, topPos + 30);
        StyledTheme.zone(g, leftPos + LIST_X, topPos + LIST_Y,
                leftPos + LIST_X + LIST_W, topPos + LIST_Y + LIST_H);
        StyledTheme.zone(g, leftPos + PANEL_X, topPos + 46,
                leftPos + PANEL_X + PANEL_W, topPos + 158);

        for (Slotish s : slotBackers()) StyledTheme.slot(g, leftPos + s.x(), topPos + s.y());
    }

    private record Slotish(int x, int y) {}

    private List<Slotish> slotBackers() {
        List<Slotish> out = new ArrayList<>();
        for (int i = 0; i < MarketMenu.SELL_SLOTS; i++) {
            out.add(new Slotish(MarketMenu.TRAY_X + (i % 3) * 18, MarketMenu.TRAY_Y + (i / 3) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                out.add(new Slotish(MarketMenu.INV_X + col * 18, MarketMenu.INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            out.add(new Slotish(MarketMenu.INV_X + col * 18, MarketMenu.HOTBAR_Y));
        }
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // AbstractContainerScreen.render already calls renderBackground; calling it here as
        // well dims the world twice and makes the panel read as murky rather than dark.
        super.render(g, mouseX, mouseY, partialTick);
        drawBalance(g);
        drawTabs(g, mouseX, mouseY);
        drawOffers(g, mouseX, mouseY);
        drawSellPanel(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
        drawOfferTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "TRADER", titleLabelX, titleLabelY, StyledTheme.HEADER_COLOR, false);
        StyledTheme.header(g, font, "STOCK", LIST_X, LIST_Y - 12, LIST_W);
        StyledTheme.header(g, font, "SELL", PANEL_X, 46, PANEL_W);
        // Below the tray, not beside it: the tray is three slots (54 px) wide starting at
        // TRAY_X 213, so a caption at tray height sits on top of it.
        StyledTheme.caption(g, font, "DRAG ITEMS HERE TO SELL", PANEL_X + 6, MarketMenu.TRAY_Y + 56);
    }

    private void drawBalance(GuiGraphics g) {
        String text = Money.withSymbol(ClientWallet.get());
        int w = font.width(text);
        g.drawString(font, text, leftPos + imageWidth - 8 - w, topPos + 8, StyledTheme.ACCENT, false);
        if (search.getValue().isEmpty() && !search.isFocused()) {
            StyledTheme.caption(g, font, "SEARCH", leftPos + LIST_X + 3, topPos + 21);
        }
    }

    // ---------------------------------------------------------------- category tabs

    private List<String> tabs() {
        List<String> out = new ArrayList<>();
        out.add("");   // ALL
        List<String> seen = new ArrayList<>();
        for (MarketOffer o : ClientMarketState.offers()) {
            if (!seen.contains(o.category())) seen.add(o.category());
        }
        seen.sort(String::compareTo);
        out.addAll(seen);
        return out;
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        for (String tab : tabs()) {
            String label = tab.isEmpty() ? "ALL" : tab.toUpperCase(Locale.ROOT);
            int w = (int) (font.width(label) * 0.8f) + 8;
            boolean active = tab.equals(category);
            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= topPos + TABS_Y && mouseY < topPos + TABS_Y + 12;
            g.fill(x, topPos + TABS_Y, x + w, topPos + TABS_Y + 12,
                    active ? StyledTheme.BUTTON_BG_HOVER
                           : (hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG));
            if (active) g.fill(x, topPos + TABS_Y + 11, x + w, topPos + TABS_Y + 12, StyledTheme.ACCENT);
            g.pose().pushPose();
            g.pose().translate(x + 4, topPos + TABS_Y + 3, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, label, 0, 0,
                    active ? StyledTheme.TEXT_COLOR : StyledTheme.LABEL_DIM, false);
            g.pose().popPose();
            x += w + 2;
            // Tabs wrap rather than running off the panel when a pack adds categories.
            if (x > leftPos + LIST_X + LIST_W - 20) break;
        }
    }

    // ---------------------------------------------------------------- offer list

    private void drawOffers(GuiGraphics g, int mouseX, int mouseY) {
        List<MarketOffer> offers = ClientMarketState.offers();
        int top = topPos + LIST_Y;
        g.enableScissor(leftPos + LIST_X, top, leftPos + LIST_X + LIST_W, top + LIST_H);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scroll + row;
            if (idx >= filtered.size()) break;
            MarketOffer offer = offers.get(filtered.get(idx));
            int y = top + row * ROW_HEIGHT;
            boolean hovered = mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) g.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_W, y + ROW_HEIGHT,
                    StyledTheme.BUTTON_BG_HOVER);
            g.fill(leftPos + LIST_X, y + ROW_HEIGHT - 1, leftPos + LIST_X + LIST_W,
                    y + ROW_HEIGHT, StyledTheme.HEADER_ACCENT);

            ItemStack stack = offer.prototype();
            g.renderItem(stack, leftPos + LIST_X + 4, y + 3);
            g.renderItemDecorations(font, stack, leftPos + LIST_X + 4, y + 3);

            String name = stack.getHoverName().getString();
            int nameWidth = LIST_W - 30 - 56;
            if (font.width(name) > nameWidth) name = font.plainSubstrByWidth(name, nameWidth - 6) + "...";
            g.drawString(font, name, leftPos + LIST_X + 26, y + 7, StyledTheme.TEXT_COLOR, false);

            String price = Money.withSymbol(offer.price());
            boolean affordable = ClientWallet.get() >= offer.price();
            g.drawString(font, price,
                    leftPos + LIST_X + LIST_W - 4 - font.width(price), y + 7,
                    affordable ? StyledTheme.ACCENT : 0xFFB04A3A, false);
        }
        g.disableScissor();

        if (filtered.isEmpty()) {
            String msg = ClientMarketState.offers().isEmpty()
                    ? "NO STOCK" : "NOTHING MATCHES";
            g.drawString(font, msg, leftPos + LIST_X + 8, top + 8, StyledTheme.LABEL_DIM, false);
        }
        drawScrollbar(g);
    }

    private void drawScrollbar(GuiGraphics g) {
        int trackTop = topPos + LIST_Y;
        g.fill(leftPos + SCROLLBAR_X, trackTop, leftPos + SCROLLBAR_X + 3, trackTop + LIST_H,
                StyledTheme.SLOT_BG);
        int max = Math.max(1, filtered.size());
        int thumb = Math.max(8, LIST_H * VISIBLE_ROWS / max);
        if (thumb >= LIST_H) return;
        int range = LIST_H - thumb;
        int maxScroll = Math.max(1, filtered.size() - VISIBLE_ROWS);
        int y = trackTop + range * scroll / maxScroll;
        g.fill(leftPos + SCROLLBAR_X, y, leftPos + SCROLLBAR_X + 3, y + thumb, StyledTheme.SLOT_BORDER);
    }

    private void drawOfferTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int idx = rowAt(mouseX, mouseY);
        if (idx < 0) return;
        MarketOffer offer = ClientMarketState.offers().get(filtered.get(idx));
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, offer.prototype()));
        lines.add(Component.literal(""));
        lines.add(Component.translatable("gui.dayzhud.market.price",
                Money.withSymbol(offer.price())));
        lines.add(Component.translatable("gui.dayzhud.market.buy_hint"));
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < leftPos + LIST_X || mouseX >= leftPos + LIST_X + LIST_W) return -1;
        int rel = (int) (mouseY - (topPos + LIST_Y));
        if (rel < 0 || rel >= LIST_H) return -1;
        int idx = scroll + rel / ROW_HEIGHT;
        return idx < filtered.size() ? idx : -1;
    }

    // ---------------------------------------------------------------- sell panel

    private void drawSellPanel(GuiGraphics g, int mouseX, int mouseY) {
        long total = menu.trayValue();
        String text = Money.withSymbol(total);
        g.drawString(font, "TOTAL", leftPos + PANEL_X + 4, topPos + 112, StyledTheme.LABEL_DIM, false);
        g.drawString(font, text, leftPos + PANEL_X + PANEL_W - 4 - font.width(text), topPos + 112,
                total > 0 ? StyledTheme.ACCENT : StyledTheme.LABEL_DIM, false);

        drawButton(g, mouseX, mouseY, SELL_BUTTON_Y, "SELL", total > 0);
        drawButton(g, mouseX, mouseY, WITHDRAW_BUTTON_Y, "WITHDRAW \u20BD10 000",
                ClientWallet.get() > 0);
    }

    private void drawButton(GuiGraphics g, int mouseX, int mouseY, int y, String label, boolean enabled) {
        int x = leftPos + PANEL_X + 4;
        int w = PANEL_W - 8;
        boolean hovered = enabled && mouseX >= x && mouseX < x + w
                && mouseY >= topPos + y && mouseY < topPos + y + 14;
        g.fill(x, topPos + y, x + w, topPos + y + 14,
                hovered ? StyledTheme.BUTTON_BG_HOVER : StyledTheme.BUTTON_BG);
        g.renderOutline(x, topPos + y, w, 14,
                enabled ? StyledTheme.SLOT_BORDER : StyledTheme.HEADER_ACCENT);
        int colour = enabled ? (hovered ? StyledTheme.ACCENT : StyledTheme.TEXT_COLOR)
                             : StyledTheme.LABEL_DIM;
        g.drawString(font, label, x + (w - font.width(label)) / 2, topPos + y + 3, colour, false);
    }

    private boolean overButton(double mouseX, double mouseY, int y) {
        int x = leftPos + PANEL_X + 4;
        return mouseX >= x && mouseX < x + PANEL_W - 8
                && mouseY >= topPos + y && mouseY < topPos + y + 14;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int idx = rowAt(mouseX, mouseY);
            if (idx >= 0) {
                int count = hasShiftDown() ? 5 : (hasControlDown() ? 16 : 1);
                NetworkHandler.CHANNEL.sendToServer(new MarketPackets.Buy(
                        ClientMarketState.revision(), filtered.get(idx), count));
                return true;
            }
            if (overButton(mouseX, mouseY, SELL_BUTTON_Y)) {
                NetworkHandler.CHANNEL.sendToServer(new MarketPackets.Sell());
                return true;
            }
            if (overButton(mouseX, mouseY, WITHDRAW_BUTTON_Y)) {
                NetworkHandler.CHANNEL.sendToServer(new MarketPackets.Withdraw(10_000L));
                return true;
            }
            int x = leftPos + LIST_X;
            for (String tab : tabs()) {
                String label = tab.isEmpty() ? "ALL" : tab.toUpperCase(Locale.ROOT);
                int w = (int) (font.width(label) * 0.8f) + 8;
                if (mouseX >= x && mouseX < x + w
                        && mouseY >= topPos + TABS_Y && mouseY < topPos + TABS_Y + 12) {
                    category = tab;
                    scroll = 0;
                    rebuild();
                    return true;
                }
                x += w + 2;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W + 6
                && mouseY >= topPos + LIST_Y && mouseY < topPos + LIST_Y + LIST_H) {
            int max = Math.max(0, filtered.size() - VISIBLE_ROWS);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // While the search box has focus, letters must reach it - otherwise typing "e"
        // closes the screen, which is the classic EditBox-in-a-container-screen bug.
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
}
