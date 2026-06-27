package com.hfstudio.diskterminal.storagebus.runtime;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.capability.IPriorityCapability;
import com.hfstudio.diskterminal.api.capability.IRefreshCapability;
import com.hfstudio.diskterminal.api.capability.IRenameCapability;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.storagebus.model.SimpleFilterSnapshot;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

/**
 * Performs the server-side capability action chain: resolve the provider for a target, look up the
 * requested capability, execute the action, then persist via the refresh capability. The chain never
 * inspects concrete AE or GT types.
 */
public class StorageBusActionExecutor {

    /**
     * Execute a capability action against the bus identified by {@code targetId}.
     *
     * @return true if the bus changed and the client read model should be refreshed
     */
    public boolean execute(StorageBusCapabilityProviderRegistry registry, StorageBusId targetId,
        ResourceLocation capability, ResourceLocation action, NBTTagCompound payload) {
        ICapabilityProvider<?> provider = registry.find(targetId)
            .orElse(null);
        if (provider == null) return false;

        boolean changed = dispatch(provider, capability, action, payload);
        if (changed) persist(provider);

        return changed;
    }

    private boolean dispatch(ICapabilityProvider<?> provider, ResourceLocation capability, ResourceLocation action,
        NBTTagCompound payload) {
        if (StorageBusCapabilityIds.RENAME.equals(capability)) {
            return executeRename(provider, action, payload);
        }
        if (StorageBusCapabilityIds.PRIORITY.equals(capability)) {
            return executePriority(provider, action, payload);
        }
        if (StorageBusCapabilityIds.FILTER.equals(capability)) {
            return executeFilter(provider, action, payload);
        }

        return false;
    }

    private boolean executeRename(ICapabilityProvider<?> provider, ResourceLocation action, NBTTagCompound payload) {
        IRenameCapability rename = provider.findCapability(IRenameCapability.class)
            .orElse(null);
        if (rename == null || !rename.canRename()) return false;

        if (StorageBusActionIds.RENAME_SET_NAME.equals(action)) {
            rename.rename(payload.getString("name"));

            return true;
        }
        if (StorageBusActionIds.RENAME_CLEAR_NAME.equals(action)) {
            rename.clearCustomName();

            return true;
        }

        return false;
    }

    private boolean executePriority(ICapabilityProvider<?> provider, ResourceLocation action, NBTTagCompound payload) {
        IPriorityCapability priority = provider.findCapability(IPriorityCapability.class)
            .orElse(null);
        if (priority == null || !priority.canEditPriority()) return false;

        if (StorageBusActionIds.PRIORITY_SET_VALUE.equals(action)) {
            priority.setPriority(payload.getInteger("priority"));

            return true;
        }

        return false;
    }

    private boolean executeFilter(ICapabilityProvider<?> provider, ResourceLocation action, NBTTagCompound payload) {
        IFilterCapability filter = provider.findCapability(IFilterCapability.class)
            .orElse(null);
        if (filter == null) return false;

        if (StorageBusActionIds.FILTER_SET_SLOT.equals(action)) {
            return filter.setFilter(payload.getInteger("slot"), readFilter(payload));
        }
        if (StorageBusActionIds.FILTER_CLEAR_SLOT.equals(action)) {
            return filter.clearFilter(payload.getInteger("slot"));
        }
        if (StorageBusActionIds.FILTER_CLEAR_ALL.equals(action)) {
            filter.clearAllFilters();

            return true;
        }
        if (StorageBusActionIds.FILTER_TOGGLE.equals(action)) {
            return filter.toggleFilter(readFilter(payload));
        }
        if (StorageBusActionIds.FILTER_FILL_FROM_PREVIEW.equals(action)) {
            return filter.supportsPreviewFill() && filter.fillFromPreview();
        }

        return false;
    }

    private FilterSnapshot readFilter(NBTTagCompound payload) {
        if (!payload.hasKey("filter")) return null;

        FilterType type = filterTypeOf(payload.getInteger("filterType"));

        return new SimpleFilterSnapshot(type, payload.getCompoundTag("filter"));
    }

    private FilterType filterTypeOf(int ordinal) {
        FilterType[] values = FilterType.values();

        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FilterType.ITEM;
    }

    private void persist(ICapabilityProvider<?> provider) {
        provider.findCapability(IRefreshCapability.class)
            .ifPresent(refresh -> {
                refresh.markDirty();
                refresh.requestRefresh();
            });
    }
}
