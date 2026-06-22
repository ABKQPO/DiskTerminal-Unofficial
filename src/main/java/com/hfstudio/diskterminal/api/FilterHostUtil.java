package com.hfstudio.diskterminal.api;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Utility methods for working with {@link IFilterHost} instances.
 */
public final class FilterHostUtil {

    private FilterHostUtil() {}

    /**
     * Snapshot all filter slots while preserving empty positions.
     */
    @Nonnull
    public static List<ItemStack> snapshotFilters(@Nonnull IFilterHost host) {
        List<ItemStack> filters = new ArrayList<>();
        int slotCount = Math.max(0, host.getFilterSlots());

        for (int slot = 0; slot < slotCount; slot++) {
            filters.add(normalizeFilter(host.getFilter(slot)));
        }

        return filters;
    }

    /**
     * Find the first slot containing a matching filter entry.
     */
    public static int findFilterSlot(@Nonnull IFilterHost host, ItemStack stack) {
        ItemStack normalized = normalizeFilter(stack);
        if (ItemStacks.isEmpty(normalized)) return -1;

        int slotCount = Math.max(0, host.getFilterSlots());
        for (int slot = 0; slot < slotCount; slot++) {
            if (matchesFilter(host.getFilter(slot), normalized)) return slot;
        }

        return -1;
    }

    /**
     * Add a filter to the first empty slot, skipping duplicates.
     */
    public static boolean addFilter(@Nonnull IFilterHost host, ItemStack stack) {
        ItemStack normalized = normalizeFilter(stack);
        if (ItemStacks.isEmpty(normalized)) return false;

        int slotCount = Math.max(0, host.getFilterSlots());
        int emptySlot = -1;

        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack existing = normalizeFilter(host.getFilter(slot));
            if (matchesFilter(existing, normalized)) return false;
            if (emptySlot < 0 && ItemStacks.isEmpty(existing)) emptySlot = slot;
        }

        if (emptySlot < 0) return false;

        host.setFilter(emptySlot, normalized);
        return true;
    }

    /**
     * Remove the first matching filter entry.
     */
    public static boolean removeFilter(@Nonnull IFilterHost host, ItemStack stack) {
        int slot = findFilterSlot(host, stack);
        if (slot < 0) return false;

        host.setFilter(slot, null);
        return true;
    }

    /**
     * Toggle a filter entry by exact item plus NBT identity.
     */
    public static boolean toggleFilter(@Nonnull IFilterHost host, ItemStack stack) {
        if (removeFilter(host, stack)) return true;
        return addFilter(host, stack);
    }

    /**
     * Compare two filter stacks by item and NBT only, ignoring count.
     */
    public static boolean matchesFilter(ItemStack left, ItemStack right) {
        boolean leftEmpty = ItemStacks.isEmpty(left);
        boolean rightEmpty = ItemStacks.isEmpty(right);
        if (leftEmpty || rightEmpty) return leftEmpty && rightEmpty;
        return left.isItemEqual(right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    /**
     * Normalize a filter stack to a single-item identity stack.
     */
    public static ItemStack normalizeFilter(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return null;

        ItemStack normalized = stack.copy();
        normalized.stackSize = 1;
        return normalized;
    }
}
