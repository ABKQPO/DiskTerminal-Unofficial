package com.hfstudio.diskterminal;

import com.hfstudio.diskterminal.config.DiskTerminalServerConfig;
import com.hfstudio.diskterminal.gui.GuiHandler;
import com.hfstudio.diskterminal.integration.AE2FluidCraftIntegration;
import com.hfstudio.diskterminal.integration.GregTechIntegration;
import com.hfstudio.diskterminal.integration.ThaumicEnergisticsIntegration;
import com.hfstudio.diskterminal.integration.storage.AE2StorageScanner;
import com.hfstudio.diskterminal.integration.storage.StorageScannerRegistry;
import com.hfstudio.diskterminal.integration.storagebus.AE2StorageBusScanner;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;
import com.hfstudio.diskterminal.integration.subnet.AE2SubnetScanner;
import com.hfstudio.diskterminal.integration.subnet.SubnetScannerRegistry;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Initialize server config (applies to both client and server)
        DiskTerminalServerConfig.init(event.getModConfigurationDirectory());
        DiskTerminalNetwork.init();
        ItemRegistry.init();
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(DiskTerminal.instance, new GuiHandler());

        // Register scanners for storage devices, storage buses, and subnets
        StorageScannerRegistry.register(AE2StorageScanner.INSTANCE);
        StorageBusScannerRegistry.register(AE2StorageBusScanner.INSTANCE);
        AE2FluidCraftIntegration.registerStorageBusScanner();
        GregTechIntegration.registerStorageBusScanner();
        ThaumicEnergisticsIntegration.registerStorageBusScanner();
        SubnetScannerRegistry.register(AE2SubnetScanner.INSTANCE);

        RecipeRegistry.init();
    }

    public void postInit(FMLPostInitializationEvent event) {
    }

    public void completeInit(FMLLoadCompleteEvent event) {
    }
}
