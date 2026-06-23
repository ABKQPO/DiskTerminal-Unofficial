package com.hfstudio.diskterminal;

import net.minecraftforge.common.MinecraftForge;

import com.hfstudio.diskterminal.client.BlockHighlightRenderer;
import com.hfstudio.diskterminal.client.KeyBindings;
import com.hfstudio.diskterminal.client.KeyInputHandler;
import com.hfstudio.diskterminal.client.UpgradeTooltipHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        KeyBindings.registerAll();

        // FML bus: key input (works outside GUIs). Forge bus: world render + item tooltips.
        FMLCommonHandler.instance()
            .bus()
            .register(new KeyInputHandler());
        MinecraftForge.EVENT_BUS.register(new BlockHighlightRenderer());
        MinecraftForge.EVENT_BUS.register(new UpgradeTooltipHandler());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }

    @Override
    public void completeInit(FMLLoadCompleteEvent event) {
        super.completeInit(event);
    }
}
