package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.integration.Mods;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

/**
 * Scanner for the AE2FluidCraft Super Stock Replenisher tile. The machine is surfaced as one mixed
 * storage target whose filter slots can independently represent fluids and items.
 */
public class StockReplenisherScanner implements IStorageBusScanner {

    public static final StockReplenisherScanner INSTANCE = new StockReplenisherScanner();

    private static final int SIDE_ORDINAL = ForgeDirection.UNKNOWN.ordinal();

    private StockReplenisherScanner() {}

    @Override
    public String getId() {
        return "ae2fc_stock_replenisher";
    }

    @Override
    public boolean isAvailable() {
        return Mods.AE2FluidCraft.isModLoaded();
    }

    @Override
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap,
        int contentLimit) {
        if (grid == null) return;

        for (IGridNode node : grid.getMachines(TileSuperStockReplenisher.class)) {
            if (!node.isActive()) continue;

            Object machine = node.getMachine();
            if (!(machine instanceof TileSuperStockReplenisher tile)) continue;

            appendConfig(tile, out, trackerMap);
        }
    }

    private void appendConfig(TileSuperStockReplenisher tile, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
        StorageBusId targetId = StorageBusId.of(tile, SIDE_ORDINAL, BusRole.STORAGE, StorageType.ITEM);
        long busId = targetId.getLegacyKey();

        ItemStack icon = iconFor(tile);
        String displayName = icon == null ? "" : icon.getDisplayName();
        NBTTagCompound busData = StorageBusDataHandler.createMixedStockReplenisherData(tile, icon, displayName, busId);
        out.appendTag(busData);

        trackerMap.put(
            busId,
            new StorageBusTracker(busId, tile, tile, SIDE_ORDINAL, StorageType.ITEM)
                .withTarget(targetId, StorageBusSource.AE2FC_STOCK_REPLENISHER));
    }

    private ItemStack iconFor(TileSuperStockReplenisher tile) {
        Block block = tile.getWorldObj()
            .getBlock(tile.xCoord, tile.yCoord, tile.zCoord);
        if (block == null) return null;

        Item item = Item.getItemFromBlock(block);
        if (item == null) return null;

        return new ItemStack(item, 1, tile.getBlockMetadata());
    }
}
