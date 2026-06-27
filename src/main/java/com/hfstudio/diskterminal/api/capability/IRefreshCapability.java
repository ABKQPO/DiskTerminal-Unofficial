package com.hfstudio.diskterminal.api.capability;

/**
 * Server-internal refresh behavior. Used after a capability mutates a target so the change is persisted
 * and the client read model is regenerated. This capability should not be exposed to GUI logic.
 */
public interface IRefreshCapability extends ICapability {

    /**
     * Mark the underlying object dirty so its state is saved.
     */
    void markDirty();

    /**
     * Request that the client read model be regenerated.
     */
    void requestRefresh();
}
