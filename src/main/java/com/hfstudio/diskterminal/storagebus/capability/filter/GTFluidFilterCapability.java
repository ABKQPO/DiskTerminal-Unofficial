package com.hfstudio.diskterminal.storagebus.capability.filter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.storagebus.model.SimpleFilterSnapshot;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/**
 * Filter capability for a GregTech ME input hatch. Filter slots map to the hatch fluid config slots.
 */
public class GTFluidFilterCapability implements IFilterCapability {

    private final MTEHatchInputME inputHatch;
    private final FluidStack[] configs;
    private final FluidStack[] extracted;
    private final boolean editable;

    public GTFluidFilterCapability(MTEHatchInputME inputHatch) {
        this.inputHatch = inputHatch;
        this.configs = new FluidStack[MTEHatchInputME.SLOT_COUNT];
        this.extracted = new FluidStack[MTEHatchInputME.SLOT_COUNT];
        // While auto-pull drives the slots, the filter is managed by the network and must not be edited.
        this.editable = !inputHatch.isAutoPullFluidList();
        readSnapshot();
    }

    @Override
    public int getFilterSlotCount() {
        return MTEHatchInputME.SLOT_COUNT;
    }

    @Override
    public List<FilterSnapshot> getFilters() {
        List<FilterSnapshot> filters = new ArrayList<>();
        for (int slot = 0; slot < configs.length; slot++) {
            FluidStack config = configs[slot];
            if (config == null) continue;

            NBTTagCompound data = new NBTTagCompound();
            AEStackUtil.writeStackToNBT(
                data,
                AEFluidStack.create(config)
                    .setStackSize(1));
            data.setInteger("slot", slot);
            filters.add(new SimpleFilterSnapshot(FilterType.FLUID, data));
        }

        return filters;
    }

    @Override
    public boolean setFilter(int slot, FilterSnapshot filter) {
        if (!editable || slot < 0 || slot >= configs.length || filter == null) return false;

        FluidStack stack = sanitize(filter);
        if (stack == null) return false;

        inputHatch.setSlotConfig(slot, stack);

        return true;
    }

    @Override
    public boolean clearFilter(int slot) {
        if (!editable || slot < 0 || slot >= configs.length) return false;

        inputHatch.setSlotConfig(slot, null);

        return true;
    }

    @Override
    public void clearAllFilters() {
        if (!editable) return;

        for (int slot = 0; slot < configs.length; slot++) inputHatch.setSlotConfig(slot, null);
    }

    @Override
    public boolean toggleFilter(FilterSnapshot filter) {
        if (!editable || filter == null) return false;

        FluidStack stack = sanitize(filter);
        if (stack == null) return false;

        int existing = findSlot(stack);
        if (existing >= 0) {
            inputHatch.setSlotConfig(existing, null);

            return true;
        }

        int empty = findEmptySlot();
        if (empty < 0) return false;

        inputHatch.setSlotConfig(empty, stack);

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
        for (FluidStack stack : extracted) {
            if (slot >= configs.length) break;
            if (stack == null) continue;

            FluidStack copy = stack.copy();
            copy.amount = 1;
            inputHatch.setSlotConfig(slot++, copy);
        }

        return true;
    }

    private FluidStack sanitize(FilterSnapshot filter) {
        // The client sends either an AE2FC fluid drop or a raw fluid container as the display stack.
        // Extract the fluid directly from that stack; fall back to the AE generic representation only
        // when no display stack is present.
        ItemStack display = AEStackUtil.readDisplayStack(filter.getData());
        if (ItemStacks.isEmpty(display)) {
            IAEStack<?> stack = AEStackUtil.readPartitionStack(filter.getData(), null);
            display = AEStackUtil.getDisplayStack(stack);
        }

        FluidStack fluid = FluidStacks.extract(display);
        if (fluid == null) return null;

        fluid.amount = 1;

        return fluid;
    }

    private int findSlot(FluidStack target) {
        for (int slot = 0; slot < configs.length; slot++) {
            FluidStack config = configs[slot];
            if (config != null && config.isFluidEqual(target)) return slot;
        }

        return -1;
    }

    private int findEmptySlot() {
        for (int slot = 0; slot < configs.length; slot++) {
            if (configs[slot] == null) return slot;
        }

        return -1;
    }

    private void readSnapshot() {
        NBTTagCompound serialized = new NBTTagCompound();
        inputHatch.saveNBTData(serialized);
        NBTTagList slots = serialized.getTagList("slots", 10);

        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slotTag = slots.getCompoundTagAt(i);
            int index = slotTag.getInteger("index");
            if (index < 0 || index >= configs.length) continue;

            if (slotTag.hasKey("config")) {
                configs[index] = FluidStack.loadFluidStackFromNBT(slotTag.getCompoundTag("config"));
            }
            if (slotTag.hasKey("extracted")) {
                extracted[index] = FluidStack.loadFluidStackFromNBT(slotTag.getCompoundTag("extracted"));
            }
        }
    }
}
