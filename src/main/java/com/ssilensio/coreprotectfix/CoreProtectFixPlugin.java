package com.ssilensio.coreprotectfix;

import org.bukkit.plugin.java.JavaPlugin;

public final class CoreProtectFixPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1) Villager/ZombieVillager profession shim
        if (getConfig().getBoolean("affect-villagers", true) ||
            getConfig().getBoolean("affect-zombie-villagers", true)) {
            getServer().getPluginManager().registerEvents(
                    new FixVillagerProfessionShim(this), this);
            getLogger().info("[CoreProtectFix] Villager/ZombieVillager profession shim enabled.");
        }

        // 2) Chat bridge for hybrids without Paper AsyncChatEvent
        if (getConfig().getBoolean("chat-bridge-enabled", true)) {
            ChatBridge bridge = new ChatBridge(this);
            bridge.registerIfNeeded();
        }

        getLogger().info("[CoreProtectFix] Enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[CoreProtectFix] Disabled.");
    }

    /* Utility */
    public boolean debug() {
        return getConfig().getBoolean("debug", false);
    }
}
