package com.hfstudio.diskterminal.api;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Immutable preview entry for an interface view.
 */
public final class ResourcePreviewEntry {

    private final ResourceType resourceType;
    private final ItemStack displayStack;
    private final long amount;

    public ResourcePreviewEntry(@Nonnull ResourceType resourceType, ItemStack displayStack, long amount) {
        this.resourceType = resourceType != null ? resourceType : ResourceType.ITEM;

        if (ItemStacks.isEmpty(displayStack)) {
            this.displayStack = null;
        } else {
            this.displayStack = displayStack.copy();
            this.displayStack.stackSize = 1;
        }

        this.amount = Math.max(0, amount);
    }

    @Nonnull
    public ResourceType getResourceType() {
        return this.resourceType;
    }

    public ItemStack getDisplayStack() {
        return this.displayStack;
    }

    public long getAmount() {
        return this.amount;
    }
}
