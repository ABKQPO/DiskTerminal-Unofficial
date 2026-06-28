package com.hfstudio.diskterminal.storagebus.capability.filter;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineClassNames;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineReflectionHelper;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.MTEHatchInputME;

public class GTFluidFilterCapability extends AbstractGTFilterCapability<FluidStack> {

    public GTFluidFilterCapability(MTEHatchInputME inputHatch) {
        this((MetaTileEntity) inputHatch);
    }

    public GTFluidFilterCapability(MetaTileEntity metaTileEntity) {
        super(createAccess(metaTileEntity), FilterType.FLUID);
    }

    @Override
    protected FluidStack sanitize(FilterSnapshot filter) {
        ItemStack display = AEStackUtil.readDisplayStack(filter.getData());
        if (ItemStacks.isEmpty(display)) {
            IAEStack<?> stack = AEStackUtil.readPartitionStack(filter.getData(), null);
            display = AEStackUtil.getDisplayStack(stack);
        }

        FluidStack fluid = GTUtility.getFluidForFilledItem(display, true);
        if (fluid == null) fluid = FluidStacks.extract(display);
        if (fluid == null) return null;

        fluid.amount = 1;
        return fluid;
    }

    @Override
    protected NBTTagCompound createFilterData(FluidStack config) {
        NBTTagCompound data = new NBTTagCompound();
        AEStackUtil.writeStackToNBT(
            data,
            AEFluidStack.create(config)
                .setStackSize(1));
        return data;
    }

    @Override
    protected FluidStack copyForConfig(FluidStack previewStack) {
        FluidStack copy = previewStack.copy();
        copy.amount = 1;
        return copy;
    }

    @Override
    protected boolean isEmpty(FluidStack stack) {
        return stack == null;
    }

    @Override
    protected boolean isSame(FluidStack left, FluidStack right) {
        return left.isFluidEqual(right);
    }

    private static FluidBusAccess createAccess(MetaTileEntity metaTileEntity) {
        if (GTMachineReflectionHelper.hasClassName(metaTileEntity, GTMachineClassNames.SUPER_INPUT_HATCH_ME)) {
            return new SuperFluidHatchAccess(metaTileEntity);
        }

        return new StandardFluidHatchAccess((MTEHatchInputME) metaTileEntity);
    }

    private interface FluidBusAccess extends FilterAccess<FluidStack> {
    }

    private static class StandardFluidHatchAccess implements FluidBusAccess {

        private final MTEHatchInputME inputHatch;
        private final FluidStack[] configs = new FluidStack[MTEHatchInputME.SLOT_COUNT];
        private final FluidStack[] extracted = new FluidStack[MTEHatchInputME.SLOT_COUNT];
        private final boolean editable;

        private StandardFluidHatchAccess(MTEHatchInputME inputHatch) {
            this.inputHatch = inputHatch;
            this.editable = !inputHatch.isAutoPullFluidList();
            readSnapshot();
        }

        @Override
        public int getFilterSlotCount() {
            return MTEHatchInputME.SLOT_COUNT;
        }

        @Override
        public FluidStack getConfig(int slot) {
            return slot < 0 || slot >= configs.length ? null : configs[slot];
        }

        @Override
        public boolean setConfig(int slot, FluidStack stack) {
            inputHatch.setSlotConfig(slot, stack);
            return true;
        }

        @Override
        public boolean clearConfig(int slot) {
            inputHatch.setSlotConfig(slot, null);
            return true;
        }

        @Override
        public FluidStack[] getPreviewStacks() {
            return extracted;
        }

        @Override
        public boolean isEditable() {
            return editable;
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

    private static class SuperFluidHatchAccess implements FluidBusAccess {

        private final MetaTileEntity metaTileEntity;
        private final int slotCount;
        private final FluidStack[] configs;
        private final FluidStack[] extracted;
        private final boolean editable;

        private SuperFluidHatchAccess(MetaTileEntity metaTileEntity) {
            this.metaTileEntity = metaTileEntity;
            this.slotCount = GTMachineReflectionHelper.invokeInt(metaTileEntity, "getFluidSlotCountForGui")
                .orElse(0);
            this.editable = !GTMachineReflectionHelper.invokeBoolean(metaTileEntity, "isAutoPullFluidListForGui")
                .orElse(false);
            this.configs = readConfigs(metaTileEntity, slotCount);
            this.extracted = readPreview(metaTileEntity, slotCount);
        }

        @Override
        public int getFilterSlotCount() {
            return slotCount;
        }

        @Override
        public FluidStack getConfig(int slot) {
            return slot < 0 || slot >= configs.length ? null : configs[slot];
        }

        @Override
        public boolean setConfig(int slot, FluidStack stack) {
            return GTMachineReflectionHelper.invokeVoid(
                metaTileEntity,
                "setFilterFluidForGui",
                GTMachineReflectionHelper.INT_FLUIDSTACK_ARG_TYPES,
                slot,
                stack == null ? null : stack.copy());
        }

        @Override
        public boolean clearConfig(int slot) {
            return setConfig(slot, null);
        }

        @Override
        public FluidStack[] getPreviewStacks() {
            return extracted;
        }

        @Override
        public boolean isEditable() {
            return editable;
        }

        private static FluidStack[] readConfigs(MetaTileEntity metaTileEntity, int slotCount) {
            FluidStack[] values = new FluidStack[Math.max(0, slotCount)];
            for (int slot = 0; slot < values.length; slot++) {
                values[slot] = GTMachineReflectionHelper.invokeFluidStack(metaTileEntity, "getFilterFluidForGui", slot)
                    .map(FluidStack::copy)
                    .orElse(null);
            }
            return values;
        }

        private static FluidStack[] readPreview(MetaTileEntity metaTileEntity, int slotCount) {
            FluidStack[] values = new FluidStack[Math.max(0, slotCount)];
            for (int slot = 0; slot < values.length; slot++) {
                values[slot] = GTMachineReflectionHelper
                    .invokeFluidStack(metaTileEntity, "getInformationFluidForGui", slot)
                    .map(FluidStack::copy)
                    .orElse(null);
            }
            return values;
        }
    }
}
