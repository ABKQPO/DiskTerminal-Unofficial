package com.hfstudio.diskterminal.storagebus.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.hfstudio.diskterminal.api.capability.ICapability;
import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.capability.IRefreshCapability;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.storagebus.capability.refresh.TileRefreshCapability;
import com.hfstudio.diskterminal.storagebus.model.SimpleFilterSnapshot;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StockReplenisherHandle;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityIds;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusHandle;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusResolver;
import com.hfstudio.diskterminal.util.AEStackUtil;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.IAEStackInventory;

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
            return Optional.of((T) new TileRefreshCapability(handle.getHostTile()));
        }

        if (capabilityType == IFilterCapability.class) {
            return buildFilterCapability(handle.getTile()).map(capabilityType::cast);
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
        if (buildFilterCapability(handle.getTile()).isPresent()) capabilities.add(StorageBusCapabilityIds.FILTER);

        return capabilities;
    }

    private Optional<IFilterCapability> buildFilterCapability(TileSuperStockReplenisher tile) {
        IAEStackInventory fluidConfig = tile.getConfigFluids();
        IAEStackInventory itemConfig = tile.getConfigItems();
        IAEStackType<?> fluidType = AEStackTypeRegistry.getType("fluid");
        IAEStackType<?> itemType = AEStackTypeRegistry.getType("item");
        if (fluidConfig == null || itemConfig == null || fluidType == null || itemType == null) return Optional.empty();

        return Optional.of(new MixedStockReplenisherFilterCapability(fluidConfig, fluidType, itemConfig, itemType));
    }

    private static class MixedStockReplenisherFilterCapability implements IFilterCapability {

        private final IAEStackInventory fluidConfig;
        private final IAEStackType<?> fluidType;
        private final IAEStackInventory itemConfig;
        private final IAEStackType<?> itemType;

        private MixedStockReplenisherFilterCapability(IAEStackInventory fluidConfig, IAEStackType<?> fluidType,
            IAEStackInventory itemConfig, IAEStackType<?> itemType) {
            this.fluidConfig = fluidConfig;
            this.fluidType = fluidType;
            this.itemConfig = itemConfig;
            this.itemType = itemType;
        }

        @Override
        public int getFilterSlotCount() {
            return fluidConfig.getSizeInventory() + itemConfig.getSizeInventory();
        }

        @Override
        public List<FilterSnapshot> getFilters() {
            List<FilterSnapshot> filters = new ArrayList<>();
            appendFilters(filters, fluidConfig, FilterType.FLUID, 0);
            appendFilters(filters, itemConfig, FilterType.ITEM, fluidConfig.getSizeInventory());
            return filters;
        }

        @Override
        public boolean setFilter(int slot, FilterSnapshot filter) {
            SlotRef slotRef = resolveSlot(slot, filter == null ? null : filter.getType());
            if (slotRef == null || filter == null) return false;

            IAEStack<?> stack = AEStackUtil.readPartitionStack(filter.getData(), slotRef.stackType);
            if (stack == null) return false;

            stack.setStackSize(1);
            slotRef.inventory.putAEStackInSlot(slotRef.localSlot, stack);
            slotRef.inventory.markDirty();
            return true;
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
                existing.inventory.putAEStackInSlot(existing.localSlot, null);
                existing.inventory.markDirty();
                return true;
            }

            SlotRef empty = findEmptySlot(filter.getType());
            if (empty == null) return false;

            IAEStack<?> stack = AEStackUtil.readPartitionStack(filter.getData(), empty.stackType);
            if (stack == null) return false;

            stack.setStackSize(1);
            empty.inventory.putAEStackInSlot(empty.localSlot, stack);
            empty.inventory.markDirty();
            return true;
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

        private SlotRef findExisting(FilterSnapshot filter) {
            return switch (filter.getType()) {
                case FLUID -> findMatching(fluidConfig, fluidType, filter, 0);
                case ITEM, ESSENTIA -> findMatching(itemConfig, itemType, filter, fluidConfig.getSizeInventory());
            };
        }

        private SlotRef findMatching(IAEStackInventory inventory, IAEStackType<?> stackType, FilterSnapshot filter,
            int slotOffset) {
            IAEStack<?> target = AEStackUtil.readPartitionStack(filter.getData(), stackType);
            if (target == null) return null;

            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                IAEStack<?> stack = inventory.getAEStackInSlot(slot);
                if (stack != null && stack.isSameType(target)) {
                    return new SlotRef(inventory, stackType, slot, slotOffset + slot);
                }
            }

            return null;
        }

        private SlotRef findEmptySlot(FilterType type) {
            return switch (type) {
                case FLUID -> firstEmpty(fluidConfig, fluidType, 0);
                case ITEM, ESSENTIA -> firstEmpty(itemConfig, itemType, fluidConfig.getSizeInventory());
            };
        }

        private SlotRef firstEmpty(IAEStackInventory inventory, IAEStackType<?> stackType, int slotOffset) {
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                if (inventory.getAEStackInSlot(slot) == null) {
                    return new SlotRef(inventory, stackType, slot, slotOffset + slot);
                }
            }

            return null;
        }

        private SlotRef resolveSlot(int absoluteSlot, FilterType typeHint) {
            int fluidSlots = fluidConfig.getSizeInventory();
            if (absoluteSlot >= 0 && absoluteSlot < fluidSlots) {
                if (typeHint == FilterType.ITEM || typeHint == FilterType.ESSENTIA) return null;
                return new SlotRef(fluidConfig, fluidType, absoluteSlot, absoluteSlot);
            }

            int itemSlot = absoluteSlot - fluidSlots;
            if (itemSlot >= 0 && itemSlot < itemConfig.getSizeInventory()) {
                if (typeHint == FilterType.FLUID) return null;
                return new SlotRef(itemConfig, itemType, itemSlot, absoluteSlot);
            }

            return null;
        }

        private record SlotRef(IAEStackInventory inventory, IAEStackType<?> stackType, int localSlot,
            int absoluteSlot) {}
    }
}
