package com.hfstudio.diskterminal.storagebus.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.hfstudio.diskterminal.api.capability.ICapability;
import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.capability.IRefreshCapability;
import com.hfstudio.diskterminal.api.capability.IRenameCapability;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineClassNames;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineReflectionHelper;
import com.hfstudio.diskterminal.storagebus.capability.refresh.GTMachineRefreshCapability;
import com.hfstudio.diskterminal.storagebus.capability.refresh.TileRefreshCapability;
import com.hfstudio.diskterminal.storagebus.capability.rename.GTRenameCapability;
import com.hfstudio.diskterminal.storagebus.model.SimpleFilterSnapshot;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StockReplenisherHandle;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityIds;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusHandle;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusResolver;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEFluidStack;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * Capability provider for the AE2FluidCraft Super Stock Replenisher tile. The tile exposes one filter
 * capability over a mixed slot space: the leading slots are fluid configs and the trailing slots are
 * item configs. The machine has no priority and no custom name, and preview-fill is unsupported.
 */
public class StockReplenisherProvider implements ICapabilityProvider<StorageBusId> {

    private final StorageBusId id;
    private final StorageBusResolver resolver;

    public StockReplenisherProvider(StorageBusId id, StorageBusResolver resolver) {
        this.id = id;
        this.resolver = resolver;
    }

    @Override
    public StorageBusId getTargetId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ICapability> Optional<T> findCapability(Class<T> capabilityType) {
        Optional<StorageBusHandle> resolved = resolver.resolve(id);
        if (resolved.isEmpty() || !(resolved.get() instanceof StockReplenisherHandle handle)) return Optional.empty();

        if (capabilityType == IRefreshCapability.class) {
            if (handle.getMetaTileEntity() != null) {
                return Optional
                    .of((T) new GTMachineRefreshCapability(handle.getMetaTileEntity(), handle.getHostTile()));
            }
            return Optional.of((T) new TileRefreshCapability(handle.getHostTile()));
        }

        if (capabilityType == IRenameCapability.class && handle.getHostTile() != null) {
            return Optional.of(
                (T) new GTRenameCapability(
                    handle.getHostTile()
                        .getWorldObj(),
                    id.getLegacyKey()));
        }

        if (capabilityType == IFilterCapability.class) {
            return buildFilterCapability(handle).map(capabilityType::cast);
        }

        return Optional.empty();
    }

    @Override
    public Set<ResourceLocation> availableCapabilities() {
        Optional<StorageBusHandle> resolved = resolver.resolve(id);
        if (resolved.isEmpty() || !(resolved.get() instanceof StockReplenisherHandle handle)) {
            return Collections.emptySet();
        }

        Set<ResourceLocation> capabilities = new LinkedHashSet<>();
        if (handle.getHostTile() != null) {
            capabilities.add(StorageBusCapabilityIds.RENAME);
        }
        if (supportsFilter(handle)) capabilities.add(StorageBusCapabilityIds.FILTER);

        return capabilities;
    }

    private boolean supportsFilter(StockReplenisherHandle handle) {
        return resolveFilterTarget(handle) != null;
    }

    private Optional<IFilterCapability> buildFilterCapability(StockReplenisherHandle handle) {
        FilterTarget target = resolveFilterTarget(handle);
        if (target == null) {
            return Optional.empty();
        }

        if (target.tile() != null) {
            return buildAe2fcFilterCapability(target.tile());
        }
        if (target.metaTileEntity() != null) {
            return buildGtMixedFilterCapability(target.metaTileEntity(), handle.getHostTile());
        }

        return Optional.empty();
    }

    private FilterTarget resolveFilterTarget(StockReplenisherHandle handle) {
        TileSuperStockReplenisher tile = handle.getTile();
        if (tile != null) {
            return new FilterTarget(tile, null);
        }

        MetaTileEntity metaTileEntity = handle.getMetaTileEntity();
        if (metaTileEntity == null
            || !GTMachineReflectionHelper.hasClassName(metaTileEntity, GTMachineClassNames.SUPER_DUAL_INPUT_HATCH_ME)) {
            return null;
        }

        int slotCount = GTMachineReflectionHelper.invokeInt(metaTileEntity, "getDualSlotCountForGui")
            .orElse(0);
        return slotCount > 0 ? new FilterTarget(null, metaTileEntity) : null;
    }

    private Optional<IFilterCapability> buildAe2fcFilterCapability(TileSuperStockReplenisher tile) {
        IAEStackInventory fluidConfig = tile.getConfigFluids();
        IAEStackInventory itemConfig = tile.getConfigItems();
        IAEStackType<?> fluidType = AEStackTypeRegistry.getType("fluid");
        IAEStackType<?> itemType = AEStackTypeRegistry.getType("item");
        if (fluidConfig == null || itemConfig == null || fluidType == null || itemType == null) return Optional.empty();

        return Optional.of(new MixedStockReplenisherFilterCapability(fluidConfig, fluidType, itemConfig, itemType));
    }

    private Optional<IFilterCapability> buildGtMixedFilterCapability(MetaTileEntity metaTileEntity,
        TileEntity hostTile) {
        int slotCount = GTMachineReflectionHelper.invokeInt(metaTileEntity, "getDualSlotCountForGui")
            .orElse(0);
        if (slotCount <= 0) {
            return Optional.empty();
        }

        return Optional.of(new GtMixedFilterCapability(metaTileEntity, slotCount, hostTile));
    }

    private static class MixedStockReplenisherFilterCapability implements IFilterCapability {

        private final IAEStackInventory fluidConfig;
        private final IAEStackType<?> fluidType;
        private final IAEStackInventory itemConfig;
        private final IAEStackType<?> itemType;
        private final int fluidSlots;
        private final int itemSlots;

        private MixedStockReplenisherFilterCapability(IAEStackInventory fluidConfig, IAEStackType<?> fluidType,
            IAEStackInventory itemConfig, IAEStackType<?> itemType) {
            this.fluidConfig = fluidConfig;
            this.fluidType = fluidType;
            this.itemConfig = itemConfig;
            this.itemType = itemType;
            this.fluidSlots = fluidConfig.getSizeInventory();
            this.itemSlots = itemConfig.getSizeInventory();
        }

        @Override
        public int getFilterSlotCount() {
            return fluidSlots + itemSlots;
        }

        @Override
        public List<FilterSnapshot> getFilters() {
            List<FilterSnapshot> filters = new ArrayList<>(fluidSlots + itemSlots);
            appendFilters(filters, fluidConfig, FilterType.FLUID, 0);
            appendFilters(filters, itemConfig, FilterType.ITEM, fluidSlots);
            return filters;
        }

        @Override
        public boolean setFilter(int slot, FilterSnapshot filter) {
            SlotRef slotRef = resolveSlot(slot, filter == null ? null : filter.getType());
            if (slotRef == null || filter == null) return false;

            return writeSlot(slotRef, filter);
        }

        @Override
        public boolean clearFilter(int slot) {
            SlotRef slotRef = resolveSlot(slot, null);
            if (slotRef == null) return false;

            slotRef.inventory.putAEStackInSlot(slotRef.localSlot, null);
            slotRef.inventory.markDirty();
            return true;
        }

        @Override
        public void clearAllFilters() {
            clearInventory(fluidConfig);
            clearInventory(itemConfig);
        }

        @Override
        public boolean toggleFilter(FilterSnapshot filter) {
            if (filter == null) return false;

            SlotRef existing = findExisting(filter);
            if (existing != null) {
                clearSlot(existing);
                return true;
            }

            SlotRef empty = findEmptySlot(filter.getType());
            if (empty == null) return false;

            return writeSlot(empty, filter);
        }

        @Override
        public boolean supportsPreviewFill() {
            return false;
        }

        @Override
        public boolean fillFromPreview() {
            return false;
        }

        private void appendFilters(List<FilterSnapshot> filters, IAEStackInventory inventory, FilterType type,
            int slotOffset) {
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                IAEStack<?> stack = inventory.getAEStackInSlot(slot);
                if (stack == null) continue;

                NBTTagCompound data = new NBTTagCompound();
                data.setInteger("slot", slotOffset + slot);
                AEStackUtil.writeStackToNBT(data, stack);
                filters.add(new SimpleFilterSnapshot(type, data));
            }
        }

        private void clearInventory(IAEStackInventory inventory) {
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                inventory.putAEStackInSlot(slot, null);
            }
            inventory.markDirty();
        }

        private boolean writeSlot(SlotRef slotRef, FilterSnapshot filter) {
            IAEStack<?> stack = AEStackUtil.readPartitionStack(filter.getData(), slotRef.stackType);
            if (stack == null) {
                return false;
            }

            stack.setStackSize(1);
            slotRef.inventory.putAEStackInSlot(slotRef.localSlot, stack);
            slotRef.inventory.markDirty();
            return true;
        }

        private void clearSlot(SlotRef slotRef) {
            slotRef.inventory.putAEStackInSlot(slotRef.localSlot, null);
            slotRef.inventory.markDirty();
        }

        private SlotRef findExisting(FilterSnapshot filter) {
            return switch (filter.getType()) {
                case FLUID -> findMatching(fluidConfig, fluidType, filter, 0);
                case ITEM, ESSENTIA -> findMatching(itemConfig, itemType, filter, fluidSlots);
            };
        }

        private SlotRef findMatching(IAEStackInventory inventory, IAEStackType<?> stackType, FilterSnapshot filter,
            int slotOffset) {
            IAEStack<?> target = AEStackUtil.readPartitionStack(filter.getData(), stackType);
            if (target == null) return null;

            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                IAEStack<?> stack = inventory.getAEStackInSlot(slot);
                if (stack != null && stack.isSameType(target)) {
                    return new SlotRef(inventory, stackType, slot);
                }
            }

            return null;
        }

        private SlotRef findEmptySlot(FilterType type) {
            return switch (type) {
                case FLUID -> firstEmpty(fluidConfig, fluidType, 0);
                case ITEM, ESSENTIA -> firstEmpty(itemConfig, itemType, fluidSlots);
            };
        }

        private SlotRef firstEmpty(IAEStackInventory inventory, IAEStackType<?> stackType, int slotOffset) {
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                if (inventory.getAEStackInSlot(slot) == null) {
                    return new SlotRef(inventory, stackType, slot);
                }
            }

            return null;
        }

        private SlotRef resolveSlot(int absoluteSlot, FilterType typeHint) {
            if (absoluteSlot >= 0 && absoluteSlot < fluidSlots) {
                if (typeHint == FilterType.ITEM || typeHint == FilterType.ESSENTIA) return null;
                return new SlotRef(fluidConfig, fluidType, absoluteSlot);
            }

            int itemSlot = absoluteSlot - fluidSlots;
            if (itemSlot >= 0 && itemSlot < itemSlots) {
                if (typeHint == FilterType.FLUID) return null;
                return new SlotRef(itemConfig, itemType, itemSlot);
            }

            return null;
        }

        private record SlotRef(IAEStackInventory inventory, IAEStackType<?> stackType, int localSlot) {}
    }

    private static class GtMixedFilterCapability implements IFilterCapability {

        private final MetaTileEntity metaTileEntity;
        private final TileEntity hostTile;
        private final int slotCount;
        private final boolean editable;
        private final IAEStackType<?> fluidType;

        private GtMixedFilterCapability(MetaTileEntity metaTileEntity, int slotCount, TileEntity hostTile) {
            this.metaTileEntity = metaTileEntity;
            this.hostTile = hostTile;
            this.slotCount = slotCount;
            this.editable = !GTMachineReflectionHelper.invokeBoolean(metaTileEntity, "isAutoPullItemListForGui")
                .orElse(false);
            this.fluidType = AEStackTypeRegistry.getType("fluid");
        }

        @Override
        public int getFilterSlotCount() {
            return slotCount * 2;
        }

        @Override
        public List<FilterSnapshot> getFilters() {
            List<FilterSnapshot> filters = new ArrayList<>(slotCount * 2);
            for (int slot = 0; slot < slotCount; slot++) {
                FluidStack fluid = GTMachineReflectionHelper
                    .invokeFluidStack(metaTileEntity, "getFilterFluidForGui", slot)
                    .orElse(null);
                if (fluid != null) {
                    NBTTagCompound data = new NBTTagCompound();
                    data.setInteger("slot", slot);
                    data.setString("stackTypeId", "fluid");
                    AEStackUtil.writeStackToNBT(
                        data,
                        AEFluidStack.create(fluid)
                            .setStackSize(1));
                    filters.add(new SimpleFilterSnapshot(FilterType.FLUID, data));
                }
            }

            for (int slot = 0; slot < slotCount; slot++) {
                ItemStack item = GTMachineReflectionHelper.invokeItemStack(metaTileEntity, "getFilterItemForGui", slot)
                    .orElse(null);
                if (item == null) {
                    continue;
                }

                NBTTagCompound data = AEStackUtil.writeItemLikePartitionStack(item);
                if (data == null) {
                    continue;
                }
                data.setInteger("slot", slotCount + slot);
                data.setString("stackTypeId", "item");
                filters.add(new SimpleFilterSnapshot(FilterType.ITEM, data));
            }

            return filters;
        }

        @Override
        public boolean setFilter(int slot, FilterSnapshot filter) {
            if (!editable || filter == null) {
                return false;
            }

            if (filter.getType() == FilterType.FLUID) {
                int localSlot = slot;
                if (localSlot < 0 || localSlot >= slotCount) {
                    return false;
                }

                ItemStack display = AEStackUtil.readDisplayStack(filter.getData());
                FluidStack fluid = display == null ? null : FluidStacks.extract(display);
                if (fluid == null && AEStackUtil
                    .readPartitionStack(filter.getData(), fluidType) instanceof IAEFluidStack fluidStack) {
                    fluid = fluidStack.getFluidStack();
                }
                if (fluid == null) {
                    return false;
                }

                fluid = fluid.copy();
                fluid.amount = 1;
                if (!GTMachineReflectionHelper.invokeVoid(
                    metaTileEntity,
                    "setFilterFluidForGui",
                    GTMachineReflectionHelper.INT_FLUIDSTACK_ARG_TYPES,
                    localSlot,
                    fluid)) {
                    return false;
                }
                markDirty();
                return true;
            }

            int localSlot = slot - slotCount;
            if (localSlot < 0 || localSlot >= slotCount) {
                return false;
            }

            ItemStack display = AEStackUtil.readDisplayStack(filter.getData());
            if (ItemStacks.isEmpty(display)) {
                IAEStack<?> stack = AEStackUtil.readPartitionStack(filter.getData(), null);
                display = AEStackUtil.getDisplayStack(stack);
            }
            if (ItemStacks.isEmpty(display)) {
                return false;
            }

            ItemStack copy = display.copy();
            copy.stackSize = 1;
            if (!GTMachineReflectionHelper.invokeVoid(
                metaTileEntity,
                "setFilterItemForGui",
                GTMachineReflectionHelper.INT_ITEMSTACK_ARG_TYPES,
                localSlot,
                copy)) {
                return false;
            }
            markDirty();
            return true;
        }

        @Override
        public boolean clearFilter(int slot) {
            if (!editable) {
                return false;
            }

            if (slot >= 0 && slot < slotCount) {
                if (!GTMachineReflectionHelper.invokeVoid(
                    metaTileEntity,
                    "setFilterFluidForGui",
                    GTMachineReflectionHelper.INT_FLUIDSTACK_ARG_TYPES,
                    slot,
                    null)) {
                    return false;
                }
                markDirty();
                return true;
            }

            int itemSlot = slot - slotCount;
            if (itemSlot >= 0 && itemSlot < slotCount) {
                if (!GTMachineReflectionHelper.invokeVoid(
                    metaTileEntity,
                    "setFilterItemForGui",
                    GTMachineReflectionHelper.INT_ITEMSTACK_ARG_TYPES,
                    itemSlot,
                    null)) {
                    return false;
                }
                markDirty();
                return true;
            }

            return false;
        }

        @Override
        public void clearAllFilters() {
            if (!editable) {
                return;
            }

            boolean changed = false;
            for (int slot = 0; slot < slotCount; slot++) {
                changed |= GTMachineReflectionHelper.invokeVoid(
                    metaTileEntity,
                    "setFilterFluidForGui",
                    GTMachineReflectionHelper.INT_FLUIDSTACK_ARG_TYPES,
                    slot,
                    null);
                changed |= GTMachineReflectionHelper.invokeVoid(
                    metaTileEntity,
                    "setFilterItemForGui",
                    GTMachineReflectionHelper.INT_ITEMSTACK_ARG_TYPES,
                    slot,
                    null);
            }
            if (changed) {
                markDirty();
            }
        }

        @Override
        public boolean toggleFilter(FilterSnapshot filter) {
            if (!editable || filter == null) {
                return false;
            }

            return switch (filter.getType()) {
                case FLUID -> toggleFluidFilter(filter);
                case ITEM, ESSENTIA -> toggleItemFilter(filter);
            };
        }

        private boolean toggleFluidFilter(FilterSnapshot filter) {
            FluidStack targetFluid = readFluid(filter.getData());
            if (targetFluid == null) {
                return false;
            }

            int emptySlot = -1;
            for (int slot = 0; slot < slotCount; slot++) {
                FluidStack existing = GTMachineReflectionHelper
                    .invokeFluidStack(metaTileEntity, "getFilterFluidForGui", slot)
                    .orElse(null);
                if (existing == null) {
                    if (emptySlot < 0) {
                        emptySlot = slot;
                    }
                    continue;
                }
                if (existing.isFluidEqual(targetFluid)) {
                    return clearFilter(slot);
                }
            }

            return emptySlot >= 0 && setFilter(emptySlot, filter);
        }

        private boolean toggleItemFilter(FilterSnapshot filter) {
            ItemStack targetItem = readItem(filter.getData());
            if (ItemStacks.isEmpty(targetItem)) {
                return false;
            }

            int emptySlot = -1;
            for (int slot = 0; slot < slotCount; slot++) {
                ItemStack existing = GTMachineReflectionHelper
                    .invokeItemStack(metaTileEntity, "getFilterItemForGui", slot)
                    .orElse(null);
                if (ItemStacks.isEmpty(existing)) {
                    if (emptySlot < 0) {
                        emptySlot = slot;
                    }
                    continue;
                }
                if (ItemStack.areItemStacksEqual(existing, targetItem)) {
                    return clearFilter(slotCount + slot);
                }
            }

            return emptySlot >= 0 && setFilter(slotCount + emptySlot, filter);
        }

        @Override
        public boolean supportsPreviewFill() {
            return false;
        }

        @Override
        public boolean fillFromPreview() {
            return false;
        }

        private FluidStack readFluid(NBTTagCompound data) {
            ItemStack display = AEStackUtil.readDisplayStack(data);
            FluidStack fluid = display == null ? null : FluidStacks.extract(display);
            if (fluid != null) {
                fluid.amount = 1;
                return fluid;
            }

            if (AEStackUtil.readPartitionStack(data, fluidType) instanceof IAEFluidStack fluidStack) {
                fluid = fluidStack.getFluidStack();
                if (fluid != null) {
                    fluid = fluid.copy();
                    fluid.amount = 1;
                }
            }

            return fluid;
        }

        private ItemStack readItem(NBTTagCompound data) {
            ItemStack display = AEStackUtil.readDisplayStack(data);
            if (ItemStacks.isEmpty(display)) {
                display = AEStackUtil.getDisplayStack(AEStackUtil.readPartitionStack(data, null));
            }
            if (ItemStacks.isEmpty(display)) {
                return null;
            }

            ItemStack copy = display.copy();
            copy.stackSize = 1;
            return copy;
        }

        private void markDirty() {
            if (hostTile != null) {
                hostTile.markDirty();
            }
        }
    }

    private record FilterTarget(TileSuperStockReplenisher tile, MetaTileEntity metaTileEntity) {}
}
