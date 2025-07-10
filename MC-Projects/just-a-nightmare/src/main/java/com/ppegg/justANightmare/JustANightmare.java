package com.ppegg.justANightmare;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class JustANightmare extends JavaPlugin implements Listener {

    private final Map<UUID, ItemStack[]> sleepInventory = new HashMap<>();
    private final Map<UUID, ItemStack[]> deathInventory = new HashMap<>();

    private File dataFolder;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        dataFolder = new File(getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        loadAllSleepData();
        getLogger().info("JustANightmare enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("JustANightmare disabled");
    }

    private String itemKey(ItemStack item) {
        return item.getType().toString();
    }

    private ItemStack[] cloneContents(ItemStack[] original) {
        ItemStack[] clone = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            clone[i] = original[i] != null ? original[i].clone() : null;
        }
        return clone;
    }

    private ItemStack[] applyDifference(ItemStack[] slept, ItemStack[] died) {
        Map<String, Integer> sleptCount = new HashMap<>();
        Map<String, ItemStack> sleptSamples = new HashMap<>();

        for (ItemStack item : slept) {
            if (item != null && item.getType() != Material.AIR) {
                String key = itemKey(item);
                sleptCount.put(key, sleptCount.getOrDefault(key, 0) + item.getAmount());
                if (!sleptSamples.containsKey(key)) {
                    sleptSamples.put(key, item.clone());
                }
            }
        }

        Map<String, Integer> diedCount = new HashMap<>();
        for (ItemStack item : died) {
            if (item != null && item.getType() != Material.AIR) {
                String key = itemKey(item);
                diedCount.put(key, diedCount.getOrDefault(key, 0) + item.getAmount());
            }
        }

        List<ItemStack> resultItems = new ArrayList<>();
        for (String key : sleptCount.keySet()) {
            int sleptAmt = sleptCount.getOrDefault(key, 0);
            int diedAmt = diedCount.getOrDefault(key, 0);
            int finalAmt = Math.min(sleptAmt, diedAmt);

            if (finalAmt <= 0) continue;

            ItemStack original = sleptSamples.get(key);
            while (finalAmt > 0) {
                int stackSize = Math.min(original.getMaxStackSize(), finalAmt);
                ItemStack clone = original.clone();
                clone.setAmount(stackSize);
                resultItems.add(clone);
                finalAmt -= stackSize;
            }
        }

        ItemStack[] result = new ItemStack[Math.max(slept.length, resultItems.size())];
        for (int i = 0; i < result.length && i < resultItems.size(); i++) {
            result[i] = resultItems.get(i);
        }

        return result;
    }

    private void saveSleepData(UUID uuid, ItemStack[] contents) {
        File file = new File(dataFolder, uuid + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("inventory", contents);
        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().warning("Inventory was not saved for " + uuid);
        }
    }

    private boolean hasSleepData(UUID uuid) {
        return new File(dataFolder, uuid + ".yml").exists();
    }

    private ItemStack[] loadInventory(UUID uuid) {
        File file = new File(dataFolder, uuid + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> list = config.getList("inventory");
        return list != null ? list.toArray(new ItemStack[0]) : new ItemStack[36];
    }

    private void loadAllSleepData() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            getLogger().info("Loading: " + file.getName());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getBlockData() instanceof Bed)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ItemStack[] contents = cloneContents(player.getInventory().getContents());
        sleepInventory.put(uuid, contents);
        saveSleepData(uuid, contents);
        player.sendMessage("§5 Inventory saved");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!sleepInventory.containsKey(uuid) && !hasSleepData(uuid)) return;

        deathInventory.put(uuid, cloneContents(player.getInventory().getContents()));

        event.setKeepInventory(true);
        event.getDrops().clear();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean hasSleep = sleepInventory.containsKey(uuid) || hasSleepData(uuid);
            boolean hasDeath = deathInventory.containsKey(uuid);

            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);

            if (hasSleep && hasDeath) {
                ItemStack[] slept = sleepInventory.containsKey(uuid) ? sleepInventory.get(uuid) : loadInventory(uuid);

                ItemStack[] restoredContents = applyDifference(slept, deathInventory.get(uuid));

                player.getInventory().setContents(restoredContents);
                player.sendMessage("§5It was just a nightmare...");
            } else {
                player.sendMessage("§cYou are nothing");
            }

            deathInventory.remove(uuid);
        }, 1L);
    }
}
