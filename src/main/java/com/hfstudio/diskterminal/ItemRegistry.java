package com.hfstudio.diskterminal;

import com.hfstudio.diskterminal.items.ItemCellTerminal;
import com.hfstudio.diskterminal.items.ItemWirelessCellTerminal;

import appeng.api.AEApi;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Holds and registers the mod's items. Called during preInit.
 */
public class ItemRegistry {

    public static ItemCellTerminal CELL_TERMINAL;
    public static ItemWirelessCellTerminal WIRELESS_CELL_TERMINAL;

    private ItemRegistry() {}

    public static void init() {
        CELL_TERMINAL = new ItemCellTerminal();
        WIRELESS_CELL_TERMINAL = new ItemWirelessCellTerminal();

        GameRegistry.registerItem(CELL_TERMINAL, "cell_terminal");
        GameRegistry.registerItem(WIRELESS_CELL_TERMINAL, "wireless_cell_terminal");

        // Render the cell terminal as a cable-bus part item.
        AEApi.instance()
            .partHelper()
            .setItemBusRenderer(CELL_TERMINAL);

        // Register the wireless terminal with AE2's wireless registry (security-terminal linking).
        AEApi.instance()
            .registries()
            .wireless()
            .registerWirelessHandler(WIRELESS_CELL_TERMINAL);
    }
}
