package com.hfstudio.diskterminal.storagebus.capability.priority;

import com.hfstudio.diskterminal.api.capability.IPriorityCapability;

import appeng.helpers.IPriorityHost;

/**
 * Priority capability for AE2 storage buses that expose {@link IPriorityHost}. Import/export buses that
 * do not support priority simply never receive this capability from their provider.
 */
public class AEStorageBusPriorityCapability implements IPriorityCapability {

    private final IPriorityHost priorityHost;

    public AEStorageBusPriorityCapability(IPriorityHost priorityHost) {
        this.priorityHost = priorityHost;
    }

    @Override
    public boolean canEditPriority() {
        return priorityHost != null;
    }

    @Override
    public int getPriority() {
        return priorityHost == null ? 0 : priorityHost.getPriority();
    }

    @Override
    public void setPriority(int priority) {
        if (priorityHost != null) priorityHost.setPriority(priority);
    }
}
