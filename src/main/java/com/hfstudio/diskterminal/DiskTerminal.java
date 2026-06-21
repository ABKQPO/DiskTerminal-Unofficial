package com.hfstudio.diskterminal;

import static com.hfstudio.diskterminal.DiskTerminal.MODID;
import static com.hfstudio.diskterminal.DiskTerminal.MODNAME;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@Mod(
    modid = MODID,
    version = Tags.VERSION,
    name = MODNAME,
    guiFactory = "com.hfstudio.diskterminal.config.DiskTerminalGuiFactory",
    dependencies = "required-after:appliedenergistics2;"
        + "after:ae2fc;after:thaumicenergistics;after:ae2wct;after:NotEnoughItems;after:gregtech;",
    acceptableRemoteVersions = "*",
    acceptedMinecraftVersions = "[1.7.10]")
public class DiskTerminal {

    @Mod.Instance(Tags.MODID)
    public static DiskTerminal instance;
    public static final String MODID = Tags.MODID;
    public static final String MODNAME = Tags.MODNAME;
    public static final String VERSION = Tags.VERSION;
    public static final String ARTHOR = "HFstudio";
    public static final Logger LOG = LogManager.getLogger(MODID);

    public static boolean debug = false;

    @SidedProxy(
        clientSide = "com.hfstudio.diskterminal.ClientProxy",
        serverSide = "com.hfstudio.diskterminal.CommonProxy")
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper network;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void completeInit(FMLLoadCompleteEvent event) {
        proxy.completeInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {}

    @Mod.EventHandler
    public void onMissingMappings(FMLMissingMappingsEvent event) {}
}
