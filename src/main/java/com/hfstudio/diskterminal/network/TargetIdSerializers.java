package com.hfstudio.diskterminal.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.api.identity.TargetIdSerializer;
import com.hfstudio.diskterminal.api.identity.TargetIdSerializerRegistry;

/**
 * Default {@link TargetIdSerializerRegistry}: a process-wide registry keyed by identity type. The
 * network layer resolves a serializer by type so it never branches on concrete identity classes.
 */
public class TargetIdSerializers implements TargetIdSerializerRegistry {

    public static final TargetIdSerializers INSTANCE = new TargetIdSerializers();

    private final Map<ResourceLocation, TargetIdSerializer<?>> serializers = new HashMap<>();

    private TargetIdSerializers() {}

    @Override
    public void register(TargetIdSerializer<?> serializer) {
        if (serializer == null) return;

        serializers.put(serializer.type(), serializer);
    }

    @Override
    public Optional<TargetIdSerializer<?>> find(ResourceLocation type) {
        return Optional.ofNullable(serializers.get(type));
    }
}
