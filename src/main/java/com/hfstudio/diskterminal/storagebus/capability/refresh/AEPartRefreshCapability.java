package com.hfstudio.diskterminal.storagebus.capability.refresh;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.hfstudio.diskterminal.api.capability.IRefreshCapability;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

/**
 * Refresh capability for AE part-based buses. Besides persisting the host, it proactively asks the
 * host to sync and tries to invoke the part's own refresh hook when one exists, so filter edits start
 * affecting bus work immediately instead of waiting for a later tick or host sync.
 */
public class AEPartRefreshCapability implements IRefreshCapability {

    private final IPart part;
    private final IPartHost host;

    public AEPartRefreshCapability(IPart part, IPartHost host) {
        this.part = part;
        this.host = host;
    }

    @Override
    public void markDirty() {
        if (host == null) return;

        host.markForSave();
        host.markForUpdate();
    }

    @Override
    public void requestRefresh() {
        invokeNoArg(part, "updateState");
        invokeNoArg(part, "resetCache", boolean.class, true);
    }

    private void invokeNoArg(Object target, String methodName) {
        invoke(target, methodName);
    }

    private void invokeNoArg(Object target, String methodName, Class<?> parameterType, Object argument) {
        if (target == null) return;

        try {
            Method method = findMethod(target.getClass(), methodName, parameterType);
            if (method == null) return;

            method.setAccessible(true);
            method.invoke(target, argument);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
    }

    private void invoke(Object target, String methodName) {
        if (target == null) return;

        try {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) return;

            method.setAccessible(true);
            method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
    }

    private Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        throw new NoSuchMethodException(methodName);
    }
}
