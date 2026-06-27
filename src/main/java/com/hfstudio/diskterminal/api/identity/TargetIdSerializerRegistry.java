package com.hfstudio.diskterminal.api.identity;

import java.util.Optional;

import net.minecraft.util.ResourceLocation;

/**
 * Registry of {@link TargetIdSerializer}s keyed by identity type. Allows the network layer to read and
 * write any registered {@link TargetId} without using {@code instanceof}.
 */
public interface TargetIdSerializerRegistry {

    /**
     * Register a serializer. Implementations key it by {@link TargetIdSerializer#type()}.
     */
    void register(TargetIdSerializer<?> serializer);

    /**
     * Find the serializer registered for the given identity type, if any.
     */
    Optional<TargetIdSerializer<?>> find(ResourceLocation type);
}
