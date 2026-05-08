package com.infinitylibrary.listener;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.ChatColor;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class BookListener implements Listener {
    private final InfinityLibraryPlugin plugin;
    private final java.util.Map<java.util.UUID, java.util.UUID> seats = new java.util.concurrent.ConcurrentHashMap<>();
    public BookListener(InfinityLibraryPlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true) public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (plugin.getSelectionManager().handle(e.getPlayer(), e.getAction(), e.getClickedBlock())) { e.setCancelled(true); return; }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            if (e.getClickedBlock().getType().name().endsWith("SLAB")) {
                sitOnSlab(e.getPlayer(), e.getClickedBlock());
                e.setCancelled(true);
                return;
            }
            if (e.getClickedBlock().getType().name().endsWith("LOG") && isSitting(e.getPlayer())) {
                plugin.getBookStorageManager().randomBook().ifPresentOrElse(book -> e.getPlayer().openBook(book), () -> e.getPlayer().sendMessage(ChatColor.RED + "No stored books available."));
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

    @EventHandler public void onSearchChat(AsyncPlayerChatEvent e) {
        if (plugin.getBookStorageManager().handleMetadataChat(e.getPlayer(), e.getMessage())) { e.setCancelled(true); return; }
        if (plugin.getBookStorageManager().handleSearchChat(e.getPlayer(), e.getMessage())) e.setCancelled(true);
    }
}
