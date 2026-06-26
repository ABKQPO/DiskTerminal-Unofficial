package com.hfstudio.diskterminal.client;

import net.minecraft.nbt.NBTTagCompound;

public enum BusRole {

    STORAGE("gui.disk_terminal.storage_bus.name", ""),
    IMPORT("gui.disk_terminal.bus.import.name", "gui.disk_terminal.storage_bus.prefix.import"),
    EXPORT("gui.disk_terminal.bus.export.name", "gui.disk_terminal.storage_bus.prefix.export");

    public static final String NBT_KEY = "busRole";

    private final String nameKey;
    private final String prefixKey;

    BusRole(String nameKey, String prefixKey) {
        this.nameKey = nameKey;
        this.prefixKey = prefixKey;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getPrefixKey() {
        return prefixKey;
    }

    public static BusRole fromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBT_KEY)) {
            int ordinal = nbt.getInteger(NBT_KEY);
            BusRole[] values = values();
            if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        }

        return STORAGE;
    }

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger(NBT_KEY, ordinal());
    }
}
