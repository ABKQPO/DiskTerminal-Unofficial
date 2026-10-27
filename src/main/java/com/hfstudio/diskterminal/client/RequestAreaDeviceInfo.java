package com.hfstudio.diskterminal.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

public class RequestAreaDeviceInfo {

    private final long id;
    private final int dimension;
    private final BlockPos position;
    private final int sideOrdinal;
    private final String typeId;
    private final String customName;
    private final ItemStack icon;
    private final boolean outputActive;
    private final boolean canToggleMode;
    private final String mode;
    private final List<RequestAreaSlotInfo> slots;
    private final List<RequestAreaControlInfo> controls;
    private final List<ItemStack> upgrades;
    private final int upgradeSlotLimit;

    public RequestAreaDeviceInfo(long id, int dimension, BlockPos position, int sideOrdinal, String typeId,
        String customName, ItemStack icon, boolean outputActive, boolean canToggleMode, String mode,
        List<RequestAreaSlotInfo> slots, List<RequestAreaControlInfo> controls, List<ItemStack> upgrades,
        int upgradeSlotLimit) {
        this.id = id;
        this.dimension = dimension;
        this.position = position;
        this.sideOrdinal = sideOrdinal;
        this.typeId = typeId;
        this.customName = customName == null ? "" : customName;
        this.icon = ItemStacks.isEmpty(icon) ? null : icon.copy();
        this.outputActive = outputActive;
        this.canToggleMode = canToggleMode;
        this.mode = mode;
        this.slots = slots == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(slots));
        this.controls = controls == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(controls));
        this.upgrades = upgrades == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(upgrades));
        this.upgradeSlotLimit = Math.max(0, Math.min(upgradeSlotLimit, this.upgrades.size()));
    }

    public RequestAreaDeviceInfo(NBTTagCompound data) {
        this(
            data.getLong("id"),
            data.getInteger("dim"),
            PosUtil.fromLong(data.getLong("pos")),
            data.getInteger("side"),
            data.getString("type"),
            data.getString("name"),
            data.hasKey("icon") ? ItemStack.loadItemStackFromNBT(data.getCompoundTag("icon")) : null,
            data.getBoolean("outputActive"),
            data.getBoolean("canToggleMode"),
            data.getString("mode"),
            RequestAreaSlotInfo.readList(data.getTagList("slots", 10)),
            readControls(data.getTagList("controls", 10)),
            readUpgrades(data.getTagList("upgrades", 10)),
            data.getInteger("upgradeSlotLimit"));
    }

    private static List<RequestAreaControlInfo> readControls(NBTTagList entries) {
        List<RequestAreaControlInfo> controls = new ArrayList<>(entries.tagCount());
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound entry = entries.getCompoundTagAt(index);
            controls.add(
                new RequestAreaControlInfo(
                    entry.getString("id"),
                    entry.getString("setting"),
                    entry.getString("value"),
                    entry.getString("name"),
                    entry.getBoolean("active")));
        }
        return controls;
    }

    private static List<ItemStack> readUpgrades(NBTTagList entries) {
        List<ItemStack> upgrades = new ArrayList<>(entries.tagCount());
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound entry = entries.getCompoundTagAt(index);
            ItemStack upgrade = entry.hasKey("stack") ? ItemStack.loadItemStackFromNBT(entry.getCompoundTag("stack"))
                : null;
            upgrades.add(ItemStacks.isEmpty(upgrade) ? null : upgrade);
        }
        return upgrades;
    }

    public long getId() {
        return id;
    }

    public int getDimension() {
        return dimension;
    }

    public BlockPos getPosition() {
        return position;
    }

    public int getSideOrdinal() {
        return sideOrdinal;
    }

    public String getTypeId() {
        return typeId;
    }

    public String getName() {
        return customName.isEmpty() ? getDisplayName() : customName;
    }

    public String getDisplayName() {
        return ItemStacks.isEmpty(icon) ? "" : icon.getDisplayName();
    }

    public String getCustomName() {
        return customName;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public boolean isOutputActive() {
        return outputActive;
    }

    public boolean canToggleMode() {
        return canToggleMode;
    }

    public String getMode() {
        return mode;
    }

    public List<RequestAreaSlotInfo> getSlots() {
        return slots;
    }

    public List<RequestAreaControlInfo> getControls() {
        return controls;
    }

    public List<ItemStack> getUpgrades() {
        return upgrades;
    }

    public int getUpgradeSlotLimit() {
        return upgradeSlotLimit;
    }

    public String getLocationString() {
        return "(" + position.getX() + ", " + position.getY() + ", " + position.getZ() + ", " + dimension + ")";
    }
}
