package com.hfstudio.diskterminal.config;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;
import com.hfstudio.diskterminal.DiskTerminal;

public class DiskTerminalGuiConfig extends SimpleGuiConfig {

    public DiskTerminalGuiConfig(GuiScreen parentScreen) throws ConfigException {
        super(parentScreen, DiskTerminal.MODID, DiskTerminal.MODNAME, true, DiskTerminalConfig.class);
    }
}
