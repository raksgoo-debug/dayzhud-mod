package com.dayzhud.mod.inventory;

import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An interactive loadout panel drawn to the LEFT of container screens, so you can see AND
 * rearrange your gear while looting.
 *
 * These aren't real container Slots - Minecraft only allows one open menu at a time, and
 * injecting slots into every container's menu risks desyncing its parallel slot lists.
 * Instead clicks are hit-tested here and sent as LoadoutClickPacket, which performs the
 * swap server-side against the open menu's carried stack (already vanilla-synced). The
 * behaviour matches normal slots: click to pick up, click again to place or swap.
 */
public final class LoadoutSidePanel {

    public static final int WIDTH = 108;
    public static final int HEIGHT = 196;

    private static final int SLOT = 18;

    private LoadoutSidePanel() {}

    public static void render(GuiGraphics graphics, Font font, LocalPlayer player, int x, int y,
                              int mouseX, int mouseY) {
        StyledTheme.panel(graphics, x, y, WIDTH, HEIGHT);
        StyledTheme.header(graphics, font, "LOADOUT", x + 8, y + 8, 44);

        // Paperdoll on the right of this panel, armor column on the left.
        StyledTheme.zone(graphics, x + 6, y + 24, x + WIDTH - 6, y + 126);
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x + 74, y + 118, 38,
                -0.8f, 0.0f, player);

        EquipmentSlot[] armor = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < armor.length; i++) {
            int sx = x + 12;
            int sy = y + 30 + i * 24;
            StyledTheme.slot(graphics, sx, sy);
            if (over(mouseX, mouseY, sx, sy)) {
                graphics.fill(sx, sy, sx + 16, sy + 16, 0x60FFFFFF);
            }
            drawStack(graphics, font, player.getItemBySlot(armor[i]), sx, sy, mouseX, mouseY);
        }

        // Curios row underneath - whatever the player actually has equipped.
        List<ItemStack> curios = equippedCurios(player);
        StyledTheme.header(graphics, font, "GEAR", x + 8, y + 130, 26);
        StyledTheme.zone(graphics, x + 6, y + 142, x + WIDTH - 6, y + 164);
        for (int i = 0; i < Math.min(curios.size(), 5); i++) {
            int sx = x + 10 + i * SLOT;
            int sy = y + 146;
            StyledTheme.slot(graphics, sx, sy);
            if (over(mouseX, mouseY, sx, sy)) {
                graphics.fill(sx, sy, sx + 16, sy + 16, 0x60FFFFFF);
            }
            drawStack(graphics, font, curios.get(i), sx, sy, mouseX, mouseY);
        }

        drawStats(graphics, font, player, x + 10, y + 172);
    }

    private static void drawStack(GuiGraphics graphics, Font font, ItemStack stack, int x, int y,
                                  int mouseX, int mouseY) {
        if (stack.isEmpty()) return;
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
    }

    /** Tooltip for whichever loadout slot the cursor is over; call after the main screen's own tooltip. */
    public static void renderTooltip(GuiGraphics graphics, Font font, LocalPlayer player, int x, int y,
                                     int mouseX, int mouseY) {
        EquipmentSlot[] armor = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < armor.length; i++) {
            if (over(mouseX, mouseY, x + 12, y + 30 + i * 24)) {
                ItemStack stack = player.getItemBySlot(armor[i]);
                if (!stack.isEmpty()) graphics.renderTooltip(font, stack, mouseX, mouseY);
                return;
            }
        }
        List<ItemStack> curios = equippedCurios(player);
        for (int i = 0; i < Math.min(curios.size(), 5); i++) {
            if (over(mouseX, mouseY, x + 10 + i * SLOT, y + 146)) {
                ItemStack stack = curios.get(i);
                if (!stack.isEmpty()) graphics.renderTooltip(font, stack, mouseX, mouseY);
                return;
            }
        }
    }

    private static boolean over(double mouseX, double mouseY, int sx, int sy) {
        return mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16;
    }

    /** Which loadout slot (if any) is under the cursor. Null when nothing is. */
    public static Hit hitTest(LocalPlayer player, int x, int y, double mouseX, double mouseY) {
        for (int i = 0; i < 4; i++) {
            if (over(mouseX, mouseY, x + 12, y + 30 + i * 24)) {
                return new Hit(LoadoutClickPacket.KIND_ARMOR, i);
            }
        }
        int curioCount = Math.min(equippedCurios(player).size(), 5);
        for (int i = 0; i < curioCount; i++) {
            if (over(mouseX, mouseY, x + 10 + i * SLOT, y + 146)) {
                return new Hit(LoadoutClickPacket.KIND_CURIO, i);
            }
        }
        return null;
    }

    public record Hit(int kind, int index) {}

    private static List<ItemStack> equippedCurios(LocalPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOpt.isEmpty()) return out;
        for (Map.Entry<String, ICurioStacksHandler> entry : curiosOpt.get().getCurios().entrySet()) {
            ICurioStacksHandler handler = entry.getValue();
            if (handler == null) continue;
            for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                ItemStack stack = handler.getStacks().getStackInSlot(i);
                if (!stack.isEmpty()) out.add(stack);
            }
        }
        return out;
    }

    private static void drawStats(GuiGraphics graphics, Font font, LocalPlayer player, int x, int y) {
        float health01 = player.getHealth() / Math.max(1f, player.getMaxHealth());
        float food01 = player.getFoodData().getFoodLevel() / 20f;
        float water01 = ThirstWasTakenCompat.getThirst01(player)
                .orElseGet(() -> player.getFoodData().getSaturationLevel() / 20f);

        drawStat(graphics, font, "icon_heart_solid", health01, x, y);
        drawStat(graphics, font, "icon_food_solid", food01, x + 32, y);
        drawStat(graphics, font, "icon_droplet_solid", water01, x + 64, y);
    }

    private static void drawStat(GuiGraphics graphics, Font font, String icon, float value01, int x, int y) {
        int color = value01 <= 0.25f ? 0xE23A2E : value01 <= 0.5f ? 0xE2A62E : 0xE6E6E6;
        ResourceLocation rl = new ResourceLocation("dayzhud", "textures/gui/" + icon + ".png");

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f, 1f);
        graphics.blit(rl, x, y, 0, 0, 10, 10, 10, 10);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        graphics.pose().pushPose();
        graphics.pose().translate(x + 12, y + 2, 0);
        graphics.pose().scale(0.7f, 0.7f, 1f);
        graphics.drawString(font, Math.round(value01 * 100) + "%", 0, 0, StyledTheme.TEXT_COLOR, false);
        graphics.pose().popPose();
    }
}
