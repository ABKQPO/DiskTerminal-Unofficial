package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import gregtech.api.metatileentity.MetaTileEntity;

public class GTMachineAdapterRegistry {

    private final List<GTMachineAdapter> adapters = new ArrayList<>();

    public void register(GTMachineAdapter adapter) {
        if (adapter != null) {
            adapters.add(adapter);
        }
    }

    public Optional<GTMachineAdapter> find(MetaTileEntity metaTileEntity) {
        if (metaTileEntity == null) {
            return Optional.empty();
        }

        for (GTMachineAdapter adapter : adapters) {
            if (adapter.supports(metaTileEntity)) {
                return Optional.of(adapter);
            }
        }

        return Optional.empty();
    }
}
