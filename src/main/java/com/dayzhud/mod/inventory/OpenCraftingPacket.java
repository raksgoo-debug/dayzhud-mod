package com.dayzhud.mod.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * Opens the full 3x3 crafting screen from the button beside the INVENTORY header.
 *
 * BALANCE NOTE: this is effectively a portable crafting table - the player no longer needs
 * to place one down. That's an intentional convenience for an extraction-shooter style
 * loadout screen, but it IS a gameplay change. To make it require a real table instead,
 * delete the button in TarkovInventoryScreen and this packet; the in-panel 2x2 grid stays
 * either way and matches vanilla behaviour.
 *
 * The menu is opened server-side through the normal NetworkHooks flow so the container is
 * properly synced, and it renders with this mod's styling because StyledScreens
 * re-registers MenuType.CRAFTING against StyledContainerScreen.
 */
public class OpenCraftingPacket {

    public static void encode(OpenCraftingPacket packet, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenCraftingPacket decode(FriendlyByteBuf buf) {
        return new OpenCraftingPacket();
    }

    public static void handle(OpenCraftingPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            NetworkHooks.openScreen(sender, new SimpleMenuProvider(
                    (windowId, inv, p) -> new CraftingMenu(windowId, inv, ContainerLevelAccess.NULL),
                    Component.literal("Crafting")));
        });
        ctx.setPacketHandled(true);
    }
}
