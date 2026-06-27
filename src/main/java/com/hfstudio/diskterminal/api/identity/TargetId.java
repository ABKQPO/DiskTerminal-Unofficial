package com.hfstudio.diskterminal.api.identity;

import net.minecraft.util.ResourceLocation;

/**
 * Common, strongly typed identity for any action target in the terminal.
 * <p>
 * Identities must be serializable and stable across the client/server boundary. Third parties may
 * register their own id types together with a {@link TargetIdSerializer}. Raw {@code String} ids are
 * intentionally not used.
 */
public interface TargetId {

    /**
     * The registered type of this identity, used to locate its serializer.
     */
    ResourceLocation type();
}
