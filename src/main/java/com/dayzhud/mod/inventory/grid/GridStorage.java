package com.dayzhud.mod.inventory.grid;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * The minimum {@link ItemGrid} actually needs: read a stack by index, write a stack by
 * index. {@code Container} and {@code IItemHandlerModifiable} both already offer exactly
 * this, under different method names, so this exists purely to let {@link ItemGrid} operate
 * over either without caring which - the player's own inventory and an opened chest are
 * {@code Container}s; a worn backpack and a corpse's loot bag are {@code IItemHandlerModifiable}s
 * (a {@link ScrollingBackpackView}, specifically, whose own index space is already "whatever
 * the current scroll window is showing" - exactly the fixed-size, indexed view {@link
 * ItemGrid} wants, so it needs no special handling here at all).
 */
public interface GridStorage {

    ItemStack get(int index);

    void set(int index, ItemStack stack);

    static GridStorage of(Container container) {
        return new GridStorage() {
            public ItemStack get(int index) {
                return container.getItem(index);
            }

            public void set(int index, ItemStack stack) {
                container.setItem(index, stack);
            }
        };
    }

    static GridStorage of(IItemHandlerModifiable handler) {
        return new GridStorage() {
            public ItemStack get(int index) {
                return handler.getStackInSlot(index);
            }

            public void set(int index, ItemStack stack) {
                // A raw setter by contract (unlike insertItem/extractItem, which validate) -
                // exactly the semantics reconcile() needs, matching Container.setItem.
                handler.setStackInSlot(index, stack);
            }
        };
    }
}
