package com.hfstudio.diskterminal.gui.widget.tab;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import com.hfstudio.diskterminal.client.RequestAreaControlInfo;
import com.hfstudio.diskterminal.client.RequestAreaDeviceInfo;
import com.hfstudio.diskterminal.client.RequestAreaSlotInfo;
import com.hfstudio.diskterminal.client.SearchFilterMode;
import com.hfstudio.diskterminal.client.TabStateManager;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.gui.handler.TerminalDataManager;
import com.hfstudio.diskterminal.gui.widget.AbstractWidget;
import com.hfstudio.diskterminal.gui.widget.CardsDisplay;
import com.hfstudio.diskterminal.gui.widget.IWidget;
import com.hfstudio.diskterminal.gui.widget.button.SmallIconButton;
import com.hfstudio.diskterminal.gui.widget.header.AbstractHeader;
import com.hfstudio.diskterminal.gui.widget.header.StorageHeader;
import com.hfstudio.diskterminal.gui.widget.line.AbstractLine;
import com.hfstudio.diskterminal.network.PacketRequestAreaAction;
import com.hfstudio.diskterminal.requestarea.RequestAreaAction;
import com.hfstudio.diskterminal.requestarea.RequestAreaAdapterRegistry;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.features.LevelState;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.gui.widgets.TypeToggleButton;
import appeng.client.texture.ExtraBlockTextures;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.server.ServerHelper;

public class RequestAreaTabWidget extends AbstractTabWidget {

    private static final int MATERIAL_X = GuiConstants.GUI_INDENT + 20;
    private static final int TARGET_X = GuiConstants.GUI_INDENT + 40;
    private static final int STATUS_BAR_RIGHT_EDGE = GuiConstants.CONTENT_RIGHT_EDGE - 5;
    private static final int STATUS_BAR_Y_OFFSET = 12;
    private static final int STATUS_BAR_HEIGHT = 5;
    private static final int INVERTED_X = STATUS_BAR_RIGHT_EDGE - GuiConstants.SMALL_BUTTON_SIZE;
    private static final int CONTROL_BUTTON_X = GuiConstants.GUI_INDENT + 18;
    private static final int CONTROL_ROW_HEIGHT = GuiConstants.ROW_HEIGHT - 8;
    private static final int CONTROL_BUTTON_Y_OFFSET = (CONTROL_ROW_HEIGHT - GuiConstants.SMALL_BUTTON_SIZE) / 2;
    private static final int CONTROL_NAME_X = CONTROL_BUTTON_X + GuiConstants.SMALL_BUTTON_SIZE + 2;
    private static final int AMOUNT_FIELD_WIDTH = (INVERTED_X - 2 - TARGET_X - 1) / 2;
    private static final int BATCH_X = TARGET_X + AMOUNT_FIELD_WIDTH + 1;
    private static final int AMOUNT_FIELD_HEIGHT = 12;
    private static final int AMOUNT_FIELD_Y_OFFSET = 3;
    private static final int MAINTAINER_SLOT_ROW_HEIGHT = GuiConstants.ROW_HEIGHT;
    private static final ResourceLocation MAINTAINER_BUTTON_TEXTURE = new ResourceLocation(
        "ae2fc",
        "textures/gui/states0.png");

    private List<RequestAreaDeviceInfo> cachedDevices = Collections.emptyList();
    private List<Object> cachedLines = Collections.emptyList();
    private final Map<String, AmountEditState> amountEdits = new HashMap<>();
    private String focusedAmountKey;
    private boolean lineCacheDirty = true;

    public RequestAreaTabWidget(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    @Override
    public List<Object> getLines(TerminalDataManager dataManager) {
        List<Object> devices = dataManager.getRequestAreaLines();
        if (lineCacheDirty || !hasSameDevices(devices)) rebuildLines(devices);
        return cachedLines;
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        return SearchFilterMode.MIXED;
    }

    @Override
    public boolean showSearchModeButton() {
        return false;
    }

    @Override
    protected int getRowStep(List<?> lines, int index) {
        if (index >= 0 && index < lines.size() && lines.get(index) instanceof ControlRow) {
            return CONTROL_ROW_HEIGHT;
        }
        if (index >= 0 && index < lines.size()
            && lines.get(index) instanceof SlotRow slotRow
            && "level_maintainer".equals(slotRow.device.getTypeId())) {
            return MAINTAINER_SLOT_ROW_HEIGHT;
        }
        return super.getRowStep(lines, index);
    }

    @Override
    public boolean handleUpgradeClick(Object hoveredData, ItemStack heldStack, boolean isShiftClick) {
        RequestAreaDeviceInfo device = getDevice(hoveredData);
        if (device == null) return isShiftClick && insertIntoFirstAvailableDevice();
        int slot = findEmptyUpgradeSlot(device);
        if (slot < 0) return false;
        send(device, slot, RequestAreaAction.INSERT_UPGRADE, 0, null);
        return true;
    }

    @Override
    public boolean handleShiftUpgradeClick(ItemStack heldStack) {
        return insertIntoFirstAvailableDevice();
    }

    @Override
    public boolean handleInventorySlotShiftClick(ItemStack upgradeStack, int sourceSlotIndex) {
        for (RequestAreaDeviceInfo device : cachedDevices) {
            int slot = findEmptyUpgradeSlot(device);
            if (slot >= 0) {
                guiContext.sendPacket(
                    new PacketRequestAreaAction(
                        device,
                        slot,
                        RequestAreaAction.INSERT_UPGRADE,
                        0,
                        null,
                        sourceSlotIndex));
                return true;
            }
        }
        return true;
    }

    private boolean insertIntoFirstAvailableDevice() {
        for (RequestAreaDeviceInfo device : cachedDevices) {
            int slot = findEmptyUpgradeSlot(device);
            if (slot < 0) continue;
            send(device, slot, RequestAreaAction.INSERT_UPGRADE, 0, null);
            return true;
        }
        return false;
    }

    private int findEmptyUpgradeSlot(RequestAreaDeviceInfo device) {
        List<ItemStack> upgrades = device.getUpgrades();
        int limit = device.getUpgradeSlotLimit();
        int installed = 0;
        for (ItemStack upgrade : upgrades) {
            if (!ItemStacks.isEmpty(upgrade)) installed++;
        }
        if (installed >= limit) return -1;
        for (int slot = 0; slot < limit; slot++) {
            if (ItemStacks.isEmpty(upgrades.get(slot))) return slot;
        }
        return -1;
    }

    private RequestAreaDeviceInfo getDevice(Object rowData) {
        if (rowData instanceof RequestAreaDeviceInfo device) return device;
        if (rowData instanceof SlotRow slotRow) return slotRow.device;
        if (rowData instanceof ControlRow controlRow) return controlRow.device;
        return null;
    }

    @Override
    protected IWidget createRowWidget(Object lineData, int y, List<?> allLines, int lineIndex) {
        if (lineData instanceof RequestAreaDeviceInfo device) return createDeviceHeader(device, y);
        if (lineData instanceof ControlRow controlRow) return new RequestAreaControlLine(controlRow, y);
        if (lineData instanceof SlotRow slotRow) return new RequestAreaSlotLine(slotRow, y);
        return null;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        return index >= 0 && index < allLines.size()
            && (allLines.get(index) instanceof ControlRow || allLines.get(index) instanceof SlotRow);
    }

    @Override
    public boolean handleKey(char typedChar, int keyCode) {
        if (focusedAmountKey != null) {
            AmountEditState state = amountEdits.get(focusedAmountKey);
            if (state != null && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
                commitAmount(state);
                state.field.setFocused(false);
                focusedAmountKey = null;
                return true;
            }
            if (keyCode == Keyboard.KEY_ESCAPE && state != null) {
                state.field.setText(Long.toString(state.currentValue));
                state.field.setFocused(false);
                focusedAmountKey = null;
                return true;
            }
            if (state != null && isAmountInput(typedChar, keyCode) && state.field.textboxKeyTyped(typedChar, keyCode))
                return true;
        }
        return super.handleKey(typedChar, keyCode);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (focusedAmountKey != null) {
            AmountEditState state = amountEdits.get(focusedAmountKey);
            if (state != null && !state.field.isMouseIn(mouseX, mouseY)) {
                commitAmount(state);
                state.field.setFocused(false);
                focusedAmountKey = null;
            }
        }
        return super.handleClick(mouseX, mouseY, button);
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("gui.disk_terminal.request_area.controls.material"));
        lines.add(I18n.format("gui.disk_terminal.request_area.controls.amount"));
        lines.add(I18n.format("gui.disk_terminal.request_area.controls.toggle"));
        lines.add(I18n.format("gui.disk_terminal.request_area.controls.upgrades"));
        lines.add(I18n.format("gui.disk_terminal.request_area.controls.highlight"));
        return lines;
    }

    @Override
    public ItemStack getTabIcon() {
        return RequestAreaAdapterRegistry.getInstance()
            .findByType("level_emitter")
            .map(adapter -> adapter.getIcon())
            .orElse(null);
    }

    @Override
    public String getTabTooltip() {
        return I18n.format("gui.disk_terminal.tab.request_area.tooltip");
    }

    private boolean hasSameDevices(List<Object> devices) {
        if (devices.size() != cachedDevices.size()) return false;
        for (int index = 0; index < devices.size(); index++) {
            if (devices.get(index) != cachedDevices.get(index)) return false;
        }
        return true;
    }

    private void rebuildLines(List<Object> devices) {
        List<RequestAreaDeviceInfo> deviceSnapshot = new ArrayList<>(devices.size());
        List<Object> rows = new ArrayList<>();
        for (Object entry : devices) {
            if (!(entry instanceof RequestAreaDeviceInfo device)) continue;
            deviceSnapshot.add(device);
            rows.add(device);
            if (!isExpanded(device)) continue;

            for (RequestAreaControlInfo control : device.getControls()) {
                rows.add(new ControlRow(device, control));
            }
            List<RequestAreaSlotInfo> slots = device.getSlots();
            for (int index = 0; index < slots.size(); index++) {
                rows.add(new SlotRow(device, slots.get(index), index));
            }
        }
        cachedDevices = deviceSnapshot;
        cachedLines = rows;
        lineCacheDirty = false;
    }

    private AbstractHeader createDeviceHeader(RequestAreaDeviceInfo device, int y) {
        StorageHeader header = new DeviceHeader(device, y, fontRenderer, itemRender);
        header.setIconSupplier(device::getIcon);
        header.setNameSupplier(device::getName);
        header.setHasCustomNameSupplier(
            () -> !device.getCustomName()
                .isEmpty());
        header.setLocationSupplier(device::getLocationString);
        header.setExpandedSupplier(() -> isExpanded(device));
        header.setOnExpandToggle(() -> {
            TabStateManager.getInstance()
                .toggleExpanded(TabStateManager.TabType.REQUEST_AREA, device.getId());
            lineCacheDirty = true;
            guiContext.rebuildAndUpdateScrollbar();
        });
        header.setOnNameDoubleClick(
            () -> guiContext.highlightInWorld(device.getPosition(), device.getDimension(), device.getName()),
            Long.MIN_VALUE / 4 ^ device.getId());
        CardsDisplay cards = createUpgradeCards(device, y);
        if (cards != null) header.setCardsDisplay(cards);
        return header;
    }

    private CardsDisplay createUpgradeCards(RequestAreaDeviceInfo device, int y) {
        List<ItemStack> upgrades = device.getUpgrades();
        if (upgrades.isEmpty()) return null;
        List<CardsDisplay.CardEntry> entries = new ArrayList<>(upgrades.size());
        for (int index = 0; index < upgrades.size(); index++) {
            entries.add(new CardsDisplay.CardEntry(upgrades.get(index), index));
        }
        CardsDisplay cards = new CardsDisplay(GuiConstants.CARDS_X, y, () -> entries, itemRender);
        cards.setClickCallback(slot -> {
            boolean toInventory = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            send(device, slot, RequestAreaAction.EXTRACT_UPGRADE, toInventory ? 1 : 0, null);
        });
        return cards;
    }

    private boolean isExpanded(RequestAreaDeviceInfo device) {
        return TabStateManager.getInstance()
            .isExpanded(TabStateManager.TabType.REQUEST_AREA, device.getId());
    }

    private boolean isOver(int mouseX, int mouseY, int targetX, int targetY, int targetWidth, int targetHeight) {
        return AbstractWidget.isPointIn(mouseX, mouseY, targetX, targetY, targetWidth, targetHeight);
    }

    private boolean isAmountInput(char typedChar, int keyCode) {
        return Character.isDigit(typedChar) || keyCode == Keyboard.KEY_BACK
            || keyCode == Keyboard.KEY_DELETE
            || keyCode == Keyboard.KEY_LEFT
            || keyCode == Keyboard.KEY_RIGHT
            || keyCode == Keyboard.KEY_HOME
            || keyCode == Keyboard.KEY_END
            || Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
            || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private void send(RequestAreaDeviceInfo device, int slot, RequestAreaAction action, long value,
        NBTTagCompound stackData) {
        guiContext.sendPacket(new PacketRequestAreaAction(device, slot, action, value, stackData));
    }

    private class DeviceHeader extends StorageHeader {

        private final RequestAreaDeviceInfo device;

        private DeviceHeader(RequestAreaDeviceInfo device, int y, FontRenderer fontRenderer, RenderItem itemRender) {
            super(y, fontRenderer, itemRender);
            this.device = device;
            this.nameMaxWidth = GuiConstants.EXPAND_ICON_X - GuiConstants.HEADER_NAME_X - 4;
        }

        @Override
        protected int drawHeaderContent(int mouseX, int mouseY) {
            int hoverRight = super.drawHeaderContent(mouseX, mouseY);
            if (cardsDisplay != null) cardsDisplay.draw(mouseX, mouseY);
            return hoverRight;
        }

        @Override
        public boolean handleClick(int mouseX, int mouseY, int button) {
            return super.handleClick(mouseX, mouseY, button);
        }

        @Override
        public List<String> getTooltip(int mouseX, int mouseY) {
            return super.getTooltip(mouseX, mouseY);
        }
    }

    private class RequestAreaControlLine extends AbstractLine {

        private final ControlRow row;
        private final SmallIconButton button;

        private RequestAreaControlLine(ControlRow row, int y) {
            super(0, y, GuiConstants.CONTENT_RIGHT_EDGE);
            setRowHeight(CONTROL_ROW_HEIGHT);
            this.row = row;
            this.button = new SmallIconButton(
                CONTROL_BUTTON_X,
                y + CONTROL_BUTTON_Y_OFFSET,
                createControlIconRenderer(row.control),
                () -> getControlTooltip(row.control),
                getControlButtonColor(row.control),
                () -> cycleControl(row.device, row.control));
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            if (!visible) return;
            if (isHovered(mouseX, mouseY)) {
                Gui.drawRect(
                    GuiConstants.GUI_INDENT,
                    y,
                    GuiConstants.CONTENT_RIGHT_EDGE,
                    y + height,
                    GuiConstants.COLOR_STORAGE_HEADER_HOVER);
            }
            drawTreeLines(mouseX, mouseY);
            drawControlIcon(mouseX, mouseY);
            String label = getControlLabel(row.control);
            fontRenderer.drawString(
                trimTextToWidth(fontRenderer, label, GuiConstants.CONTENT_RIGHT_EDGE - CONTROL_NAME_X - 3),
                CONTROL_NAME_X,
                y + (height - fontRenderer.FONT_HEIGHT) / 2,
                0xFFFFFFFF);
        }

        private void drawControlIcon(int mouseX, int mouseY) {
            button.setPosition(CONTROL_BUTTON_X, y + CONTROL_BUTTON_Y_OFFSET);
            button.draw(mouseX, mouseY);
        }

        @Override
        public int getTreeLineCutY() {
            return y + height / 2 + 1;
        }

        @Override
        protected void drawTreeLines(int mouseX, int mouseY) {
            if (!drawTreeLine) return;
            int branchY = y + height / 2;
            if (lineAboveCutY < branchY) {
                Gui.drawRect(TREE_LINE_X, lineAboveCutY, TREE_LINE_X + 1, branchY, GuiConstants.COLOR_TREE_LINE);
            }
            Gui.drawRect(
                TREE_LINE_X,
                branchY,
                TREE_LINE_X + TREE_BRANCH_WIDTH,
                branchY + 1,
                GuiConstants.COLOR_TREE_LINE);
        }

        @Override
        public boolean handleClick(int mouseX, int mouseY, int button) {
            if (!visible || !isHovered(mouseX, mouseY)) return false;
            if (super.handleClick(mouseX, mouseY, button)) return true;
            if (button != 0 && button != 1 || !isOver(
                mouseX,
                mouseY,
                CONTROL_BUTTON_X,
                y + CONTROL_BUTTON_Y_OFFSET,
                GuiConstants.SMALL_BUTTON_SIZE,
                GuiConstants.SMALL_BUTTON_SIZE)) return true;
            boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            int direction = shiftDown && "AE2FC_LITE_MODE".equals(row.control.settingId()) ? 0 : button == 1 ? -1 : 1;
            cycleControl(row.device, row.control, direction);
            return true;
        }

        @Override
        public List<String> getTooltip(int mouseX, int mouseY) {
            if (!isOver(
                mouseX,
                mouseY,
                CONTROL_BUTTON_X,
                y + CONTROL_BUTTON_Y_OFFSET,
                GuiConstants.SMALL_BUTTON_SIZE,
                GuiConstants.SMALL_BUTTON_SIZE)) return Collections.emptyList();
            return getControlTooltip(row.control);
        }
    }

    private GuiImgButton createNativeButton(RequestAreaControlInfo control) {
        try {
            Settings setting = Settings.valueOf(control.settingId());
            for (Object option : setting.getPossibleValues()) {
                if (option instanceof Enum<?>value && value.name()
                    .equals(control.valueId())) {
                    return createNativeButton(setting, value);
                }
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private GuiImgButton createNativeButton(Settings setting, Enum<?> value) {
        return new GuiImgButton(0, 0, setting, value) {

            @Override
            public void drawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height) {
                // The terminal owns the frame; retain only the native setting icon.
                if (textureX == 240 && textureY == 240) return;
                super.drawTexturedModalRect(x, y, textureX, textureY, width, height);
            }
        };
    }

    private Runnable createControlIconRenderer(RequestAreaControlInfo control) {
        if ("AE2FC_LITE_MODE".equals(control.settingId())) {
            int icon = control.active() ? 178 : 194;
            return () -> drawTextureIcon(
                ExtraBlockTextures.GuiTexture("guis/states.png"),
                icon % 16 * 16,
                icon / 16 * 16,
                16);
        }
        TypeToggleButton typeButton = createTypeToggleButton(control);
        if (typeButton != null) return nativeIconRenderer(typeButton);
        return nativeIconRenderer(createNativeButton(control));
    }

    private Runnable nativeIconRenderer(GuiButton button) {
        return button == null ? null : () -> button.drawButton(Minecraft.getMinecraft(), -1, -1);
    }

    private void drawTextureIcon(ResourceLocation texture, int textureX, int textureY, int sourceSize) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        GuiConstants.drawScaledCustomSizeModalRect(0, 0, textureX, textureY, sourceSize, sourceSize, 16, 16, 256, 256);
    }

    private TypeToggleButton createTypeToggleButton(RequestAreaControlInfo control) {
        if (!"TYPE_FILTER".equals(control.settingId()) || !control.id()
            .startsWith("type:")) return null;
        IAEStackType<?> type = AEStackTypeRegistry.getType(
            control.id()
                .substring("type:".length()));
        if (type == null) return null;
        TypeToggleButton button = new TypeToggleButton(
            0,
            0,
            type.getButtonTexture(),
            type.getButtonIcon(),
            type.getDisplayName()) {

            @Override
            public void drawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height) {
                // Type icons are drawn separately by the native button.
            }
        };
        button.setEnabled(control.active());
        return button;
    }

    private List<String> getNativeTooltip(GuiImgButton button) {
        return List.of(
            button.getMessage()
                .split("\\n", -1));
    }

    private int getControlButtonColor(RequestAreaControlInfo control) {
        if (!isOnOffControl(control)) return 0xFF858585;
        boolean enabled = "YES".equals(control.valueId()) || (!"NO".equals(control.valueId()) && control.active());
        return enabled ? 0xFF48A848 : 0xFF9A4D4D;
    }

    private boolean isOnOffControl(RequestAreaControlInfo control) {
        if ("TYPE_FILTER".equals(control.settingId()) || "AE2FC_LITE_MODE".equals(control.settingId())) return true;
        return "YES".equals(control.valueId()) || "NO".equals(control.valueId());
    }

    private List<String> getControlTooltip(RequestAreaControlInfo control) {
        if ("AE2FC_LITE_MODE".equals(control.settingId())) {
            return List.of(I18n.format("ae2fc.tooltip.level_maintainer.lite_craft_desc"));
        }
        TypeToggleButton typeButton = createTypeToggleButton(control);
        if (typeButton != null) return List.of(
            typeButton.getMessage()
                .split("\\n", -1));
        GuiImgButton button = createNativeButton(control);
        if (button != null) return List.of(
            button.getMessage()
                .split("\\n", -1));
        String name = control.displayName()
            .isEmpty() ? control.id() : control.displayName();
        return List.of(
            I18n.format(name),
            I18n.format(
                control.active() ? "gui.disk_terminal.request_area.control.enabled"
                    : "gui.disk_terminal.request_area.control.disabled"));
    }

    private String getControlLabel(RequestAreaControlInfo control) {
        if ("AE2FC_LITE_MODE".equals(control.settingId())) {
            return I18n.format(control.displayName());
        }
        TypeToggleButton typeButton = createTypeToggleButton(control);
        if (typeButton != null) return typeButton.getMessage()
            .split("\\n", -1)[0];
        GuiImgButton button = createNativeButton(control);
        if (button != null) return button.getMessage()
            .split("\\n", -1)[0];
        return I18n.format(
            control.displayName()
                .isEmpty() ? control.id() : control.displayName());
    }

    private void cycleControl(RequestAreaDeviceInfo device, RequestAreaControlInfo control) {
        cycleControl(device, control, 1);
    }

    private void cycleControl(RequestAreaDeviceInfo device, RequestAreaControlInfo control, int direction) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("controlId", control.id());
        send(device, -1, RequestAreaAction.CYCLE_CONTROL, direction, data);
    }

    private List<String> getMaintainerAmountTooltip(boolean batch, long value) {
        String key = batch ? "ae2fc.tooltip.level_maintainer.batch_size"
            : "ae2fc.tooltip.level_maintainer.request_size";
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format(key));
        lines.add(
            I18n.format("ae2fc.tooltip.level_maintainer.current") + " "
                + NumberFormat.getNumberInstance()
                    .format(value));
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            lines.add(I18n.format(key + ".hint"));
        } else {
            lines.add(I18n.format("ae2fc.tooltip.shift_for_more"));
        }
        return lines;
    }

    private List<String> getMaintainerStateTooltip(int stateOrdinal) {
        LevelState[] states = LevelState.values();
        LevelState state = stateOrdinal >= 0 && stateOrdinal < states.length ? states[stateOrdinal] : LevelState.None;
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("ae2fc.tooltip.level_maintainer.title"));
        lines.add(
            I18n.format("ae2fc.tooltip.level_maintainer.current") + " "
                + I18n.format(getMaintainerStateNameKey(state)));
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            lines.add("");
            addMaintainerStateDescription(lines, LevelState.Idle, "ae2fc.tooltip.level_maintainer.idle_desc");
            addMaintainerStateDescription(lines, LevelState.Craft, "ae2fc.tooltip.level_maintainer.link_desc");
            addMaintainerStateDescription(lines, LevelState.Export, "ae2fc.tooltip.level_maintainer.export_desc");
            addMaintainerStateDescription(lines, LevelState.Error, "ae2fc.tooltip.level_maintainer.error_desc");
            addMaintainerStateDescription(lines, LevelState.NotFound, "ae2fc.tooltip.level_maintainer.not_found_desc");
            addMaintainerStateDescription(
                lines,
                LevelState.CantCraft,
                "ae2fc.tooltip.level_maintainer.cant_craft_desc");
        } else {
            lines.add("");
            lines.add(I18n.format("ae2fc.tooltip.shift_for_more"));
        }
        return lines;
    }

    private String getMaintainerStateNameKey(LevelState state) {
        return switch (state) {
            case Idle -> "ae2fc.tooltip.level_maintainer.idle";
            case Craft -> "ae2fc.tooltip.level_maintainer.link";
            case Export -> "ae2fc.tooltip.level_maintainer.export";
            case Error -> "ae2fc.tooltip.level_maintainer.error";
            case NotFound -> "ae2fc.tooltip.level_maintainer.not_found";
            case CantCraft -> "ae2fc.tooltip.level_maintainer.cant_craft";
            default -> "ae2fc.tooltip.level_maintainer.none";
        };
    }

    private void addMaintainerStateDescription(List<String> lines, LevelState state, String descriptionKey) {
        lines.add(I18n.format(getMaintainerStateNameKey(state)));
        lines.add(I18n.format(descriptionKey));
    }

    private int getMaintainerStateColor(int stateOrdinal) {
        LevelState[] states = LevelState.values();
        LevelState state = stateOrdinal >= 0 && stateOrdinal < states.length ? states[stateOrdinal] : LevelState.None;
        return switch (state) {
            case Idle -> 0xFF55FF55;
            case Craft -> 0xFFFFFF55;
            case Export -> 0xFFAA00AA;
            case Error, NotFound, CantCraft -> 0xFFFF5555;
            default -> GuiConstants.COLOR_SEPARATOR;
        };
    }

    private static String amountEditKey(RequestAreaDeviceInfo device, int slot, String id) {
        return device.getId() + ":" + slot + ":" + id;
    }

    private AmountEditState getAmountEditState(String key, RequestAreaDeviceInfo device, int slot,
        RequestAreaAction action, long value, int x, int y, int width) {
        AmountEditState state = amountEdits.get(key);
        if (state == null) {
            state = new AmountEditState(device, slot, action, value, x, y, width);
            amountEdits.put(key, state);
        } else {
            state.field.x = x;
            state.field.y = y;
            state.field.w = width;
            if (state.expectedValue != null && state.expectedValue == value) {
                state.expectedValue = null;
                state.currentValue = value;
                if (!state.field.isFocused()) state.field.setText(Long.toString(value));
            } else if (state.expectedValue == null && !state.field.isFocused() && state.currentValue != value) {
                state.currentValue = value;
                state.field.setText(Long.toString(value));
            }
        }
        return state;
    }

    private void focusAmount(RequestAreaDeviceInfo device, RequestAreaSlotInfo slot, String id,
        RequestAreaAction action, long value, int x, int y, int width, int mouseX, int mouseY, int mouseButton) {
        String key = amountEditKey(device, slot.getIndex(), id);
        AmountEditState state = getAmountEditState(key, device, slot.getIndex(), action, value, x, y, width);
        if (focusedAmountKey != null && !focusedAmountKey.equals(key)) {
            AmountEditState previous = amountEdits.get(focusedAmountKey);
            if (previous != null) {
                commitAmount(previous);
                previous.field.setFocused(false);
            }
        }
        focusedAmountKey = key;
        state.field.setFocused(true);
        state.field.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void commitAmount(AmountEditState state) {
        try {
            long value = Math.max(0, Long.parseLong(state.field.getText()));
            state.currentValue = value;
            state.expectedValue = value;
            send(state.device, state.slot, state.action, value, null);
        } catch (NumberFormatException exception) {
            state.field.setText(Long.toString(state.currentValue));
        }
    }

    private class RequestAreaSlotLine extends AbstractLine {

        private final RequestAreaDeviceInfo device;
        private final RequestAreaSlotInfo slot;
        private final int slotIndex;
        private final SmallIconButton enabledButton;
        private final SmallIconButton invertedButton;
        private final GuiImgButton enabledTooltipButton;
        private final GuiImgButton invertedTooltipButton;
        private final int enabledX;
        private final int targetWidth;
        private final int amountYOffset;
        private final int enabledYOffset;

        private RequestAreaSlotLine(SlotRow row, int y) {
            super(0, y, GuiConstants.CONTENT_RIGHT_EDGE);
            this.device = row.device;
            this.slot = row.slot;
            this.slotIndex = row.index;
            this.enabledX = slot.canInvert() ? INVERTED_X - GuiConstants.SMALL_BUTTON_SIZE - 3 : INVERTED_X;
            int amountRight = slot.canToggle() ? enabledX - 2
                : slot.canInvert() ? INVERTED_X - 2 : STATUS_BAR_RIGHT_EDGE;
            this.targetWidth = slot.canEditBatch() ? AMOUNT_FIELD_WIDTH : amountRight - TARGET_X;
            this.enabledTooltipButton = "advanced_level_emitter".equals(device.getTypeId())
                ? createNativeButton(
                    Settings.ADVANCED_LEVEL_EMITTER_SLOT_ENABLED,
                    slot.isEnabled() ? YesNo.YES : YesNo.NO)
                : null;
            this.invertedTooltipButton = "advanced_level_emitter".equals(device.getTypeId())
                ? createNativeButton(
                    Settings.ADVANCED_LEVEL_EMITTER_SLOT_INVERT,
                    slot.isInverted() ? RedstoneMode.LOW_SIGNAL : RedstoneMode.HIGH_SIGNAL)
                : null;
            boolean isMaintainer = "level_maintainer".equals(device.getTypeId());
            this.amountYOffset = isMaintainer ? AMOUNT_FIELD_Y_OFFSET - 3 : AMOUNT_FIELD_Y_OFFSET;
            this.enabledYOffset = isMaintainer ? 2 : 5;
            if (isMaintainer) {
                setRowHeight(MAINTAINER_SLOT_ROW_HEIGHT);
            }
            this.enabledButton = slot.canToggle()
                ? new SmallIconButton(
                    enabledX,
                    y + enabledYOffset,
                    isMaintainer ? () -> drawTextureIcon(MAINTAINER_BUTTON_TEXTURE, slot.isEnabled() ? 85 : 0, 171, 85)
                        : nativeIconRenderer(enabledTooltipButton),
                    () -> isMaintainer
                        ? List.of(I18n.format(slot.isEnabled() ? "ae2fc.tooltip.enable" : "ae2fc.tooltip.disable"))
                        : List.of(
                            I18n.format("gui.disk_terminal.request_area.tooltip.enabled"),
                            I18n.format(
                                slot.isEnabled() ? "gui.disk_terminal.request_area.slot.enabled"
                                    : "gui.disk_terminal.request_area.slot.disabled")),
                    slot.isEnabled() ? 0xFF48A848 : 0xFF9A4D4D,
                    () -> send(device, slotIndex, RequestAreaAction.TOGGLE_ENABLED, 0, null))
                : null;
            this.invertedButton = slot.canInvert()
                ? new SmallIconButton(
                    INVERTED_X,
                    y + 5,
                    nativeIconRenderer(invertedTooltipButton),
                    () -> List.of(
                        I18n.format("gui.disk_terminal.request_area.tooltip.invert"),
                        I18n.format(
                            slot.isInverted() ? "gui.disk_terminal.request_area.slot.inverted"
                                : "gui.disk_terminal.request_area.slot.normal")),
                    0xFF858585,
                    () -> send(device, slotIndex, RequestAreaAction.TOGGLE_INVERTED, 0, null))
                : null;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            if (!visible) return;
            boolean hovered = isHovered(mouseX, mouseY);
            if (hovered) {
                Gui.drawRect(
                    GuiConstants.GUI_INDENT,
                    y,
                    GuiConstants.CONTENT_RIGHT_EDGE,
                    y + height,
                    GuiConstants.COLOR_STORAGE_HEADER_HOVER);
            }
            drawTreeLines(mouseX, mouseY);
            drawMaterial();
            drawAmount(
                slot.getTarget(),
                slot.canEditTarget(),
                TARGET_X,
                targetWidth,
                "target",
                RequestAreaAction.SET_TARGET);
            drawAmount(
                slot.getBatch(),
                slot.canEditBatch(),
                BATCH_X,
                AMOUNT_FIELD_WIDTH,
                "batch",
                RequestAreaAction.SET_BATCH);
            drawEnabled(mouseX, mouseY);
            drawInverted(mouseX, mouseY);
            if ("level_maintainer".equals(device.getTypeId()) && !ItemStacks.isEmpty(slot.getMaterial())) {
                Gui.drawRect(
                    TARGET_X,
                    y + STATUS_BAR_Y_OFFSET,
                    STATUS_BAR_RIGHT_EDGE,
                    y + STATUS_BAR_Y_OFFSET + STATUS_BAR_HEIGHT,
                    getMaintainerStateColor(slot.getState()));
            }
        }

        private void drawMaterial() {
            GuiConstants.drawAtlasSprite(
                MATERIAL_X,
                y,
                GuiConstants.MINI_SLOT_X,
                GuiConstants.MINI_SLOT_Y,
                GuiConstants.MINI_SLOT_SIZE);
            if (!ItemStacks.isEmpty(slot.getMaterial())) {
                AbstractWidget.renderItemStack(itemRender, slot.getMaterial(), MATERIAL_X + 1, y + 1);
            } else {
                fontRenderer.drawString("+", MATERIAL_X + 6, y + 5, 0xFF888888);
            }
        }

        private void drawAmount(long value, boolean editable, int amountX, int width, String editId,
            RequestAreaAction action) {
            if (!editable) return;
            String key = amountEditKey(device, slotIndex, editId);
            int fieldY = y + amountYOffset;
            GuiConstants
                .drawHorizontalSlicedTerminalSprite(amountX, fieldY, 131, 38, 71, 12, width, AMOUNT_FIELD_HEIGHT, 4);
            AmountEditState state = getAmountEditState(key, device, slotIndex, action, value, amountX, fieldY, width);
            state.field.drawTextBox();
        }

        private void drawEnabled(int mouseX, int mouseY) {
            if (enabledButton == null) return;
            enabledButton.setPosition(enabledX, y + enabledYOffset);
            enabledButton.draw(mouseX, mouseY);
        }

        private void drawInverted(int mouseX, int mouseY) {
            if (invertedButton == null) return;
            invertedButton.setPosition(INVERTED_X, y + 5);
            invertedButton.draw(mouseX, mouseY);
        }

        @Override
        public boolean handleClick(int mouseX, int mouseY, int button) {
            if (!visible || !isHovered(mouseX, mouseY)) return false;
            if (super.handleClick(mouseX, mouseY, button)) return true;

            if (isOver(mouseX, mouseY, MATERIAL_X, y, GuiConstants.MINI_SLOT_SIZE, GuiConstants.MINI_SLOT_SIZE)) {
                ItemStack heldStack = guiContext.getHeldStack();
                if ((button == 0 || button == 1) && ItemStacks.isEmpty(heldStack)) {
                    send(device, slotIndex, RequestAreaAction.CLEAR_MATERIAL, 0, null);
                } else if ((button == 0 || button == 1) && !ItemStacks.isEmpty(heldStack)) {
                    NBTTagCompound material = AEStackUtil
                        .writeItemLikePartitionStack(heldStack, AEBaseGui.isCtrlKeyDown());
                    if (material != null) send(device, slotIndex, RequestAreaAction.SET_MATERIAL, 0, material);
                }
                return true;
            }

            if (button == 0) {
                if (slot.canEditTarget()
                    && isOver(mouseX, mouseY, TARGET_X, y + amountYOffset, targetWidth, AMOUNT_FIELD_HEIGHT)) {
                    focusAmount(
                        device,
                        slot,
                        "target",
                        RequestAreaAction.SET_TARGET,
                        slot.getTarget(),
                        TARGET_X,
                        y + amountYOffset,
                        targetWidth,
                        mouseX,
                        mouseY,
                        button);
                    return true;
                }
                if (slot.canEditBatch()
                    && isOver(mouseX, mouseY, BATCH_X, y + amountYOffset, AMOUNT_FIELD_WIDTH, AMOUNT_FIELD_HEIGHT)) {
                    focusAmount(
                        device,
                        slot,
                        "batch",
                        RequestAreaAction.SET_BATCH,
                        slot.getBatch(),
                        BATCH_X,
                        y + amountYOffset,
                        AMOUNT_FIELD_WIDTH,
                        mouseX,
                        mouseY,
                        button);
                    return true;
                }
            }

            if (button == 0 && slot.canToggle()
                && isOver(
                    mouseX,
                    mouseY,
                    enabledX,
                    y + enabledYOffset,
                    GuiConstants.SMALL_BUTTON_SIZE,
                    GuiConstants.SMALL_BUTTON_SIZE)) {
                enabledButton.handleClick(mouseX, mouseY, button);
                return true;
            }
            if (button == 0 && slot.canInvert()
                && isOver(
                    mouseX,
                    mouseY,
                    INVERTED_X,
                    y + 5,
                    GuiConstants.SMALL_BUTTON_SIZE,
                    GuiConstants.SMALL_BUTTON_SIZE)) {
                invertedButton.handleClick(mouseX, mouseY, button);
                return true;
            }
            return true;
        }

        @Override
        public List<String> getTooltip(int mouseX, int mouseY) {
            if (!isHovered(mouseX, mouseY)) return Collections.emptyList();
            if (isOver(mouseX, mouseY, MATERIAL_X, y, GuiConstants.MINI_SLOT_SIZE, GuiConstants.MINI_SLOT_SIZE)) {
                List<String> tooltip = new ArrayList<>();
                if (!ItemStacks.isEmpty(slot.getMaterial())) {
                    tooltip.addAll(
                        slot.getMaterial()
                            .getTooltip(
                                Minecraft.getMinecraft().thePlayer,
                                Minecraft.getMinecraft().gameSettings.advancedItemTooltips));
                } else {
                    tooltip.add(I18n.format("gui.disk_terminal.request_area.material.empty"));
                }
                IAEStack<?> containerContent = AEConfig.instance.showContainerInteractionTooltips
                    ? getContainerContent(guiContext.getHeldStack())
                    : null;
                if (containerContent != null) {
                    tooltip.add("");
                    tooltip.add(
                        ButtonToolTips.RegisterContainerContent.getLocal(
                            GameSettings.getKeyDisplayString(ServerHelper.CONTAINER_INTERACTION_KEY.getKeyCode()),
                            containerContent.getDisplayName()));
                }
                return tooltip;
            }
            if (slot.canEditTarget()
                && isOver(mouseX, mouseY, TARGET_X, y + amountYOffset, targetWidth, AMOUNT_FIELD_HEIGHT)) {
                if ("level_maintainer".equals(device.getTypeId())) {
                    return getMaintainerAmountTooltip(false, slot.getTarget());
                }
                return Collections.emptyList();
            }
            if (slot.canEditBatch()
                && isOver(mouseX, mouseY, BATCH_X, y + amountYOffset, AMOUNT_FIELD_WIDTH, AMOUNT_FIELD_HEIGHT)) {
                if ("level_maintainer".equals(device.getTypeId())) {
                    return getMaintainerAmountTooltip(true, slot.getBatch());
                }
                return Collections.emptyList();
            }
            if ("level_maintainer".equals(device.getTypeId()) && !ItemStacks.isEmpty(slot.getMaterial())
                && isOver(
                    mouseX,
                    mouseY,
                    TARGET_X,
                    y + STATUS_BAR_Y_OFFSET,
                    STATUS_BAR_RIGHT_EDGE - TARGET_X,
                    STATUS_BAR_HEIGHT)) {
                return getMaintainerStateTooltip(slot.getState());
            }
            if (enabledButton != null && isOver(
                mouseX,
                mouseY,
                enabledX,
                y + enabledYOffset,
                GuiConstants.SMALL_BUTTON_SIZE,
                GuiConstants.SMALL_BUTTON_SIZE)) {
                return enabledTooltipButton != null ? getNativeTooltip(enabledTooltipButton)
                    : enabledButton.getTooltip(mouseX, mouseY);
            }
            if (invertedTooltipButton != null && isOver(
                mouseX,
                mouseY,
                INVERTED_X,
                y + 5,
                GuiConstants.SMALL_BUTTON_SIZE,
                GuiConstants.SMALL_BUTTON_SIZE)) {
                return getNativeTooltip(invertedTooltipButton);
            }
            return Collections.emptyList();
        }

        @Override
        public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
            if (isOver(mouseX, mouseY, MATERIAL_X, y, GuiConstants.MINI_SLOT_SIZE, GuiConstants.MINI_SLOT_SIZE)) {
                return slot.getMaterial();
            }
            return null;
        }

        private IAEStack<?> getContainerContent(ItemStack stack) {
            if (ItemStacks.isEmpty(stack)) return null;

            ItemStack single = stack.copy();
            single.stackSize = 1;
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                if (!type.isContainerItemForType(single)) continue;
                IAEStack<?> content = type.getStackFromContainerItem(single);
                if (content != null && content.getStackSize() > 0) return content;
            }
            return null;
        }
    }

    private static class SlotRow {

        private final RequestAreaDeviceInfo device;
        private final RequestAreaSlotInfo slot;
        private final int index;

        private SlotRow(RequestAreaDeviceInfo device, RequestAreaSlotInfo slot, int index) {
            this.device = device;
            this.slot = slot;
            this.index = index;
        }
    }

    private static class ControlRow {

        private final RequestAreaDeviceInfo device;
        private final RequestAreaControlInfo control;

        private ControlRow(RequestAreaDeviceInfo device, RequestAreaControlInfo control) {
            this.device = device;
            this.control = control;
        }
    }

    private static class AmountEditState {

        private final RequestAreaDeviceInfo device;
        private final int slot;
        private final RequestAreaAction action;
        private final MEGuiTextField field;
        private long currentValue;
        private Long expectedValue;

        private AmountEditState(RequestAreaDeviceInfo device, int slot, RequestAreaAction action, long value, int x,
            int y, int width) {
            this.device = device;
            this.slot = slot;
            this.action = action;
            this.currentValue = value;
            this.field = new MEGuiTextField(width, AMOUNT_FIELD_HEIGHT);
            this.field.x = x;
            this.field.y = y;
            this.field.setMaxStringLength(19);
            this.field.setText(Long.toString(value));
        }
    }
}
