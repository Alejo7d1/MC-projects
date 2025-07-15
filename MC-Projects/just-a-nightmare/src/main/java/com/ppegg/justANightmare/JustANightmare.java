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
    private final Map<UUID, ItemStack[]> sleepArmor = new HashMap<>();

    private File dataFolder;

    //Enable the plugin and register events
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        dataFolder = new File(getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        loadAllSleepData();
        getLogger().info("JustANightmare enabled");
    }

    //Disable the plugin and log a message
    @Override
    public void onDisable() {
        getLogger().info("JustANightmare disabled");
    }

    // Generate a unique key for each item type
    private String itemKey(ItemStack item) {
        return item.getType().toString();
    }

    // Clone the contents of an inventory to avoid modifying the original
    private ItemStack[] cloneContents(ItemStack[] original) {
        ItemStack[] clone = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            clone[i] = original[i] != null ? original[i].clone() : null;
        }
        return clone;
    }

    // Apply the difference between two inventories
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

    //Saves the inventory data for a player when they click a bed
    private void saveSleepData(UUID uuid, ItemStack[] contents, ItemStack[] armorContents) {
        File file = new File(dataFolder, uuid + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("inventory", contents);
        config.set("armor", armorContents);
        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().warning("Inventory was not saved for " + uuid);
        }
    }

    // Checks if the player has sleep data saved
    private boolean hasSleepData(UUID uuid) {
        return new File(dataFolder, uuid + ".yml").exists();
    }

    // Loads the inventory data for a player who has a saved sleep state
    private ItemStack[] loadInventory(UUID uuid) {
        File file = new File(dataFolder, uuid + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> list = config.getList("inventory");
        return list != null ? list.toArray(new ItemStack[0]) : new ItemStack[36];
    }

    private ItemStack[] loadArmor(UUID uuid) {
        File file = new File(dataFolder, uuid + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> list = config.getList("armor");
        return list != null ? list.toArray(new ItemStack[4]) : new ItemStack[4];
    }


    // Loads all sleep data from the data folder
    private void loadAllSleepData() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            getLogger().info("Loading: " + file.getName());
        }
    }

    // Event handler for player interaction with beds
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getBlockData() instanceof Bed)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ItemStack[] contents = cloneContents(player.getInventory().getContents());
        ItemStack[] armorContents = cloneContents(player.getInventory().getArmorContents());
        sleepInventory.put(uuid, contents);
        sleepArmor.put(uuid, armorContents);
        saveSleepData(uuid, contents, armorContents);
        player.sendMessage("§5 Inventory saved");
    }

    // Event handler for player death
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!sleepInventory.containsKey(uuid) && !hasSleepData(uuid)) return;

        deathInventory.put(uuid, cloneContents(player.getInventory().getContents()));

        //Get death inventory contents
        ItemStack[] saved = sleepInventory.containsKey(uuid) ? sleepInventory.get(uuid) : loadInventory(uuid);
        ItemStack[] savedArmor = sleepArmor.containsKey(uuid) ? sleepArmor.get(uuid) : loadArmor(uuid);

        // count saved items by type
        Map<String, Integer> savedCount = new HashMap<>();
        for (ItemStack item : saved) {
            if (item != null && item.getType() != Material.AIR) {
                String key = itemKey(item);
                savedCount.put(key, savedCount.getOrDefault(key, 0) + item.getAmount());
            }
        }

        // Count saved armor items
        for (ItemStack item : savedArmor) {
            if (item != null && item.getType() != Material.AIR) {
                String key = itemKey(item);
                savedCount.put(key, savedCount.getOrDefault(key, 0) + item.getAmount());
            }
        }

        // Remove from drops the items that were saved
        List<ItemStack> drops = event.getDrops();
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop == null || drop.getType() == Material.AIR) continue;

            String key = itemKey(drop);
            int savedAmt = savedCount.getOrDefault(key, 0);
            if (savedAmt > 0) {
                int dropAmt = drop.getAmount();
                if (dropAmt <= savedAmt) {
                    iterator.remove(); // Remove the drop if it was fully saved
                    savedCount.put(key, savedAmt - dropAmt); // Decrease the saved count
                } else {
                    drop.setAmount(dropAmt - savedAmt); // Reduce the drop amount
                    savedCount.put(key, 0); // Set to zero since we used all saved items
                }
            }
        }

    }


    // Event handler for player respawn
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
                ItemStack[] sleptArmor = sleepArmor.containsKey(uuid) ? sleepArmor.get(uuid) : loadArmor(uuid);

                ItemStack[] restoredContents = applyDifference(slept, deathInventory.get(uuid));

                player.getInventory().setContents(restoredContents);
                player.getInventory().setArmorContents(sleptArmor);
                player.sendMessage("§5It was just a nightmare...");
            } else {
                player.sendMessage("§cYou are nothing");
            }

            deathInventory.remove(uuid);
        }, 1L);
    }
}
