package com.hfstudio.diskterminal.storagebus.model;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.api.identity.TargetIdSerializer;

/**
 * {@link TargetIdSerializer} for the storage bus domain identity. Writes the identity type alongside
 * the data so the network layer can round-trip any registered {@code TargetId} without {@code instanceof}.
 */
public class StorageBusIdSerializer implements TargetIdSerializer<StorageBusId> {

    public static final StorageBusIdSerializer INSTANCE = new StorageBusIdSerializer();

    private StorageBusIdSerializer() {}

    @Override
    public ResourceLocation type() {
        return StorageBusId.TYPE;
    }

    @Override
    public void write(NBTTagCompound tag, StorageBusId id) {
        id.writeToNBT(tag);
    }

    @Override
    public StorageBusId read(NBTTagCompound tag) {
        return StorageBusId.readFromNBT(tag);
    }
}
