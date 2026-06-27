package com.hfstudio.diskterminal.integration;

import com.hfstudio.diskterminal.config.DiskTerminalServerConfig;
import com.hfstudio.diskterminal.integration.storagebus.GregTechMEInputBusScanner;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;

import cpw.mods.fml.common.Optional;

public class GregTechIntegration {

    private GregTechIntegration() {}

    public static void registerStorageBusScanner() {
        if (!isEnabled()) return;

        registerStorageBusScannerInternal();
    }

    public static boolean isEnabled() {
        return Mods.GregTech.isModLoaded()
            && (!DiskTerminalServerConfig.isInitialized() || DiskTerminalServerConfig.getInstance()
                .isIntegrationGregTechEnabled());
    }

    @Optional.Method(modid = "gregtech")
    private static void registerStorageBusScannerInternal() {
        StorageBusScannerRegistry.register(GregTechMEInputBusScanner.INSTANCE);
    }
}
