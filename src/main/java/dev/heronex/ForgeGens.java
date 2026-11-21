package dev.heronex;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
 import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
 import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ForgeGens extends JavaPlugin implements Listener {
    private static final Material ANCHOR = Material.LODESTONE;
    private static final int MAX_LEVEL = 5;

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::produce, 1200L, 1200L);
    }

    private String key(Block block) {
        return "forges." + block.getWorld().getUID() + "_" + block.getX()
            + "_" + block.getY() + "_" + block.getZ();
    }

    private enum Blueprint {
        STANDARD(Material.IRON_NUGGET, 10_000, 1, 64),
        RAPID(Material.GOLD_NUGGET, 5_000, 1, 48),
        VAULT(Material.COPPER_INGOT, 20_000, 2, 128);

        final Material output;
        final long interval;
        final int rate;
        final int capacity;

        Blueprint(Material output, long interval, int rate, int capacity) {
            this.output = output;
            this.interval = interval;
            this.rate = rate;
            this.capacity = capacity;
        }
    }

    private Blueprint blueprint(String key) {
        return Blueprint.valueOf(getConfig().getString(key + ".blueprint", "STANDARD"));
    }

    private void produce() {
        var forges = getConfig().getConfigurationSection("forges");
        if (forges == null) return;
        long now = System.currentTimeMillis();
        for (String id : forges.getKeys(false)) {
            String key = "forges." + id;
            if (getConfig().isString(key + ".owner")) accrue(key, now);
        }
        saveConfig();
    }

    private void accrue(String key, long now) {
        Blueprint type = blueprint(key);
        long last = getConfig().getLong(key + ".lastProduced", now);
        if (last > now || !getConfig().contains(key + ".lastProduced")) {
            getConfig().set(key + ".lastProduced", now);
            return;
        }
        int level = getConfig().getInt(key + ".level", 1);
        int stored = getConfig().getInt(key + ".stored");
        long elapsed = now - last;
        long cycles = Math.min(elapsed, 43_200_000L) / type.interval;
        if (cycles == 0) return;
        getConfig().set(key + ".stored", (int) Math.min(type.capacity * level, stored + cycles * level * type.rate));
        getConfig().set(key + ".lastProduced", now - elapsed % type.interval);
    }

    private void audit(String key, Player player, String action) {
        List<String> entries = getConfig().getStringList(key + ".audit");
        entries.add(java.time.Instant.now() + " " + player.getName() + " " + action);
        if (entries.size() > 25) entries.removeFirst();
        getConfig().set(key + ".audit", entries);
    }

    @Override public void onDisable() { saveConfig(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0 || args.length > 2) {
            player.sendMessage("/forge register [standard|rapid|vault], claim, status, collect, upgrade, grant <player>, revoke <player>, audit, remove");
            return true;
        }
        Block block = player.getTargetBlockExact(5);
        if (block == null || block.getType() != ANCHOR) {
            player.sendMessage("Look at a lodestone within five blocks.");
            return true;
        }
        String key = key(block);
        String action = args[0].toLowerCase(Locale.ROOT);
        String id = player.getUniqueId().toString();
        if (args.length == 2 && !Set.of("register", "grant", "revoke").contains(action)) return false;
        if (action.equals("register") || action.equals("remove")) {
            if (!player.hasPermission("forgegens.admin")) {
                player.sendMessage("Only an administrator can manage forge locations.");
            } else if (action.equals("register") && !getConfig().contains(key)) {
                Blueprint type;
                try {
                    type = args.length == 2 ? Blueprint.valueOf(args[1].toUpperCase(Locale.ROOT)) : Blueprint.STANDARD;
                } catch (IllegalArgumentException invalid) {
                    player.sendMessage("Choose STANDARD, RAPID or VAULT.");
                    return true;
                }
                getConfig().set(key + ".blueprint", type.name());
                getConfig().set(key + ".level", 1);
                saveConfig();
                player.sendMessage("Forge registered. A player can now claim it.");
            } else if (action.equals("remove") && getConfig().contains(key)) {
                getConfig().set(key, null);
                saveConfig();
                player.sendMessage("Forge removed.");
            } else {
                player.sendMessage("The forge is already in that state.");
            }
            return true;
        }
        if (!getConfig().contains(key)) {
            player.sendMessage("That lodestone is not a registered forge.");
            return true;
        }
        String owner = getConfig().getString(key + ".owner");
        if (action.equals("claim") && owner == null) {
            getConfig().set(key + ".owner", id);
            getConfig().set(key + ".lastProduced", System.currentTimeMillis());
            audit(key, player, "claimed");
            saveConfig();
            player.sendMessage("Forge claimed.");
            return true;
        }
        boolean isOwner = id.equals(owner);
        List<String> members = getConfig().getStringList(key + ".members");
        if (!isOwner && !(members.contains(id) && Set.of("status", "collect").contains(action))) {
            player.sendMessage("You need forge ownership or collection access.");
            return true;
        }
        if (action.equals("grant") || action.equals("revoke")) {
            if (args.length != 2) return false;
            Player online = getServer().getPlayerExact(args[1]);
            String member;
            try {
                member = online == null ? UUID.fromString(args[1]).toString() : online.getUniqueId().toString();
            } catch (IllegalArgumentException invalid) {
                player.sendMessage("Use an online player name or a UUID.");
                return true;
            }
            if (member.equals(owner)) return true;
            if (action.equals("grant")) {
                if (!members.contains(member)) members.add(member);
            } else {
                members.remove(member);
            }
            getConfig().set(key + ".members", members);
            audit(key, player, action + " " + member);
            saveConfig();
            player.sendMessage("Forge access updated for " + args[1] + ".");
            return true;
        }
        accrue(key, System.currentTimeMillis());
        Blueprint type = blueprint(key);
        int level = getConfig().getInt(key + ".level");
        int stored = getConfig().getInt(key + ".stored");
        switch (action) {
            case "status" -> player.sendMessage(type + " | Level " + level + " | " + stored + "/" + (type.capacity * level) + " " + type.output);
            case "audit" -> getConfig().getStringList(key + ".audit").forEach(player::sendMessage);
            case "collect" -> {
                if (stored == 0) { player.sendMessage("The forge is empty."); break; }
                int remaining = stored;
                while (remaining > 0) {
                    int stackSize = Math.min(type.output.getMaxStackSize(), remaining);
                    int overflow = player.getInventory().addItem(new ItemStack(type.output, stackSize))
                        .values().stream().mapToInt(ItemStack::getAmount).sum();
                    remaining -= stackSize - overflow;
                    if (overflow > 0) break;
                }
                getConfig().set(key + ".stored", remaining);
                if (stored != remaining) audit(key, player, "collected " + (stored - remaining) + " " + type.output);
                saveConfig();
                player.sendMessage("Collected " + (stored - remaining) + " " + type.output + ".");
            }
            case "upgrade" -> {
                if (level >= MAX_LEVEL) { player.sendMessage("Maximum level reached."); break; }
                ItemStack cost = new ItemStack(Material.IRON_INGOT, 4 * level);
                if (!player.getInventory().containsAtLeast(cost, cost.getAmount())) {
                    player.sendMessage("Upgrade costs " + cost.getAmount() + " iron ingots.");
                    break;
                }
                player.getInventory().removeItem(cost);
                getConfig().set(key + ".level", level + 1);
                getConfig().set(key + ".lastProduced", System.currentTimeMillis());
                audit(key, player, "upgraded to " + (level + 1));
                saveConfig();
                player.sendMessage("Forge upgraded to level " + (level + 1) + ".");
            }
            default -> player.sendMessage("Use /forge status, collect or upgrade.");
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void protect(BlockBreakEvent event) {
        if (!getConfig().contains(key(event.getBlock()))) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("A registered forge must be removed by an administrator first.");
    }

    @EventHandler(ignoreCancelled = true)
    public void protect(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> getConfig().contains(key(block)))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void protect(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> getConfig().contains(key(block)))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void protect(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> getConfig().contains(key(block)));
    }

    @EventHandler(ignoreCancelled = true)
    public void protect(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(block -> getConfig().contains(key(block)));
    }
}
