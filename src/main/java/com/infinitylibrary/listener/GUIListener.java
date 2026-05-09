package com.infinitylibrary.listener;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
        if (title.equals(plugin.getGuiManager().lecternBookEditorTitle())) {
            handleLecternBookEditorClick(e);
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

    private void handleLecternBookEditorClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) { e.setCancelled(true); return; }
        int rawSlot = e.getRawSlot();
        if (rawSlot == 22) {
            if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) { e.setCancelled(true); return; }
            ItemStack cursor = e.getCursor();
            ItemStack hotbar = e.getHotbarButton() >= 0 ? player.getInventory().getItem(e.getHotbarButton()) : null;
            if (hotbar != null && !hotbar.getType().isAir() && hotbar.getType() != Material.WRITTEN_BOOK) {
                e.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Only signed written books can be submitted here.");
                return;
            }
            if (cursor != null && !cursor.getType().isAir() && cursor.getType() != Material.WRITTEN_BOOK) {
                e.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Only signed written books can be submitted here.");
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getGuiManager().refreshLecternBookEditor(player));
            return;
        }
        if (rawSlot >= e.getView().getTopInventory().getSize()) {
            if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        if (rawSlot < 0) return;
        ItemStack book = e.getView().getTopInventory().getItem(22);
        try {
            switch (rawSlot) {
                case 9 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "title");
                case 10 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "author");
                case 11 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "category");
                case 12 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "tags");
                case 13 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "rating");
                case 14 -> plugin.getBookStorageManager().beginLecternBookEdit(player, book, "comments");
                case 15 -> { plugin.getBookStorageManager().toggleLecternBookVisibility(player, book); plugin.getGuiManager().refreshLecternBookEditor(player); }
                case 17 -> {
                    boolean placedInShelf = plugin.getBookStorageManager().saveLecternBook(player, book);
                    e.getView().getTopInventory().setItem(22, null);
                    player.closeInventory();
                    player.sendMessage(placedInShelf ? ChatColor.GREEN + "Book saved into a nearby chiseled bookshelf." : ChatColor.GREEN + "Book saved to the library bookshelf pool.");
                }
            }
        } catch (IllegalArgumentException ex) { player.sendMessage(ChatColor.RED + ex.getMessage()); }
    }

    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (!e.getView().getTitle().equals(plugin.getGuiManager().lecternBookEditorTitle())) return;
        boolean touchesProtectedSlot = e.getRawSlots().stream().anyMatch(slot -> slot < e.getView().getTopInventory().getSize() && slot != 22);
        ItemStack submitted = e.getNewItems().get(22);
        boolean putsInvalidBookInSubmitSlot = submitted != null && submitted.getType() != Material.WRITTEN_BOOK;
        if (touchesProtectedSlot || putsInvalidBookInSubmitSlot) e.setCancelled(true);
        else plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (e.getWhoClicked() instanceof Player player) plugin.getGuiManager().refreshLecternBookEditor(player);
        });
    }

    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().equals(plugin.getGuiManager().lecternBookEditorTitle())) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        if (plugin.getBookStorageManager().hasPendingLecternEdit(player)) return;
        ItemStack book = e.getInventory().getItem(22);
        plugin.getBookStorageManager().returnItem(player, book);
    }
}
