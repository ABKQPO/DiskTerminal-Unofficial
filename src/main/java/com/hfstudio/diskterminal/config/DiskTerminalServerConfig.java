package com.hfstudio.diskterminal.config;

import java.io.File;
import java.util.Set;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.hfstudio.diskterminal.gui.GuiConstants;

/**
 * Server-side configuration for Disk Terminal features.
 * Controls tab availability, storage bus polling, cell operations, and other server-enforced settings.
 * <p>
 * These settings affect all players connecting to the server.
 * On single-player, they are still enforced as if it were a server.
 */
public class DiskTerminalServerConfig {

    private static final String CONFIG_FILE = "disk_terminal_server.cfg";

    private static final String CATEGORY_TABS = "tabs";
    private static final String CATEGORY_POLLING = "polling";
    private static final String CATEGORY_CELL_OPERATIONS = "cell_operations";
    private static final String CATEGORY_MISC = "misc";
    private static final String CATEGORY_INTEGRATION = "integration";
    private static final String CATEGORY_NETWORK = "network";

    private static DiskTerminalServerConfig instance;

    private final Configuration config;

    // Tab settings
    private final Property tabTerminalEnabledProperty;
    private final Property tabInventoryEnabledProperty;
    private final Property tabPartitionEnabledProperty;
    private final Property tabTempAreaEnabledProperty;
    private final Property tabStorageBusInventoryEnabledProperty;
    private final Property tabStorageBusPartitionEnabledProperty;
    private final Property tabNetworkToolsEnabledProperty;

    private boolean tabTerminalEnabled = true;
    private boolean tabInventoryEnabled = true;
    private boolean tabPartitionEnabled = true;
    private boolean tabTempAreaEnabled = true;
    private boolean tabStorageBusInventoryEnabled = true;
    private boolean tabStorageBusPartitionEnabled = true;
    private boolean tabNetworkToolsEnabled = true;

    // Polling settings
    private final Property storageBusPollingEnabledProperty;
    private final Property pollingIntervalProperty;

    private boolean storageBusPollingEnabled = false; // Disabled by default for performance
    private int pollingInterval = 20; // Ticks (20 = 1 second)

    // Cell operation settings
    private final Property cellEjectEnabledProperty;
    private final Property cellInsertEnabledProperty;
    private final Property cellSwapEnabledProperty;

    private boolean cellEjectEnabled = true;
    private boolean cellInsertEnabled = true;
    private boolean cellSwapEnabled = true;

    // Misc settings
    private final Property partitionEditEnabledProperty;
    private final Property priorityEditEnabledProperty;
    private final Property upgradeInsertEnabledProperty;
    private final Property upgradeExtractEnabledProperty;

    private boolean partitionEditEnabled = true;
    private boolean priorityEditEnabled = true;
    private boolean upgradeInsertEnabled = true;
    private boolean upgradeExtractEnabled = true;

    // Integration settings
    private final Property integrationThaumicEnergisticsProperty;
    private final Property integrationAE2FCProperty;
    private final Property integrationGregTechProperty;
    private final Property integrationNeiProperty;

    private boolean integrationThaumicEnergisticsEnabled = true;
    private boolean integrationAE2FCEnabled = true;
    private boolean integrationGregTechEnabled = true;
    private boolean integrationNeiEnabled = true;

    // Network / data-streaming settings
    private final Property maxChunkBytesProperty;
    private final Property minRefreshIntervalTicksProperty;
    private final Property enableDeltaUpdatesProperty;

    // Default 512 KiB: stays well under vanilla's S2C custom-payload cap of 1 MiB
    // even after framing/varint overhead and the wrapping FMLProxyPacket header.
    private int maxChunkBytes = 524288;
    private int minRefreshIntervalTicks = 10;
    private boolean enableDeltaUpdates = true;

    private DiskTerminalServerConfig(File configDir) {
        File configFile = new File(configDir, CONFIG_FILE);
        this.config = new Configuration(configFile);

        // Tab settings
        config.setCategoryComment(
            CATEGORY_TABS,
            "Enable or disable individual tabs in the Disk Terminal GUI.\n"
                + "Disabled tabs will appear grayed out and show a message in the tooltip.");
        config.setCategoryLanguageKey(CATEGORY_TABS, "config.disk_terminal.config.server.tabs");

        this.tabTerminalEnabledProperty = config
            .get(CATEGORY_TABS, "terminalTabEnabled", true, "Enable the Terminal tab (overview of all cells)");
        this.tabTerminalEnabledProperty.setLanguageKey("config.disk_terminal.config.server.tabs.terminal");
        this.tabTerminalEnabled = this.tabTerminalEnabledProperty.getBoolean();

        this.tabInventoryEnabledProperty = config
            .get(CATEGORY_TABS, "inventoryTabEnabled", true, "Enable the Inventory tab (view cell contents)");
        this.tabInventoryEnabledProperty.setLanguageKey("config.disk_terminal.config.server.tabs.inventory");
        this.tabInventoryEnabled = this.tabInventoryEnabledProperty.getBoolean();

        this.tabPartitionEnabledProperty = config
            .get(CATEGORY_TABS, "partitionTabEnabled", true, "Enable the Partition tab (edit cell filters)");
        this.tabPartitionEnabledProperty.setLanguageKey("config.disk_terminal.config.server.tabs.partition");
        this.tabPartitionEnabled = this.tabPartitionEnabledProperty.getBoolean();

        this.tabTempAreaEnabledProperty = config.get(
            CATEGORY_TABS,
            "tempAreaTabEnabled",
            true,
            "Enable the Temp Area tab (temporary cell staging area for partitioning)");
        this.tabTempAreaEnabledProperty.setLanguageKey("config.disk_terminal.config.server.tabs.temp_area");
        this.tabTempAreaEnabled = this.tabTempAreaEnabledProperty.getBoolean();

        this.tabStorageBusInventoryEnabledProperty = config.get(
            CATEGORY_TABS,
            "storageBusInventoryTabEnabled",
            true,
            "Enable the Storage Bus Inventory tab (view storage bus contents)");
        this.tabStorageBusInventoryEnabledProperty
            .setLanguageKey("config.disk_terminal.config.server.tabs.storage_bus_inventory");
        this.tabStorageBusInventoryEnabled = this.tabStorageBusInventoryEnabledProperty.getBoolean();

        this.tabStorageBusPartitionEnabledProperty = config.get(
            CATEGORY_TABS,
            "storageBusPartitionTabEnabled",
            true,
            "Enable the Storage Bus Partition tab (edit storage bus filters)");
        this.tabStorageBusPartitionEnabledProperty
            .setLanguageKey("config.disk_terminal.config.server.tabs.storage_bus_partition");
        this.tabStorageBusPartitionEnabled = this.tabStorageBusPartitionEnabledProperty.getBoolean();

        this.tabNetworkToolsEnabledProperty = config.get(
            CATEGORY_TABS,
            "networkToolsTabEnabled",
            true,
            "Enable the Network Tools tab (mass operations on cells and storage buses)");
        this.tabNetworkToolsEnabledProperty.setLanguageKey("config.disk_terminal.config.server.tabs.network_tools");
        this.tabNetworkToolsEnabled = this.tabNetworkToolsEnabledProperty.getBoolean();

        // Polling settings
        config.setCategoryComment(
            CATEGORY_POLLING,
            "Storage bus polling settings.\n" + "WARNING: Storage bus polling can be expensive on large networks!\n"
                + "It requires iterating through all storage buses and their inventories.\n"
                + "Consider keeping polling disabled and reopening the terminal to refresh.");
        config.setCategoryLanguageKey(CATEGORY_POLLING, "config.disk_terminal.config.server.polling");

        this.storageBusPollingEnabledProperty = config.get(
            CATEGORY_POLLING,
            "storageBusPollingEnabled",
            false,
            "Enable automatic polling of storage bus data while on storage bus tabs.\n"
                + "WARNING: This can impact server performance on large networks!\n"
                + "When disabled, storage bus data is only fetched once per terminal session.\n"
                + "Reopen the terminal to manually refresh.");
        this.storageBusPollingEnabledProperty.setLanguageKey("config.disk_terminal.config.server.polling.enabled");
        this.storageBusPollingEnabled = this.storageBusPollingEnabledProperty.getBoolean();

        this.pollingIntervalProperty = config.get(
            CATEGORY_POLLING,
            "pollingInterval",
            20,
            "How often to poll for storage bus updates, in ticks (20 ticks = 1 second).\n"
                + "Higher values reduce server load but make data less responsive.\n"
                + "Only applies when storage bus polling is enabled.",
            1,
            1200);
        this.pollingIntervalProperty.setLanguageKey("config.disk_terminal.config.server.polling.interval");
        this.pollingInterval = this.pollingIntervalProperty.getInt();

        // Cell operation settings
        config.setCategoryComment(
            CATEGORY_CELL_OPERATIONS,
            "Cell operation permissions.\n"
                + "These settings control whether cells can be inserted/ejected from drives using the Disk Terminal GUI.\n"
                + "Disabling these forces players to manage cells directly at the drives/chests instead.");
        config.setCategoryLanguageKey(CATEGORY_CELL_OPERATIONS, "config.disk_terminal.config.server.cell_ops");

        this.cellEjectEnabledProperty = config.get(
            CATEGORY_CELL_OPERATIONS,
            "cellEjectEnabled",
            true,
            "Allow ejecting/picking up cells from drives/chests through the Disk Terminal.\n"
                + "Affects the eject button and clicking cells in Inventory/Partition tabs.");
        this.cellEjectEnabledProperty.setLanguageKey("config.disk_terminal.config.server.cell_ops.eject");
        this.cellEjectEnabled = this.cellEjectEnabledProperty.getBoolean();

        this.cellInsertEnabledProperty = config.get(
            CATEGORY_CELL_OPERATIONS,
            "cellInsertEnabled",
            true,
            "Allow inserting cells into drives/chests through the Disk Terminal.\n"
                + "When disabled, clicking empty cell slots with a cell in hand will do nothing.");
        this.cellInsertEnabledProperty.setLanguageKey("config.disk_terminal.config.server.cell_ops.insert");
        this.cellInsertEnabled = this.cellInsertEnabledProperty.getBoolean();

        this.cellSwapEnabledProperty = config.get(
            CATEGORY_CELL_OPERATIONS,
            "cellSwapEnabled",
            true,
            "Allow swapping cells between drives/chests through the Disk Terminal.\n"
                + "When disabled, clicking a cell slot with a cell in hand will not swap.\n"
                + "Requires both eject and insert to be enabled to function.");
        this.cellSwapEnabledProperty.setLanguageKey("config.disk_terminal.config.server.cell_ops.swap");
        this.cellSwapEnabled = this.cellSwapEnabledProperty.getBoolean();

        // Misc settings
        config.setCategoryComment(CATEGORY_MISC, "Miscellaneous settings for various Disk Terminal features.");
        config.setCategoryLanguageKey(CATEGORY_MISC, "config.disk_terminal.config.server.misc");

        this.partitionEditEnabledProperty = config.get(
            CATEGORY_MISC,
            "partitionEditEnabled",
            true,
            "Allow editing cell partitions through the Disk Terminal.\n"
                + "When disabled, all partition modification features are blocked.");
        this.partitionEditEnabledProperty.setLanguageKey("config.disk_terminal.config.server.misc.partition_edit");
        this.partitionEditEnabled = this.partitionEditEnabledProperty.getBoolean();

        this.priorityEditEnabledProperty = config.get(
            CATEGORY_MISC,
            "priorityEditEnabled",
            true,
            "Allow editing drive/storage bus priorities through the Disk Terminal.\n"
                + "When disabled, the priority field will be read-only.");
        this.priorityEditEnabledProperty.setLanguageKey("config.disk_terminal.config.server.misc.priority_edit");
        this.priorityEditEnabled = this.priorityEditEnabledProperty.getBoolean();

        this.upgradeInsertEnabledProperty = config.get(
            CATEGORY_MISC,
            "upgradeInsertEnabled",
            true,
            "Allow inserting upgrades into cells and storage buses through the Disk Terminal.\n"
                + "When disabled, clicking entries while holding upgrades will do nothing.");
        this.upgradeInsertEnabledProperty.setLanguageKey("config.disk_terminal.config.server.misc.upgrade_insert");
        this.upgradeInsertEnabled = this.upgradeInsertEnabledProperty.getBoolean();

        this.upgradeExtractEnabledProperty = config.get(
            CATEGORY_MISC,
            "upgradeExtractEnabled",
            true,
            "Allow extracting upgrades from cells and storage buses through the Disk Terminal.\n"
                + "When disabled, clicking upgrade icons will do nothing.");
        this.upgradeExtractEnabledProperty.setLanguageKey("config.disk_terminal.config.server.misc.upgrade_extract");
        this.upgradeExtractEnabled = this.upgradeExtractEnabledProperty.getBoolean();

        // Integration settings
        config.setCategoryComment(CATEGORY_INTEGRATION, "Settings for integration with other mods.");
        config.setCategoryLanguageKey(CATEGORY_INTEGRATION, "config.disk_terminal.config.server.integration");

        this.integrationThaumicEnergisticsProperty = config.get(
            CATEGORY_INTEGRATION,
            "enableThaumicEnergistics",
            true,
            "Enable integration with Thaumic Energistics (essentia cells).\n"
                + "Set to false to disable Thaumic Energistics-specific code.");
        this.integrationThaumicEnergisticsProperty
            .setLanguageKey("config.disk_terminal.config.server.integration.enable_thaumicenergistics");
        this.integrationThaumicEnergisticsEnabled = this.integrationThaumicEnergisticsProperty.getBoolean();

        this.integrationAE2FCProperty = config.get(
            CATEGORY_INTEGRATION,
            "enableAE2FluidCraft",
            true,
            "Enable integration with AE2 Fluid Craft (fluid cells and fluid storage buses).\n"
                + "Set to false to disable AE2FC-specific code.");
        this.integrationAE2FCProperty.setLanguageKey("config.disk_terminal.config.server.integration.enable_ae2fc");
        this.integrationAE2FCEnabled = this.integrationAE2FCProperty.getBoolean();

        this.integrationGregTechProperty = config.get(
            CATEGORY_INTEGRATION,
            "enableGregTech",
            true,
            "Enable integration with GregTech 5 (ME hatches and digital chests).\n"
                + "Set to false to disable GregTech-specific code.");
        this.integrationGregTechProperty
            .setLanguageKey("config.disk_terminal.config.server.integration.enable_gregtech");
        this.integrationGregTechEnabled = this.integrationGregTechProperty.getBoolean();

        this.integrationNeiProperty = config.get(
            CATEGORY_INTEGRATION,
            "enableNEI",
            true,
            "Enable NEI integration (ghost ingredients, quick partition).\n"
                + "Set to false to disable NEI integration.");
        this.integrationNeiProperty.setLanguageKey("config.disk_terminal.config.server.integration.enable_nei");
        this.integrationNeiEnabled = this.integrationNeiProperty.getBoolean();

        // Network / data-streaming settings
        config.setCategoryComment(
            CATEGORY_NETWORK,
            "Settings controlling how the Disk Terminal streams data from server to client.");
        config.setCategoryLanguageKey(CATEGORY_NETWORK, "config.disk_terminal.config.server.network");

        this.maxChunkBytesProperty = config.get(
            CATEGORY_NETWORK,
            "maxChunkBytes",
            524288,
            "Maximum payload size (in bytes) per network chunk packet.\n"
                + "Larger values mean fewer round-trips but risk exceeding the engine's packet limit.\n"
                + "Default 524288 (512 KiB) stays safely under vanilla's ~1 MiB hardcoded S2C cap.\n"
                + "Lower this if you observe disconnects with messages like 'Payload may not be larger than'.\n"
                + "Range: 4096 (4 KiB) - 10485760 (10 MiB).",
            4096,
            10485760);
        this.maxChunkBytesProperty.setLanguageKey("config.disk_terminal.config.server.network.max_chunk_bytes");
        this.maxChunkBytes = this.maxChunkBytesProperty.getInt();

        this.minRefreshIntervalTicksProperty = config.get(
            CATEGORY_NETWORK,
            "minRefreshIntervalTicks",
            10,
            "Minimum number of ticks between full data refreshes (20 ticks = 1 second).\n"
                + "Throttles regen of storages/buses/subnets when many trigger events fire in quick succession.\n"
                + "Lower for snappier updates, higher to reduce server load on large networks.\n"
                + "Range: 1 - 200.",
            1,
            200);
        this.minRefreshIntervalTicksProperty
            .setLanguageKey("config.disk_terminal.config.server.network.min_refresh_interval_ticks");
        this.minRefreshIntervalTicks = this.minRefreshIntervalTicksProperty.getInt();

        this.enableDeltaUpdatesProperty = config.get(
            CATEGORY_NETWORK,
            "enableDeltaUpdates",
            true,
            "Enable delta updates: after the first full payload, send only changed entries.\n"
                + "Greatly reduces bandwidth on large networks where most state is static between ticks.\n"
                + "Disable to always send full payloads (useful for debugging desync issues).");
        this.enableDeltaUpdatesProperty
            .setLanguageKey("config.disk_terminal.config.server.network.enable_delta_updates");
        this.enableDeltaUpdates = this.enableDeltaUpdatesProperty.getBoolean();

        if (config.hasChanged()) config.save();
    }

    public static void init(File configDir) {
        if (instance == null) instance = new DiskTerminalServerConfig(configDir);
    }

    public static DiskTerminalServerConfig getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DiskTerminalServerConfig not initialized! Call init() during preInit.");
        }

        return instance;
    }

    /**
     * Check if the instance has been initialized.
     * Useful for client-side checks where server config may not be available yet.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Sync values from the config file after GUI changes.
     * Call this after the config GUI closes or when ConfigChangedEvent fires.
     */
    public void syncFromConfig() {
        this.tabTerminalEnabled = this.tabTerminalEnabledProperty.getBoolean();
        this.tabInventoryEnabled = this.tabInventoryEnabledProperty.getBoolean();
        this.tabPartitionEnabled = this.tabPartitionEnabledProperty.getBoolean();
        this.tabTempAreaEnabled = this.tabTempAreaEnabledProperty.getBoolean();
        this.tabStorageBusInventoryEnabled = this.tabStorageBusInventoryEnabledProperty.getBoolean();
        this.tabStorageBusPartitionEnabled = this.tabStorageBusPartitionEnabledProperty.getBoolean();
        this.tabNetworkToolsEnabled = this.tabNetworkToolsEnabledProperty.getBoolean();

        this.storageBusPollingEnabled = this.storageBusPollingEnabledProperty.getBoolean();
        this.pollingInterval = this.pollingIntervalProperty.getInt();

        this.cellEjectEnabled = this.cellEjectEnabledProperty.getBoolean();
        this.cellInsertEnabled = this.cellInsertEnabledProperty.getBoolean();
        this.cellSwapEnabled = this.cellSwapEnabledProperty.getBoolean();

        this.partitionEditEnabled = this.partitionEditEnabledProperty.getBoolean();
        this.priorityEditEnabled = this.priorityEditEnabledProperty.getBoolean();
        this.upgradeInsertEnabled = this.upgradeInsertEnabledProperty.getBoolean();
        this.upgradeExtractEnabled = this.upgradeExtractEnabledProperty.getBoolean();

        this.integrationThaumicEnergisticsEnabled = this.integrationThaumicEnergisticsProperty.getBoolean();
        this.integrationAE2FCEnabled = this.integrationAE2FCProperty.getBoolean();
        this.integrationGregTechEnabled = this.integrationGregTechProperty.getBoolean();
        this.integrationNeiEnabled = this.integrationNeiProperty.getBoolean();

        this.maxChunkBytes = this.maxChunkBytesProperty.getInt();
        this.minRefreshIntervalTicks = this.minRefreshIntervalTicksProperty.getInt();
        this.enableDeltaUpdates = this.enableDeltaUpdatesProperty.getBoolean();

        if (config.hasChanged()) config.save();
    }

    /**
     * Save the config file.
     */
    public void save() {
        if (config.hasChanged()) config.save();
    }

    /**
     * Get the underlying Configuration object.
     * Useful for ConfigChangedEvent handling.
     */
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the path to the config file for display in the config GUI.
     */
    public String getConfigFilePath() {
        return config.getConfigFile()
            .getAbsolutePath();
    }

    /**
     * Get all category names for the config GUI.
     */
    public Set<String> getCategoryNames() {
        return config.getCategoryNames();
    }

    /**
     * Get a category by name for the config GUI.
     */
    public ConfigCategory getCategory(String name) {
        return config.getCategory(name);
    }

    public boolean isTabTempAreaEnabled() {
        return tabTempAreaEnabled;
    }

    /**
     * Check if a specific tab is enabled by its index.
     *
     * @param tabIndex The tab index (0-6)
     * @return true if the tab is enabled
     */
    public boolean isTabEnabled(int tabIndex) {
        return switch (tabIndex) {
            case GuiConstants.TAB_TERMINAL -> tabTerminalEnabled;
            case GuiConstants.TAB_INVENTORY -> tabInventoryEnabled;
            case GuiConstants.TAB_PARTITION -> tabPartitionEnabled;
            case GuiConstants.TAB_TEMP_AREA -> tabTempAreaEnabled;
            case GuiConstants.TAB_STORAGE_BUS_INVENTORY -> tabStorageBusInventoryEnabled;
            case GuiConstants.TAB_STORAGE_BUS_PARTITION -> tabStorageBusPartitionEnabled;
            case GuiConstants.TAB_NETWORK_TOOLS -> tabNetworkToolsEnabled;
            default -> false;
        };
    }

    public boolean isStorageBusPollingEnabled() {
        return storageBusPollingEnabled;
    }

    public int getPollingInterval() {
        return pollingInterval;
    }

    public boolean isCellEjectEnabled() {
        return cellEjectEnabled;
    }

    public boolean isCellInsertEnabled() {
        return cellInsertEnabled;
    }

    public boolean isCellSwapEnabled() {
        // Swap requires both eject and insert
        return cellSwapEnabled && cellEjectEnabled && cellInsertEnabled;
    }

    public boolean isPartitionEditEnabled() {
        return partitionEditEnabled;
    }

    public boolean isPriorityEditEnabled() {
        return priorityEditEnabled;
    }

    public boolean isUpgradeInsertEnabled() {
        return upgradeInsertEnabled;
    }

    public boolean isUpgradeExtractEnabled() {
        return upgradeExtractEnabled;
    }

    public boolean isIntegrationThaumicEnergisticsEnabled() {
        return integrationThaumicEnergisticsEnabled;
    }

    public boolean isIntegrationAE2FCEnabled() {
        return integrationAE2FCEnabled;
    }

    public boolean isIntegrationGregTechEnabled() {
        return integrationGregTechEnabled;
    }

    public boolean isIntegrationNeiEnabled() {
        return integrationNeiEnabled;
    }

    public int getMaxChunkBytes() {
        return maxChunkBytes;
    }

    public int getMinRefreshIntervalTicks() {
        return minRefreshIntervalTicks;
    }

    public boolean isDeltaUpdatesEnabled() {
        return enableDeltaUpdates;
    }
}
