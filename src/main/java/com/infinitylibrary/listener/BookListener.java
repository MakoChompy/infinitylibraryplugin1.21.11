package com.infinitylibrary.listener;

import com.infinitylibrary.InfinityLibraryPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class BookListener implements Listener {
    private final InfinityLibraryPlugin plugin;
    private final java.util.Map<java.util.UUID, java.util.UUID> seats = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, java.util.UUID> readingIndicators = new java.util.concurrent.ConcurrentHashMap<>();
    public BookListener(InfinityLibraryPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::sendBookshelfActionBars, 20L, 10L);
    }

    @EventHandler(ignoreCancelled = true) public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.LECTERN) {
            plugin.getGuiManager().openLecternBookEditor(e.getPlayer());
            e.setCancelled(true);
            return;
        }
        if (plugin.getSelectionManager().handle(e.getPlayer(), e.getAction(), e.getClickedBlock())) { e.setCancelled(true); return; }
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.CHISELED_BOOKSHELF) {
            plugin.getGuiManager().openBookshelfReturn(e.getPlayer(), null);
            e.setCancelled(true);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            if (isReadingSeat(e.getClickedBlock())) {
                sitOnSlab(e.getPlayer(), e.getClickedBlock());
                e.setCancelled(true);
                return;
            }
            if (isReadingTable(e.getClickedBlock()) && isSitting(e.getPlayer())) {
                openPreferredReadingBook(e.getPlayer());
                e.setCancelled(true);
                return;
            }
            if (e.getClickedBlock().getType() == Material.LECTERN) { plugin.getGuiManager().openLectern(e.getPlayer()); e.setCancelled(true); return; }
            if (e.getClickedBlock().getType() == Material.CHISELED_BOOKSHELF) {
                ItemStack item = e.getItem();
                if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                    plugin.getBookStorageManager().beginMetadataPrompt(e.getPlayer(), item.clone(), e.getClickedBlock().getLocation());
                    e.setCancelled(true);
                }
                return;
            }
        }
        ItemStack item = e.getItem();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && item != null && item.getType() == Material.WRITTEN_BOOK && !plugin.getBookStorageManager().canRead(e.getPlayer(), item)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "This library book is private. You can carry it, but only its linked owner can read it.");
        }
    }

    private void sitOnSlab(Player player, Block slab) {
        if (isSitting(player)) return;
        Location seatLoc = slab.getLocation().add(0.5, 0.2, 0.5);
        ArmorStand seat = (ArmorStand) slab.getWorld().spawnEntity(seatLoc, EntityType.ARMOR_STAND);
        seat.setInvisible(true); seat.setInvulnerable(true); seat.setGravity(false); seat.setMarker(true);
        seat.addPassenger(player);
        seats.put(player.getUniqueId(), seat.getUniqueId());
    }

    private boolean isSitting(Player player) {
        java.util.UUID seatId = seats.get(player.getUniqueId());
        if (seatId == null) return false;
        return player.getWorld().getEntities().stream().anyMatch(e -> e.getUniqueId().equals(seatId));
    }


    private boolean isReadingSeat(Block block) {
        return plugin.getSelectionManager().isReadingSeat(block.getLocation());
    }

    private boolean isReadingTable(Block block) {
        return plugin.getSelectionManager().isReadingTable(block.getLocation());
    }

    private void openPreferredReadingBook(Player player) {
        ItemStack inventoryBook = firstReadableInventoryBook(player);
        if (inventoryBook != null) {
            player.openBook(inventoryBook);
            showReadingIndicator(player);
            return;
        }
        plugin.getBookStorageManager().randomBook().ifPresentOrElse(book -> {
            player.openBook(book);
            showReadingIndicator(player);
        }, () -> player.sendMessage(ChatColor.RED + "No stored books available."));
    }

    private ItemStack firstReadableInventoryBook(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.WRITTEN_BOOK && stack.getItemMeta() instanceof BookMeta && plugin.getBookStorageManager().canRead(player, stack)) return stack;
        }
        return null;
    }

    private void showReadingIndicator(Player player) {
        clearReadingIndicator(player);
        ArmorStand indicator = (ArmorStand) player.getWorld().spawnEntity(player.getLocation().add(0, 1.9, 0), EntityType.ARMOR_STAND);
        indicator.setInvisible(true); indicator.setInvulnerable(true); indicator.setGravity(false); indicator.setMarker(true);
        indicator.setCustomName(ChatColor.GOLD + "📖"); indicator.setCustomNameVisible(true);
        player.addPassenger(indicator);
        readingIndicators.put(player.getUniqueId(), indicator.getUniqueId());
    }

    private void clearReadingIndicator(Player player) {
        java.util.UUID indicatorId = readingIndicators.remove(player.getUniqueId());
        if (indicatorId == null) return;
        player.getWorld().getEntities().stream().filter(e -> e.getUniqueId().equals(indicatorId)).findFirst().ifPresent(org.bukkit.entity.Entity::remove);
    }

    private void sendBookshelfActionBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Block target = player.getTargetBlockExact(6);
            if (target == null || target.getType() != Material.CHISELED_BOOKSHELF) continue;
            plugin.getBookStorageManager().peekBookshelfBook(target).ifPresent(book -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(plugin.getBookStorageManager().bookSummary(book))));
        }
    }

    @EventHandler public void onSneak(PlayerToggleSneakEvent e) { if (e.isSneaking()) clearReadingIndicator(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { clearReadingIndicator(e.getPlayer()); }

    @EventHandler public void onSearchChat(AsyncPlayerChatEvent e) {
        if (plugin.getBookStorageManager().handleMetadataChat(e.getPlayer(), e.getMessage())) { e.setCancelled(true); return; }
        if (plugin.getBookStorageManager().handleSearchChat(e.getPlayer(), e.getMessage())) e.setCancelled(true);
    }
}
