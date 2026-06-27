package com.hfstudio.diskterminal.storagebus.capability.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.storagebus.model.SimpleFilterSnapshot;
import com.hfstudio.diskterminal.util.AEStackUtil;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.IAEStackInventory;

/**
 * Filter capability for AE2 storage buses, operating on the bus config inventory. Contents-based fill is
 * supplied lazily so this capability stays free of grid/monitor lookup details.
 */
public class AEStorageBusFilterCapability implements IFilterCapability {

    private final IAEStackInventory config;
    private final IAEStackType<?> stackType;
    private final int availableSlots;
    private final FilterType filterType;
    private final Supplier<List<IAEStack<?>>> contentsSupplier;

    public AEStorageBusFilterCapability(IAEStackInventory config, IAEStackType<?> stackType, int availableSlots,
        FilterType filterType, Supplier<List<IAEStack<?>>> contentsSupplier) {
        this.config = config;
        this.stackType = stackType;
        this.availableSlots = availableSlots;
        this.filterType = filterType;
        this.contentsSupplier = contentsSupplier;
    }

    @Override
    public int getFilterSlotCount() {
        return Math.min(config.getSizeInventory(), availableSlots);
    }

    @Override
    public List<FilterSnapshot> getFilters() {
        List<FilterSnapshot> filters = new ArrayList<>();
        int slots = getFilterSlotCount();

        for (int slot = 0; slot < slots; slot++) {
            IAEStack<?> stack = config.getAEStackInSlot(slot);
            if (stack == null) continue;

            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("slot", slot);
            AEStackUtil.writeStackToNBT(data, stack);
            filters.add(new SimpleFilterSnapshot(filterType, data));
        }

        return filters;
    }

    @Override
    public boolean setFilter(int slot, FilterSnapshot filter) {
        if (slot < 0 || slot >= getFilterSlotCount() || filter == null) return false;

        IAEStack<?> stack = AEStackUtil.readPartitionStack(filter.getData(), stackType);
        if (stack == null) return false;

        setSlot(slot, stack);

        return true;
    }

    @Override
    public boolean clearFilter(int slot) {
        if (slot < 0 || slot >= getFilterSlotCount()) return false;

        setSlot(slot, null);

        return true;
    }

    @Override
    public void clearAllFilters() {
        for (int slot = 0; slot < config.getSizeInventory(); slot++) setSlot(slot, null);
    }

    @Override
    public boolean toggleFilter(FilterSnapshot filter) {
        if (filter == null) return false;

        IAEStack<?> stack = AEStackUtil.readPartitionStack(filter.getData(), stackType);
        if (stack == null) return false;

        int existing = findSlot(stack);
        if (existing >= 0) {
            setSlot(existing, null);

            return true;
        }

        int empty = findEmptySlot();
        if (empty < 0) return false;

        setSlot(empty, stack);

        return true;
    }

    @Override
    public boolean supportsPreviewFill() {
        return contentsSupplier != null;
    }

    @Override
    public boolean fillFromPreview() {
        if (contentsSupplier == null) return false;

        clearAllFilters();

        int slot = 0;
        for (IAEStack<?> stack : contentsSupplier.get()) {
            if (slot >= getFilterSlotCount()) break;
            if (stack == null) continue;

            IAEStack<?> partition = stack.copy();
            partition.setStackSize(1);
            setSlot(slot++, partition);
        }

        return true;
    }

    private int findSlot(IAEStack<?> stack) {
        int slots = getFilterSlotCount();
        for (int slot = 0; slot < slots; slot++) {
            IAEStack<?> slotStack = config.getAEStackInSlot(slot);
            if (slotStack != null && slotStack.isSameType(stack)) return slot;
        }

        return -1;
    }

    private int findEmptySlot() {
        int slots = getFilterSlotCount();
        for (int slot = 0; slot < slots; slot++) {
            if (config.getAEStackInSlot(slot) == null) return slot;
        }

        return -1;
    }

    private void setSlot(int slot, IAEStack<?> stack) {
        config.putAEStackInSlot(slot, stack);
        config.markDirty();
    }
}
