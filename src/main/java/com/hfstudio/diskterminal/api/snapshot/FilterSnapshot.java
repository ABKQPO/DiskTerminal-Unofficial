package com.hfstudio.diskterminal.api.snapshot;

import net.minecraft.nbt.NBTTagCompound;

/**
 * A single filter entry in a {@link Snapshot}. Filters are modelled as a typed NBT payload rather than
 * a concrete {@code ItemStack} so that fluid, essentia, tag and fuzzy filters remain expressible.
 */
public interface FilterSnapshot {

    /**
     * The resource kind this filter entry represents.
     */
    FilterType getType();

    /**
     * Opaque, type-specific data describing the filtered resource.
     */
    NBTTagCompound getData();
}
