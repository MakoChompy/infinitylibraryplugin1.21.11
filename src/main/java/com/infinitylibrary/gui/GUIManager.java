package com.infinitylibrary.gui;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIManager {
    private final InfinityLibraryPlugin plugin;
    private final Map<UUID, String> selectedRoom = new ConcurrentHashMap<>();
    public GUIManager(InfinityLibraryPlugin plugin) { this.plugin = plugin; }

    public void openStats(Player player) {
        int size = plugin.getConfig().getInt("gui.size", 27);
        Inventory inv = Bukkit.createInventory(null, size, statsTitle());
        Material filler = material("gui.filler-material", Material.BLACK_STAINED_GLASS_PANE);
        for (int i=0;i<size;i++) inv.setItem(i, item(filler, " ", List.of()));
        inv.setItem(plugin.getConfig().getInt("gui.total-books-slot", 11), item(material("gui.total-books-material", Material.WRITTEN_BOOK), "&dTotal Books", List.of("&7Stored written books: &f" + plugin.getBookStorageManager().totalBooks())));
        inv.setItem(plugin.getConfig().getInt("gui.contributors-slot", 13), item(material("gui.contributors-material", Material.PLAYER_HEAD), "&bContributors", List.of("&7Unique contributors: &f" + plugin.getBookStorageManager().totalContributors())));
        inv.setItem(plugin.getConfig().getInt("gui.player-stats-slot", 15), item(material("gui.player-stats-material", Material.BOOK), "&aYour Stats", List.of("&7Books contributed: &f" + plugin.getBookStorageManager().playerCount(player.getUniqueId()))));
        player.openInventory(inv);
    }

    public void openLectern(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, lecternTitle());
        for (int i = 0; i < 27; i++) inv.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(13, item(Material.WRITABLE_BOOK, "&dClaim Daily Writable Book", List.of("&7One per player per day.", "&7Books start private and linked to you.")));
        inv.setItem(11, item(Material.COMPASS, "&bSearch Stored Books", List.of("&7Click, then type a title in chat.", "&7Particles show the shelf location.")));
        inv.setItem(15, item(Material.ENDER_EYE, "&aLibrary Stats", List.of("&7Open the stats interface.")));
        player.openInventory(inv);
    }

    public String statsTitle() { return color(plugin.getConfig().getString("gui.title", "&5Infinity Library")); }
    public String lecternTitle() { return color(plugin.getConfig().getString("lectern-gui.title", "&5Library Lectern")); }
    public String roomEditorTitle() { return color("&5Room Editor"); }
    public void openRoomEditor(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, roomEditorTitle());
        for (int i=0;i<54;i++) inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(10, item(Material.EMERALD_BLOCK, "&aCreate New Room", List.of("&7Use wands, then /il saveroom")));
        int slot = 19;
        for (var room : plugin.getRoomManager().allRooms()) {
            if (slot >= 54) break;
            inv.setItem(slot++, item(Material.BOOK, "&b" + room.id(), List.of("&7Type: &f" + room.type(), "&7Connections: &f" + room.connections().size(), "&7Variations: &f" + room.variations().size(), "&eUse /il applyvariations " + room.id())));
        }
        player.openInventory(inv);
    }
    public String roomDetailsTitle() { return color("&5Room Details"); }
    public void openRoomDetails(Player player, String roomId) {
        selectedRoom.put(player.getUniqueId(), roomId);
        Inventory inv = Bukkit.createInventory(null, 27, roomDetailsTitle());
        for (int i=0;i<27;i++) inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(11, item(Material.WOODEN_AXE, "&eEdit Selected Area", List.of("&7Use /il pos1 and /il pos2", "&7Then resave/edit room.")));
        inv.setItem(13, item(Material.SLIME_BALL, "&aAdd Variations", List.of("&7Use variation wand then", "&7/il applyvariations " + roomId)));
        inv.setItem(15, item(Material.BARRIER, "&cDelete Room", List.of("&7Shift+Right click from room list")));
        player.openInventory(inv);
    }
    public String selectedRoomId(Player player) { return selectedRoom.get(player.getUniqueId()); }
    private Material material(String path, Material fallback) { Material m = Material.matchMaterial(plugin.getConfig().getString(path, fallback.name())); return m == null ? fallback : m; }
    private ItemStack item(Material mat, String name, List<String> lore) { ItemStack stack = new ItemStack(mat); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(color(name)); meta.setLore(lore.stream().map(this::color).toList()); stack.setItemMeta(meta); return stack; }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
