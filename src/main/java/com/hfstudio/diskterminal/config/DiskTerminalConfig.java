package com.hfstudio.diskterminal.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.Config.Comment;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultBoolean;
import com.gtnewhorizon.gtnhlib.config.Config.RequiresMcRestart;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.diskterminal.DiskTerminal;

@Config(modid = DiskTerminal.MODID, filename = "disk_terminal", configSubDirectory = "disk_terminal")
@Config.LangKeyPattern(pattern = "disk_terminal.gui.config.%cat.%field", fullyQualified = true)
@Comment("DiskTerminal configuration")
public class DiskTerminalConfig {

    public static void registerConfig() throws ConfigException {
        ConfigurationManager.registerConfig(DiskTerminalConfig.class);
    }

    public static final Debug debug = new Debug();

    @Comment("Debug section")
    public static class Debug {

        @Comment("Enable Debug Print Log")
        @DefaultBoolean(false)
        @RequiresMcRestart
        public boolean enableDebugMode = false;
    }
}
