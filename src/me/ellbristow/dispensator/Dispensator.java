package me.ellbristow.dispensator;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class Dispensator extends JavaPlugin implements Listener {
    private File blockFile;
    private FileConfiguration blockStore;

    @Override
    public void onEnable() {
        blockStore = getDispensators();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
    }

    /****************\
    *                *
    *    COMMANDS    *
    *                *
    \****************/
    
    @Override
    public boolean onCommand( CommandSender sender, Command cmd, String commandLabel, String[] args ) {
        if (commandLabel.equalsIgnoreCase("dispenser") || commandLabel.equalsIgnoreCase("disp")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command is not available from the console!");
                return true;
            }
            return commandDispenser((Player)sender, args);
        }
        return true;
    }
    
    private boolean commandDispenser( Player commandPlayer, String[] args ) {
        if (args.length == 0) {
            commandPlayer.sendMessage(ChatColor.RED + "You must specify create or remove!");
            return false;
        }
        Block block = commandPlayer.getTargetBlock(null, 5);
        if (args[0].equals("create")) {
            // Check looking at Dispenser
            if (!block.getType().equals(Material.DISPENSER)) {
                commandPlayer.sendMessage(ChatColor.RED + "You can only turn dispensers into Dispensators!");
                commandPlayer.sendMessage(ChatColor.RED + "(You're looking at a "+block.getType()+"!)");
                return true;
            }
            createDispensator(block);
            commandPlayer.sendMessage(ChatColor.GOLD+"Dispensator Created!");
        } else if (args[0].equals("remove")) {
            // Check looking at Dispensator
            if (!(block.getState() instanceof Dispenser) || !isDispensator(block)) {
                commandPlayer.sendMessage(ChatColor.RED + "You are not looking at a Dispensator!");
                return true;
            }
            removeDispensator(block);
            commandPlayer.sendMessage(ChatColor.GOLD+"Dispensator Removed!");
        }
        return true;
    }
    
    /*****************\
    *                 *
    *    LISTENERS    *
    *                 *
    \*****************/
    
    @EventHandler (priority = EventPriority.NORMAL)
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        if (isDispensator(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot break a Dispensator!");
        }
    }
    
    @EventHandler (priority = EventPriority.NORMAL)
    public void onExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;
        List<Block> blocks = event.blockList();
        if (blocks != null) {
            Collection<Block> saveBlocks = new HashSet<Block>();
            for (Block block : blocks) {
                if (isDispensator(block)) {
                    saveBlocks.add(block);
                }
            }
            if (!saveBlocks.isEmpty()) {
                blocks.removeAll(saveBlocks);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && isDispensator(event.getClickedBlock()) && !(event.getPlayer().isSneaking() && event.getPlayer().hasPermission("dispensator.admin"))) {
            Player player = event.getPlayer();
            if (player.hasPermission("dispensator.use")) {
                Dispenser disp = (Dispenser)event.getClickedBlock().getState();
                ItemStack stack = disp.getInventory().getItem(0);
                List<Entity> entities = player.getNearbyEntities(5, 5, 5);
                for (Entity entity : entities) {
                    if (entity.getType().equals(EntityType.DROPPED_ITEM)) {
                        Item item = (Item)entity;
                        if (item.getItemStack().getType().equals(stack.getType())) {
                            player.sendMessage(ChatColor.RED + "You can only dispense one item at a time!");
                            player.sendMessage(ChatColor.RED + "Please collect dropped items first!");
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
                if (stack != null) {
                    stack = stack.clone();
                    if (!player.getInventory().contains(stack)) {
                        disp.dispense();
                        disp.getInventory().setItem(0, stack);
                    } else {
                        player.sendMessage(ChatColor.RED + "You already have this item in your inventory!");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Oh Dear! This Dispensator is empty!");
                }
            } else {
                player.sendMessage(ChatColor.RED + "You do not have permission to use Dispensators!");
            }
            event.setCancelled(true);
        }
    }
    
    @EventHandler (priority = EventPriority.NORMAL)
    public void onDispense(BlockDispenseEvent event) {
        if (event.isCancelled()) return;
        if (isDispensator(event.getBlock()) && event.getBlock().isBlockPowered()) {
            event.setCancelled(true);
        }
    }
    
    /*************************\
    *                         *
    *    GENERAL FUNCTIONS    *
    *                         *
    \*************************/
    
    private void createDispensator(Block block) {
        blockStore.set(block.getWorld().getName()+"_"+block.getX()+"_"+block.getY()+"_"+block.getZ(), true);
        saveDispensators();
    }
    
    private void removeDispensator(Block block) {
        blockStore.set(block.getWorld().getName()+"_"+block.getX()+"_"+block.getY()+"_"+block.getZ(), null);
        saveDispensators();
    }
    
    private boolean isDispensator(Block block) {
        if (!(block.getState() instanceof Dispenser)) {
            return false;
        }
        Boolean disp = blockStore.getBoolean(block.getWorld().getName()+"_"+block.getX()+"_"+block.getY()+"_"+block.getZ());
        if (disp != null && disp) {
            return true;
        }
        return false;
    }
    
    /************************\
    *                        *
    *    CONFIG FUNCTIONS    *
    *                        *
    \************************/
    
    private void loadDispensators() {
        if (blockFile == null) {
            blockFile = new File(getDataFolder(),"dispensators.yml");
        }
        blockStore = YamlConfiguration.loadConfiguration(blockFile);
    }
	
    private FileConfiguration getDispensators() {
        if (blockStore == null) {
            loadDispensators();
        }
        return blockStore;
    }
	
    private void saveDispensators() {
        if (blockStore == null || blockFile == null) {
            return;
        }
        try {
            blockStore.save(blockFile);
        } catch (IOException ex) {
            getLogger().severe("Could not save " + blockFile);
            ex.printStackTrace();
        }
    }

}
