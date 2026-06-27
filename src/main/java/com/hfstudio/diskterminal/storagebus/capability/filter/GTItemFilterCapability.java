package com.hfstudio.diskterminal.storagebus.capability.filter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.storagebus.model.SimpleFilterSnapshot;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.data.IAEStack;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

/**
 * Filter capability for a GregTech ME input bus. Filter slots map to the bus item config slots; the
 * current configuration is read from the machine's serialized slot list and written through
 * {@link MTEHatchInputBusME#setSlotConfig(int, ItemStack)}.
 */
public class GTItemFilterCapability implements IFilterCapability {

    private final MTEHatchInputBusME inputBus;
    private final ItemStack[] configs;
    private final ItemStack[] extracted;
    private final boolean editable;

    public GTItemFilterCapability(MTEHatchInputBusME inputBus) {
        this.inputBus = inputBus;
        this.configs = new ItemStack[MTEHatchInputBusME.SLOT_COUNT];
        this.extracted = new ItemStack[MTEHatchInputBusME.SLOT_COUNT];
        // While auto-pull drives the slots, the filter is managed by the network and must not be edited.
        this.editable = !inputBus.isAutoPullItemList();
        readSnapshot();
    }

    @Override
    public int getFilterSlotCount() {
        return MTEHatchInputBusME.SLOT_COUNT;
    }

    @Override
    public List<FilterSnapshot> getFilters() {
        List<FilterSnapshot> filters = new ArrayList<>();
        for (int slot = 0; slot < configs.length; slot++) {
            ItemStack config = configs[slot];
            if (ItemStacks.isEmpty(config)) continue;

            NBTTagCompound data = AEStackUtil.writeItemLikePartitionStack(config);
            if (data == null) continue;

            data.setInteger("slot", slot);
            filters.add(new SimpleFilterSnapshot(FilterType.ITEM, data));
        }

        return filters;
    }

    @Override
    public boolean setFilter(int slot, FilterSnapshot filter) {
        if (!editable || slot < 0 || slot >= configs.length || filter == null) return false;

        ItemStack stack = sanitize(filter);
        if (ItemStacks.isEmpty(stack)) return false;

        inputBus.setSlotConfig(slot, stack);

        return true;
    }

    @Override
    public boolean clearFilter(int slot) {
        if (!editable || slot < 0 || slot >= configs.length) return false;

        inputBus.setSlotConfig(slot, null);

        return true;
    }

    @Override
    public void clearAllFilters() {
        if (!editable) return;

        for (int slot = 0; slot < configs.length; slot++) inputBus.setSlotConfig(slot, null);
    }

    @Override
    public boolean toggleFilter(FilterSnapshot filter) {
        if (!editable || filter == null) return false;

        ItemStack stack = sanitize(filter);
        if (ItemStacks.isEmpty(stack)) return false;

        int existing = findSlot(stack);
        if (existing >= 0) {
            inputBus.setSlotConfig(existing, null);

            return true;
        }

        int empty = findEmptySlot();
        if (empty < 0) return false;

        inputBus.setSlotConfig(empty, stack);

        return true;
    }

    @Override
    public boolean supportsPreviewFill() {
        return editable;
    }

    @Override
    public boolean fillFromPreview() {
        if (!editable) return false;

        clearAllFilters();

        int slot = 0;
        for (ItemStack stack : extracted) {
            if (slot >= configs.length) break;
            if (ItemStacks.isEmpty(stack)) continue;

            ItemStack copy = stack.copy();
            copy.stackSize = 1;
            inputBus.setSlotConfig(slot++, copy);
        }

        return true;
    }

    private ItemStack sanitize(FilterSnapshot filter) {
        // A GregTech item bus stores plain ItemStacks. Use the display stack the client sent verbatim
        // (e.g. a fluid container placed on an item bus must stay that container, not be reinterpreted
        // as a fluid by the registered-type conversion). Fall back to the AE generic only if absent.
        ItemStack display = AEStackUtil.readDisplayStack(filter.getData());
        if (ItemStacks.isEmpty(display)) {
            IAEStack<?> stack = AEStackUtil.readPartitionStack(filter.getData(), null);
            display = AEStackUtil.getDisplayStack(stack);
        }
        if (ItemStacks.isEmpty(display)) return null;

        ItemStack copy = display.copy();
        copy.stackSize = 1;

        return copy;
    }

    private int findSlot(ItemStack target) {
        for (int slot = 0; slot < configs.length; slot++) {
            ItemStack config = configs[slot];
            if (!ItemStacks.isEmpty(config) && ItemStack.areItemStacksEqual(config, target)) return slot;
        }

        return -1;
    }

    private int findEmptySlot() {
        for (int slot = 0; slot < configs.length; slot++) {
            if (ItemStacks.isEmpty(configs[slot])) return slot;
        }

        return -1;
    }

    private void readSnapshot() {
        NBTTagCompound serialized = new NBTTagCompound();
        inputBus.saveNBTData(serialized);
        NBTTagList slots = serialized.getTagList("slots", 10);

        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slotTag = slots.getCompoundTagAt(i);
            int index = slotTag.getInteger("index");
            if (index < 0 || index >= configs.length) continue;

            if (slotTag.hasKey("config")) {
                configs[index] = ItemStack.loadItemStackFromNBT(slotTag.getCompoundTag("config"));
            }
            if (slotTag.hasKey("extracted")) {
                extracted[index] = ItemStack.loadItemStackFromNBT(slotTag.getCompoundTag("extracted"));
            }
        }
    }
}
