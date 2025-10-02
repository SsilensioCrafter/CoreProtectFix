package com.ssilensio.coreprotectfix;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

final class ChatBridge implements Listener {

    private final JavaPlugin plugin;
    private final boolean active;

    ChatBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        // Activate bridge if Paper AsyncChatEvent is missing
        this.active = !classExists("io.papermc.paper.event.player.AsyncChatEvent");
    }

    void registerIfNeeded() {
        if (active) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[CP Bridge] Paper AsyncChatEvent not found — bridging via AsyncPlayerChatEvent.");
        } else if (plugin instanceof CoreProtectFixPlugin p && p.debug()) {
            plugin.getLogger().info("[CP Bridge] AsyncChatEvent exists — no bridge needed.");
        }
    }

    private boolean classExists(String fqn) {
        try {
            Class.forName(fqn, false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!active) return;

        Plugin cp = plugin.getServer().getPluginManager().getPlugin("CoreProtect");
        if (cp == null || !cp.isEnabled()) return;

        try {
            // CoreProtect.getInstance().getAPI()
            Class<?> cpClazz = Class.forName("net.coreprotect.CoreProtect");
            Method getInstance = cpClazz.getMethod("getInstance");
            Object cpInstance = getInstance.invoke(null);

            Method getAPI = cpClazz.getMethod("getAPI");
            Object api = getAPI.invoke(cpInstance);

            Class<?> apiClazz = Class.forName("net.coreprotect.CoreProtectAPI");
            Boolean ok = (Boolean) apiClazz.getMethod("isEnabled").invoke(api);
            if (Boolean.TRUE.equals(ok)) {
                try {
                    apiClazz.getMethod("logChat", String.class, String.class)
                            .invoke(api, e.getPlayer().getName(), e.getMessage());
                } catch (NoSuchMethodException ex) {
                    if (((CoreProtectFixPlugin) plugin).debug()) {
                        plugin.getLogger().warning("[CP Bridge] CoreProtectAPI.logChat(String,String) not found.");
                    }
                }
            }
        } catch (Throwable ex) {
            if (((CoreProtectFixPlugin) plugin).debug()) {
                plugin.getLogger().warning("[CP Bridge] CoreProtect API call failed: "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            if (plugin instanceof CoreProtectFixPlugin coreProtectFixPlugin) {
                coreProtectFixPlugin.logHandledError("ChatBridge:onChat", ex);
            }
        }
    }
}
