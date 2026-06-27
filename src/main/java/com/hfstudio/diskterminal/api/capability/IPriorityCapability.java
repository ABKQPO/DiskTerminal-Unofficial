package com.hfstudio.diskterminal.api.capability;

/**
 * Priority editing behavior. The interface intentionally does not encode a priority range; any limits
 * belong to the concrete implementation.
 */
public interface IPriorityCapability extends ICapability {

    /**
     * Whether priority can currently be edited on this target.
     */
    boolean canEditPriority();

    /**
     * The current priority value.
     */
    int getPriority();

    /**
     * Apply a new priority value.
     */
    void setPriority(int priority);
}
