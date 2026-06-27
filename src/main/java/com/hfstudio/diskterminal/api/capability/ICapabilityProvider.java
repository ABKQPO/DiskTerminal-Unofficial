package com.hfstudio.diskterminal.api.capability;

import java.util.Optional;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.api.identity.TargetId;

/**
 * Answers <em>what behaviors are currently available for a target</em>. A provider holds only the
 * target identity and re-resolves the real object at execution time; it must not expose the underlying
 * runtime object or hold it long-term.
 *
 * @param <ID> the identity type of the target
 */
public interface ICapabilityProvider<ID extends TargetId> {

    /**
     * Identity of the target this provider serves.
     */
    ID getTargetId();

    /**
     * Resolve a capability by its interface type, if currently available.
     *
     * @param capabilityType the capability interface to look up
     * @param <T>            the capability type
     * @return the capability, or empty if not available right now
     */
    <T extends ICapability> Optional<T> findCapability(Class<T> capabilityType);

    /**
     * The set of capability ids this provider can currently resolve. Implementations should compute
     * this with a single resolution so capability metadata can be built without repeatedly resolving
     * the same target. Used for read-model assembly only; execution still goes through
     * {@link #findCapability(Class)}.
     */
    Set<ResourceLocation> availableCapabilities();
}
