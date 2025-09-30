package com.ssilensio.coreprotectfix;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

final class FixVillagerProfessionShim implements Listener {

    private final CoreProtectFixPlugin plugin;

    FixVillagerProfessionShim(CoreProtectFixPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        Entity ent = e.getEntity();

        try {
            if (ent instanceof Villager villager && plugin.getConfig().getBoolean("affect-villagers", true)) {
                safeSetVillagerProfession(villager, Villager.Profession.NONE);
            } else if (ent instanceof ZombieVillager zv && plugin.getConfig().getBoolean("affect-zombie-villagers", true)) {
                safeSetZombieVillagerProfession(zv, Villager.Profession.NONE);
            }
        } catch (Throwable ex) {
            if (plugin.debug()) {
                plugin.getLogger().warning("[VillagerShim] Failed to neutralize profession: "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    private void safeSetVillagerProfession(Villager villager, Villager.Profession p) {
        try {
            if (villager.getProfession() != p) {
                villager.setProfession(p);
                if (plugin.debug()) plugin.getLogger().info("[VillagerShim] Villager profession set to " + p);
            }
        } catch (Throwable ignored) { }
    }

    private void safeSetZombieVillagerProfession(ZombieVillager zv, Villager.Profession p) {
        try {
            if (zv.getVillagerProfession() != p) {
                zv.setVillagerProfession(p);
                if (plugin.debug()) plugin.getLogger().info("[VillagerShim] ZombieVillager profession set to " + p);
            }
        } catch (Throwable ignored) { }
    }
}
