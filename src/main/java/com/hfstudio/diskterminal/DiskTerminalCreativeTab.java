package com.hfstudio.diskterminal;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public class DiskTerminalCreativeTab extends CreativeTabs {

    public static final DiskTerminalCreativeTab INSTANCE = new DiskTerminalCreativeTab();

    public DiskTerminalCreativeTab() {
        super(Tags.MODID);
    }

    @Override
    public Item getTabIconItem() {
        return ItemRegistry.WIRELESS_CELL_TERMINAL;
    }
}
