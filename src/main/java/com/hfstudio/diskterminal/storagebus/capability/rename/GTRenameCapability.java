package com.hfstudio.diskterminal.storagebus.capability.rename;

import net.minecraft.world.World;

import com.hfstudio.diskterminal.api.capability.IRenameCapability;
import com.hfstudio.diskterminal.data.StorageBusCustomNameData;

/**
 * Rename capability for GregTech ME input bus/hatch machines. GregTech machines do not expose a
 * mutable custom name, so names are persisted in world-saved data keyed by the bus's stable id.
 */
public class GTRenameCapability implements IRenameCapability {

    private final World world;
    private final long busKey;

    public GTRenameCapability(World world, long busKey) {
        this.world = world;
        this.busKey = busKey;
    }

    @Override
    public boolean canRename() {
        return StorageBusCustomNameData.get(world) != null;
    }

    @Override
    public String getCustomName() {
        StorageBusCustomNameData data = StorageBusCustomNameData.get(world);
        if (data == null) return "";

        String name = data.getCustomName(busKey);

        return name == null ? "" : name;
    }

    @Override
    public void rename(String newName) {
        StorageBusCustomNameData data = StorageBusCustomNameData.get(world);
        if (data == null) return;

        String trimmed = newName == null ? "" : newName.trim();
        data.setCustomName(busKey, trimmed.isEmpty() ? null : trimmed);
    }

    @Override
    public void clearCustomName() {
        StorageBusCustomNameData data = StorageBusCustomNameData.get(world);
        if (data != null) data.setCustomName(busKey, null);
    }
}
