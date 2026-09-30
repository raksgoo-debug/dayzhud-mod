package com.dayzhud.mod.inventory.stash;

import com.dayzhud.mod.inventory.TarkovInventoryMenu;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's personal stash (2.14.0): one 9x10 grid per player, the same contents from
 * any stash block, and nobody else's to open. Tarkov's stash - what you keep between raids.
 *
 * Saved once, with the overworld, rather than per block or per dimension: a stash belongs to
 * the player, not to where it was opened. Opened as the ordinary inventory-plus-container
 * screen, so it gets the grid, footprints, rotation and shift-click placement for free.
 * Items are saved with their slot indices - their grid positions are part of the layout.
 */
public class StashData extends SavedData {

    public static final int COLS = TarkovInventoryMenu.CONTAINER_COLS, ROWS = 10, SIZE = COLS * ROWS;
    private static final String NAME = "dayzhud_stashes";

    private final Map<UUID, SimpleContainer> stashes = new HashMap<>();

    public static StashData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StashData::load, StashData::new, NAME);
    }

    /** Opens {@code player}'s stash. */
    public static void open(ServerPlayer player) {
        SimpleContainer stash = get(player.server).stashOf(player.getUUID());
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((windowId, inv, p) -> new TarkovInventoryMenu(windowId, inv, stash),
                        Component.translatable("container.dayzhud.stash")),
                buf -> {
                    // TarkovMenuTypes' payload: container size, not a corpse, no curios.
                    buf.writeVarInt(SIZE);
                    buf.writeBoolean(false);
                    buf.writeVarInt(0);
                });
    }

    private SimpleContainer stashOf(UUID owner) {
        return stashes.computeIfAbsent(owner, id -> newStash());
    }

    private SimpleContainer newStash() {
        return new SimpleContainer(SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                setDirty();
            }

            @Override
            public void stopOpen(Player player) {
                super.stopOpen(player);
                setDirty();
            }
        };
    }

    public static StashData load(CompoundTag tag) {
        StashData data = new StashData();
        ListTag list = tag.getList("Stashes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Owner")) continue;
            NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(entry, items);
            SimpleContainer stash = data.newStash();
            for (int s = 0; s < SIZE; s++) stash.setItem(s, items.get(s));
            data.stashes.put(entry.getUUID("Owner"), stash);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, SimpleContainer> e : stashes.entrySet()) {
            SimpleContainer stash = e.getValue();
            NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            for (int s = 0; s < SIZE; s++) items.set(s, stash.getItem(s));
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", e.getKey());
            ContainerHelper.saveAllItems(entry, items, true);
            list.add(entry);
        }
        tag.put("Stashes", list);
        return tag;
    }
}
