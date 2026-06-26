package com.hfstudio.diskterminal.util;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * Helpers that provide capability-style insert/extract semantics over a 1.7.10 {@link IInventory}.
 * <p>
 * The 1.12 source operated on Forge's {@code IItemHandler}; in 1.7.10 cell config/upgrade
 * inventories are plain {@code IInventory}. These helpers mirror the handful of
 * {@code IItemHandler} operations the port relies on so handler logic stays close to the original.
 */
public class InventoryHelper {

    private InventoryHelper() {}

    /**
     * Insert a single-slot stack, honoring the slot's accept rule and max stack size.
     * Returns the remainder that did not fit (null when everything fit).
     */
    public static ItemStack insert(IInventory inv, int slot, ItemStack stack, boolean simulate) {
        if (ItemStacks.isEmpty(stack)) return null;
        if (!inv.isItemValidForSlot(slot, stack)) return stack;

        ItemStack existing = inv.getStackInSlot(slot);
        int limit = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());

        if (ItemStacks.isEmpty(existing)) {
            int toPlace = Math.min(limit, stack.stackSize);
            if (!simulate) {
                ItemStack placed = stack.copy();
                placed.stackSize = toPlace;
                inv.setInventorySlotContents(slot, placed);
            }
            if (toPlace >= stack.stackSize) return null;
            ItemStack rem = stack.copy();
            rem.stackSize -= toPlace;
            return rem;
        }

        if (!existing.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(existing, stack)) return stack;

        int space = limit - existing.stackSize;
        if (space <= 0) return stack;

        int toPlace = Math.min(space, stack.stackSize);
        if (!simulate) {
            existing.stackSize += toPlace;
            inv.setInventorySlotContents(slot, existing);
        }
        if (toPlace >= stack.stackSize) return null;
        ItemStack rem = stack.copy();
        rem.stackSize -= toPlace;
        return rem;
    }

    /**
     * Extract up to {@code amount} from a slot. Returns what was removed (null if nothing).
     */
    public static ItemStack extract(IInventory inv, int slot, int amount, boolean simulate) {
        ItemStack existing = inv.getStackInSlot(slot);
        if (ItemStacks.isEmpty(existing)) return null;

        int toRemove = Math.min(amount, existing.stackSize);
        ItemStack removed = existing.copy();
        removed.stackSize = toRemove;

        if (!simulate) {
            if (toRemove >= existing.stackSize) {
                inv.setInventorySlotContents(slot, null);
            } else {
                ItemStack left = existing.copy();
                left.stackSize -= toRemove;
                inv.setInventorySlotContents(slot, left);
            }
        }

        return removed;
    }

    /**
     * Set a slot's contents (single-item config semantics). Null clears the slot.
     */
    public static void setSlot(IInventory inv, int slot, ItemStack stack) {
        inv.setInventorySlotContents(slot, ItemStacks.isEmpty(stack) ? null : stack.copy());
    }

    /**
     * Clear every slot of an inventory.
     */
    public static void clear(IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) inv.setInventorySlotContents(i, null);
    }

    /**
     * Find the first empty slot, or -1 if full.
     */
    public static int findEmptySlot(IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (ItemStacks.isEmpty(inv.getStackInSlot(i))) return i;
        }

        return -1;
    }
}
