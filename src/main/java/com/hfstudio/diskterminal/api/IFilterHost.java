package com.hfstudio.diskterminal.api;

import net.minecraft.item.ItemStack;

/**
 * Shared filter contract for CELLS-style devices.
 */
public interface IFilterHost {

    /**
     * Total number of currently available filter slots.
     */
    int getFilterSlots();

    /**
     * Get the filter in the given slot.
     */
    ItemStack getFilter(int slot);

    /**
     * Set or clear the filter in the given slot.
     */
    void setFilter(int slot, ItemStack stack);

    /**
     * Clear every filter slot.
     */
    void clearFilters();
}
