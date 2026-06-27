package com.hfstudio.diskterminal.api.snapshot;

/**
 * Kind of resource a {@link FilterSnapshot} carries. Kept open enough to model items, fluids and
 * essentia without hardcoding {@code ItemStack} into the filter contract.
 */
public enum FilterType {

    ITEM,
    FLUID,
    ESSENTIA
}
