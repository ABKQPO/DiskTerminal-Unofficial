package com.hfstudio.diskterminal.container.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.data.StorageBusCustomNameData;
import com.hfstudio.diskterminal.integration.Mods;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineClassNames;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineReflectionHelper;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.helpers.ICustomNameObject;
import appeng.me.GridAccessException;
import appeng.parts.automation.PartBaseExportBus;
import appeng.parts.automation.PartBaseImportBus;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.automation.PartUpgradeable;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.IterationCounter;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import thaumcraft.api.aspects.IAspectContainer;
import thaumicenergistics.common.integration.tc.EssentiaTileContainerHelper;
import thaumicenergistics.common.storage.AEEssentiaStack;

/**
 * Handles storage bus data collection and NBT generation.
 * Server-side handler that generates the data sent to clients when they view storage bus tabs.
 * <p>
 * In the 1.7.10 fork the fluid storage bus (AE2FluidCraft) is a subclass of {@link PartStorageBus}
 * that only differs by its {@link PartStorageBus#getStackType() stack type}, so item and fluid
 * buses share a single code path here. Stored contents are read uniformly through the bus's
 * internal ME handler.
 */
public class StorageBusDataHandler {

    private static final int MAX_CONTENT_ENTRIES_PER_BUS_PAYLOAD = 1024;
    private static final String PARTITION_SUMMARY_KEY = "_dtHasPartition";
    private static final String CONTENT_SUMMARY_KEY = "_dtHasContents";

    private record GregTechItemSnapshot(ItemStack[] configs, ItemStack[] extracted, int[] extractedAmounts) {}

    private record GregTechFluidSnapshot(FluidStack[] configs, FluidStack[] extracted, int[] extractedAmounts) {}

    /**
     * Tracker for storage bus instances, keyed by a synthetic bus ID.
     */
    public static class StorageBusTracker {

        public final long id;
        public final Object storageBus; // A PartStorageBus (item or fluid) or storage-bus-like machine.
        public final TileEntity hostTile;
        public final int sideOrdinal;
        public final StorageType storageType;
        public StorageBusId targetId;
        public StorageBusSource source;
        public boolean hasPartitionConfigured;
        public boolean hasConnectedContents;
        public boolean partitionSummaryKnown;
        public boolean contentSummaryKnown;
        public Set<ResourceLocation> availableCapabilities = Collections.emptySet();

        public StorageBusTracker(long id, Object storageBus, TileEntity hostTile) {
            this(id, storageBus, hostTile, -1, null);
        }

        public StorageBusTracker(long id, Object storageBus, TileEntity hostTile, int sideOrdinal,
            StorageType storageType) {
            this.id = id;
            this.storageBus = storageBus;
            this.hostTile = hostTile;
            this.sideOrdinal = sideOrdinal;
            this.storageType = storageType;
        }

        /**
         * Attach the stable identity and source family used by the formal capability runtime. Returns
         * this tracker for fluent use from scanners.
         */
        public StorageBusTracker withTarget(StorageBusId targetId, StorageBusSource source) {
            this.targetId = targetId;
            this.source = source;

            return this;
        }
    }

    /**
     * Collect all storage buses from the grid and generate NBT data.
     *
     * @param grid       The ME network grid
     * @param trackerMap Map to populate with trackers (keyed by bus ID)
     * @return NBTTagList containing all storage bus data
     */
    public static NBTTagList collectStorageBuses(IGrid grid, Map<Long, StorageBusTracker> trackerMap,
        int contentLimit) {
        NBTTagList storageBusList = new NBTTagList();

        if (grid == null) return storageBusList;

        StorageBusScannerRegistry.scanAll(grid, storageBusList, trackerMap, contentLimit);

        return storageBusList;
    }

    /**
     * Create a unique bus ID from position, dimension, side, and type flag.
     */
    public static long createBusId(TileEntity hostTile, int sideOrdinal, int typeFlag) {
        long pos = PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord);

        return pos ^ ((long) hostTile.getWorldObj().provider.dimensionId << 48)
            ^ ((long) sideOrdinal << 40)
            ^ ((long) typeFlag << 39);
    }

    /**
     * Create NBT data for an item storage bus.
     */
    public static NBTTagCompound createItemStorageBusData(PartStorageBus bus, long busId, int contentLimit) {
        return createStorageBusData(bus, busId, StorageType.ITEM, contentLimit);
    }

    /**
     * Create NBT data for a fluid storage bus.
     */
    public static NBTTagCompound createFluidStorageBusData(PartStorageBus bus, long busId, int contentLimit) {
        return createStorageBusData(bus, busId, StorageType.FLUID, contentLimit);
    }

    public static NBTTagCompound createSharedBusData(PartSharedItemBus<?> bus, long busId, StorageType storageType,
        BusRole busRole, int contentLimit) {
        TileEntity hostTile = bus.getHost()
            .getTile();
        ForgeDirection side = bus.getSide();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord));
        busData.setInteger("dim", hostTile.getWorldObj().provider.dimensionId);
        busData.setInteger("side", side.ordinal());
        busData.setInteger("priority", 0);
        storageType.writeToNBT(busData);
        busRole.writeToNBT(busData);

        busData.setInteger("access", AccessRestriction.READ_WRITE.ordinal());
        busData.setString("namePrefixKey", busRole.getPrefixKey());
        applySharedBusSlotParameters(busData, bus);

        addBusItemIcon(busData, bus.getItemStack());
        addCustomName(busData, bus);
        addConnectedInventoryInfo(busData, hostTile, side);
        addBusItemIconAsConnectedIconIfMissing(busData, bus.getItemStack());

        int capacityUpgrades = bus.getInstalledUpgrades(Upgrades.CAPACITY);
        addSharedBusPartitionData(busData, bus, capacityUpgrades);
        addSharedBusContentsData(busData, bus, contentLimit);
        addUpgradesData(busData, bus.getInventoryByName("upgrades"));

        return busData;
    }

    public static NBTTagCompound createGregTechInputBusData(MTEHatchInputBusME inputBus, long busId,
        StorageType storageType, BusRole busRole, int contentLimit) {
        NBTTagCompound busData = createGregTechBusBaseData(inputBus, busId, storageType, busRole);
        addGregTechItemBusData(busData, inputBus, contentLimit);
        return busData;
    }

    public static NBTTagCompound createGregTechInputHatchData(MTEHatchInputME inputHatch, long busId,
        StorageType storageType, BusRole busRole, int contentLimit) {
        NBTTagCompound busData = createGregTechBusBaseData(inputHatch, busId, storageType, busRole);
        addGregTechFluidHatchData(busData, inputHatch, contentLimit);
        return busData;
    }

    public static NBTTagCompound createGenericGregTechItemBusData(MetaTileEntity metaTileEntity, long busId,
        StorageType storageType, BusRole busRole, boolean supportsAutoPull, boolean autoPullEnabled,
        ItemStack[] configs, ItemStack[] extracted, long[] extractedAmounts, int contentLimit) {
        NBTTagCompound busData = createGregTechBusBaseData(metaTileEntity, busId, storageType, busRole);
        addGenericGregTechItemBusData(
            busData,
            supportsAutoPull,
            autoPullEnabled,
            configs,
            extracted,
            extractedAmounts,
            contentLimit);
        return busData;
    }

    public static NBTTagCompound createGenericGregTechFluidHatchData(MetaTileEntity metaTileEntity, long busId,
        StorageType storageType, BusRole busRole, boolean supportsAutoPull, boolean autoPullEnabled,
        FluidStack[] configs, FluidStack[] extracted, long[] extractedAmounts, int contentLimit) {
        NBTTagCompound busData = createGregTechBusBaseData(metaTileEntity, busId, storageType, busRole);
        addGenericGregTechFluidBusData(
            busData,
            supportsAutoPull,
            autoPullEnabled,
            configs,
            extracted,
            extractedAmounts,
            contentLimit);
        return busData;
    }

    public static NBTTagCompound createGenericGregTechMixedBusData(MetaTileEntity metaTileEntity, long busId,
        BusRole busRole, boolean supportsAutoPull, boolean autoPullEnabled, ItemStack[] itemConfigs,
        ItemStack[] itemPreview, long[] itemAmounts, FluidStack[] fluidConfigs, FluidStack[] fluidPreview,
        long[] fluidAmounts, int contentLimit) {
        NBTTagCompound busData = createGregTechBusBaseData(metaTileEntity, busId, StorageType.ITEM, busRole);
        busData.setString("stackType", "mixed");
        busData.setBoolean("supportsAutoPull", supportsAutoPull);
        busData.setBoolean("autoPullEnabled", autoPullEnabled);

        int fluidSlotCount = fluidConfigs == null ? 0 : fluidConfigs.length;
        int itemSlotCount = itemConfigs == null ? 0 : itemConfigs.length;
        addStockReplenisherSlotTypes(busData, fluidSlotCount, itemSlotCount);

        NBTTagList partitionList = new NBTTagList();
        appendMixedFluidPartition(partitionList, fluidConfigs, 0);
        appendMixedItemPartition(partitionList, itemConfigs, fluidSlotCount);
        busData.setTag("partition", partitionList);

        NBTTagList contentsList = new NBTTagList();
        int writtenContents = 0;
        writtenContents = appendMixedFluidContents(
            contentsList,
            fluidPreview,
            fluidAmounts,
            writtenContents,
            0,
            contentLimit);
        appendMixedItemContents(contentsList, itemPreview, itemAmounts, writtenContents, fluidSlotCount, contentLimit);
        busData.setTag("contents", contentsList);
        busData.setBoolean(PARTITION_SUMMARY_KEY, partitionList.tagCount() > 0);
        busData.setBoolean(CONTENT_SUMMARY_KEY, hasMixedContents(itemPreview, fluidPreview));
        return busData;
    }

    /**
     * Create read-model NBT for the AE2FluidCraft Super Stock Replenisher as a single mixed-type target.
     * The tile exposes multiple independent config inventories (currently fluids and items). They are
     * flattened into one slot-addressable model so the GUI and capability layer can treat the target as
     * one extensible object with typed slots rather than two fake buses.
     */
    public static NBTTagCompound createMixedStockReplenisherData(TileSuperStockReplenisher tile, ItemStack iconStack,
        String displayName, long busId, int contentLimit) {
        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", PosUtil.toLong(tile.xCoord, tile.yCoord, tile.zCoord));
        busData.setInteger("dim", tile.getWorldObj().provider.dimensionId);
        busData.setInteger("side", ForgeDirection.UNKNOWN.ordinal());
        busData.setInteger("priority", 0);
        StorageType.ITEM.writeToNBT(busData);
        BusRole.STORAGE.writeToNBT(busData);
        busData.setInteger("access", AccessRestriction.READ_WRITE.ordinal());
        busData.setString("stackType", "mixed");
        busData.setBoolean("preferDisplayName", true);
        busData.setInteger("upgradeSlotCount", 0);
        if (displayName != null) busData.setString("displayName", displayName);

        int fluidSlots = tile.getConfigFluids() == null ? 0
            : tile.getConfigFluids()
                .getSizeInventory();
        int itemSlots = tile.getConfigItems() == null ? 0
            : tile.getConfigItems()
                .getSizeInventory();
        int totalSlots = fluidSlots + itemSlots;

        busData.setInteger("baseConfigSlots", totalSlots);
        busData.setInteger("slotsPerUpgrade", 0);
        busData.setInteger("maxConfigSlots", totalSlots);

        addBusItemIcon(busData, iconStack);
        addStorageBusCustomName(busData, tile.getWorldObj(), busId);
        addBusItemIconAsConnectedIconIfMissing(busData, iconStack);
        addStockReplenisherSlotTypes(busData, fluidSlots, itemSlots);
        addMixedStockReplenisherPartitionData(busData, tile, fluidSlots);
        addMixedStockReplenisherContentsData(busData, tile, contentLimit);

        return busData;
    }

    private static void addStockReplenisherSlotTypes(NBTTagCompound busData, int fluidSlots, int itemSlots) {
        NBTTagList slotTypes = new NBTTagList();
        for (int i = 0; i < fluidSlots; i++) {
            slotTypes.appendTag(new NBTTagString("fluid"));
        }
        for (int i = 0; i < itemSlots; i++) {
            slotTypes.appendTag(new NBTTagString("item"));
        }
        busData.setTag("slotTypes", slotTypes);
    }

    private static void addMixedStockReplenisherPartitionData(NBTTagCompound busData, TileSuperStockReplenisher tile,
        int fluidSlotCount) {
        NBTTagList partitionList = new NBTTagList();
        appendStockReplenisherPartitionGroup(partitionList, tile.getConfigFluids(), 0, "fluid");
        appendStockReplenisherPartitionGroup(partitionList, tile.getConfigItems(), fluidSlotCount, "item");
        busData.setTag("partition", partitionList);
        busData.setBoolean(PARTITION_SUMMARY_KEY, partitionList.tagCount() > 0);
    }

    private static void appendStockReplenisherPartitionGroup(NBTTagList partitionList, IAEStackInventory config,
        int slotOffset, String stackTypeId) {
        if (config == null) return;

        for (int i = 0; i < config.getSizeInventory(); i++) {
            IAEStack<?> stack = config.getAEStackInSlot(i);
            if (stack == null) continue;

            NBTTagCompound partNbt = createPartitionSlotData(slotOffset + i, stack);
            partNbt.setString("stackTypeId", stackTypeId);
            partitionList.appendTag(partNbt);
        }
    }

    private static void addMixedStockReplenisherContentsData(NBTTagCompound busData, TileSuperStockReplenisher tile,
        int contentLimit) {
        NBTTagList contentsList = new NBTTagList();
        int effectiveContentLimit = getEffectiveContentLimit(contentLimit);
        boolean hasContents = false;
        int writtenContents = 0;

        if (tile.getInternalFluid() != null) {
            for (int i = 0; i < tile.getInternalFluid()
                .getSlots(); i++) {
                IAEFluidStack fluid = tile.getInternalFluid()
                    .getFluidInSlot(i);
                if (fluid == null) continue;

                hasContents = true;
                if (writtenContents >= effectiveContentLimit) continue;

                NBTTagCompound stackNbt = new NBTTagCompound();
                AEStackUtil.writeStackToNBT(stackNbt, fluid);
                stackNbt.setLong("Cnt", fluid.getStackSize());
                stackNbt.setString("stackTypeId", "fluid");
                contentsList.appendTag(stackNbt);
                writtenContents++;
            }
        }

        if (tile.getInternalInventory() != null) {
            for (int i = 0; i < tile.getInternalInventory()
                .getSizeInventory(); i++) {
                ItemStack stack = tile.getInternalInventory()
                    .getStackInSlot(i);
                if (ItemStacks.isEmpty(stack)) continue;

                hasContents = true;
                if (writtenContents >= effectiveContentLimit) continue;

                NBTTagCompound stackNbt = new NBTTagCompound();
                AEStackUtil.writeStackToNBT(stackNbt, AEStackUtil.createItemStack(stack));
                stackNbt.setLong("Cnt", stack.stackSize);
                stackNbt.setString("stackTypeId", "item");
                contentsList.appendTag(stackNbt);
                writtenContents++;
            }
        }

        busData.setTag("contents", contentsList);
        busData.setBoolean(CONTENT_SUMMARY_KEY, hasContents);
    }

    private static void appendMixedFluidPartition(NBTTagList partitionList, FluidStack[] configs, int slotOffset) {
        if (configs == null) {
            return;
        }

        for (int slot = 0; slot < configs.length; slot++) {
            FluidStack stack = configs[slot];
            if (stack == null) {
                continue;
            }

            NBTTagCompound partNbt = new NBTTagCompound();
            partNbt.setInteger("slot", slotOffset + slot);
            partNbt.setString("stackTypeId", "fluid");
            AEStackUtil.writeStackToNBT(
                partNbt,
                AEFluidStack.create(stack)
                    .setStackSize(1));
            partitionList.appendTag(partNbt);
        }
    }

    private static void appendMixedItemPartition(NBTTagList partitionList, ItemStack[] configs, int slotOffset) {
        if (configs == null) {
            return;
        }

        for (int slot = 0; slot < configs.length; slot++) {
            ItemStack stack = configs[slot];
            if (ItemStacks.isEmpty(stack)) {
                continue;
            }

            NBTTagCompound partNbt = AEStackUtil.writeItemLikePartitionStack(stack);
            if (partNbt == null) {
                continue;
            }
            partNbt.setInteger("slot", slotOffset + slot);
            partNbt.setString("stackTypeId", "item");
            partitionList.appendTag(partNbt);
        }
    }

    private static int appendMixedFluidContents(NBTTagList contentsList, FluidStack[] preview, long[] amounts,
        int writtenContents, int slotOffset, int contentLimit) {
        if (preview == null) {
            return writtenContents;
        }

        for (int slot = 0; slot < preview.length; slot++) {
            if (isContentLimitReached(writtenContents, contentLimit)) {
                return writtenContents;
            }

            FluidStack stack = preview[slot];
            if (stack == null) {
                continue;
            }

            NBTTagCompound stackNbt = new NBTTagCompound();
            AEStackUtil.writeStackToNBT(stackNbt, AEFluidStack.create(stack));
            stackNbt.setLong("Cnt", getLongAt(amounts, slot));
            stackNbt.setInteger("slot", slotOffset + slot);
            stackNbt.setString("stackTypeId", "fluid");
            contentsList.appendTag(stackNbt);
            writtenContents++;
        }

        return writtenContents;
    }

    private static int appendMixedItemContents(NBTTagList contentsList, ItemStack[] preview, long[] amounts,
        int writtenContents, int slotOffset, int contentLimit) {
        if (preview == null) {
            return writtenContents;
        }

        for (int slot = 0; slot < preview.length; slot++) {
            if (isContentLimitReached(writtenContents, contentLimit)) {
                return writtenContents;
            }

            ItemStack stack = preview[slot];
            if (ItemStacks.isEmpty(stack)) {
                continue;
            }

            NBTTagCompound stackNbt = AEStackUtil.writeItemLikePartitionStack(stack);
            if (stackNbt == null) {
                continue;
            }
            stackNbt.setLong("Cnt", getLongAt(amounts, slot));
            stackNbt.setInteger("slot", slotOffset + slot);
            stackNbt.setString("stackTypeId", "item");
            contentsList.appendTag(stackNbt);
            writtenContents++;
        }

        return writtenContents;
    }

    private static NBTTagCompound createStorageBusData(PartStorageBus bus, long busId, StorageType storageType,
        int contentLimit) {
        TileEntity hostTile = bus.getHost()
            .getTile();
        ForgeDirection side = bus.getSide();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord));
        busData.setInteger("dim", hostTile.getWorldObj().provider.dimensionId);
        busData.setInteger("side", side.ordinal());
        busData.setInteger("priority", bus.getPriority());
        storageType.writeToNBT(busData);
        BusRole.STORAGE.writeToNBT(busData);

        AccessRestriction access = (AccessRestriction) bus.getConfigManager()
            .getSetting(Settings.ACCESS);
        busData.setInteger("access", access.ordinal());

        addBusItemIcon(busData, bus.getItemStack());
        addCustomName(busData, bus);
        addConnectedInventoryInfo(busData, hostTile, side);
        addBusItemIconAsConnectedIconIfMissing(busData, bus.getItemStack());

        int capacityUpgrades = bus.getInstalledUpgrades(Upgrades.CAPACITY);
        addPartitionData(busData, bus, capacityUpgrades);
        addContentsData(busData, bus, storageType, contentLimit);
        addUpgradesData(busData, bus.getInventoryByName("upgrades"));

        return busData;
    }

    private static NBTTagCompound createGregTechBusBaseData(MetaTileEntity metaTileEntity, long busId,
        StorageType storageType, BusRole busRole) {
        TileEntity hostTile = (TileEntity) metaTileEntity.getBaseMetaTileEntity();
        ForgeDirection facing = metaTileEntity.getBaseMetaTileEntity()
            .getFrontFacing();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord));
        busData.setInteger("dim", hostTile.getWorldObj().provider.dimensionId);
        busData.setInteger("side", facing.ordinal());
        busData.setInteger("priority", 0);
        storageType.writeToNBT(busData);
        busRole.writeToNBT(busData);
        busData.setInteger("access", AccessRestriction.READ.ordinal());
        busData.setString("namePrefixKey", busRole.getPrefixKey());
        busData.setBoolean("preferDisplayName", true);
        busData.setInteger("upgradeSlotCount", 0);
        busData.setString("displayName", metaTileEntity.getLocalName());

        ItemStack iconStack = metaTileEntity.getStackForm(1);
        addBusItemIcon(busData, iconStack);
        addStorageBusCustomName(busData, hostTile.getWorldObj(), busId);
        addConnectedInventoryInfo(busData, hostTile, facing);
        addBusItemIconAsConnectedIconIfMissing(busData, iconStack);

        return busData;
    }

    private static void addGregTechItemBusData(NBTTagCompound busData, MTEHatchInputBusME inputBus, int contentLimit) {
        GregTechItemSnapshot snapshot = createGregTechItemSnapshot(inputBus);
        long[] amounts = new long[snapshot.extractedAmounts().length];
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = snapshot.extractedAmounts()[i];
        }

        addGenericGregTechItemBusData(
            busData,
            inputBus.autoPullAvailable,
            inputBus.isAutoPullItemList(),
            snapshot.configs(),
            snapshot.extracted(),
            amounts,
            contentLimit);
    }

    private static void addGregTechFluidHatchData(NBTTagCompound busData, MTEHatchInputME inputHatch,
        int contentLimit) {
        GregTechFluidSnapshot snapshot = createGregTechFluidSnapshot(inputHatch);
        long[] amounts = new long[snapshot.extractedAmounts().length];
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = snapshot.extractedAmounts()[i];
        }

        addGenericGregTechFluidBusData(
            busData,
            inputHatch.autoPullAvailable,
            inputHatch.isAutoPullFluidList(),
            snapshot.configs(),
            snapshot.extracted(),
            amounts,
            contentLimit);
    }

    private static void addGenericGregTechItemBusData(NBTTagCompound busData, boolean supportsAutoPull,
        boolean autoPullEnabled, ItemStack[] configs, ItemStack[] extracted, long[] extractedAmounts,
        int contentLimit) {
        busData.setString("stackType", "item");
        busData.setBoolean("supportsAutoPull", supportsAutoPull);
        busData.setBoolean("autoPullEnabled", autoPullEnabled);

        NBTTagList partitionList = new NBTTagList();
        NBTTagList contentsList = new NBTTagList();
        int writtenContents = 0;
        int slotCount = Math.max(
            configs == null ? 0 : configs.length,
            Math.max(extracted == null ? 0 : extracted.length, extractedAmounts == null ? 0 : extractedAmounts.length));

        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            ItemStack configStack = getItemAt(configs, slotIndex);
            if (!ItemStacks.isEmpty(configStack)) {
                NBTTagCompound partNbt = AEStackUtil.writeItemLikePartitionStack(configStack);
                if (partNbt != null) {
                    partNbt.setInteger("slot", slotIndex);
                    partNbt.setString("stackTypeId", "item");
                    partitionList.appendTag(partNbt);
                }
            }

            if (isContentLimitReached(writtenContents, contentLimit)) {
                continue;
            }

            ItemStack extractedStack = getItemAt(extracted, slotIndex);
            if (ItemStacks.isEmpty(extractedStack)) {
                continue;
            }

            NBTTagCompound stackNbt = AEStackUtil.writeItemLikePartitionStack(extractedStack);
            if (stackNbt == null) {
                continue;
            }
            stackNbt.setLong("Cnt", getLongAt(extractedAmounts, slotIndex));
            stackNbt.setString("stackTypeId", "item");
            contentsList.appendTag(stackNbt);
            writtenContents++;
        }

        busData.setTag("partition", partitionList);
        busData.setTag("contents", contentsList);
        busData.setBoolean(PARTITION_SUMMARY_KEY, partitionList.tagCount() > 0);
        busData.setBoolean(CONTENT_SUMMARY_KEY, hasItemContents(extracted));
    }

    private static void addGenericGregTechFluidBusData(NBTTagCompound busData, boolean supportsAutoPull,
        boolean autoPullEnabled, FluidStack[] configs, FluidStack[] extracted, long[] extractedAmounts,
        int contentLimit) {
        busData.setString("stackType", "fluid");
        busData.setBoolean("supportsAutoPull", supportsAutoPull);
        busData.setBoolean("autoPullEnabled", autoPullEnabled);

        NBTTagList partitionList = new NBTTagList();
        NBTTagList contentsList = new NBTTagList();
        int writtenContents = 0;
        int slotCount = Math.max(
            configs == null ? 0 : configs.length,
            Math.max(extracted == null ? 0 : extracted.length, extractedAmounts == null ? 0 : extractedAmounts.length));

        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            FluidStack configStack = getFluidAt(configs, slotIndex);
            if (configStack != null) {
                NBTTagCompound partNbt = new NBTTagCompound();
                partNbt.setInteger("slot", slotIndex);
                partNbt.setString("stackTypeId", "fluid");
                AEStackUtil.writeStackToNBT(
                    partNbt,
                    AEFluidStack.create(configStack)
                        .setStackSize(1));
                partitionList.appendTag(partNbt);
            }

            if (isContentLimitReached(writtenContents, contentLimit)) {
                continue;
            }

            FluidStack extractedStack = getFluidAt(extracted, slotIndex);
            if (extractedStack == null) {
                continue;
            }

            NBTTagCompound stackNbt = new NBTTagCompound();
            AEStackUtil.writeStackToNBT(stackNbt, AEFluidStack.create(extractedStack));
            stackNbt.setLong("Cnt", getLongAt(extractedAmounts, slotIndex));
            stackNbt.setString("stackTypeId", "fluid");
            contentsList.appendTag(stackNbt);
            writtenContents++;
        }

        busData.setTag("partition", partitionList);
        busData.setTag("contents", contentsList);
        busData.setBoolean(PARTITION_SUMMARY_KEY, partitionList.tagCount() > 0);
        busData.setBoolean(CONTENT_SUMMARY_KEY, hasFluidContents(extracted));
    }

    private static void addCustomName(NBTTagCompound busData, Object bus) {
        if (!(bus instanceof ICustomNameObject nameable)) return;

        if (nameable.hasCustomName()) {
            String customName = nameable.getCustomName();
            if (customName != null && !customName.isEmpty()) busData.setString("customName", customName);
        }
    }

    private static void addStorageBusCustomName(NBTTagCompound busData, World world, long busId) {
        StorageBusCustomNameData data = StorageBusCustomNameData.get(world);
        if (data == null) return;

        String customName = data.getCustomName(busId);
        if (customName != null && !customName.isEmpty()) {
            busData.setString("customName", customName);
        }
    }

    private static void addConnectedInventoryInfo(NBTTagCompound busData, TileEntity hostTile, ForgeDirection facing) {
        World world = hostTile.getWorldObj();
        int x = hostTile.xCoord + facing.offsetX;
        int y = hostTile.yCoord + facing.offsetY;
        int z = hostTile.zCoord + facing.offsetZ;

        ItemStack blockStack = getBlockAsItemStack(world, x, y, z);
        if (ItemStacks.isEmpty(blockStack)) return;

        busData.setBoolean("connectedIconIsTarget", true);
        busData.setString("connectedName", blockStack.getDisplayName());

        NBTTagCompound iconNbt = new NBTTagCompound();
        blockStack.writeToNBT(iconNbt);
        busData.setTag("connectedIcon", iconNbt);
    }

    private static void addBusItemIcon(NBTTagCompound busData, ItemStack busStack) {
        if (ItemStacks.isEmpty(busStack)) return;

        NBTTagCompound iconNbt = new NBTTagCompound();
        busStack.writeToNBT(iconNbt);
        busData.setTag("busIcon", iconNbt);
    }

    private static void addBusItemIconAsConnectedIconIfMissing(NBTTagCompound busData, ItemStack busStack) {
        if (busData.hasKey("connectedIcon") || ItemStacks.isEmpty(busStack)) return;

        busData.setBoolean("connectedIconIsTarget", false);
        busData.setString("connectedName", busStack.getDisplayName());

        NBTTagCompound iconNbt = new NBTTagCompound();
        busStack.writeToNBT(iconNbt);
        busData.setTag("connectedIcon", iconNbt);
        busData.removeTag("busIcon");
    }

    private static void applySharedBusSlotParameters(NBTTagCompound busData, PartSharedItemBus<?> bus) {
        IAEStackInventory configInv = bus.getAEInventoryByName(StorageName.CONFIG);
        int maxConfigSlots = configInv == null ? 9 : configInv.getSizeInventory();

        busData.setInteger("baseConfigSlots", 1);
        busData.setInteger("slotsPerUpgrade", 4);
        busData.setInteger("maxConfigSlots", maxConfigSlots);
    }

    private static void addPartitionData(NBTTagCompound busData, PartStorageBus bus, int capacityUpgrades) {
        IAEStackInventory configInv = bus.getAEInventoryByName(StorageName.CONFIG);
        if (configInv == null) return;

        int slotsToUse = computeAvailableSlotsFrom(busData, capacityUpgrades);
        NBTTagList partitionList = new NBTTagList();

        for (int i = 0; i < configInv.getSizeInventory() && i < slotsToUse; i++) {
            IAEStack<?> partitionStack = configInv.getAEStackInSlot(i);
            if (partitionStack == null) continue;

            NBTTagCompound partNbt = createPartitionSlotData(i, partitionStack);
            partitionList.appendTag(partNbt);
        }

        busData.setTag("partition", partitionList);
        busData.setBoolean(PARTITION_SUMMARY_KEY, partitionList.tagCount() > 0);
    }

    private static void addSharedBusPartitionData(NBTTagCompound busData, PartSharedItemBus<?> bus,
        int capacityUpgrades) {
        IAEStackInventory configInv = bus.getAEInventoryByName(StorageName.CONFIG);
        if (configInv == null) return;

        int slotsToUse = computeAvailableSlotsFrom(busData, capacityUpgrades);
        NBTTagList partitionList = new NBTTagList();

        for (int i = 0; i < configInv.getSizeInventory() && i < slotsToUse; i++) {
            IAEStack<?> partitionStack = configInv.getAEStackInSlot(i);
            if (partitionStack == null) continue;

            NBTTagCompound partNbt = createPartitionSlotData(i, partitionStack);
            partitionList.appendTag(partNbt);
        }

        busData.setTag("partition", partitionList);
        busData.setBoolean(PARTITION_SUMMARY_KEY, partitionList.tagCount() > 0);
    }

    private static NBTTagCompound createPartitionSlotData(int slot, IAEStack<?> partitionStack) {
        NBTTagCompound partNbt = new NBTTagCompound();
        partNbt.setInteger("slot", slot);
        AEStackUtil.writeStackToNBT(partNbt, partitionStack);

        return partNbt;
    }

    private static void addContentsData(NBTTagCompound busData, PartStorageBus bus, StorageType storageType,
        int contentLimit) {
        IMEInventoryHandler<?> handler = bus.getInternalHandler();
        if (handler == null) return;

        IAEStackType<?> type = handler.getStackType();
        if (type == null) return;

        busData.setString("stackType", type.getId());
        addContentsForUnknownType(busData, handler, type, contentLimit);
    }

    private static void addSharedBusContentsData(NBTTagCompound busData, PartSharedItemBus<?> bus, int contentLimit) {
        IAEStackType<?> type = bus.getStackType();
        if (type == null) return;

        busData.setString("stackType", type.getId());
        List<IAEStack<?>> contents = collectSharedBusPreviewContents(bus, contentLimit);
        writeSharedBusContents(busData, contents, contentLimit);
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> IMEInventoryHandler<T> castHandler(IMEInventoryHandler<?> handler) {
        return (IMEInventoryHandler<T>) handler;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void addContentsForUnknownType(NBTTagCompound busData,
        IMEInventoryHandler<?> handler, IAEStackType<?> type, int contentLimit) {
        addContentsForType(busData, castHandler(handler), (IAEStackType<T>) type, contentLimit);
    }

    private static <T extends IAEStack<T>> void addContentsForType(NBTTagCompound busData,
        IMEInventoryHandler<T> handler, IAEStackType<T> type, int contentLimit) {
        int effectiveContentLimit = getEffectiveContentLimit(contentLimit);
        IItemList<T> list = type.createList();
        handler.getAvailableItems(list, IterationCounter.fetchNewId());

        if (effectiveContentLimit <= 0) {
            busData.setTag("contents", new NBTTagList());
            busData.setBoolean(CONTENT_SUMMARY_KEY, !list.isEmpty());
            return;
        }

        NBTTagList contentsList = new NBTTagList();
        int written = 0;
        for (T stack : list) {
            if (written >= effectiveContentLimit) break;

            NBTTagCompound stackNbt = new NBTTagCompound();
            AEStackUtil.writeStackToNBT(stackNbt, stack);
            stackNbt.setLong("Cnt", stack.getStackSize());
            contentsList.appendTag(stackNbt);
            written++;
        }

        busData.setTag("contents", contentsList);
        busData.setBoolean(CONTENT_SUMMARY_KEY, !list.isEmpty());
    }

    public static List<IAEStack<?>> collectSharedBusPreviewContents(PartSharedItemBus<?> bus, int contentLimit) {
        if (bus instanceof PartBaseImportBus<?>importBus) {
            return collectSharedImportPreview(importBus, contentLimit);
        }
        if (bus instanceof PartBaseExportBus<?>exportBus) {
            return collectSharedExportPreview(exportBus, contentLimit);
        }

        return Collections.emptyList();
    }

    private static void writeSharedBusContents(NBTTagCompound busData, List<IAEStack<?>> contents, int contentLimit) {
        int effectiveContentLimit = getEffectiveContentLimit(contentLimit);
        NBTTagList contentsList = new NBTTagList();
        int written = 0;
        for (IAEStack<?> stack : contents) {
            if (stack == null) continue;
            if (effectiveContentLimit >= 0 && written >= effectiveContentLimit) break;

            NBTTagCompound stackNbt = new NBTTagCompound();
            AEStackUtil.writeStackToNBT(stackNbt, stack);
            stackNbt.setLong("Cnt", stack.getStackSize());
            contentsList.appendTag(stackNbt);
            written++;
        }

        busData.setTag("contents", contentsList);
        busData.setBoolean(CONTENT_SUMMARY_KEY, !contents.isEmpty());
    }

    private static List<IAEStack<?>> collectSharedImportPreview(PartBaseImportBus<?> bus, int contentLimit) {
        Object target = resolveSharedBusTarget(bus);
        if (target == null) return Collections.emptyList();

        int limit = normalizePreviewLimit(contentLimit);
        if (limit <= 0) return Collections.emptyList();

        if (isItemSharedBus(bus)) {
            InventoryAdaptor adaptor = target instanceof InventoryAdaptor inventoryAdaptor ? inventoryAdaptor
                : InventoryAdaptor.getAdaptor(
                    target,
                    bus.getSide()
                        .getOpposite(),
                    InventoryAdaptor.ALLOW_ITEMS | InventoryAdaptor.FOR_EXTRACTS);
            if (adaptor == null) return Collections.emptyList();

            ItemStack simulated = simulateItemImport(bus, adaptor, null);
            if (ItemStacks.isEmpty(simulated)) return Collections.emptyList();

            IAEItemStack stack = AEItemStack.create(simulated);
            if (stack == null) return Collections.emptyList();

            return List.of(stack);
        }

        if (isFluidSharedBus(bus)) {
            return collectFluidImportPreview(bus, target, limit);
        }

        if (isEssentiaSharedBus(bus)) {
            return collectEssentiaImportPreview(target, limit);
        }

        return Collections.emptyList();
    }

    private static List<IAEStack<?>> collectSharedExportPreview(PartBaseExportBus<?> bus, int contentLimit) {
        Object target = resolveSharedBusTarget(bus);
        if (target == null) return Collections.emptyList();

        int limit = normalizePreviewLimit(contentLimit);
        if (limit <= 0) return Collections.emptyList();

        List<IAEStack<?>> preview = new ArrayList<>(limit);
        IAEStackInventory config = bus.getAEInventoryByName(StorageName.CONFIG);
        int availableSlots = getAvailableConfigSlots(bus, config);

        if (config != null) {
            for (int slot = 0; slot < availableSlots && preview.size() < limit; slot++) {
                IAEStack<?> configured = config.getAEStackInSlot(slot);
                if (configured == null) continue;

                IAEStack<?> visible = simulateConfiguredExport(bus, target, configured);
                if (visible != null) preview.add(visible);
            }
        }

        if (!preview.isEmpty()) return preview;
        if (config != null && hasAnyConfiguredFilter(config, availableSlots)) return preview;

        return collectUnconfiguredExportPreview(bus, target, limit);
    }

    private static List<IAEStack<?>> collectFluidImportPreview(PartBaseImportBus<?> bus, Object target, int limit) {
        if (!(target instanceof net.minecraftforge.fluids.IFluidHandler fluidHandler)) return Collections.emptyList();

        ForgeDirection side = bus.getSide()
            .getOpposite();
        net.minecraftforge.fluids.FluidTankInfo[] infos = fluidHandler.getTankInfo(side);
        if (infos == null || infos.length == 0) return Collections.emptyList();

        List<IAEStack<?>> preview = new ArrayList<>(Math.min(limit, infos.length));
        for (net.minecraftforge.fluids.FluidTankInfo info : infos) {
            if (preview.size() >= limit) break;
            FluidStack fluid = info == null ? null : info.fluid;
            if (fluid == null || fluid.amount <= 0) continue;

            FluidStack drained = fluidHandler.drain(side, new FluidStack(fluid, fluid.amount), false);
            if (drained == null || drained.amount <= 0) continue;

            IAEFluidStack stack = AEFluidStack.create(drained);
            if (stack != null) preview.add(stack);
        }

        return preview;
    }

    private static List<IAEStack<?>> collectEssentiaImportPreview(Object target, int limit) {
        if (!(target instanceof IAspectContainer container)) return Collections.emptyList();
        if (!Mods.ThaumicEnergistics.isModLoaded()) return Collections.emptyList();

        List<IAEStack<?>> preview = new ArrayList<>(limit);
        for (AEEssentiaStack stack : EssentiaTileContainerHelper.INSTANCE.getEssentiaStacksFromContainer(container)) {
            if (preview.size() >= limit) break;
            preview.add(stack);
        }

        return preview;
    }

    private static ItemStack simulateItemImport(PartBaseImportBus<?> bus, InventoryAdaptor adaptor, ItemStack filter) {
        int toSend = Math.min(bus.calculateAmountToSend(), 64);
        if (toSend <= 0) return null;

        if (bus.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            return adaptor.simulateSimilarRemove(
                toSend,
                filter,
                (FuzzyMode) bus.getConfigManager()
                    .getSetting(Settings.FUZZY_MODE),
                null);
        }

        return adaptor.simulateRemove(toSend, filter, null);
    }

    private static IAEStack<?> simulateConfiguredExport(PartBaseExportBus<?> bus, Object target,
        IAEStack<?> configured) {
        if (configured == null || configured.getStackSize() <= 0) return null;

        long previewAmount = Math.max(1L, Math.min(configured.getStackSize(), bus.calculateAmountToSend()));
        IAEStack<?> probe = configured.copy();
        probe.setStackSize(previewAmount);

        if (target instanceof InventoryAdaptor adaptor) {
            IAEStack<?> remainder = adaptor.simulateAddStack(probe, InsertionMode.DEFAULT);
            long inserted = remainder == null ? probe.getStackSize() : probe.getStackSize() - remainder.getStackSize();
            if (inserted <= 0) return null;

            IAEStack<?> visible = configured.copy();
            visible.setStackSize(inserted);
            return visible;
        }

        if (configured instanceof AEEssentiaStack essentiaStack && target instanceof IAspectContainer container) {
            long inserted = EssentiaTileContainerHelper.INSTANCE.injectEssentiaIntoContainer(
                container,
                (int) Math.min(Integer.MAX_VALUE, previewAmount),
                essentiaStack.getAspect(),
                Actionable.SIMULATE);
            if (inserted <= 0) return null;

            AEEssentiaStack visible = essentiaStack.copy();
            visible.setStackSize(inserted);
            return visible;
        }

        return null;
    }

    private static List<IAEStack<?>> collectUnconfiguredExportPreview(PartBaseExportBus<?> bus, Object target,
        int limit) {
        IMEMonitor<?> monitor = getSharedBusMonitor(bus);
        if (monitor == null || monitor.getStorageList() == null
            || monitor.getStorageList()
                .isEmpty()) {
            return Collections.emptyList();
        }

        List<IAEStack<?>> preview = new ArrayList<>(limit);
        for (Object raw : monitor.getStorageList()) {
            if (preview.size() >= limit) break;
            if (!(raw instanceof IAEStack<?>stack)) continue;

            IAEStack<?> visible = simulateConfiguredExport(bus, target, stack);
            if (visible != null) preview.add(visible);
        }

        return preview;
    }

    private static IMEMonitor<?> getSharedBusMonitor(PartSharedItemBus<?> bus) {
        try {
            return switch (normalizeStackTypeId(bus.getStackType())) {
                case "fluid" -> bus.getProxy()
                    .getStorage()
                    .getFluidInventory();
                case "essentia" -> bus.getProxy()
                    .getStorage()
                    .getMEMonitor(bus.getStackType());
                default -> bus.getProxy()
                    .getStorage()
                    .getItemInventory();
            };
        } catch (GridAccessException e) {
            return null;
        }
    }

    private static Object resolveSharedBusTarget(PartSharedItemBus<?> bus) {
        TileEntity self = bus.getHost()
            .getTile();
        if (self == null || self.getWorldObj() == null) return null;

        ForgeDirection side = bus.getSide();
        int x = self.xCoord + side.offsetX;
        int y = self.yCoord + side.offsetY;
        int z = self.zCoord + side.offsetZ;
        World world = self.getWorldObj();
        if (!world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return null;

        TileEntity target = world.getTileEntity(x, y, z);
        if (target == null) return null;

        if (isFluidSharedBus(bus) && target instanceof net.minecraftforge.fluids.IFluidHandler fluidHandler) {
            return fluidHandler;
        }
        if (isEssentiaSharedBus(bus) && target instanceof IAspectContainer aspectContainer) {
            return aspectContainer;
        }
        if (isItemSharedBus(bus)) {
            return InventoryAdaptor.getAdaptor(
                target,
                side.getOpposite(),
                InventoryAdaptor.ALLOW_ITEMS | InventoryAdaptor.FOR_INSERTS | InventoryAdaptor.FOR_EXTRACTS);
        }

        return target;
    }

    private static boolean hasAnyConfiguredFilter(IAEStackInventory config, int availableSlots) {
        if (config == null) return false;
        for (int slot = 0; slot < availableSlots; slot++) {
            if (config.getAEStackInSlot(slot) != null) return true;
        }
        return false;
    }

    private static int normalizePreviewLimit(int contentLimit) {
        return Math.max(0, getEffectiveContentLimit(contentLimit));
    }

    private static boolean isItemSharedBus(PartSharedItemBus<?> bus) {
        return "item".equals(normalizeStackTypeId(bus.getStackType()));
    }

    private static boolean isFluidSharedBus(PartSharedItemBus<?> bus) {
        return "fluid".equals(normalizeStackTypeId(bus.getStackType()));
    }

    private static boolean isEssentiaSharedBus(PartSharedItemBus<?> bus) {
        return "essentia".equals(normalizeStackTypeId(bus.getStackType()));
    }

    private static String normalizeStackTypeId(IAEStackType<?> stackType) {
        if (stackType == null || stackType.getId() == null) return "";
        if ("fluid".equals(stackType.getId())) return "fluid";
        if ("essentia".equals(stackType.getId())) return "essentia";
        return "item";
    }

    private static boolean isContentLimitReached(int written, int contentLimit) {
        return written >= getEffectiveContentLimit(contentLimit);
    }

    private static int getEffectiveContentLimit(int contentLimit) {
        if (contentLimit == 0) return 0;
        if (contentLimit < 0 || contentLimit == Integer.MAX_VALUE) return MAX_CONTENT_ENTRIES_PER_BUS_PAYLOAD;

        return Math.min(contentLimit, MAX_CONTENT_ENTRIES_PER_BUS_PAYLOAD);
    }

    private static int computeAvailableSlotsFrom(NBTTagCompound busData, int capacityUpgrades) {
        int base = busData.hasKey("baseConfigSlots") ? busData.getInteger("baseConfigSlots") : 18;
        int perUpg = busData.hasKey("slotsPerUpgrade") ? busData.getInteger("slotsPerUpgrade") : 9;
        int max = busData.hasKey("maxConfigSlots") ? busData.getInteger("maxConfigSlots") : 63;

        int raw = base + perUpg * Math.max(0, capacityUpgrades);

        return Math.min(raw, max);
    }

    private static void addUpgradesData(NBTTagCompound busData, IInventory upgradesInv) {
        if (upgradesInv == null) return;

        busData.setInteger("upgradeSlotCount", upgradesInv.getSizeInventory());

        NBTTagList upgradeList = new NBTTagList();
        for (int i = 0; i < upgradesInv.getSizeInventory(); i++) {
            ItemStack upgrade = upgradesInv.getStackInSlot(i);
            if (ItemStacks.isEmpty(upgrade)) continue;

            NBTTagCompound upgradeNbt = new NBTTagCompound();
            upgrade.writeToNBT(upgradeNbt);
            upgradeNbt.setInteger("slot", i);
            upgradeList.appendTag(upgradeNbt);
        }

        busData.setTag("upgrades", upgradeList);
    }

    /**
     * Toggle IO mode (access restriction) for a storage bus.
     *
     * @return true if mode was changed
     */
    public static boolean toggleIOMode(StorageBusTracker tracker) {
        boolean isSuperItemVariant = GTMachineReflectionHelper.readBooleanField(tracker.storageBus, "isSuper")
            .orElse(false);

        if (GTMachineReflectionHelper.hasClassName(tracker.storageBus, GTMachineClassNames.SUPER_DUAL_INPUT_HATCH_ME)) {
            boolean autoPullEnabled = GTMachineReflectionHelper
                .invokeBoolean(tracker.storageBus, "isAutoPullItemListForGui")
                .orElse(false);
            boolean supportsAutoPull = GTMachineReflectionHelper.readBooleanField(tracker.storageBus, "allowAuto")
                .orElse(false);
            if (!supportsAutoPull) return false;

            boolean changed = GTMachineReflectionHelper.invokeVoid(
                tracker.storageBus,
                "setAutoPullItemList",
                GTMachineReflectionHelper.BOOLEAN_ARG_TYPES,
                !autoPullEnabled);
            if (!changed) return false;

            tracker.hostTile.markDirty();
            return true;
        }

        if (GTMachineReflectionHelper.hasClassName(tracker.storageBus, GTMachineClassNames.SUPER_INPUT_BUS_ME)
            || GTMachineReflectionHelper
                .hasAnyClassName(tracker.storageBus, GTMachineClassNames.GTNL_SUPER_ITEM_INPUT_CLASSES)
                && isSuperItemVariant) {
            boolean supportsAutoPull = GTMachineReflectionHelper
                .readBooleanField(tracker.storageBus, "autoPullAvailable")
                .orElse(false);
            if (!supportsAutoPull) return false;

            boolean autoPullEnabled = GTMachineReflectionHelper.invokeBoolean(tracker.storageBus, "isAutoPullItemList")
                .orElse(false);
            boolean changed = GTMachineReflectionHelper.invokeVoid(
                tracker.storageBus,
                "setAutoPullItemList",
                GTMachineReflectionHelper.BOOLEAN_ARG_TYPES,
                !autoPullEnabled);
            if (!changed) return false;

            tracker.hostTile.markDirty();
            return true;
        }

        if (GTMachineReflectionHelper.hasClassName(tracker.storageBus, GTMachineClassNames.SUPER_INPUT_HATCH_ME)) {
            boolean supportsAutoPull = GTMachineReflectionHelper
                .readBooleanField(tracker.storageBus, "autoPullAvailable")
                .orElse(false);
            if (!supportsAutoPull) return false;

            boolean autoPullEnabled = GTMachineReflectionHelper
                .invokeBoolean(tracker.storageBus, "isAutoPullFluidListForGui")
                .orElse(false);
            boolean changed = GTMachineReflectionHelper.invokeVoid(
                tracker.storageBus,
                "setAutoPullFluidList",
                GTMachineReflectionHelper.BOOLEAN_ARG_TYPES,
                !autoPullEnabled);
            if (!changed) return false;

            tracker.hostTile.markDirty();
            return true;
        }

        if (tracker.storageBus instanceof MTEHatchInputBusME inputBus) {
            if (!inputBus.autoPullAvailable) return false;

            inputBus.setAutoPullItemList(!inputBus.isAutoPullItemList());
            tracker.hostTile.markDirty();
            return true;
        }

        if (tracker.storageBus instanceof MTEHatchInputME inputHatch) {
            if (!inputHatch.autoPullAvailable) return false;

            inputHatch.setAutoPullFluidList(!inputHatch.isAutoPullFluidList());
            tracker.hostTile.markDirty();
            return true;
        }

        if (!(tracker.storageBus instanceof PartUpgradeable)) return false;

        IConfigManager configManager = ((PartUpgradeable) tracker.storageBus).getConfigManager();
        if (configManager == null) return false;

        AccessRestriction current = (AccessRestriction) configManager.getSetting(Settings.ACCESS);
        AccessRestriction next;

        switch (current) {
            case READ_WRITE -> next = AccessRestriction.READ;
            case READ -> next = AccessRestriction.WRITE;
            default -> next = AccessRestriction.READ_WRITE;
        }

        configManager.putSetting(Settings.ACCESS, next);
        tracker.hostTile.markDirty();

        return true;
    }

    /**
     * Check if a storage bus has a partition configured.
     */
    public static boolean busHasPartition(StorageBusTracker tracker) {
        if (tracker.partitionSummaryKnown) return tracker.hasPartitionConfigured;

        boolean isSuperItemVariant = GTMachineReflectionHelper.readBooleanField(tracker.storageBus, "isSuper")
            .orElse(false);

        if (GTMachineReflectionHelper.hasClassName(tracker.storageBus, GTMachineClassNames.SUPER_DUAL_INPUT_HATCH_ME)) {
            return hasMixedPartition(
                GTMachineReflectionHelper.readItemStackArrayField(tracker.storageBus, "i_mark")
                    .orElse(null),
                GTMachineReflectionHelper.readFluidStackArrayField(tracker.storageBus, "f_mark")
                    .orElse(null));
        }

        if (GTMachineReflectionHelper.hasClassName(tracker.storageBus, GTMachineClassNames.SUPER_INPUT_HATCH_ME)) {
            return hasFluidContents(
                GTMachineReflectionHelper.readFluidStackArrayField(tracker.storageBus, "storedFluids")
                    .orElse(null));
        }

        if (GTMachineReflectionHelper.hasClassName(tracker.storageBus, GTMachineClassNames.SUPER_INPUT_BUS_ME)
            || GTMachineReflectionHelper
                .hasAnyClassName(tracker.storageBus, GTMachineClassNames.GTNL_SUPER_ITEM_INPUT_CLASSES)
                && isSuperItemVariant) {
            int filterSlotCount = GTMachineReflectionHelper.invokeInt(tracker.storageBus, "getFilterSlotCountForGui")
                .orElse(0);
            return GTMachineReflectionHelper.readItemStackArrayField(tracker.storageBus, "mInventory")
                .map(inv -> hasItemContents(inv, 0, filterSlotCount))
                .orElse(false);
        }

        if (tracker.storageBus instanceof MTEHatchInputBusME inputBus) {
            return hasGregTechItemPartition(createGregTechItemSnapshot(inputBus));
        }

        if (tracker.storageBus instanceof MTEHatchInputME inputHatch) {
            return hasGregTechFluidPartition(createGregTechFluidSnapshot(inputHatch));
        }

        IAEStackInventory configInv = getConfigInventory(tracker.storageBus);
        if (configInv != null) {
            int availableSlots = getAvailableConfigSlots(tracker.storageBus, configInv);

            for (int i = 0; i < availableSlots; i++) {
                if (configInv.getAEStackInSlot(i) != null) return true;
            }
        }

        return false;
    }

    /**
     * Check if a storage bus can see at least one stack from its connected inventory.
     */
    public static boolean busHasConnectedInventory(StorageBusTracker tracker) {
        if (tracker.contentSummaryKnown) return tracker.hasConnectedContents;

        boolean isSuperItemVariant = GTMachineReflectionHelper.readBooleanField(tracker.storageBus, "isSuper")
            .orElse(false);

        if (GTMachineReflectionHelper.hasClassName(tracker.storageBus, GTMachineClassNames.SUPER_DUAL_INPUT_HATCH_ME)) {
            return hasMixedContents(
                GTMachineReflectionHelper.readItemStackArrayField(tracker.storageBus, "i_display")
                    .orElse(null),
                GTMachineReflectionHelper.readFluidStackArrayField(tracker.storageBus, "f_display")
                    .orElse(null));
        }

        if (GTMachineReflectionHelper.hasClassName(tracker.storageBus, GTMachineClassNames.SUPER_INPUT_HATCH_ME)) {
            return hasFluidContents(
                GTMachineReflectionHelper.readFluidStackArrayField(tracker.storageBus, "storedInformationFluids")
                    .orElse(null));
        }

        if (GTMachineReflectionHelper.hasClassName(tracker.storageBus, GTMachineClassNames.SUPER_INPUT_BUS_ME)) {
            int filterSlotCount = GTMachineReflectionHelper.invokeInt(tracker.storageBus, "getFilterSlotCountForGui")
                .orElse(0);
            return GTMachineReflectionHelper.readItemStackArrayField(tracker.storageBus, "mInventory")
                .map(inv -> hasItemContents(inv, filterSlotCount, filterSlotCount))
                .orElse(false);
        }

        if (GTMachineReflectionHelper
            .hasAnyClassName(tracker.storageBus, GTMachineClassNames.GTNL_SUPER_ITEM_INPUT_CLASSES)
            && isSuperItemVariant) {
            return hasItemContents(
                GTMachineReflectionHelper.readItemStackArrayField(tracker.storageBus, "shadowInventory")
                    .orElse(null));
        }

        if (tracker.storageBus instanceof MTEHatchInputBusME inputBus) {
            return hasGregTechItemContents(createGregTechItemSnapshot(inputBus));
        }

        if (tracker.storageBus instanceof MTEHatchInputME inputHatch) {
            return hasGregTechFluidContents(createGregTechFluidSnapshot(inputHatch));
        }

        if (tracker.storageBus instanceof PartStorageBus bus) {
            IMEInventoryHandler<?> handler = bus.getInternalHandler();
            if (handler == null) return false;

            IAEStackType<?> type = handler.getStackType();
            if (type == null) return false;

            return hasContentsForUnknownType(handler, type);
        }

        if (tracker.storageBus instanceof PartSharedItemBus<?>) return false;

        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> boolean hasContentsForUnknownType(IMEInventoryHandler<?> handler,
        IAEStackType<?> type) {
        return hasContentsForType(castHandler(handler), (IAEStackType<T>) type);
    }

    private static <T extends IAEStack<T>> boolean hasContentsForType(IMEInventoryHandler<T> handler,
        IAEStackType<T> type) {
        IItemList<T> contents = type.createList();
        handler.getAvailableItems(contents, IterationCounter.fetchNewId());

        return !contents.isEmpty();
    }

    private static IAEStackInventory getConfigInventory(Object bus) {
        if (bus instanceof PartStorageBus storageBus) return storageBus.getAEInventoryByName(StorageName.CONFIG);
        if (bus instanceof PartSharedItemBus<?>sharedBus) return sharedBus.getAEInventoryByName(StorageName.CONFIG);

        return null;
    }

    private static int getAvailableConfigSlots(Object bus, IAEStackInventory config) {
        if (bus instanceof PartStorageBus storageBus) {
            return Math.min(config.getSizeInventory(), 18 + storageBus.getInstalledUpgrades(Upgrades.CAPACITY) * 9);
        }

        if (bus instanceof PartSharedItemBus<?>sharedBus) {
            return Math.min(config.getSizeInventory(), 1 + sharedBus.getInstalledUpgrades(Upgrades.CAPACITY) * 4);
        }

        return 0;
    }

    private static ItemStack getBlockAsItemStack(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (block == null || block == Blocks.air || block.isAir(world, x, y, z)) return null;

        Item item = Item.getItemFromBlock(block);
        if (item == null) return null;

        int meta = block.getDamageValue(world, x, y, z);

        return new ItemStack(item, 1, meta);
    }

    private static boolean hasGregTechItemPartition(GregTechItemSnapshot snapshot) {
        for (ItemStack config : snapshot.configs()) {
            if (!ItemStacks.isEmpty(config)) return true;
        }

        return false;
    }

    private static boolean hasGregTechFluidPartition(GregTechFluidSnapshot snapshot) {
        for (FluidStack config : snapshot.configs()) {
            if (config != null) return true;
        }

        return false;
    }

    private static boolean hasGregTechItemContents(GregTechItemSnapshot snapshot) {
        for (ItemStack extracted : snapshot.extracted()) {
            if (!ItemStacks.isEmpty(extracted)) return true;
        }

        return false;
    }

    private static boolean hasGregTechFluidContents(GregTechFluidSnapshot snapshot) {
        for (FluidStack extracted : snapshot.extracted()) {
            if (extracted != null) return true;
        }

        return false;
    }

    private static boolean hasItemContents(ItemStack[] stacks) {
        return hasItemContents(stacks, 0, stacks == null ? 0 : stacks.length);
    }

    private static boolean hasItemContents(ItemStack[] stacks, int offset, int length) {
        if (stacks == null) {
            return false;
        }

        int start = Math.max(0, offset);
        int end = Math.min(stacks.length, start + Math.max(0, length));
        for (int i = start; i < end; i++) {
            if (!ItemStacks.isEmpty(stacks[i])) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasFluidContents(FluidStack[] stacks) {
        if (stacks == null) {
            return false;
        }

        for (FluidStack stack : stacks) {
            if (stack != null) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasMixedPartition(ItemStack[] itemConfigs, FluidStack[] fluidConfigs) {
        return hasItemContents(itemConfigs) || hasFluidContents(fluidConfigs);
    }

    private static boolean hasMixedContents(ItemStack[] itemPreview, FluidStack[] fluidPreview) {
        return hasItemContents(itemPreview) || hasFluidContents(fluidPreview);
    }

    private static ItemStack getItemAt(ItemStack[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return null;
        }

        return values[index];
    }

    private static FluidStack getFluidAt(FluidStack[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return null;
        }

        return values[index];
    }

    private static long getLongAt(long[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return 0L;
        }

        return values[index];
    }

    private static GregTechItemSnapshot createGregTechItemSnapshot(MTEHatchInputBusME inputBus) {
        ItemStack[] configs = new ItemStack[MTEHatchInputBusME.SLOT_COUNT];
        ItemStack[] extracted = new ItemStack[MTEHatchInputBusME.SLOT_COUNT];
        int[] extractedAmounts = new int[MTEHatchInputBusME.SLOT_COUNT];
        NBTTagCompound serialized = new NBTTagCompound();
        inputBus.saveNBTData(serialized);
        NBTTagList slots = serialized.getTagList("slots", 10);

        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slotTag = slots.getCompoundTagAt(i);
            int slotIndex = slotTag.getInteger("index");
            if (!isValidGregTechSlotIndex(slotIndex, configs.length)) continue;

            if (slotTag.hasKey("config")) {
                configs[slotIndex] = ItemStacks.loadDisplay(slotTag.getCompoundTag("config"));
            }
            if (slotTag.hasKey("extracted")) {
                extracted[slotIndex] = ItemStacks.loadDisplay(slotTag.getCompoundTag("extracted"));
                extractedAmounts[slotIndex] = slotTag.getInteger("extractedAmount");
            }
        }

        return new GregTechItemSnapshot(configs, extracted, extractedAmounts);
    }

    private static GregTechFluidSnapshot createGregTechFluidSnapshot(MTEHatchInputME inputHatch) {
        FluidStack[] configs = new FluidStack[MTEHatchInputME.SLOT_COUNT];
        FluidStack[] extracted = new FluidStack[MTEHatchInputME.SLOT_COUNT];
        int[] extractedAmounts = new int[MTEHatchInputME.SLOT_COUNT];
        NBTTagCompound serialized = new NBTTagCompound();
        inputHatch.saveNBTData(serialized);
        NBTTagList slots = serialized.getTagList("slots", 10);

        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slotTag = slots.getCompoundTagAt(i);
            int slotIndex = slotTag.getInteger("index");
            if (!isValidGregTechSlotIndex(slotIndex, configs.length)) continue;

            if (slotTag.hasKey("config")) {
                configs[slotIndex] = FluidStack.loadFluidStackFromNBT(slotTag.getCompoundTag("config"));
            }
            if (slotTag.hasKey("extracted")) {
                extracted[slotIndex] = FluidStack.loadFluidStackFromNBT(slotTag.getCompoundTag("extracted"));
                extractedAmounts[slotIndex] = slotTag.getInteger("extractedAmount");
            }
        }

        return new GregTechFluidSnapshot(configs, extracted, extractedAmounts);
    }

    private static boolean isValidGregTechSlotIndex(int slotIndex, int slotCount) {
        return slotIndex >= 0 && slotIndex < slotCount;
    }

    public static boolean readAndStripPartitionSummary(NBTTagCompound busData) {
        boolean hasPartition = busData.getBoolean(PARTITION_SUMMARY_KEY);
        busData.removeTag(PARTITION_SUMMARY_KEY);
        return hasPartition;
    }

    public static boolean readAndStripContentSummary(NBTTagCompound busData) {
        boolean hasContents = busData.getBoolean(CONTENT_SUMMARY_KEY);
        busData.removeTag(CONTENT_SUMMARY_KEY);
        return hasContents;
    }
}
