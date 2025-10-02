package com.ssilensio.coreprotectfix;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class CoreProtectFixPlugin extends JavaPlugin {

    private static final String[] BANNER_LINES = new String[] {
            "\u001B[36m   ██████╗  ██████╗ ██████╗ ███████╗██████╗ ██████╗ ███████╗████████╗\u001B[0m",
            "\u001B[36m   ██╔══██╗██╔════╝██╔═══██╗██╔════╝██╔══██╗██╔══██╗██╔════╝╚══██╔══╝\u001B[0m",
            "\u001B[36m   ██████╔╝██║     ██║   ██║█████╗  ██████╔╝██████╔╝█████╗     ██║   \u001B[0m",
            "\u001B[36m   ██╔══██╗██║     ██║   ██║██╔══╝  ██╔══██╗██╔══██╗██╔══╝     ██║   \u001B[0m",
            "\u001B[36m   ██║  ██║╚██████╗╚██████╔╝███████╗██║  ██║██║  ██║███████╗   ██║   \u001B[0m",
            "\u001B[36m   ╚═╝  ╚═╝ ╚═════╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝   ╚═╝   \u001B[0m",
            "\u001B[35m      CoreProtectFix — bridging CoreProtect with modern chat APIs\u001B[0m"
    };

    private HandledErrorLogger handledErrorLogger;

    @Override
    public void onEnable() {
        printBanner();

        saveDefaultConfig();
        handledErrorLogger = HandledErrorLogger.tryCreate(getDataFolder().toPath(), getLogger());
        if (handledErrorLogger == null) {
            getLogger().warning("[CoreProtectFix] Handled error logging disabled — see previous warning for details.");
        }

        if (getConfig().getBoolean("affect-villagers", true) ||
            getConfig().getBoolean("affect-zombie-villagers", true)) {
            getServer().getPluginManager().registerEvents(
                    new FixVillagerProfessionShim(this), this);
            getLogger().info("[CoreProtectFix] Villager/ZombieVillager profession shim enabled.");
        }

        if (getConfig().getBoolean("chat-bridge-enabled", true)) {
            ChatBridge bridge = new ChatBridge(this);
            bridge.registerIfNeeded();
        }

        getLogger().info("[CoreProtectFix] Enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[CoreProtectFix] Disabled.");
        handledErrorLogger = null;
    }

    public boolean debug() {
        return getConfig().getBoolean("debug", false);
    }

    void logHandledError(String source, Throwable error) {
        if (handledErrorLogger == null || error == null) {
            return;
        }

        handledErrorLogger.append(source, error);
    }

    private void printBanner() {
        for (String line : BANNER_LINES) {
            System.out.println(line);
        }
        getLogger().log(Level.INFO, "[CoreProtectFix] Banner printed to console.");
    }
}
