package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

public class GTMachineAdapters {

    private static final GTMachineAdapterRegistry REGISTRY = createRegistry();

    private GTMachineAdapters() {}

    public static GTMachineAdapterRegistry registry() {
        return REGISTRY;
    }

    private static GTMachineAdapterRegistry createRegistry() {
        GTMachineAdapterRegistry registry = new GTMachineAdapterRegistry();
        registry.register(new OredictInputBusAdapter());
        registry.register(new SuperInputBusAdapter());
        registry.register(new SuperInputHatchAdapter());
        registry.register(new SuperDualInputHatchAdapter());
        registry.register(new ProgrammableHatchesItemAdapter());
        registry.register(new ProgrammableHatchesFluidAdapter());
        registry.register(new StandardGTItemBusAdapter());
        registry.register(new StandardGTFluidHatchAdapter());
        return registry;
    }
}
