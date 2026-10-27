package com.hfstudio.diskterminal.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.data.IAEStack;

public class RequestAreaSlotInfo {

    private final int index;
    private final ItemStack material;
    private final String materialName;
    private final long stored;
    private final long target;
    private final long batch;
    private final long threshold;
    private final int state;
    private final boolean canEditTarget;
    private final boolean canEditBatch;
    private final boolean canToggle;
    private final boolean enabled;
    private final boolean canInvert;
    private final boolean inverted;

    public RequestAreaSlotInfo(int index, ItemStack material, String materialName, long stored, long target, long batch,
        long threshold, int state, boolean canEditTarget, boolean canEditBatch, boolean canToggle, boolean enabled,
        boolean canInvert, boolean inverted) {
        this.index = index;
        this.material = ItemStacks.isEmpty(material) ? null : material.copy();
        this.materialName = materialName == null ? "" : materialName;
        this.stored = stored;
        this.target = target;
        this.batch = batch;
        this.threshold = threshold;
        this.state = state;
        this.canEditTarget = canEditTarget;
        this.canEditBatch = canEditBatch;
        this.canToggle = canToggle;
        this.enabled = enabled;
        this.canInvert = canInvert;
        this.inverted = inverted;
    }

    public static List<RequestAreaSlotInfo> readList(NBTTagList entries) {
        List<RequestAreaSlotInfo> slots = new ArrayList<>(entries.tagCount());
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound data = entries.getCompoundTagAt(index);
            IAEStack<?> stack = AEStackUtil.readStackFromNBT(data);
            ItemStack material = AEStackUtil.getDisplayStack(stack);
            String materialName = stack == null ? "" : stack.getDisplayName();
            if (materialName == null || materialName.isEmpty()) {
                materialName = ItemStacks.isEmpty(material) ? "" : material.getDisplayName();
            }
            slots.add(
                new RequestAreaSlotInfo(
                    index,
                    material,
                    materialName,
                    data.getLong("stored"),
                    data.getLong("target"),
                    data.getLong("batch"),
                    data.getLong("threshold"),
                    data.getInteger("state"),
                    data.getBoolean("canEditTarget"),
                    data.getBoolean("canEditBatch"),
                    data.getBoolean("canToggle"),
                    data.getBoolean("enabled"),
                    data.getBoolean("canInvert"),
                    data.getBoolean("inverted")));
        }
        return slots;
    }

    public int getIndex() {
        return index;
    }

    public ItemStack getMaterial() {
        return material;
    }

    public String getMaterialName() {
        return materialName;
    }

    public long getStored() {
        return stored;
    }

    public long getTarget() {
        return target;
    }

    public long getBatch() {
        return batch;
    }

    public long getThreshold() {
        return threshold;
    }

    public int getState() {
        return state;
    }

    public boolean canEditTarget() {
        return canEditTarget;
    }

    public boolean canEditBatch() {
        return canEditBatch;
    }

    public boolean canToggle() {
        return canToggle;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canInvert() {
        return canInvert;
    }

    public boolean isInverted() {
        return inverted;
    }
}
