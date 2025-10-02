package com.ssilensio.coreprotectfix;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point.
 */
public final class CoreProtectFixPlugin extends JavaPlugin {

    private static final String PREFIX = "[COFIX]";
    private static final String[] BANNER_LINES = {
            "",
            "\u001B[38;5;214m╔════════════════════════════════════════════════════╗\u001B[0m",
            "\u001B[38;5;214m║ \u001B[38;5;202m    ____                 ____            _          \u001B[38;5;214m║\u001B[0m",
            "\u001B[38;5;214m║ \u001B[38;5;202m   / ___| ___   ___ _ __|  _ \\ ___  __ _| |_ ___    \u001B[38;5;214m║\u001B[0m",
            "\u001B[38;5;214m║ \u001B[38;5;202m  | |    / _ \\ / _ \\ '__| |_) / _ \\/ _` | __/ _ \\   \u001B[38;5;214m║\u001B[0m",
            "\u001B[38;5;214m║ \u001B[38;5;202m  | |___| (_) |  __/ |  |  _ <  __/ (_| | ||  __/   \u001B[38;5;214m║\u001B[0m",
            "\u001B[38;5;214m║ \u001B[38;5;202m   \\____|\\___/ \\___|_|  |_| \\_\\\\___|\\__,_|\\__\\___|  \u001B[38;5;214m║\u001B[0m",
            "\u001B[38;5;214m║ \u001B[38;5;45m            CoreProtect Fix — ready to serve.       \u001B[38;5;214m║\u001B[0m",
            "\u001B[38;5;214m╚════════════════════════════════════════════════════╝\u001B[0m",
            ""
    };

    private HandledErrorLogger handledErrorLogger;

    @Override
    public void onEnable() {
        printBanner();

        saveDefaultConfig();
        handledErrorLogger = HandledErrorLogger.tryCreate(getDataFolder().toPath(), getLogger());
        if (handledErrorLogger == null) {
            logWarning("Handled error logging disabled — see previous warning for details.");
        }

        registerProfessionShimIfNeeded();
        registerChatBridgeIfNeeded();

        logInfo("Enabled.");
    }

    @Override
    public void onDisable() {
        logInfo("Disabled.");
        handledErrorLogger = null;
    }

    boolean debug() {
        return getConfig().getBoolean("debug", false);
    }

    void logHandledError(String source, Throwable error) {
        if (handledErrorLogger == null || error == null) {
            return;
        }

        handledErrorLogger.append(source, error);
    }

    void logInfo(String message) {
        getLogger().info(prefix(message));
    }

    void logWarning(String message) {
        getLogger().warning(prefix(message));
    }

    void logDebug(String message) {
        if (debug()) {
            getLogger().info(prefix("[DEBUG] " + message));
        }
    }

    private void registerProfessionShimIfNeeded() {
        boolean affectVillagers = getConfig().getBoolean("affect-villagers", true);
        boolean affectZombieVillagers = getConfig().getBoolean("affect-zombie-villagers", true);
        if (!affectVillagers && !affectZombieVillagers) {
            logDebug("Villager/ZombieVillager shim disabled in config.");
            return;
        }

        getServer().getPluginManager().registerEvents(new FixVillagerProfessionShim(this), this);
        logInfo("Villager/ZombieVillager profession shim enabled.");
    }

    private void registerChatBridgeIfNeeded() {
        if (!getConfig().getBoolean("chat-bridge-enabled", true)) {
            logDebug("Chat bridge disabled in config.");
            return;
        }

        new ChatBridge(this).registerIfNeeded();
    }

    private void printBanner() {
        for (String line : BANNER_LINES) {
            System.out.println(line);
        }
    }

    private String prefix(String message) {
        return PREFIX + ' ' + message;
    }
}
