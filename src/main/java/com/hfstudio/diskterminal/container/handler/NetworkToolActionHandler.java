package com.hfstudio.diskterminal.container.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.DiskTerminal;
import com.hfstudio.diskterminal.api.IItemCompactingCell;
import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.api.capability.IFilterCapability;
import com.hfstudio.diskterminal.api.capability.IRefreshCapability;
import com.hfstudio.diskterminal.client.CellFilter;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.ContainerCellTerminalBase.StorageTracker;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.gui.networktools.AttributeUniqueTool;
import com.hfstudio.diskterminal.gui.networktools.MassPartitionBusTool;
import com.hfstudio.diskterminal.gui.networktools.MassPartitionCellTool;
import com.hfstudio.diskterminal.network.PacketPartitionAction;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusProviderFactory;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PlayerMessageHelper;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;

/**
 * Executes network-tool batch operations on cells and storage buses.
 */
public class NetworkToolActionHandler {

    private NetworkToolActionHandler() {}

    public static boolean affectsStorages(String toolId) {
        return MassPartitionCellTool.TOOL_ID.equals(toolId) || AttributeUniqueTool.TOOL_ID.equals(toolId);
    }

    public static boolean affectsStorageBuses(String toolId) {
        return MassPartitionBusTool.TOOL_ID.equals(toolId);
    }

    public static void handleAction(String toolId, Map<CellFilter, CellFilter.State> activeFilters,
        Map<Long, StorageTracker> storageById, Map<Long, StorageBusTracker> storageBusById, IGrid grid,
        EntityPlayer player) {
        switch (toolId) {
            case MassPartitionCellTool.TOOL_ID -> handleMassPartitionCells(activeFilters, storageById);
            case MassPartitionBusTool.TOOL_ID -> handleMassPartitionBuses(activeFilters, storageBusById);
            case AttributeUniqueTool.TOOL_ID -> handleAttributeUnique(activeFilters, storageById, player);
            default -> {
                DiskTerminal.LOG.warn("Unknown network tool ID: {}", toolId);
                PlayerMessageHelper.error(player, "disk_terminal.networktools.error.unknown_tool");
            }
        }
    }

    private static void handleMassPartitionCells(Map<CellFilter, CellFilter.State> activeFilters,
        Map<Long, StorageTracker> storageById) {
        for (StorageTracker tracker : storageById.values()) {
            IInventory cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) continue;

            for (int slot = 0; slot < tracker.storage.getCellCount(); slot++) {
                ItemStack cellStack = CellDataHandler.getCellStack(cellInventory, tracker.storage, slot);
                if (!isPartitionableCell(cellStack)) continue;
                if (!matchesCellFilters(cellStack, activeFilters)) continue;

                CellActionHandler.handlePartitionAction(
                    tracker.storage,
                    tracker.tile,
                    slot,
                    PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS,
                    -1,
                    null);
            }
        }
    }

    private static void handleMassPartitionBuses(Map<CellFilter, CellFilter.State> activeFilters,
        Map<Long, StorageBusTracker> storageBusById) {
        StorageBusProviderFactory providerFactory = new StorageBusProviderFactory();

        for (StorageBusTracker tracker : storageBusById.values()) {
            if (!matchesBusFilters(tracker, activeFilters)) continue;
            if (tracker.targetId == null || tracker.source == null) continue;

            ICapabilityProvider<?> provider = providerFactory.create(tracker.targetId, tracker.source);
            provider.findCapability(IFilterCapability.class)
                .filter(IFilterCapability::supportsPreviewFill)
                .ifPresent(filter -> {
                    if (!filter.fillFromPreview()) return;

                    provider.findCapability(IRefreshCapability.class)
                        .ifPresent(refresh -> {
                            refresh.markDirty();
                            refresh.requestRefresh();
                        });
                });
        }
    }

    private static void handleAttributeUnique(Map<CellFilter, CellFilter.State> activeFilters,
        Map<Long, StorageTracker> storageById, EntityPlayer player) {
        List<CellTarget> allTargets = collectCellTargets(storageById);
        List<CellTarget> filteredTargets = new ArrayList<>();
        for (CellTarget target : allTargets) {
            if (matchesCellFilters(target.cellStack, activeFilters)) filteredTargets.add(target);
        }

        List<TypePlan> plans = new ArrayList<>();
        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            TypePlan plan = buildTypePlan(type, filteredTargets, allTargets);
            if (plan.hasContent()) plans.add(plan);
        }

        if (plans.isEmpty()) {
            PlayerMessageHelper.error(player, "disk_terminal.networktools.error.no_items");
            return;
        }

        StringBuilder shortage = new StringBuilder();
        for (TypePlan plan : plans) {
            if (plan.targets.size() < plan.uniqueStacks.size()) {
                shortage.append('\n')
                    .append(plan.type.getDisplayName())
                    .append(": ")
                    .append(plan.targets.size())
                    .append(" cells, ")
                    .append(plan.uniqueStacks.size())
                    .append(" types");
            }
        }

        if (!shortage.isEmpty()) {
            PlayerMessageHelper.error(
                player,
                "disk_terminal.networktools.attribute_unique.error.not_enough_cells_by_type",
                shortage.toString());
            return;
        }

        BaseActionSource source = player != null ? new PlayerSource(player, null) : new BaseActionSource();
        int moved = 0;
        int failed = 0;
        int affectedCells = 0;

        for (TypePlan plan : plans) {
            RedistributionCounts counts = redistributeType(plan, source);
            moved += counts.moved;
            failed += counts.failed;
            affectedCells += counts.affectedCells;
        }

        if (moved == 0 && failed > 0) {
            PlayerMessageHelper
                .error(player, "disk_terminal.networktools.attribute_unique.error.redistribution_failed");
        } else if (failed > 0) {
            PlayerMessageHelper.warning(
                player,
                "disk_terminal.networktools.attribute_unique.partial_success",
                moved,
                affectedCells,
                failed);
        } else {
            PlayerMessageHelper
                .success(player, "disk_terminal.networktools.attribute_unique.success", moved, affectedCells);
        }
    }

    private static List<CellTarget> collectCellTargets(Map<Long, StorageTracker> storageById) {
        List<CellTarget> targets = new ArrayList<>();

        for (StorageTracker tracker : storageById.values()) {
            IInventory cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) continue;

            for (int slot = 0; slot < tracker.storage.getCellCount(); slot++) {
                ItemStack cellStack = CellDataHandler.getCellStack(cellInventory, tracker.storage, slot);
                if (!isPartitionableCell(cellStack)) continue;

                targets.add(new CellTarget(tracker, slot, cellStack));
            }
        }

        return targets;
    }

    private static TypePlan buildTypePlan(IAEStackType<?> type, List<CellTarget> filteredTargets,
        List<CellTarget> allTargets) {
        TypePlan plan = new TypePlan(type);
        Set<String> uniqueKeys = new HashSet<>();

        for (CellTarget target : filteredTargets) {
            CellAccess access = getCellAccess(target, type);
            if (access == null) continue;
            if (access.inventory.getUsedBytes() <= 0) continue;

            plan.filteredTargets.add(target);
            IItemList<?> contents = collectContents(access, type);
            for (Object entry : contents) {
                IAEStack<?> stack = (IAEStack<?>) entry;
                String key = createStackKey(stack);
                if (uniqueKeys.add(key)) {
                    IAEStack<?> one = stack.copy();
                    one.setStackSize(1);
                    plan.uniqueStacks.add(one);
                }
            }
        }

        if (plan.uniqueStacks.isEmpty()) return plan;

        plan.targets.addAll(plan.filteredTargets);
        for (CellTarget target : allTargets) {
            if (plan.targets.contains(target)) continue;
            CellAccess access = getCellAccess(target, type);
            if (access != null && isCellEmptyAndNonPartitioned(access)) plan.targets.add(target);
        }

        return plan;
    }

    private static RedistributionCounts redistributeType(TypePlan plan, BaseActionSource source) {
        RedistributionCounts counts = new RedistributionCounts();
        for (CellTarget target : plan.targets) clearCellPartition(target);

        Map<String, IAEStack<?>> extractedStacks = extractAllStacksByType(plan, source);
        if (extractedStacks.isEmpty()) return counts;

        int targetIndex = 0;
        for (IAEStack<?> stack : extractedStacks.values()) {
            if (targetIndex >= plan.targets.size()) {
                counts.failed++;
                if (!putBackStack(plan.targets, stack, source)) counts.failed++;
                continue;
            }

            CellTarget target = plan.targets.get(targetIndex++);
            IAEStack<?> partitionStack = stack.copy();
            partitionStack.setStackSize(1);
            setCellPartition(target, partitionStack);

            IAEStack<?> remainder = injectIntoTarget(target, stack, source);
            long remainderSize = remainder != null ? remainder.getStackSize() : 0;
            long inserted = stack.getStackSize() - remainderSize;

            if (inserted > 0) {
                counts.moved++;
                counts.affectedCells++;
                markDirty(target);
            } else {
                counts.failed++;
            }

            if (remainder != null && remainder.getStackSize() > 0 && !putBackStack(plan.targets, remainder, source)) {
                counts.failed++;
            }
        }

        return counts;
    }

    private static Map<String, IAEStack<?>> extractAllStacksByType(TypePlan plan, BaseActionSource source) {
        Map<String, IAEStack<?>> stacks = new LinkedHashMap<>();

        for (CellTarget sourceTarget : plan.filteredTargets) {
            CellAccess sourceAccess = getCellAccess(sourceTarget, plan.type);
            if (sourceAccess == null) continue;

            List<IAEStack<?>> available = new ArrayList<>();
            IItemList<?> contents = collectContents(sourceAccess, plan.type);
            for (Object entry : contents) available.add(((IAEStack<?>) entry).copy());

            for (IAEStack<?> stack : available) {
                IAEStack<?> extracted = extractStack(sourceAccess, stack, source);
                if (extracted == null || extracted.getStackSize() <= 0) continue;

                mergeStack(stacks, extracted);
                markDirty(sourceTarget);
            }
        }

        return stacks;
    }

    private static void mergeStack(Map<String, IAEStack<?>> stacks, IAEStack<?> stack) {
        String key = createStackKey(stack);
        IAEStack<?> existing = stacks.get(key);
        if (existing == null) {
            stacks.put(key, stack.copy());
            return;
        }

        existing.setStackSize(existing.getStackSize() + stack.getStackSize());
    }

    private static IAEStack<?> injectIntoTarget(CellTarget target, IAEStack<?> stack, BaseActionSource source) {
        CellAccess targetAccess = getCellAccess(target, stack.getStackType());
        if (targetAccess == null) return stack;

        return injectStack(targetAccess, stack, source);
    }

    private static boolean putBackStack(List<CellTarget> targets, IAEStack<?> stack, BaseActionSource source) {
        IAEStack<?> remainder = injectIntoTargets(targets, stack, source);
        if (remainder == null || remainder.getStackSize() <= 0) return true;

        for (CellTarget target : targets) clearCellPartition(target);
        remainder = injectIntoTargets(targets, remainder, source);

        return remainder == null || remainder.getStackSize() <= 0;
    }

    private static IAEStack<?> injectIntoTargets(List<CellTarget> targets, IAEStack<?> stack, BaseActionSource source) {
        IAEStack<?> remainder = stack.copy();
        for (CellTarget target : targets) {
            remainder = injectIntoTarget(target, remainder, source);
            markDirty(target);
            if (remainder == null || remainder.getStackSize() <= 0) return null;
        }

        return remainder;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> IItemList<T> collectContents(CellAccess access, IAEStackType<?> type) {
        IAEStackType<T> stackType = (IAEStackType<T>) type;
        ICellInventory<T> inventory = (ICellInventory<T>) access.inventory;

        return inventory.getAvailableItems(stackType.createList(), IterationCounter.fetchNewId());
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> IAEStack<?> extractStack(CellAccess access, IAEStack<?> request,
        BaseActionSource source) {
        ICellInventory<T> inventory = (ICellInventory<T>) access.inventory;

        return inventory.extractItems((T) request.copy(), Actionable.MODULATE, source);
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> IAEStack<?> injectStack(CellAccess access, IAEStack<?> input,
        BaseActionSource source) {
        ICellInventory<T> inventory = (ICellInventory<T>) access.inventory;

        return inventory.injectItems((T) input.copy(), Actionable.MODULATE, source);
    }

    private static void clearCellPartition(CellTarget target) {
        CellActionHandler.handlePartitionAction(
            target.tracker.storage,
            target.tracker.tile,
            target.slot,
            PacketPartitionAction.Action.CLEAR_ALL,
            -1,
            null);
    }

    private static void setCellPartition(CellTarget target, IAEStack<?> stack) {
        NBTTagCompound stackData = new NBTTagCompound();
        AEStackUtil.writeStackToNBT(stackData, stack);

        CellActionHandler.handlePartitionAction(
            target.tracker.storage,
            target.tracker.tile,
            target.slot,
            PacketPartitionAction.Action.ADD_ITEM,
            0,
            stackData);
    }

    private static void markDirty(CellTarget target) {
        if (target.tracker != null && target.tracker.tile != null) target.tracker.tile.markDirty();
    }

    private static CellAccess getCellAccess(CellTarget target, IAEStackType<?> type) {
        ICellHandler cellHandler = AEApi.instance()
            .registries()
            .cell()
            .getHandler(target.cellStack);
        if (cellHandler == null) return null;

        IMEInventoryHandler<?> raw = cellHandler.getCellInventory(target.cellStack, null, type);
        if (!(raw instanceof ICellInventoryHandler)) return null;

        ICellInventory<?> inventory = ((ICellInventoryHandler<?>) raw).getCellInv();
        if (inventory == null) return null;

        return new CellAccess(inventory);
    }

    private static boolean isCellEmptyAndNonPartitioned(CellAccess access) {
        if (access.inventory.getUsedBytes() != 0) return false;

        IAEStackInventory config = access.inventory.getConfigAEInventory();
        if (config == null) return true;

        for (int i = 0; i < config.getSizeInventory(); i++) {
            if (config.getAEStackInSlot(i) != null) return false;
        }

        return true;
    }

    private static boolean isPartitionableCell(ItemStack cellStack) {
        if (ItemStacks.isEmpty(cellStack)) return false;
        if (cellStack.getItem() instanceof IItemCompactingCell) return false;

        return AEApi.instance()
            .registries()
            .cell()
            .getHandler(cellStack) != null;
    }

    private static boolean matchesCellFilters(ItemStack cellStack, Map<CellFilter, CellFilter.State> activeFilters) {
        IAEStackType<?> type = getCellStackType(cellStack);
        StorageType storageType = storageTypeFrom(type);

        if (!matchesStorageType(storageType, activeFilters)) return false;

        CellFilter.State hasItems = activeFilters.getOrDefault(CellFilter.HAS_ITEMS, CellFilter.State.SHOW_ALL);
        if (hasItems != CellFilter.State.SHOW_ALL) {
            boolean cellHasContents = checkCellHasContents(cellStack, type);
            if (hasItems == CellFilter.State.SHOW_ONLY && !cellHasContents) return false;
            if (hasItems == CellFilter.State.HIDE && cellHasContents) return false;
        }

        CellFilter.State partitioned = activeFilters.getOrDefault(CellFilter.PARTITIONED, CellFilter.State.SHOW_ALL);
        if (partitioned != CellFilter.State.SHOW_ALL) {
            boolean cellHasPartition = checkCellHasPartition(cellStack, type);
            if (partitioned == CellFilter.State.SHOW_ONLY && !cellHasPartition) return false;
            if (partitioned == CellFilter.State.HIDE && cellHasPartition) return false;
        }

        return true;
    }

    private static boolean matchesBusFilters(StorageBusTracker tracker,
        Map<CellFilter, CellFilter.State> activeFilters) {
        if (!(tracker.storageBus instanceof PartStorageBus bus)) return false;

        StorageType storageType = storageTypeFrom(bus.getStackType());

        if (!matchesStorageType(storageType, activeFilters)) return false;

        CellFilter.State hasItems = activeFilters.getOrDefault(CellFilter.HAS_ITEMS, CellFilter.State.SHOW_ALL);
        if (hasItems != CellFilter.State.SHOW_ALL) {
            boolean busHasContents = tracker.hasConnectedContents
                || StorageBusDataHandler.busHasConnectedInventory(tracker);
            if (hasItems == CellFilter.State.SHOW_ONLY && !busHasContents) return false;
            if (hasItems == CellFilter.State.HIDE && busHasContents) return false;
        }

        CellFilter.State partitioned = activeFilters.getOrDefault(CellFilter.PARTITIONED, CellFilter.State.SHOW_ALL);
        if (partitioned != CellFilter.State.SHOW_ALL) {
            boolean busHasPartition = StorageBusDataHandler.busHasPartition(tracker);
            if (partitioned == CellFilter.State.SHOW_ONLY && !busHasPartition) return false;
            if (partitioned == CellFilter.State.HIDE && busHasPartition) return false;
        }

        return true;
    }

    private static boolean matchesStorageType(StorageType storageType,
        Map<CellFilter, CellFilter.State> activeFilters) {
        CellFilter.State item = activeFilters.getOrDefault(CellFilter.ITEM_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State fluid = activeFilters.getOrDefault(CellFilter.FLUID_CELLS, CellFilter.State.SHOW_ALL);
        CellFilter.State essentia = activeFilters.getOrDefault(CellFilter.ESSENTIA_CELLS, CellFilter.State.SHOW_ALL);

        if (storageType == StorageType.ITEM && item == CellFilter.State.HIDE) return false;
        if (storageType == StorageType.FLUID && fluid == CellFilter.State.HIDE) return false;
        if (storageType == StorageType.ESSENTIA && essentia == CellFilter.State.HIDE) return false;

        boolean hasShowOnly = item == CellFilter.State.SHOW_ONLY || fluid == CellFilter.State.SHOW_ONLY
            || essentia == CellFilter.State.SHOW_ONLY;

        if (!hasShowOnly) return true;

        return switch (storageType) {
            case ITEM -> item == CellFilter.State.SHOW_ONLY;
            case FLUID -> fluid == CellFilter.State.SHOW_ONLY;
            case ESSENTIA -> essentia == CellFilter.State.SHOW_ONLY;
        };
    }

    private static IAEStackType<?> getCellStackType(ItemStack cellStack) {
        ICellHandler cellHandler = AEApi.instance()
            .registries()
            .cell()
            .getHandler(cellStack);
        if (cellHandler == null) return null;

        if (cellStack.getItem() instanceof ICellWorkbenchItem workbenchItem) {
            return workbenchItem.getStackType();
        }

        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            IMEInventoryHandler<?> raw = cellHandler.getCellInventory(cellStack, null, type);
            if (raw instanceof ICellInventoryHandler) return type;
        }

        return null;
    }

    private static boolean checkCellHasContents(ItemStack cellStack, IAEStackType<?> preferredType) {
        if (preferredType != null && checkCellHasContentsForType(cellStack, preferredType)) return true;

        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            if (type != preferredType && checkCellHasContentsForType(cellStack, type)) return true;
        }

        return false;
    }

    private static boolean checkCellHasContentsForType(ItemStack cellStack, IAEStackType<?> type) {
        CellAccess access = getCellAccess(new CellTarget(null, -1, cellStack), type);

        return access != null && access.inventory.getUsedBytes() > 0;
    }

    private static boolean checkCellHasPartition(ItemStack cellStack, IAEStackType<?> preferredType) {
        if (preferredType != null && checkCellHasPartitionForType(cellStack, preferredType)) return true;

        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            if (type != preferredType && checkCellHasPartitionForType(cellStack, type)) return true;
        }

        return false;
    }

    private static boolean checkCellHasPartitionForType(ItemStack cellStack, IAEStackType<?> type) {
        CellAccess access = getCellAccess(new CellTarget(null, -1, cellStack), type);
        if (access == null) return false;

        IAEStackInventory config = access.inventory.getConfigAEInventory();
        if (config == null) return false;

        for (int i = 0; i < config.getSizeInventory(); i++) {
            if (config.getAEStackInSlot(i) != null) return true;
        }

        return false;
    }

    private static StorageType storageTypeFrom(IAEStackType<?> type) {
        if (type == null) return StorageType.ITEM;

        return switch (type.getId()) {
            case "fluid" -> StorageType.FLUID;
            case "essentia" -> StorageType.ESSENTIA;
            default -> StorageType.ITEM;
        };
    }

    private static String createStackKey(IAEStack<?> stack) {
        IAEStack<?> keyStack = stack.copy();
        keyStack.setStackSize(1);

        NBTTagCompound tag = new NBTTagCompound();
        AEStackUtil.writeStackToNBT(tag, keyStack);

        return tag.toString();
    }

    private static class CellTarget {

        private final StorageTracker tracker;
        private final int slot;
        private final ItemStack cellStack;

        private CellTarget(StorageTracker tracker, int slot, ItemStack cellStack) {
            this.tracker = tracker;
            this.slot = slot;
            this.cellStack = cellStack;
        }
    }

    private static class CellAccess {

        private final ICellInventory<?> inventory;

        private CellAccess(ICellInventory<?> inventory) {
            this.inventory = inventory;
        }
    }

    private static class TypePlan {

        private final IAEStackType<?> type;
        private final List<CellTarget> filteredTargets = new ArrayList<>();
        private final List<CellTarget> targets = new ArrayList<>();
        private final List<IAEStack<?>> uniqueStacks = new ArrayList<>();

        private TypePlan(IAEStackType<?> type) {
            this.type = type;
        }

        private boolean hasContent() {
            return !uniqueStacks.isEmpty();
        }
    }

    private static class RedistributionCounts {

        private int moved;
        private int failed;
        private int affectedCells;
    }
}
