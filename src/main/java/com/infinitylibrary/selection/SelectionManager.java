package com.infinitylibrary.selection;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.model.ConnectionPoint;
import com.infinitylibrary.model.RoomBlock;
import com.infinitylibrary.model.VariationArea;
import com.infinitylibrary.model.Vector3i;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class SelectionManager {
    public enum Mode { POS1, POS2 }

    private final InfinityLibraryPlugin plugin;
    private final NamespacedKey selectionWandKey;
    private final NamespacedKey connectionWandKey;
    private final NamespacedKey variationWandKey;
    private final NamespacedKey bookshelfCategoryWandKey;
    private final NamespacedKey readingTableWandKey;
    private final NamespacedKey readingSeatWandKey;
    private final Map<UUID, Mode> modes = new HashMap<>();
    private final Map<UUID, String> connectionPrefixes = new HashMap<>();
    private final Map<UUID, List<ConnectionPoint>> stagedConnections = new HashMap<>();
    private final Map<UUID, Integer> connectionCounters = new HashMap<>();
    private final Map<UUID, Vector3i> pendingConnectionStart = new HashMap<>();
    private final Map<UUID, VariationSettings> variationSettings = new HashMap<>();
    private final Map<UUID, Vector3i> pendingVariationStart = new HashMap<>();
    private final Map<UUID, List<VariationArea>> stagedVariations = new HashMap<>();
    private final Map<UUID, Integer> variationCounters = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> wildcardVariationCounters = new HashMap<>();
    private final Map<UUID, Map<String, Vector3i>> wildcardVariationSizes = new HashMap<>();
    private final Map<UUID, String> bookshelfCategoryModes = new HashMap<>();
    private final Map<UUID, Vector3i> pendingBookshelfCategoryStart = new HashMap<>();
    private final Map<UUID, Vector3i> pendingReadingTableStart = new HashMap<>();
    private final Map<UUID, Vector3i> pendingReadingSeatStart = new HashMap<>();
    private final Set<String> readingTables = new HashSet<>();
    private final Set<String> readingSeats = new HashSet<>();

    public SelectionManager(InfinityLibraryPlugin plugin) {
        this.plugin = plugin;
        this.selectionWandKey = new NamespacedKey(plugin, "selection_wand");
        this.connectionWandKey = new NamespacedKey(plugin, "connection_wand");
        this.variationWandKey = new NamespacedKey(plugin, "variation_wand");
        this.bookshelfCategoryWandKey = new NamespacedKey(plugin, "bookshelf_category_wand");
        this.readingTableWandKey = new NamespacedKey(plugin, "reading_table_wand");
        this.readingSeatWandKey = new NamespacedKey(plugin, "reading_seat_wand");
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Infinity Library Selection Wand");
        meta.getPersistentDataContainer().set(selectionWandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public ItemStack createConnectionWand() {
        ItemStack wand = new ItemStack(Material.END_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Infinity Library Connection Wand");
        meta.setLore(List.of(ChatColor.GRAY + "Click blocks inside a selected room", ChatColor.GRAY + "to stage connection points."));
        meta.getPersistentDataContainer().set(connectionWandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }


    public ItemStack createBookshelfCategoryWand() {
        ItemStack wand = new ItemStack(Material.BOOKSHELF);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Infinity Library Bookshelf Category Wand");
        meta.setLore(List.of(ChatColor.GRAY + "Select two corners to categorize", ChatColor.GRAY + "all chiseled bookshelves in the area."));
        meta.getPersistentDataContainer().set(bookshelfCategoryWandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public ItemStack createReadingTableWand() { return createRegionWand(Material.LECTERN, ChatColor.YELLOW + "Infinity Library Reading Table Wand", readingTableWandKey); }
    public ItemStack createReadingSeatWand() { return createRegionWand(Material.OAK_SLAB, ChatColor.YELLOW + "Infinity Library Reading Seat Wand", readingSeatWandKey); }
    private ItemStack createRegionWand(Material material, String name, NamespacedKey key) {
        ItemStack wand = new ItemStack(material);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(ChatColor.GRAY + "Select two corners of this", ChatColor.GRAY + "reading structure region."));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isSelectionWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(selectionWandKey, PersistentDataType.BYTE);
    }

    public boolean isConnectionWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(connectionWandKey, PersistentDataType.BYTE);
    }
    public boolean isVariationWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(variationWandKey, PersistentDataType.BYTE);
    }
    public ItemStack createVariationWand() {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Infinity Library Variation Wand");
        meta.getPersistentDataContainer().set(variationWandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }


    private boolean isBookshelfCategoryWand(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(bookshelfCategoryWandKey, PersistentDataType.BYTE); }
    private boolean isReadingTableWand(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(readingTableWandKey, PersistentDataType.BYTE); }
    private boolean isReadingSeatWand(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(readingSeatWandKey, PersistentDataType.BYTE); }

    public void setBookshelfCategoryMode(Player player, String category) {
        bookshelfCategoryModes.put(player.getUniqueId(), category == null || category.isBlank() ? "general" : category);
        player.sendMessage(ChatColor.GOLD + "Bookshelf category wand set to category: " + bookshelfCategoryModes.get(player.getUniqueId()));
    }

    public boolean isReadingTable(org.bukkit.Location location) { return readingTables.contains(locationKey(location)); }
    public boolean isReadingSeat(org.bukkit.Location location) { return readingSeats.contains(locationKey(location)); }

    public Mode mode(Player player) {
        return modes.getOrDefault(player.getUniqueId(), Mode.POS1);
    }

    public void setMode(Player player, String modeName) {
        Mode mode = Mode.valueOf(modeName.toUpperCase(Locale.ROOT));
        modes.put(player.getUniqueId(), mode);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Selection wand mode set to " + mode.name() + ".");
    }

    public void setConnectionPrefix(Player player, String prefix) {
        String effective = prefix == null || prefix.isBlank() ? "conn" : prefix;
        connectionPrefixes.put(player.getUniqueId(), effective);
        player.sendMessage(ChatColor.AQUA + "Connection wand prefix set to " + effective + ". Direction and size are auto-detected from your 2-point selection.");
    }

    public List<ConnectionPoint> stagedConnections(Player player) {
        return List.copyOf(stagedConnections.getOrDefault(player.getUniqueId(), List.of()));
    }

    public void addStagedConnection(Player player, ConnectionPoint connectionPoint) {
        stagedConnections.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(connectionPoint);
    }

    public void clearStagedConnections(Player player) {
        stagedConnections.remove(player.getUniqueId());
        connectionCounters.remove(player.getUniqueId());
        pendingConnectionStart.remove(player.getUniqueId());
    }
    public void setVariationSettings(Player player, String category, double chance, String prefix) {
        variationSettings.put(player.getUniqueId(), new VariationSettings(category, chance, prefix));
    }
    public List<VariationArea> stagedVariations(Player player) { return List.copyOf(stagedVariations.getOrDefault(player.getUniqueId(), List.of())); }
    public void clearStagedVariations(Player player) { stagedVariations.remove(player.getUniqueId()); pendingVariationStart.remove(player.getUniqueId()); variationCounters.remove(player.getUniqueId()); }

    public boolean handle(Player player, Action action, Block clickedBlock) {
        if (clickedBlock == null) return false;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (isConnectionWand(held)) return handleConnectionWand(player, action, clickedBlock);
        if (isVariationWand(held)) return handleVariationWand(player, action, clickedBlock);
        if (isBookshelfCategoryWand(held)) return handleBookshelfCategoryWand(player, action, clickedBlock);
        if (isReadingTableWand(held)) return handleReadingRegionWand(player, action, clickedBlock, true);
        if (isReadingSeatWand(held)) return handleReadingRegionWand(player, action, clickedBlock, false);
        if (!isSelectionWand(held)) return false;
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return false;
        Mode mode = mode(player);
        if (mode == Mode.POS1 || action == Action.LEFT_CLICK_BLOCK) {
            plugin.getRoomManager().setPos1(player, clickedBlock.getLocation());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Selection position 1 set to " + format(clickedBlock));
        } else {
            plugin.getRoomManager().setPos2(player, clickedBlock.getLocation());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Selection position 2 set to " + format(clickedBlock));
        }
        return true;
    }


    private boolean handleBookshelfCategoryWand(Player player, Action action, Block clickedBlock) {
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return false;
        UUID id = player.getUniqueId();
        Vector3i current = absolute(clickedBlock);
        Vector3i first = pendingBookshelfCategoryStart.get(id);
        if (first == null) { pendingBookshelfCategoryStart.put(id, current); player.sendMessage(ChatColor.GOLD + "Bookshelf category point 1 set."); return true; }
        pendingBookshelfCategoryStart.remove(id);
        Vector3i min = min(first, current), max = max(first, current);
        String category = bookshelfCategoryModes.getOrDefault(id, "general");
        int changed = 0;
        for (int x=min.x(); x<=max.x(); x++) for (int y=min.y(); y<=max.y(); y++) for (int z=min.z(); z<=max.z(); z++) {
            Block block = clickedBlock.getWorld().getBlockAt(x, y, z);
            if (block.getType() == Material.CHISELED_BOOKSHELF) { plugin.getBookStorageManager().setShelfCategory(block.getLocation(), category); changed++; }
        }
        player.sendMessage(ChatColor.GOLD + "Categorized " + changed + " chiseled bookshelf(s) as " + category + ".");
        return true;
    }

    private boolean handleReadingRegionWand(Player player, Action action, Block clickedBlock, boolean table) {
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return false;
        UUID id = player.getUniqueId();
        Map<UUID, Vector3i> pending = table ? pendingReadingTableStart : pendingReadingSeatStart;
        Vector3i current = absolute(clickedBlock);
        Vector3i first = pending.get(id);
        if (first == null) { pending.put(id, current); player.sendMessage(ChatColor.YELLOW + (table ? "Reading table" : "Reading seat") + " point 1 set."); return true; }
        pending.remove(id);
        Vector3i min = min(first, current), max = max(first, current);
        Set<String> target = table ? readingTables : readingSeats;
        int changed = 0;
        for (int x=min.x(); x<=max.x(); x++) for (int y=min.y(); y<=max.y(); y++) for (int z=min.z(); z<=max.z(); z++) { target.add(locationKey(clickedBlock.getWorld().getName(), x, y, z)); changed++; }
        player.sendMessage(ChatColor.YELLOW + "Marked " + changed + " block(s) as a " + (table ? "reading table" : "reading seat") + " area.");
        return true;
    }

    private boolean handleVariationWand(Player player, Action action, Block clickedBlock) {
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return false;
        Vector3i relative = plugin.getRoomManager().relativeToSelection(player, clickedBlock.getLocation());
        UUID id = player.getUniqueId();
        Vector3i first = pendingVariationStart.get(id);
        if (first == null) { pendingVariationStart.put(id, relative); player.sendMessage(ChatColor.GREEN + "Variation point 1 set at " + relative); return true; }
        pendingVariationStart.remove(id);
        VariationSettings settings = variationSettings.getOrDefault(id, new VariationSettings("decor", 100.0, "var"));
        Vector3i min = min(first, relative), max = max(first, relative);
        int index = variationCounters.merge(id, 1, Integer::sum);
        String category = settings.category;
        String variationId = settings.prefix + "_" + index;
        Vector3i size = new Vector3i(max.x()-min.x()+1, max.y()-min.y()+1, max.z()-min.z()+1);
        if (category.endsWith("*")) {
            String base = category.substring(0, category.length() - 1);
            Map<String, Integer> byBase = wildcardVariationCounters.computeIfAbsent(id, k -> new HashMap<>());
            int wildcardIndex = byBase.merge(base, 1, Integer::sum);
            category = base;
            variationId = settings.prefix + "_" + base + wildcardIndex;
            Map<String, Vector3i> bySize = wildcardVariationSizes.computeIfAbsent(id, k -> new HashMap<>());
            Vector3i expected = bySize.putIfAbsent(base, size);
            if (expected != null && !expected.equals(size)) throw new IllegalArgumentException("Wildcard variation '" + base + "*' must use same area size: expected " + expected + " got " + size);
        }
        List<RoomBlock> blocks = new ArrayList<>();
        Block roomOriginBlock = player.getWorld().getBlockAt(plugin.getRoomManager().selectionBounds(player).min().x(), plugin.getRoomManager().selectionBounds(player).min().y(), plugin.getRoomManager().selectionBounds(player).min().z());
        for (int x=min.x();x<=max.x();x++) for (int y=min.y();y<=max.y();y++) for (int z=min.z();z<=max.z();z++) {
            Block b = roomOriginBlock.getWorld().getBlockAt(roomOriginBlock.getX()+x, roomOriginBlock.getY()+y, roomOriginBlock.getZ()+z);
            blocks.add(new RoomBlock(new Vector3i(x,y,z), b.getBlockData().getAsString()));
        }
        stagedVariations.computeIfAbsent(id, k -> new ArrayList<>()).add(new VariationArea(variationId, category, settings.chancePercent, blocks));
        player.sendMessage(ChatColor.GREEN + "Variation staged: " + category + " chance=" + settings.chancePercent + "%" + (settings.category.endsWith("*") ? " (wildcard group)" : ""));
        return true;
    }
    private record VariationSettings(String category, double chancePercent, String prefix) {}

    private boolean handleConnectionWand(Player player, Action action, Block clickedBlock) {
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return false;
        try {
            Vector3i relative = plugin.getRoomManager().relativeToSelection(player, clickedBlock.getLocation());
            UUID playerId = player.getUniqueId();
            Vector3i first = pendingConnectionStart.get(playerId);
            if (first == null) {
                pendingConnectionStart.put(playerId, relative);
                player.sendMessage(ChatColor.AQUA + "Connection point 1 set at " + relative + ". Click point 2 to finish this connection.");
                return true;
            }
            pendingConnectionStart.remove(playerId);
            RoomBounds bounds = RoomBounds.from(plugin.getRoomManager().selectionBounds(player));
            Vector3i min = min(first, relative);
            Vector3i max = max(first, relative);
            BlockFace face = detectOutwardFace(min, max, bounds);
            int width = face == BlockFace.NORTH || face == BlockFace.SOUTH ? max.x() - min.x() + 1 : face == BlockFace.EAST || face == BlockFace.WEST ? max.z() - min.z() + 1 : Math.max(max.x() - min.x() + 1, max.z() - min.z() + 1);
            int height = face == BlockFace.UP || face == BlockFace.DOWN ? 1 : max.y() - min.y() + 1;
            Vector3i center = new Vector3i((min.x() + max.x()) / 2, min.y(), (min.z() + max.z()) / 2);
            String prefix = connectionPrefixes.getOrDefault(playerId, "conn");
            int number = connectionCounters.merge(player.getUniqueId(), 1, Integer::sum);
            ConnectionPoint connectionPoint = new ConnectionPoint(prefix + "_" + number, center, face, width, height);
            addStagedConnection(player, connectionPoint);
            player.sendMessage(ChatColor.AQUA + "Staged connection " + connectionPoint.id() + " at " + center + " facing " + face.name() + " (" + width + "x" + height + ").");
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
        }
        return true;
    }

    private BlockFace detectOutwardFace(Vector3i min, Vector3i max, RoomBounds bounds) {
        List<BlockFace> touchingFaces = new ArrayList<>();
        if (min.x() == bounds.minX) touchingFaces.add(BlockFace.WEST);
        if (max.x() == bounds.maxX) touchingFaces.add(BlockFace.EAST);
        if (min.y() == bounds.minY) touchingFaces.add(BlockFace.DOWN);
        if (max.y() == bounds.maxY) touchingFaces.add(BlockFace.UP);
        if (min.z() == bounds.minZ) touchingFaces.add(BlockFace.NORTH);
        if (max.z() == bounds.maxZ) touchingFaces.add(BlockFace.SOUTH);
        if (touchingFaces.size() != 1) throw new IllegalArgumentException("Connection selection must touch exactly one outer room face. Current selection touches " + touchingFaces.size() + ".");
        return touchingFaces.get(0);
    }

    private Vector3i min(Vector3i a, Vector3i b) { return new Vector3i(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())); }
    private Vector3i max(Vector3i a, Vector3i b) { return new Vector3i(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())); }
    private Vector3i absolute(Block block) { return new Vector3i(block.getX(), block.getY(), block.getZ()); }
    private String locationKey(org.bukkit.Location location) { return locationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    private String locationKey(String world, int x, int y, int z) { return world + ";" + x + ";" + y + ";" + z; }

    private record RoomBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static RoomBounds from(com.infinitylibrary.room.RoomManager.SelectionBounds bounds) {
            Vector3i min = bounds.min();
            Vector3i max = bounds.max();
            return new RoomBounds(0, 0, 0, max.x() - min.x(), max.y() - min.y(), max.z() - min.z());
        }
    }

    private String format(Block block) {
        return block.getX() + ", " + block.getY() + ", " + block.getZ();
    }

}
