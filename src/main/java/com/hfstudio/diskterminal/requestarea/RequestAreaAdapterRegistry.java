package com.hfstudio.diskterminal.requestarea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import appeng.api.features.ILevelViewable;

public class RequestAreaAdapterRegistry {

    private static final RequestAreaAdapterRegistry INSTANCE = createDefault();

    private final List<RequestAreaAdapter> adapters = new ArrayList<>();

    public static RequestAreaAdapterRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void register(RequestAreaAdapter adapter) {
        if (adapter == null) return;

        adapters.removeIf(
            existing -> existing.getTypeId()
                .equals(adapter.getTypeId()));
        adapters.add(0, adapter);
    }

    public synchronized Optional<RequestAreaAdapter> find(ILevelViewable device) {
        for (RequestAreaAdapter adapter : adapters) {
            if (adapter.supports(device)) return Optional.of(adapter);
        }

        return Optional.empty();
    }

    public synchronized Optional<RequestAreaAdapter> findByType(String typeId) {
        if (typeId == null) return Optional.empty();

        for (RequestAreaAdapter adapter : adapters) {
            if (typeId.equals(adapter.getTypeId())) return Optional.of(adapter);
        }

        return Optional.empty();
    }

    public synchronized List<RequestAreaAdapter> getAdapters() {
        return Collections.unmodifiableList(new ArrayList<>(adapters));
    }

    private static RequestAreaAdapterRegistry createDefault() {
        RequestAreaAdapterRegistry registry = new RequestAreaAdapterRegistry();
        registry.register(new AdvancedLevelEmitterAdapter());
        registry.register(new LevelEmitterAdapter());
        return registry;
    }
}
