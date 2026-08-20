package com.dayzhud.mod.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.NetworkEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Makes the loadout panel beside container screens genuinely interactive.
 *
 * WHY A PACKET INSTEAD OF REAL SLOTS: Minecraft allows one open container menu at a time,
 * and Slots belong to that menu. Injecting equipment slots into every container's menu
 * would mean mutating the menu's parallel slot/lastSlot/remoteSlot lists identically on
 * both sides - any mismatch desyncs or duplicates items. Instead the panel sends the click
 * here, and the server performs the swap against the open menu's CARRIED stack, which is
 * already synced by vanilla. Same end result (pick up / place / swap gear while a container
 * is open) with none of the slot-list desync risk.
 *
 * Curios ordering is the iteration order of the player's curios map, which is derived from
 * the same registered slot types on both sides, so index N means the same slot to both.
 */
public class LoadoutClickPacket {

    public static final int KIND_ARMOR = 0;
    public static final int KIND_CURIO = 1;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final int kind;
    private final int index;

    public LoadoutClickPacket(int kind, int index) {
        this.kind = kind;
        this.index = index;
    }

    public static void encode(LoadoutClickPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.kind);
        buf.writeVarInt(packet.index);
    }

    public static LoadoutClickPacket decode(FriendlyByteBuf buf) {
        return new LoadoutClickPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(LoadoutClickPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null) return;

            if (packet.kind == KIND_ARMOR) {
                handleArmor(player, menu, packet.index);
            } else {
                handleCurio(player, menu, packet.index);
            }
            menu.broadcastChanges();
        });
        ctx.setPacketHandled(true);
    }

    private static void handleArmor(ServerPlayer player, AbstractContainerMenu menu, int index) {
        if (index < 0 || index >= ARMOR.length) return;
        EquipmentSlot slot = ARMOR[index];

        ItemStack carried = menu.getCarried();
        ItemStack current = player.getItemBySlot(slot);

        if (carried.isEmpty()) {
            if (current.isEmpty()) return;
            player.setItemSlot(slot, ItemStack.EMPTY);
            menu.setCarried(current);
            return;
        }

        // Only accept armour that actually belongs in this slot.
        if (LivingEntity.getEquipmentSlotForItem(carried) != slot) return;

        player.setItemSlot(slot, carried);
        menu.setCarried(current);
    }

    private static void handleCurio(ServerPlayer player, AbstractContainerMenu menu, int index) {
        Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOpt.isEmpty()) return;

        int cursor = 0;
        for (Map.Entry<String, ICurioStacksHandler> entry : curiosOpt.get().getCurios().entrySet()) {
            ICurioStacksHandler handler = entry.getValue();
            if (handler == null) continue;
            IItemHandlerModifiable stacks = handler.getStacks();

            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                // The panel only draws non-empty curios, so only those are clickable and
                // the index counting here must match: skip empties.
                if (stack.isEmpty()) continue;

                if (cursor == index) {
                    ItemStack carried = menu.getCarried();
                    if (carried.isEmpty()) {
                        stacks.setStackInSlot(i, ItemStack.EMPTY);
                        menu.setCarried(stack);
                    } else if (stacks.isItemValid(i, carried)) {
                        stacks.setStackInSlot(i, carried);
                        menu.setCarried(stack);
                    }
                    return;
                }
                cursor++;
            }
        }
    }
}
