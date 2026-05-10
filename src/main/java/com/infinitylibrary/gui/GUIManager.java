package com.infinitylibrary.gui;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

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
    public String lecternBookEditorTitle() { return color(plugin.getConfig().getString("lectern-gui.book-editor-title", "&5Library Book Editor")); }

    public void openLecternBookEditor(Player player) { openLecternBookEditor(player, null); }
    public void openLecternBookEditor(Player player, ItemStack book) {
        Inventory inv = Bukkit.createInventory(null, 27, lecternBookEditorTitle());
        drawLecternBookEditor(inv, player, book);
        player.openInventory(inv);
    }
    public void refreshLecternBookEditor(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (!player.getOpenInventory().getTitle().equals(lecternBookEditorTitle())) return;
        drawLecternBookEditor(inv, player, inv.getItem(22));
    }

    private void drawLecternBookEditor(Inventory inv, Player player, ItemStack book) {
        for (int i = 0; i < 27; i++) if (i != 22) inv.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        if (book == null || book.getType().isAir()) {
            inv.setItem(4, item(Material.LECTERN, "&dSubmit a Written Book", List.of("&7Put a written book in the", "&7middle bottom slot below.")));
            inv.setItem(22, null);
            return;
        }
        if (book.getType() != Material.WRITTEN_BOOK || !(book.getItemMeta() instanceof BookMeta meta)) {
            inv.setItem(4, item(Material.BARRIER, "&cWritten Books Only", List.of("&7Remove this item and place", "&7a signed written book here.")));
            inv.setItem(22, book);
            return;
        }
        inv.setItem(22, book);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        inv.setItem(4, item(Material.WRITTEN_BOOK, "&d" + value(meta.getTitle(), "Untitled"), List.of("&7Author: &f" + value(meta.getAuthor(), "Unknown"), "&7Pages: &f" + meta.getPageCount())));
        inv.setItem(9, item(Material.NAME_TAG, "&bEdit Title", List.of("&7Current: &f" + value(meta.getTitle(), "Untitled"))));
        inv.setItem(10, item(Material.PLAYER_HEAD, "&bEdit Author", List.of("&7Current: &f" + value(meta.getAuthor(), player.getName()))));
        inv.setItem(11, item(Material.BOOKSHELF, "&aEdit Category", List.of("&7Current: &f" + metadata(pdc, "book_category", "general"))));
        inv.setItem(12, item(Material.PAPER, "&aEdit Tags", List.of("&7Current: &f" + metadata(pdc, "book_tags", "none"), "&7Use comma separated tags.")));
        inv.setItem(13, item(Material.EXPERIENCE_BOTTLE, "&eEdit Rating", List.of("&7Current: &f" + metadata(pdc, "book_rating", "unrated"))));
        inv.setItem(14, item(Material.OAK_SIGN, "&eEdit Comments", List.of("&7Current: &f" + metadata(pdc, "book_comments", "none"))));
        inv.setItem(15, item(Material.ENDER_EYE, "&6Toggle Visibility", List.of("&7Current: &f" + (plugin.getBookStorageManager().isPublicBook(book) ? "Public" : "Private"))));
        inv.setItem(17, item(Material.EMERALD_BLOCK, "&aSave To Library", List.of("&7Stores this book so it can", "&7appear in chiseled bookshelves.")));
    }

    private String metadata(PersistentDataContainer pdc, String key, String fallback) { return value(plugin.getBookStorageManager().bookMetadataValue(pdc, key), fallback); }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    public String bookshelfReturnTitle() { return color(plugin.getConfig().getString("bookshelf-return-gui.title", "&5Return Library Book")); }
    public void openBookshelfReturn(Player player, ItemStack book) {
        Inventory inv = Bukkit.createInventory(null, 27, bookshelfReturnTitle());
        drawBookshelfReturn(inv, player, book);
        player.openInventory(inv);
    }
    public void refreshBookshelfReturn(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (!player.getOpenInventory().getTitle().equals(bookshelfReturnTitle())) return;
        drawBookshelfReturn(inv, player, inv.getItem(13));
    }
    private void drawBookshelfReturn(Inventory inv, Player player, ItemStack book) {
        for (int i = 0; i < 27; i++) if (i != 13) inv.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        if (book == null || book.getType().isAir()) {
            inv.setItem(4, item(Material.CHISELED_BOOKSHELF, "&dReturn A Library Book", List.of("&7Drag a written book into", "&7the middle slot below.")));
            inv.setItem(13, null);
            return;
        }
        if (book.getType() != Material.WRITTEN_BOOK || !(book.getItemMeta() instanceof BookMeta meta)) {
            inv.setItem(4, item(Material.BARRIER, "&cWritten Books Only", List.of("&7Remove this item and place", "&7a signed written book here.")));
            inv.setItem(13, book);
            return;
        }
        inv.setItem(13, book);
        inv.setItem(4, item(Material.WRITTEN_BOOK, "&d" + value(meta.getTitle(), "Untitled"), List.of("&7Author: &f" + value(meta.getAuthor(), "Unknown"), "&7Average rating: &e" + plugin.getBookStorageManager().averageRatingText(book))));
        inv.setItem(11, item(Material.EXPERIENCE_BOTTLE, "&eAdd Rating", List.of("&7Type a 1-5 rating in chat.", "&7Average: &e" + plugin.getBookStorageManager().averageRatingText(book))));
        inv.setItem(15, item(Material.OAK_SIGN, "&aAdd Comment", List.of("&7Add a comment for this book.")));
        inv.setItem(17, item(Material.EMERALD_BLOCK, "&aReturn To Library", List.of("&7Stores this book back", "&7into the library.")));
    }

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
