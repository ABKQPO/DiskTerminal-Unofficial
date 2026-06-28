package com.hfstudio.diskterminal.storagebus.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.api.capability.ICapability;
import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.capability.IPriorityCapability;
import com.hfstudio.diskterminal.api.capability.IRefreshCapability;
import com.hfstudio.diskterminal.api.capability.IRenameCapability;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.storagebus.capability.filter.AEStorageBusFilterCapability;
import com.hfstudio.diskterminal.storagebus.capability.priority.AEStorageBusPriorityCapability;
import com.hfstudio.diskterminal.storagebus.capability.refresh.AEPartRefreshCapability;
import com.hfstudio.diskterminal.storagebus.capability.rename.AEStorageBusRenameCapability;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.AEStorageBusHandle;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityIds;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusHandle;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusResolver;

import appeng.api.config.Upgrades;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.helpers.IPriorityHost;
import appeng.parts.automation.PartUpgradeable;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;

/**
 * Capability provider for AE2 part-based storage buses. Holds only the {@link StorageBusId}; the live
 * part is re-resolved on every {@link #findCapability(Class)} call so no runtime reference is retained.
 */
public class AEStorageBusProvider implements ICapabilityProvider<StorageBusId> {

    private final StorageBusId id;
    private final StorageBusResolver resolver;

    public AEStorageBusProvider(StorageBusId id, StorageBusResolver resolver) {
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
        if (resolved.isEmpty() || !(resolved.get() instanceof AEStorageBusHandle handle)) return Optional.empty();

        IPart part = handle.getPart();
        IPartHost host = handle.getHost();

        if (capabilityType == IRenameCapability.class) {
            return Optional.of((T) new AEStorageBusRenameCapability(part));
        }

        if (capabilityType == IPriorityCapability.class) {
            if (!(part instanceof IPriorityHost priorityHost)) return Optional.empty();

            return Optional.of((T) new AEStorageBusPriorityCapability(priorityHost));
        }

        if (capabilityType == IRefreshCapability.class) {
            return Optional.of((T) new AEPartRefreshCapability(part, host));
        }

        if (capabilityType == IFilterCapability.class) {
            return buildFilterCapability(part).map(capabilityType::cast);
        }

        return Optional.empty();
    }

    @Override
    public Set<ResourceLocation> availableCapabilities() {
        Optional<StorageBusHandle> resolved = resolver.resolve(id);
        if (resolved.isEmpty() || !(resolved.get() instanceof AEStorageBusHandle handle)) {
            return Collections.emptySet();
        }

        IPart part = handle.getPart();
        Set<ResourceLocation> capabilities = new LinkedHashSet<>();
        capabilities.add(StorageBusCapabilityIds.RENAME);
        if (part instanceof IPriorityHost) capabilities.add(StorageBusCapabilityIds.PRIORITY);
        if (buildFilterCapability(part).isPresent()) capabilities.add(StorageBusCapabilityIds.FILTER);

        return capabilities;
    }

    private Optional<IFilterCapability> buildFilterCapability(IPart part) {
        IAEStackInventory config = configInventory(part);
        IAEStackType<?> stackType = stackType(part);
        if (config == null || stackType == null) return Optional.empty();

        int availableSlots = availableSlots(part, config);
        FilterType filterType = filterTypeOf(stackType);

        return Optional.of(
            new AEStorageBusFilterCapability(config, stackType, availableSlots, filterType, contentsSupplier(part)));
    }

    private IAEStackInventory configInventory(IPart part) {
        if (part instanceof PartStorageBus bus) return bus.getAEInventoryByName(StorageName.CONFIG);

        return null;
    }

    private IAEStackType<?> stackType(IPart part) {
        if (part instanceof PartStorageBus bus) return bus.getStackType();

        return null;
    }

    private int availableSlots(IPart part, IAEStackInventory config) {
        if (part instanceof PartUpgradeable upgradeable) {
            int capacity = upgradeable.getInstalledUpgrades(Upgrades.CAPACITY);

            return Math.min(config.getSizeInventory(), 18 + capacity * 9);
        }

        return config.getSizeInventory();
    }

    private Supplier<List<IAEStack<?>>> contentsSupplier(IPart part) {
        if (!(part instanceof PartStorageBus bus)) return null;

        return () -> collectContents(bus);
    }

    private List<IAEStack<?>> collectContents(PartStorageBus bus) {
        List<IAEStack<?>> result = new ArrayList<>();
        IMEInventoryHandler<?> handler = bus.getInternalHandler();
        IAEStackType<?> type = bus.getStackType();
        if (handler == null || type == null) return result;

        collectFromHandler(result, handler, type);

        return result;
    }

    @SuppressWarnings("unchecked")
    private <S extends IAEStack<S>> void collectFromHandler(List<IAEStack<?>> out, IMEInventoryHandler<?> handler,
        IAEStackType<?> type) {
        IItemList<S> list = ((IAEStackType<S>) type).createList();
        ((IMEInventoryHandler<S>) handler).getAvailableItems(list, IterationCounter.fetchNewId());
        for (S stack : list) out.add(stack);
    }

    private FilterType filterTypeOf(IAEStackType<?> stackType) {
        String typeId = stackType.getId();
        if ("fluid".equals(typeId)) return FilterType.FLUID;
        if ("essentia".equals(typeId)) return FilterType.ESSENTIA;

        return FilterType.ITEM;
    }
}
