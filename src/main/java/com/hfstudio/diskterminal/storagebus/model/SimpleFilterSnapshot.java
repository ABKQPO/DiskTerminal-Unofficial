package com.hfstudio.diskterminal.storagebus.model;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;

/**
 * Immutable {@link FilterSnapshot} carrying a type and an opaque NBT payload.
 */
public class SimpleFilterSnapshot implements FilterSnapshot {

    private final FilterType type;
    private final NBTTagCompound data;

    public SimpleFilterSnapshot(FilterType type, NBTTagCompound data) {
        this.type = type;
        this.data = data;
    }

    @Override
    public FilterType getType() {
        return type;
    }

    @Override
    public NBTTagCompound getData() {
        return data;
    }
}
