package io.canvasmc.canvas;

import org.jetbrains.annotations.NotNull;

/**
 * Manages server branding configuration and applies it to the server instance
 */
public class BrandingManager {
    private static String customModName;
    private static String customGUIName;

    /**
     * Initialize branding from config
     */
    public static void init(@NotNull Config config) {
        if (config.serverBrand != null) {
            customModName = config.serverBrand.serverModName;
            customGUIName = config.serverBrand.serverGUIName;
            
            Config.LOGGER.info("Server branding initialized: modName='{}', guiName='{}'", customModName, customGUIName);
        }
    }

    /**
     * Get the custom mod name, or default if not set
     */
    public static String getServerModName() {
        if (customModName != null && !customModName.isEmpty()) {
            return customModName;
        }
        return io.papermc.paper.ServerBuildInfo.buildInfo().brandName();
    }

    /**
     * Get the custom GUI name, or default if not set
     */
    public static String getServerGUIName() {
        if (customGUIName != null && !customGUIName.isEmpty()) {
            return customGUIName;
        }
        return "Canvas";
    }

    /**
     * Update branding at runtime
     */
    public static void updateBranding(@NotNull String modName, @NotNull String guiName) {
        customModName = modName;
        customGUIName = guiName;
        Config.LOGGER.info("Server branding updated: modName='{}', guiName='{}'", modName, guiName);
    }
}

