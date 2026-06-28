package com.hfstudio.diskterminal.storagebus.capability.filter;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineClassNames;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineReflectionHelper;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

public class GTItemFilterCapability extends AbstractGTFilterCapability<ItemStack> {

    public GTItemFilterCapability(MTEHatchInputBusME inputBus) {
        this((MetaTileEntity) inputBus);
    }

    public GTItemFilterCapability(MetaTileEntity metaTileEntity) {
        super(createAccess(metaTileEntity), FilterType.ITEM);
    }

    @Override
    protected ItemStack sanitize(FilterSnapshot filter) {
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

    @Override
    protected NBTTagCompound createFilterData(ItemStack config) {
        return AEStackUtil.writeItemLikePartitionStack(config);
    }

    @Override
    protected ItemStack copyForConfig(ItemStack previewStack) {
        ItemStack copy = previewStack.copy();
        copy.stackSize = 1;
        return copy;
    }

    @Override
    protected boolean isEmpty(ItemStack stack) {
        return ItemStacks.isEmpty(stack);
    }

    @Override
    protected boolean isSame(ItemStack left, ItemStack right) {
        return ItemStack.areItemStacksEqual(left, right);
    }

    private static ItemBusAccess createAccess(MetaTileEntity metaTileEntity) {
        if (GTMachineReflectionHelper.hasClassName(metaTileEntity, GTMachineClassNames.SUPER_INPUT_BUS_ME)) {
            return new SuperInputBusAccess(metaTileEntity);
        }

        if (GTMachineReflectionHelper.hasAnyClassName(metaTileEntity, GTMachineClassNames.GTNL_SUPER_ITEM_INPUT_CLASSES)
            && GTMachineReflectionHelper.readBooleanField(metaTileEntity, "isSuper")
                .orElse(false)) {
            return new SuperInputBusAccess(metaTileEntity);
        }

        return new StandardItemBusAccess((MTEHatchInputBusME) metaTileEntity);
    }

    private interface ItemBusAccess extends FilterAccess<ItemStack> {
    }

    private static class StandardItemBusAccess implements ItemBusAccess {

        private final MTEHatchInputBusME inputBus;
        private final ItemStack[] configs = new ItemStack[MTEHatchInputBusME.SLOT_COUNT];
        private final ItemStack[] extracted = new ItemStack[MTEHatchInputBusME.SLOT_COUNT];
        private final boolean editable;

        private StandardItemBusAccess(MTEHatchInputBusME inputBus) {
            this.inputBus = inputBus;
            this.editable = !inputBus.isAutoPullItemList();
            readSnapshot();
        }

        @Override
        public int getFilterSlotCount() {
            return MTEHatchInputBusME.SLOT_COUNT;
        }

        @Override
        public ItemStack getConfig(int slot) {
            return slot < 0 || slot >= configs.length ? null : configs[slot];
        }

        @Override
        public boolean setConfig(int slot, ItemStack stack) {
            inputBus.setSlotConfig(slot, stack);
            return true;
        }

        @Override
        public boolean clearConfig(int slot) {
            inputBus.setSlotConfig(slot, null);
            return true;
        }

        @Override
        public ItemStack[] getPreviewStacks() {
            return extracted;
        }

        @Override
        public boolean isEditable() {
            return editable;
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
                    configs[index] = ItemStacks.loadDisplay(slotTag.getCompoundTag("config"));
                }
                if (slotTag.hasKey("extracted")) {
                    extracted[index] = ItemStacks.loadDisplay(slotTag.getCompoundTag("extracted"));
                }
            }
        }
    }

    private static class SuperInputBusAccess implements ItemBusAccess {

        private final MetaTileEntity metaTileEntity;
        private final ItemStack[] inventory;
        private final int slotCount;
        private final ItemStack[] configs;
        private final ItemStack[] extracted;
        private final boolean editable;

        private SuperInputBusAccess(MetaTileEntity metaTileEntity) {
            this.metaTileEntity = metaTileEntity;
            this.slotCount = GTMachineReflectionHelper.invokeInt(metaTileEntity, "getFilterSlotCountForGui")
                .orElse(0);
            this.editable = !GTMachineReflectionHelper.invokeBoolean(metaTileEntity, "isAutoPullItemList")
                .orElse(false);
            this.inventory = GTMachineReflectionHelper.readItemStackArrayField(metaTileEntity, "mInventory")
                .orElse(null);
            this.configs = readConfigs(inventory, slotCount);
            this.extracted = readPreview(metaTileEntity, slotCount);
        }

        @Override
        public int getFilterSlotCount() {
            return slotCount;
        }

        @Override
        public ItemStack getConfig(int slot) {
            return slot < 0 || slot >= configs.length ? null : configs[slot];
        }

        @Override
        public boolean setConfig(int slot, ItemStack stack) {
            if (inventory == null || slot < 0 || slot >= slotCount) {
                return false;
            }

            inventory[slot] = ItemStacks.isEmpty(stack) ? null : stack.copy();
            return GTMachineReflectionHelper.invokeVoid(
                metaTileEntity,
                "updateInformationSlotForGui",
                GTMachineReflectionHelper.INT_ITEMSTACK_ARG_TYPES,
                slot,
                inventory[slot]);
        }

        @Override
        public boolean clearConfig(int slot) {
            return setConfig(slot, null);
        }

        @Override
        public ItemStack[] getPreviewStacks() {
            return extracted;
        }

        @Override
        public boolean isEditable() {
            return editable;
        }

        private static ItemStack[] readConfigs(ItemStack[] inventory, int slotCount) {
            ItemStack[] values = new ItemStack[Math.max(0, slotCount)];
            if (inventory == null) {
                return values;
            }

            for (int slot = 0; slot < values.length && slot < inventory.length; slot++) {
                values[slot] = ItemStacks.isEmpty(inventory[slot]) ? null : inventory[slot].copy();
            }
            return values;
        }

        private static ItemStack[] readPreview(MetaTileEntity metaTileEntity, int slotCount) {
            ItemStack[] values = new ItemStack[Math.max(0, slotCount)];
            for (int slot = 0; slot < values.length; slot++) {
                values[slot] = GTMachineReflectionHelper.invokeItemStack(metaTileEntity, "getShadowItemStack", slot)
                    .map(ItemStack::copy)
                    .orElse(null);
            }
            return values;
        }
    }
}
