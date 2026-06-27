package com.hfstudio.diskterminal.api.identity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * Serialization contract for a single {@link TargetId} type. The network layer resolves a serializer
 * by {@link #type()} so it never needs to branch on concrete identity classes.
 *
 * @param <T> the identity type handled by this serializer
 */
public interface TargetIdSerializer<T extends TargetId> {

    /**
     * The identity type this serializer handles. Must match {@link TargetId#type()}.
     */
    ResourceLocation type();

    /**
     * Write the given identity into the provided tag.
     */
    void write(NBTTagCompound tag, T id);

    /**
     * Read an identity back from the provided tag.
     */
    T read(NBTTagCompound tag);
}
