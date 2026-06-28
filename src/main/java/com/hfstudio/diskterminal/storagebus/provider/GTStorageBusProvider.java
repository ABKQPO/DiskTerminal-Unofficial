package com.hfstudio.diskterminal.storagebus.provider;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.api.capability.ICapability;
import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.capability.IRefreshCapability;
import com.hfstudio.diskterminal.api.capability.IRenameCapability;
import com.hfstudio.diskterminal.storagebus.capability.filter.GTFluidFilterCapability;
import com.hfstudio.diskterminal.storagebus.capability.filter.GTItemFilterCapability;
import com.hfstudio.diskterminal.storagebus.capability.refresh.GTMachineRefreshCapability;
import com.hfstudio.diskterminal.storagebus.capability.rename.GTRenameCapability;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.GTStorageBusHandle;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityIds;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusHandle;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusResolver;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/**
 * Capability provider for GregTech ME input bus/hatch machines. Supports rename (via world-saved data)
 * and filter editing; GregTech machines do not expose priority. The live meta tile is re-resolved on
 * every call.
 */
public class GTStorageBusProvider implements ICapabilityProvider<StorageBusId> {

    private final StorageBusId id;
    private final StorageBusResolver resolver;

    public GTStorageBusProvider(StorageBusId id, StorageBusResolver resolver) {
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
        if (resolved.isEmpty() || !(resolved.get() instanceof GTStorageBusHandle handle)) return Optional.empty();

        MetaTileEntity meta = handle.getMetaTileEntity();
        TileEntity hostTile = handle.getHostTile();

        if (capabilityType == IRenameCapability.class) {
            return Optional.of((T) new GTRenameCapability(hostTile.getWorldObj(), id.getLegacyKey()));
        }

        if (capabilityType == IRefreshCapability.class) {
            return Optional.of((T) new GTMachineRefreshCapability(meta, hostTile));
        }

        if (capabilityType == IFilterCapability.class) {
            return buildFilterCapability(meta).map(capabilityType::cast);
        }

        return Optional.empty();
    }

    @Override
    public Set<ResourceLocation> availableCapabilities() {
        Optional<StorageBusHandle> resolved = resolver.resolve(id);
        if (resolved.isEmpty() || !(resolved.get() instanceof GTStorageBusHandle handle)) {
            return Collections.emptySet();
        }

        Set<ResourceLocation> capabilities = new LinkedHashSet<>();
        capabilities.add(StorageBusCapabilityIds.RENAME);
        // The filter capability is exposed even while auto-pull is active so the partition view still
        // renders; the capability itself rejects edits in that state. Only the meta-tile type matters
        // here, so avoid constructing (and serializing) the full filter capability.
        MetaTileEntity meta = handle.getMetaTileEntity();
        if (meta instanceof MTEHatchInputBusME || meta instanceof MTEHatchInputME) {
            capabilities.add(StorageBusCapabilityIds.FILTER);
        }

        return capabilities;
    }

    private Optional<IFilterCapability> buildFilterCapability(MetaTileEntity meta) {
        if (meta instanceof MTEHatchInputBusME inputBus) {
            return Optional.of(new GTItemFilterCapability(inputBus));
        }

        if (meta instanceof MTEHatchInputME inputHatch) {
            return Optional.of(new GTFluidFilterCapability(inputHatch));
        }

        return Optional.empty();
    }
}
