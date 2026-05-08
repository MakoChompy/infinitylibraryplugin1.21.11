package com.infinitylibrary.listener;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {
    private final InfinityLibraryPlugin plugin;
    public GUIListener(InfinityLibraryPlugin plugin) { this.plugin = plugin; }
    @EventHandler public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (title.equals(plugin.getGuiManager().statsTitle())) { e.setCancelled(true); return; }
        if (title.equals(plugin.getGuiManager().roomEditorTitle())) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player player)) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            if (name == null || name.isBlank() || name.equals("Create New Room")) return;
            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                try { plugin.getRoomManager().delete(name); player.sendMessage(ChatColor.LIGHT_PURPLE + "Deleted room: " + name); }
                catch (Exception ex) { player.sendMessage(ChatColor.RED + ex.getMessage()); }
                plugin.getGuiManager().openRoomEditor(player);
                return;
            }
            if (e.getClick().isLeftClick()) plugin.getGuiManager().openRoomDetails(player, name);
            return;
        }
        if (title.equals(plugin.getGuiManager().roomDetailsTitle())) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player player)) return;
            if (e.getRawSlot() == 11) {
                player.closeInventory();
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Set new room bounds with /il pos1 and /il pos2, then save/edit the room.");
            }
            return;
        }
        if (!title.equals(plugin.getGuiManager().lecternTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getRawSlot() == 13) {
            boolean given = plugin.getBookStorageManager().giveDailyWritableBook(player);
            player.sendMessage(given ? ChatColor.LIGHT_PURPLE + "A private writable library book was added to your inventory." : ChatColor.RED + "You already claimed today's writable book.");
            player.closeInventory();
        } else if (e.getRawSlot() == 11) {
            player.closeInventory();
            plugin.getBookStorageManager().beginSearchPrompt(player);
        } else if (e.getRawSlot() == 15) {
            plugin.getGuiManager().openStats(player);
        }
    }
}
