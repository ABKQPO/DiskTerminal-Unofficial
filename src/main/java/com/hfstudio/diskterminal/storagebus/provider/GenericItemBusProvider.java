package com.hfstudio.diskterminal.storagebus.provider;

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
import com.hfstudio.diskterminal.api.capability.IRefreshCapability;
import com.hfstudio.diskterminal.api.capability.IRenameCapability;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.storagebus.capability.filter.AEStorageBusFilterCapability;
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
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.parts.automation.PartSharedItemBus;
import appeng.tile.inventory.IAEStackInventory;

/**
 * Capability provider for AE2 shared import/export buses ({@link PartSharedItemBus}), including AE2FC
 * fluid and Thaumic Energistics essentia variants. These buses support rename and filter editing but
 * not priority. They do not expose stable local contents, so preview fill intentionally avoids using
 * the entire AE network as a fallback.
 */
public class GenericItemBusProvider implements ICapabilityProvider<StorageBusId> {

    private final StorageBusId id;
    private final StorageBusResolver resolver;

    public GenericItemBusProvider(StorageBusId id, StorageBusResolver resolver) {
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
        if (!(part instanceof PartSharedItemBus<?>bus)) return Optional.empty();

        if (capabilityType == IRenameCapability.class) {
            return Optional.of((T) new AEStorageBusRenameCapability(part));
        }

        if (capabilityType == IRefreshCapability.class) {
            return Optional.of((T) new AEPartRefreshCapability(part, host));
        }

        if (capabilityType == IFilterCapability.class) {
            return buildFilterCapability(bus).map(capabilityType::cast);
        }

        return Optional.empty();
    }

    @Override
    public Set<ResourceLocation> availableCapabilities() {
        Optional<StorageBusHandle> resolved = resolver.resolve(id);
        if (resolved.isEmpty() || !(resolved.get() instanceof AEStorageBusHandle handle)) {
            return Collections.emptySet();
        }

        if (!(handle.getPart() instanceof PartSharedItemBus<?>bus)) return Collections.emptySet();

        Set<ResourceLocation> capabilities = new LinkedHashSet<>();
        capabilities.add(StorageBusCapabilityIds.RENAME);
        if (buildFilterCapability(bus).isPresent()) capabilities.add(StorageBusCapabilityIds.FILTER);

        return capabilities;
    }

    private Optional<IFilterCapability> buildFilterCapability(PartSharedItemBus<?> bus) {
        IAEStackInventory config = bus.getAEInventoryByName(StorageName.CONFIG);
        IAEStackType<?> stackType = bus.getStackType();
        if (config == null || stackType == null) return Optional.empty();

        int capacity = bus.getInstalledUpgrades(Upgrades.CAPACITY);
        int availableSlots = Math.min(config.getSizeInventory(), 1 + capacity * 4);
        FilterType filterType = filterTypeOf(stackType);

        return Optional
            .of(new AEStorageBusFilterCapability(config, stackType, availableSlots, filterType, contentsSupplier(bus)));
    }

    private Supplier<List<IAEStack<?>>> contentsSupplier(PartSharedItemBus<?> bus) {
        return () -> collectContents(bus);
    }

    private List<IAEStack<?>> collectContents(PartSharedItemBus<?> bus) {
        return StorageBusDataHandler.collectSharedBusPreviewContents(bus, Integer.MAX_VALUE);
    }

    private FilterType filterTypeOf(IAEStackType<?> stackType) {
        String typeId = stackType.getId();
        if ("fluid".equals(typeId)) return FilterType.FLUID;
        if ("essentia".equals(typeId)) return FilterType.ESSENTIA;

        return FilterType.ITEM;
    }
}
