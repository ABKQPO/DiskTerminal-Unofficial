package com.hfstudio.diskterminal.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class StorageBusCustomNameData extends WorldSavedData {

    private static final String DATA_NAME = "disk_terminal_storage_bus_names";

    private final NBTTagCompound names = new NBTTagCompound();

    public StorageBusCustomNameData() {
        super(DATA_NAME);
    }

    public StorageBusCustomNameData(String name) {
        super(name);
    }

    public static StorageBusCustomNameData get(World world) {
        if (world == null || world.mapStorage == null) return null;

        StorageBusCustomNameData data = (StorageBusCustomNameData) world.mapStorage
            .loadData(StorageBusCustomNameData.class, DATA_NAME);
        if (data == null) {
            data = new StorageBusCustomNameData();
            world.mapStorage.setData(DATA_NAME, data);
            data.markDirty();
        }

        return data;
    }

    public String getCustomName(long busId) {
        String key = Long.toString(busId);
        if (!names.hasKey(key)) return null;

        String value = names.getString(key);
        return value == null || value.isEmpty() ? null : value;
    }

    public void setCustomName(long busId, String customName) {
        String key = Long.toString(busId);
        if (customName == null || customName.trim()
            .isEmpty()) {
            if (names.hasKey(key)) {
                names.removeTag(key);
                markDirty();
            }
            return;
        }

        String trimmed = customName.trim();
        if (trimmed.equals(names.getString(key))) return;

        names.setString(key, trimmed);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        for (Object keyObject : names.func_150296_c()
            .toArray()) {
            names.removeTag((String) keyObject);
        }
        NBTTagCompound storedNames = nbt.getCompoundTag("names");
        for (Object keyObject : storedNames.func_150296_c()) {
            String key = (String) keyObject;
            names.setString(key, storedNames.getString(key));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("names", names.copy());
    }
}
