package com.dayzhud.mod.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
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
 * properly synced, and it picks up this mod's styling because StyledScreens substitutes
 * StyledContainerScreen at screen-open time.
 *
 * The menu is a {@link PortableCraftingMenu}, NOT a bare CraftingMenu - see that class for
 * why a plain one built on ContainerLevelAccess.NULL could never craft anything at all.
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
            // A REAL level access, anchored where the player is standing. CraftingMenu runs
            // both its result calculation and its give-your-items-back-on-close through
            // access.execute(), and ContainerLevelAccess.NULL never invokes the callback at
            // all - so with NULL the grid could never produce a result and ate anything left
            // on it. PortableCraftingMenu re-opens stillValid() so the real access doesn't
            // then demand an actual crafting table block underfoot.
            ContainerLevelAccess access =
                    ContainerLevelAccess.create(sender.level(), sender.blockPosition());
            NetworkHooks.openScreen(sender, new SimpleMenuProvider(
                    (windowId, inv, p) -> new PortableCraftingMenu(windowId, inv, access),
                    Component.literal("Crafting")));
        });
        ctx.setPacketHandled(true);
    }
}
