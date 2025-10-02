package com.ssilensio.coreprotectfix;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

final class ChatBridge implements Listener {

    private final CoreProtectFixPlugin plugin;
    private final boolean active;

    ChatBridge(CoreProtectFixPlugin plugin) {
        this.plugin = plugin;
        this.active = !classExists("io.papermc.paper.event.player.AsyncChatEvent");
    }

    void registerIfNeeded() {
        if (active) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.logInfo("Paper AsyncChatEvent not found — bridging via AsyncPlayerChatEvent.");
        } else {
            plugin.logDebug("AsyncChatEvent exists — no chat bridge needed.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!active) {
            return;
        }

        Optional<Object> api = resolveCoreProtectApi();
        if (api.isEmpty()) {
            return;
        }

        Object coreProtectApi = api.get();
        try {
            Method method = coreProtectApi.getClass().getMethod("logChat", String.class, String.class);
            method.invoke(coreProtectApi, event.getPlayer().getName(), event.getMessage());
        } catch (NoSuchMethodException ex) {
            plugin.logDebug("CoreProtectAPI.logChat(String,String) not found.");
        } catch (IllegalAccessException | InvocationTargetException ex) {
            plugin.logWarning("CoreProtect API call failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            plugin.logHandledError("ChatBridge:onChat", ex);
        }
    }

    private Optional<Object> resolveCoreProtectApi() {
        Plugin coreProtect = plugin.getServer().getPluginManager().getPlugin("CoreProtect");
        if (coreProtect == null || !coreProtect.isEnabled()) {
            return Optional.empty();
        }

        try {
            Class<?> cpClazz = Class.forName("net.coreprotect.CoreProtect");
            Method getInstance = cpClazz.getMethod("getInstance");
            Object cpInstance = getInstance.invoke(null);

            Method getAPI = cpClazz.getMethod("getAPI");
            Object api = getAPI.invoke(cpInstance);

            Class<?> apiClazz = Class.forName("net.coreprotect.CoreProtectAPI");
            Method isEnabled = apiClazz.getMethod("isEnabled");
            Boolean enabled = (Boolean) isEnabled.invoke(api);
            if (Boolean.TRUE.equals(enabled)) {
                return Optional.of(api);
            }
        } catch (Throwable ex) {
            plugin.logWarning("CoreProtect API discovery failed: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            plugin.logHandledError("ChatBridge:resolveCoreProtectApi", ex);
        }
        return Optional.empty();
    }

    private boolean classExists(String fqn) {
        try {
            Class.forName(fqn, false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
