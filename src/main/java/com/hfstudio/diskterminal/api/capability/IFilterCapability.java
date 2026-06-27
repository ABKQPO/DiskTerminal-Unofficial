package com.hfstudio.diskterminal.api.capability;

import java.util.List;

import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;

/**
 * Filter (partition) editing behavior for a target with a configurable filter inventory.
 * <p>
 * {@link #fillFromPreview()} only models the current behavior slice and may later evolve into a more
 * generic {@code applyFilter(FilterSource)}; it is intentionally not abstracted further here.
 */
public interface IFilterCapability extends ICapability {

    /**
     * Number of available filter slots.
     */
    int getFilterSlotCount();

    /**
     * The current filter entries.
     */
    List<FilterSnapshot> getFilters();

    /**
     * Set the filter at the given slot.
     *
     * @return true if the filter changed
     */
    boolean setFilter(int slot, FilterSnapshot filter);

    /**
     * Clear the filter at the given slot.
     *
     * @return true if the filter changed
     */
    boolean clearFilter(int slot);

    /**
     * Clear every filter slot.
     */
    void clearAllFilters();

    /**
     * Toggle the given filter: remove it if present, otherwise add it to the first free slot.
     *
     * @return true if the filter changed
     */
    boolean toggleFilter(FilterSnapshot filter);

    /**
     * Whether this target can fill its filters from its currently visible contents.
     */
    boolean supportsPreviewFill();

    /**
     * Fill the filter slots from the target's currently visible contents.
     *
     * @return true if the filter changed
     */
    boolean fillFromPreview();
}
