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
    public void onDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        try {
            if (entity instanceof Villager villager) {
                neutralizeVillager(villager);
            } else if (entity instanceof ZombieVillager zombieVillager) {
                neutralizeZombieVillager(zombieVillager);
            }
        } catch (Throwable ex) {
            plugin.logWarning("Failed to neutralize villager profession: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            plugin.logHandledError("VillagerShim:onDeath", ex);
        }
    }

    private void neutralizeVillager(Villager villager) {
        if (!plugin.getConfig().getBoolean("affect-villagers", true)) {
            return;
        }
        safeSetProfession(villager, Villager.Profession.NONE);
    }

    private void neutralizeZombieVillager(ZombieVillager zombieVillager) {
        if (!plugin.getConfig().getBoolean("affect-zombie-villagers", true)) {
            return;
        }
        safeSetZombieProfession(zombieVillager, Villager.Profession.NONE);
    }

    private void safeSetProfession(Villager villager, Villager.Profession profession) {
        try {
            if (villager.getProfession() != profession) {
                villager.setProfession(profession);
                plugin.logDebug("Villager profession set to " + profession);
            }
        } catch (Throwable ex) {
            plugin.logHandledError("VillagerShim:safeSetVillagerProfession", ex);
        }
    }

    private void safeSetZombieProfession(ZombieVillager zombieVillager, Villager.Profession profession) {
        try {
            if (zombieVillager.getVillagerProfession() != profession) {
                zombieVillager.setVillagerProfession(profession);
                plugin.logDebug("ZombieVillager profession set to " + profession);
            }
        } catch (Throwable ex) {
            plugin.logHandledError("VillagerShim:safeSetZombieVillagerProfession", ex);
        }
    }
}
