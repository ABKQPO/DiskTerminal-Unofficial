package com.hfstudio.diskterminal.gui.handler;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.client.CellContentRow;
import com.hfstudio.diskterminal.client.CellInfo;
import com.hfstudio.diskterminal.client.StorageInfo;
import com.hfstudio.diskterminal.integration.NEIIntegration;
import com.hfstudio.diskterminal.integration.ThaumicEnergisticsIntegration;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketPartitionAction;
import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Handles quick-partition keybind actions: find the hovered item, locate a suitable cell with an
 * empty partition, and send a partition packet.
 * <p>
 * The 1.12 source pulled the hovered ingredient from NEI's overlays; here we read the hovered
 * inventory slot directly and leave a hook ({@link #getModIngredientUnderMouse}) for the NEI bridge
 * bridge.
 */
public class QuickPartitionHandler {

    public enum PartitionType {

        AUTO,
        ITEM,
        FLUID,
        ESSENTIA
    }

    /**
     * Result of a quick partition attempt.
     */
    public static class QuickPartitionResult {

        public final boolean success;
        public final String message;
        public final int scrollToLine;

        public QuickPartitionResult(boolean success, String message, int scrollToLine) {
            this.success = success;
            this.message = message;
            this.scrollToLine = scrollToLine;
        }

        public static QuickPartitionResult error(String messageKey) {
            return new QuickPartitionResult(false, I18n.format(messageKey), -1);
        }

        public static QuickPartitionResult error(String messageKey, Object... args) {
            return new QuickPartitionResult(false, I18n.format(messageKey, args), -1);
        }

        public static QuickPartitionResult success(String message, int scrollToLine) {
            return new QuickPartitionResult(true, message, scrollToLine);
        }
    }

    private QuickPartitionHandler() {}

    /**
     * Attempt to quick partition based on the hovered item.
     */
    public static QuickPartitionResult attemptQuickPartition(PartitionType type, List<Object> partitionLines,
        Map<Long, StorageInfo> storageMap) {
        if (type == PartitionType.ESSENTIA && !ThaumicEnergisticsIntegration.isModLoaded()) {
            return QuickPartitionResult.error("disk_terminal.quick_partition.essentia_unavailable");
        }

        HoveredIngredient hoveredIngredient = getHoveredIngredient();
        if (hoveredIngredient == null || ItemStacks.isEmpty(hoveredIngredient.stack)) {
            return QuickPartitionResult.error("disk_terminal.quick_partition.no_item");
        }

        PartitionType targetType = type;
        if (type == PartitionType.AUTO) targetType = hoveredIngredient.inferredType;

        ItemStack partitionStack = convertStackForCellType(
            hoveredIngredient.stack,
            targetType,
            hoveredIngredient.originalIngredient);
        if (ItemStacks.isEmpty(partitionStack)) {
            switch (targetType) {
                case FLUID:
                    return QuickPartitionResult.error("disk_terminal.error.fluid_cell_item");
                case ESSENTIA:
                    return QuickPartitionResult.error("disk_terminal.error.essentia_cell_item");
                default:
                    return QuickPartitionResult.error("disk_terminal.quick_partition.no_item");
            }
        }

        CellSearchResult searchResult = findFirstCellWithoutPartition(targetType, partitionLines, storageMap);
        if (searchResult == null) {
            switch (targetType) {
                case ITEM:
                    return QuickPartitionResult.error("disk_terminal.quick_partition.no_cell_item");
                case FLUID:
                    return QuickPartitionResult.error("disk_terminal.quick_partition.no_cell_fluid");
                case ESSENTIA:
                    return QuickPartitionResult.error("disk_terminal.quick_partition.no_cell_essentia");
                default:
                    return QuickPartitionResult.error("disk_terminal.quick_partition.no_cell_auto");
            }
        }

        DiskTerminalNetwork.INSTANCE.sendToServer(
            new PacketPartitionAction(
                searchResult.cell.getParentStorageId(),
                searchResult.cell.getSlot(),
                PacketPartitionAction.Action.ADD_ITEM,
                0,
                partitionStack));

        String cellName = searchResult.cell.getCellItem()
            .getDisplayName();
        String itemName = partitionStack.getDisplayName();
        String successMessage = I18n.format("disk_terminal.quick_partition.success", itemName, cellName);

        return QuickPartitionResult.success(successMessage, searchResult.lineIndex);
    }

    /**
     * Holds information about a hovered ingredient, preserving its original type.
     */
    public static class HoveredIngredient {

        public final ItemStack stack;
        public final Object originalIngredient;
        public final PartitionType inferredType;

        HoveredIngredient(ItemStack stack, Object originalIngredient, PartitionType inferredType) {
            this.stack = stack;
            this.originalIngredient = originalIngredient;
            this.inferredType = inferredType;
        }
    }

    /**
     * Get the ingredient currently under the mouse cursor (inventory slot, or a mod overlay).
     */
    @Nullable
    public static HoveredIngredient getHoveredIngredient() {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.currentScreen instanceof GuiContainer) {
            Slot hoveredSlot = SlotAccess.slotUnderMouse((GuiContainer) mc.currentScreen);

            if (hoveredSlot != null && hoveredSlot.getHasStack()) {
                ItemStack stack = hoveredSlot.getStack()
                    .copy();
                return new HoveredIngredient(stack, stack, PartitionType.ITEM);
            }
        }

        Object modIngredient = getModIngredientUnderMouse();
        if (modIngredient instanceof ItemStack) {
            ItemStack stack = ((ItemStack) modIngredient).copy();
            return new HoveredIngredient(stack, modIngredient, PartitionType.ITEM);
        }
        if (modIngredient instanceof FluidStack) {
            ItemStack rep = FluidStacks.toDisplayStack((FluidStack) modIngredient);
            if (!ItemStacks.isEmpty(rep)) return new HoveredIngredient(rep, modIngredient, PartitionType.FLUID);
        }

        return null;
    }

    /**
     * Hook for the NEI bridge: return the item ingredient under the mouse in the NEI item-list
     * overlay, or null. NEI only surfaces ItemStacks (fluids appear as their AE2FC drop items).
     */
    @Nullable
    public static Object getModIngredientUnderMouse() {
        return NEIIntegration.getStackUnderMouse();
    }

    private static ItemStack convertStackForCellType(ItemStack stack, PartitionType type,
        @Nullable Object originalIngredient) {
        switch (type) {
            case FLUID:
                if (originalIngredient instanceof FluidStack) return stack;

                FluidStack contained = FluidStacks.extract(stack);
                if (contained != null) return FluidStacks.toDisplayStack(contained);

                return null;

            case ESSENTIA:
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(stack);
                if (!ItemStacks.isEmpty(essentiaRep)) return essentiaRep;

                return null;

            case ITEM:
            case AUTO:
            default:
                return stack.copy();
        }
    }

    private static class CellSearchResult {

        final CellInfo cell;
        final int lineIndex;

        CellSearchResult(CellInfo cell, int lineIndex) {
            this.cell = cell;
            this.lineIndex = lineIndex;
        }
    }

    private static CellSearchResult findFirstCellWithoutPartition(PartitionType type, List<Object> partitionLines,
        Map<Long, StorageInfo> storageMap) {
        for (int i = 0; i < partitionLines.size(); i++) {
            Object line = partitionLines.get(i);
            if (!(line instanceof CellContentRow)) continue;

            CellContentRow row = (CellContentRow) line;
            if (!row.isFirstRow()) continue;

            CellInfo cell = row.getCell();
            if (!matchesCellType(cell, type)) continue;

            if (!hasEmptyPartition(cell)) continue;

            return new CellSearchResult(cell, i);
        }

        return null;
    }

    private static boolean matchesCellType(CellInfo cell, PartitionType type) {
        switch (type) {
            case ITEM:
                return cell.isItem();
            case FLUID:
                return cell.isFluid();
            case ESSENTIA:
                return cell.isEssentia();
            case AUTO:
                return true;
            default:
                return false;
        }
    }

    private static boolean hasEmptyPartition(CellInfo cell) {
        List<ItemStack> partition = cell.getPartition();

        if (partition.isEmpty()) return true;

        for (ItemStack stack : partition) {
            if (!ItemStacks.isEmpty(stack)) return false;
        }

        return true;
    }
}
