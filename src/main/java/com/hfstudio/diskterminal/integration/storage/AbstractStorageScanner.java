package com.hfstudio.diskterminal.integration.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.container.handler.CellDataHandler;

import appeng.api.networking.IGrid;

/**
 * Abstract base class for storage scanners providing sane defaults.
 * Implementations can override methods to customize behavior for specific storage types.
 */
public abstract class AbstractStorageScanner implements IStorageScanner {

    @Override
    public abstract String getId();

    @Override
    public abstract boolean isAvailable();

    @Override
    public abstract void scanStorages(IGrid grid, NBTTagList storageList,
        CellDataHandler.StorageTrackerCallback callback, int slotLimit);

    /**
     * Check if drives from this scanner support priority. Default: true.
     *
     * @return true if priority editing should be shown for this storage type
     */
    public boolean supportsPriority() {
        return true;
    }

    /**
     * Apply common capability flags to the provided NBT payload.
     *
     * @param nbt The storage NBT data to modify
     */
    protected void applyCapabilities(NBTTagCompound nbt) {
        nbt.setBoolean("supportsPriority", supportsPriority());
    }
}
