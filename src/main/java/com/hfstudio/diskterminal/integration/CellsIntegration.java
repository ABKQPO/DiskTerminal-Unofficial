package com.hfstudio.diskterminal.integration;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.hfstudio.diskterminal.api.FilterHostUtil;
import com.hfstudio.diskterminal.api.IFilterHost;
import com.hfstudio.diskterminal.api.IInterfaceHost;
import com.hfstudio.diskterminal.api.ResourcePreviewEntry;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.networking.IGrid;
import appeng.api.parts.PartItemStack;
import appeng.parts.AEBasePart;

/**
 * Generic display/normalize/host helpers shared by the terminal handlers.
 * <p>
 * In the 1.12 source these lived in a CELLS-mod integration. The CELLS mod is not ported, so the
 * external-interface preview methods return empty here while the generic ItemStack/tile/filter
 * helpers remain fully functional.
 */
public class CellsIntegration {

    private CellsIntegration() {}

    @Nullable
    public static ItemStack normalizeStack(ItemStack stack) {
        return FilterHostUtil.normalizeFilter(stack);
    }

    @Nullable
    public static TileEntity getHostTile(@Nullable Object host) {
        if (host instanceof TileEntity) return (TileEntity) host;

        if (host instanceof AEBasePart part) {

            return part.getHost() != null ? part.getHost()
                .getTile() : null;
        }

        return null;
    }

    public static ItemStack getPartDisplayStack(@Nullable Object part) {
        if (!(part instanceof AEBasePart)) return null;

        return normalizeStack(((AEBasePart) part).getItemStack(PartItemStack.Pick));
    }

    public static ItemStack getTileDisplayStack(@Nullable TileEntity tile) {
        if (tile == null || tile.getWorldObj() == null) return null;

        return getBlockDisplayStack(tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord);
    }

    public static ItemStack getHostDisplayStack(@Nullable Object host) {
        ItemStack partStack = getPartDisplayStack(host);
        if (!ItemStacks.isEmpty(partStack)) return partStack;

        return getTileDisplayStack(getHostTile(host));
    }

    public static ItemStack getBlockDisplayStack(@Nullable World world, int x, int y, int z) {
        if (world == null) return null;

        Block block = world.getBlock(x, y, z);
        if (block == null) return null;

        Item item = Item.getItemFromBlock(block);
        if (item == null) return null;

        return new ItemStack(item, 1, block.getDamageValue(world, x, y, z));
    }

    /**
     * Whether a filter host has any non-empty filter slot.
     */
    public static boolean hasPartition(@Nonnull IFilterHost host) {
        int slotCount = Math.max(0, host.getFilterSlots());
        for (int slot = 0; slot < slotCount; slot++) {
            if (!ItemStacks.isEmpty(host.getFilter(slot))) return true;
        }

        return false;
    }

    /**
     * Collect preview entries from an external interface host. CELLS is not ported, so this is
     * empty unless a future integration registers interface hosts.
     */
    @Nonnull
    public static List<ResourcePreviewEntry> collectInterfacePreviewEntries(@Nullable IInterfaceHost host, int limit) {
        return new ArrayList<>();
    }

    /**
     * Collect preview entries from a grid. CELLS is not ported, so this is empty.
     */
    @Nonnull
    public static List<ResourcePreviewEntry> collectGridPreviewEntries(@Nullable IGrid grid, int limit) {
        return new ArrayList<>();
    }
}
