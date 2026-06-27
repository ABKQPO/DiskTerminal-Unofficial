package com.hfstudio.diskterminal.storagebus.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.FilterType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.model.SimpleCapabilityMetadata;
import com.hfstudio.diskterminal.storagebus.model.SimpleFilterSnapshot;
import com.hfstudio.diskterminal.storagebus.model.SimpleStorageBusDescriptor;
import com.hfstudio.diskterminal.storagebus.model.SimpleStorageBusSnapshot;
import com.hfstudio.diskterminal.storagebus.model.StorageBusDescriptor;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.model.StorageBusScanEntry;
import com.hfstudio.diskterminal.storagebus.model.StorageBusSnapshot;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityProviderRegistry;
import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Builds the {@link StorageBusScanEntry} (descriptor + snapshot) for a scanned bus and writes its
 * capability metadata back into the read-model NBT. Capability availability is computed once on the
 * server by asking the freshly registered provider which capabilities it can resolve, so the client
 * can decide what to show without an RPC round-trip or any concrete type knowledge.
 * <p>
 * The metadata is advisory for the GUI only; the server still re-checks the capability before
 * executing any action.
 */
public class StorageBusSnapshotAssembler {

    public static final String CAPABILITIES_KEY = "availableCapabilities";

    /**
     * Assemble the scan entry for the given tracker and its read-model NBT, and write the computed
     * capability metadata back into {@code busData}.
     *
     * @return the assembled entry, or null when the tracker carries no stable identity
     */
    public StorageBusScanEntry assemble(StorageBusTracker tracker, NBTTagCompound busData,
        StorageBusCapabilityProviderRegistry registry) {
        StorageBusId id = tracker.targetId;
        if (id == null) return null;

        Set<ResourceLocation> capabilities = resolveCapabilities(id, registry);
        writeCapabilities(busData, capabilities);

        StorageBusDescriptor descriptor = new SimpleStorageBusDescriptor(
            id,
            id.getPosition(),
            id.getDimension(),
            readIcon(busData),
            ForgeDirection.getOrientation(id.getSideOrdinal()),
            id.getRole(),
            id.getStorageType());

        StorageBusSnapshot snapshot = new SimpleStorageBusSnapshot(
            id,
            busData.hasKey("displayName") ? busData.getString("displayName") : "",
            busData.getInteger("priority"),
            readFilters(
                busData,
                id.getStorageType()
                    .ordinal()),
            new SimpleCapabilityMetadata(capabilities));

        return new StorageBusScanEntry(descriptor, snapshot);
    }

    private Set<ResourceLocation> resolveCapabilities(StorageBusId id, StorageBusCapabilityProviderRegistry registry) {
        return registry.find(id)
            .map(ICapabilityProvider::availableCapabilities)
            .orElseGet(Collections::emptySet);
    }

    private ItemStack readIcon(NBTTagCompound busData) {
        if (busData.hasKey("busIcon")) return ItemStacks.load(busData.getCompoundTag("busIcon"));
        if (busData.hasKey("connectedIcon")) return ItemStacks.load(busData.getCompoundTag("connectedIcon"));

        return null;
    }

    private List<FilterSnapshot> readFilters(NBTTagCompound busData, int storageTypeOrdinal) {
        List<FilterSnapshot> filters = new ArrayList<>();
        if (!busData.hasKey("partition")) return filters;

        FilterType type = filterTypeOf(storageTypeOrdinal);
        NBTTagList partition = busData.getTagList("partition", 10);
        for (int i = 0; i < partition.tagCount(); i++) {
            filters.add(new SimpleFilterSnapshot(type, partition.getCompoundTagAt(i)));
        }

        return filters;
    }

    private FilterType filterTypeOf(int ordinal) {
        FilterType[] values = FilterType.values();

        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FilterType.ITEM;
    }

    private void writeCapabilities(NBTTagCompound busData, Set<ResourceLocation> capabilities) {
        NBTTagList list = new NBTTagList();
        for (ResourceLocation capability : capabilities) {
            list.appendTag(new NBTTagString(capability.toString()));
        }

        busData.setTag(CAPABILITIES_KEY, list);
    }
}
