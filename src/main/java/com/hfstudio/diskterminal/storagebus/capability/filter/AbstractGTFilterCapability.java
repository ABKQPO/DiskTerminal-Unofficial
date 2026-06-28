package com.hfstudio.diskterminal.storagebus.capability.filter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.storagebus.model.SimpleFilterSnapshot;

public abstract class AbstractGTFilterCapability<T> implements IFilterCapability {

    private final FilterAccess<T> access;
    private final FilterType filterType;
    private final int slotCount;
    private final boolean editable;
    private final T[] previewStacks;

    protected AbstractGTFilterCapability(FilterAccess<T> access, FilterType filterType) {
        this.access = access;
        this.filterType = filterType;
        this.slotCount = access.getFilterSlotCount();
        this.editable = access.isEditable();
        this.previewStacks = access.getPreviewStacks();
    }

    @Override
    public int getFilterSlotCount() {
        return slotCount;
    }

    @Override
    public List<FilterSnapshot> getFilters() {
        List<FilterSnapshot> filters = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            T config = access.getConfig(slot);
            if (isEmpty(config)) {
                continue;
            }

            NBTTagCompound data = createFilterData(config);
            if (data == null) {
                continue;
            }

            data.setInteger("slot", slot);
            filters.add(new SimpleFilterSnapshot(filterType, data));
        }

        return filters;
    }

    @Override
    public boolean setFilter(int slot, FilterSnapshot filter) {
        if (!editable || slot < 0 || slot >= slotCount || filter == null) {
            return false;
        }

        T stack = sanitize(filter);
        if (isEmpty(stack)) {
            return false;
        }

        return access.setConfig(slot, stack);
    }

    @Override
    public boolean clearFilter(int slot) {
        if (!editable || slot < 0 || slot >= slotCount) {
            return false;
        }

        return access.clearConfig(slot);
    }

    @Override
    public void clearAllFilters() {
        if (!editable) {
            return;
        }

        for (int slot = 0; slot < slotCount; slot++) {
            access.clearConfig(slot);
        }
    }

    @Override
    public boolean toggleFilter(FilterSnapshot filter) {
        if (!editable || filter == null) {
            return false;
        }

        T stack = sanitize(filter);
        if (isEmpty(stack)) {
            return false;
        }

        int existing = findSlot(stack);
        if (existing >= 0) {
            return access.clearConfig(existing);
        }

        int empty = findEmptySlot();
        return empty >= 0 && access.setConfig(empty, stack);
    }

    @Override
    public boolean supportsPreviewFill() {
        return editable;
    }

    @Override
    public boolean fillFromPreview() {
        if (!editable) {
            return false;
        }

        clearAllFilters();

        int slot = 0;
        for (T previewStack : previewStacks) {
            if (slot >= slotCount) {
                break;
            }
            if (isEmpty(previewStack)) {
                continue;
            }

            access.setConfig(slot++, copyForConfig(previewStack));
        }

        return true;
    }

    protected abstract T sanitize(FilterSnapshot filter);

    protected abstract NBTTagCompound createFilterData(T config);

    protected abstract T copyForConfig(T previewStack);

    protected abstract boolean isEmpty(T stack);

    protected abstract boolean isSame(T left, T right);

    private int findSlot(T target) {
        for (int slot = 0; slot < slotCount; slot++) {
            T config = access.getConfig(slot);
            if (!isEmpty(config) && isSame(config, target)) {
                return slot;
            }
        }

        return -1;
    }

    private int findEmptySlot() {
        for (int slot = 0; slot < slotCount; slot++) {
            if (isEmpty(access.getConfig(slot))) {
                return slot;
            }
        }

        return -1;
    }

    protected interface FilterAccess<T> {

        int getFilterSlotCount();

        T getConfig(int slot);

        boolean setConfig(int slot, T stack);

        boolean clearConfig(int slot);

        T[] getPreviewStacks();

        boolean isEditable();
    }
}
