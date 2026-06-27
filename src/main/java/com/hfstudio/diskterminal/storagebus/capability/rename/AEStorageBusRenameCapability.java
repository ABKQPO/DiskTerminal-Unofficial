package com.hfstudio.diskterminal.storagebus.capability.rename;

import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.api.capability.IRenameCapability;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.helpers.ICustomNameObject;

/**
 * Rename capability for AE2 part-based storage buses. Names are stored on the part itself through
 * {@link ICustomNameObject}; clearing falls back to wiping the custom name on the part's world stack.
 */
public class AEStorageBusRenameCapability implements IRenameCapability {

    private final IPart part;

    public AEStorageBusRenameCapability(IPart part) {
        this.part = part;
    }

    @Override
    public boolean canRename() {
        return part instanceof ICustomNameObject;
    }

    @Override
    public String getCustomName() {
        if (part instanceof ICustomNameObject nameable && nameable.hasCustomName()) {
            String name = nameable.getCustomName();

            return name == null ? "" : name;
        }

        return "";
    }

    @Override
    public void rename(String newName) {
        if (!(part instanceof ICustomNameObject nameable)) return;

        String trimmed = newName == null ? "" : newName.trim();
        nameable.setCustomName(trimmed.isEmpty() ? null : trimmed);
    }

    @Override
    public void clearCustomName() {
        if (part instanceof ICustomNameObject nameable) {
            nameable.setCustomName(null);

            return;
        }

        ItemStack worldStack = part.getItemStack(PartItemStack.World);
        if (!ItemStacks.isEmpty(worldStack)) ItemStacks.clearCustomName(worldStack);
    }
}
