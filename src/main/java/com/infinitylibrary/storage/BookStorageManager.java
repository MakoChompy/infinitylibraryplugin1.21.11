package com.infinitylibrary.storage;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BookStorageManager {
    private final InfinityLibraryPlugin plugin;
    private final File file;
    private final File dailyFile;
    private final NamespacedKey ownerUuidKey;
    private final NamespacedKey ownerNameKey;
    private final NamespacedKey publicKey;
    private final NamespacedKey categoryKey;
    private final NamespacedKey tagsKey;
    private final NamespacedKey ratingKey;
    private final NamespacedKey commentsKey;
    private final Map<UUID, StoredBook> books = new ConcurrentHashMap<>();
    private final Set<UUID> awaitingSearch = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingBookMetadata> pendingMetadata = new ConcurrentHashMap<>();
    private final Map<String, String> shelfCategories = new ConcurrentHashMap<>();
    private YamlConfiguration daily;

    public BookStorageManager(InfinityLibraryPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "books.yml");
        this.dailyFile = new File(plugin.getDataFolder(), "daily-books.yml");
        this.ownerUuidKey = new NamespacedKey(plugin, "book_owner_uuid");
        this.ownerNameKey = new NamespacedKey(plugin, "book_owner_name");
        this.publicKey = new NamespacedKey(plugin, "book_public");
        this.categoryKey = new NamespacedKey(plugin, "book_category");
        this.tagsKey = new NamespacedKey(plugin, "book_tags");
        this.ratingKey = new NamespacedKey(plugin, "book_rating");
        this.commentsKey = new NamespacedKey(plugin, "book_comments");
    }

    public void load() {
        books.clear();
        daily = YamlConfiguration.loadConfiguration(dailyFile);
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("books");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try { StoredBook b = StoredBook.read(UUID.fromString(key), root.getConfigurationSection(key)); books.put(b.id(), b); }
            catch (Exception ex) { plugin.getLogger().warning("Skipping invalid stored book " + key + ": " + ex.getMessage()); }
        }
        ConfigurationSection shelves = y.getConfigurationSection("shelf-categories");
        if (shelves != null) for (String key : shelves.getKeys(false)) shelfCategories.put(key, shelves.getString(key, ""));
    }

    public void saveAsync() { Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveNow); }
    public synchronized void saveNow() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("books");
        for (StoredBook b : books.values()) b.write(root.createSection(b.id().toString()));
        ConfigurationSection shelfRoot = y.createSection("shelf-categories");
        for (var e : shelfCategories.entrySet()) shelfRoot.set(e.getKey(), e.getValue());
        try { y.save(file); if (daily != null) daily.save(dailyFile); } catch (IOException e) { plugin.getLogger().severe("Unable to save book data: " + e.getMessage()); }
    }

    public boolean giveDailyWritableBook(Player player) {
        String today = LocalDate.now().toString();
        String path = "players." + player.getUniqueId() + ".last-claimed";
        if (!player.hasPermission("infinitylibrary.book.bypassdaily") && today.equals(daily.getString(path))) return false;
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Private Library Draft");
        tagLibraryBook(meta, player, false);
        book.setItemMeta(meta);
        player.getInventory().addItem(book);
        daily.set(path, today);
        saveAsync();
        return true;
    }

    public void recordBook(Player contributor, ItemStack stack, Location shelfLocation) {
        if (stack == null || stack.getType() != Material.WRITTEN_BOOK || !(stack.getItemMeta() instanceof BookMeta meta)) return;
        UUID id = UUID.randomUUID();
        BookOwnership ownership = ownership(meta, contributor);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String fp = fingerprint(meta);
        Optional<StoredBook> existing = books.values().stream().filter(b -> fp.equals(fingerprint((BookMeta) Objects.requireNonNull(b.toItemStack().getItemMeta())))).findFirst();
        if (existing.isPresent()) { books.put(existing.get().id(), existing.get().withLocation(serializeLocation(shelfLocation))); saveAsync(); return; }
        books.put(id, new StoredBook(id, contributor.getUniqueId(), contributor.getName(), ownership.ownerUuid(), ownership.ownerName(), ownership.isPublic(), safe(meta.getTitle()), safe(meta.getAuthor()), List.copyOf(meta.getPages()), stack.serialize(), Instant.now().toString(), serializeLocation(shelfLocation), safe(pdc.get(categoryKey, PersistentDataType.STRING)), safe(pdc.get(tagsKey, PersistentDataType.STRING)), safe(pdc.get(ratingKey, PersistentDataType.STRING)), safe(pdc.get(commentsKey, PersistentDataType.STRING))));
        saveAsync();
    }

    public void beginMetadataPrompt(Player player, ItemStack stack, Location shelfLocation) {
        pendingMetadata.put(player.getUniqueId(), new PendingBookMetadata(stack.clone(), shelfLocation));
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Enter book metadata as: <category>|<rating>|<comments> (or cancel)");
    }

    public void setHeldBookMetadata(Player player, String category, String tags, String rating, String comments) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || (item.getType() != Material.WRITTEN_BOOK && item.getType() != Material.WRITABLE_BOOK) || !item.hasItemMeta()) throw new IllegalArgumentException("Hold a library book first.");
        ItemMeta meta = item.getItemMeta();
        ensureOwner(meta, player);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (category != null) pdc.set(categoryKey, PersistentDataType.STRING, category);
        if (tags != null) pdc.set(tagsKey, PersistentDataType.STRING, tags);
        if (rating != null) pdc.set(ratingKey, PersistentDataType.STRING, rating);
        if (comments != null) pdc.set(commentsKey, PersistentDataType.STRING, comments);
        item.setItemMeta(meta);
    }

    public int totalBooks() { return books.size(); }
    public long totalContributors() { return books.values().stream().map(StoredBook::contributor).distinct().count(); }
    public long playerCount(UUID uuid) { return books.values().stream().filter(b -> b.contributor().equals(uuid)).count(); }
    public Collection<StoredBook> allBooks() { return Collections.unmodifiableCollection(books.values()); }

    public Optional<ItemStack> randomBook() {
        if (books.isEmpty()) return Optional.empty();
        List<StoredBook> list = new ArrayList<>(books.values());
        return Optional.ofNullable(list.get(ThreadLocalRandom.current().nextInt(list.size())).toItemStack());
    }

    public void populateBookshelf(Block block) {
        if (block.getType() != Material.CHISELED_BOOKSHELF || books.isEmpty()) return;
        Inventory inv = bookshelfInventory(block);
        if (inv == null) return;
        String shelfLoc = serializeLocation(block.getLocation());
        List<StoredBook> unplaced = books.values().stream().filter(b -> b.shelfLocation() == null || b.shelfLocation().isBlank() || b.shelfLocation().equals(shelfLoc)).toList();
        if (unplaced.isEmpty()) return;
        int slot = 0;
        for (StoredBook book : unplaced) {
            if (slot >= Math.min(inv.getSize(), 6)) break;
            inv.setItem(slot++, book.toItemStack());
            books.put(book.id(), book.withLocation(shelfLoc));
        }
        saveAsync();
    }

    public void beginSearchPrompt(Player player) {
        awaitingSearch.add(player.getUniqueId());
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Type a book title search in chat. Type cancel to stop.");
    }

    public boolean handleSearchChat(Player player, String query) {
        if (!awaitingSearch.remove(player.getUniqueId())) return false;
        if (query.equalsIgnoreCase("cancel")) { player.sendMessage(ChatColor.GRAY + "Book search cancelled."); return true; }
        Optional<StoredBook> result = books.values().stream()
                .filter(book -> book.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .filter(book -> book.shelfLocation() != null && !book.shelfLocation().isBlank())
                .findFirst();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (result.isEmpty()) { player.sendMessage(ChatColor.RED + "No stored book with a known shelf location matched: " + query); return; }
            Location target = deserializeLocation(result.get().shelfLocation());
            if (target == null) { player.sendMessage(ChatColor.RED + "That book has no valid shelf location yet."); return; }
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Showing particles to: " + result.get().title());
            traceParticles(player, target);
        });
        return true;
    }

    public boolean handleMetadataChat(Player player, String input) {
        PendingBookMetadata pending = pendingMetadata.remove(player.getUniqueId());
        if (pending == null) return false;
        if (input.equalsIgnoreCase("cancel")) { player.sendMessage(ChatColor.GRAY + "Book metadata input cancelled."); return true; }
        String[] parts = input.split("\\|", 3);
        String category = parts.length > 0 ? parts[0] : "general";
        String rating = parts.length > 1 ? parts[1] : "unrated";
        String comments = parts.length > 2 ? parts[2] : "";
        if (pending.stack().getItemMeta() instanceof BookMeta meta) {
            meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, category);
            meta.getPersistentDataContainer().set(ratingKey, PersistentDataType.STRING, rating);
            meta.getPersistentDataContainer().set(commentsKey, PersistentDataType.STRING, comments);
            pending.stack().setItemMeta(meta);
        }
        recordBook(player, pending.stack(), pending.shelfLocation());
        player.sendMessage(ChatColor.GREEN + "Book saved with metadata.");
        return true;
    }

    public void setShelfCategory(Location shelf, String category) { shelfCategories.put(serializeLocation(shelf), category); saveAsync(); }
    public String shelfCategory(Location shelf) { return shelfCategories.getOrDefault(serializeLocation(shelf), ""); }
    private String fingerprint(BookMeta meta) { return safe(meta.getTitle()) + "|" + safe(meta.getAuthor()) + "|" + String.join("\n", meta.getPages()); }
    private record PendingBookMetadata(ItemStack stack, Location shelfLocation) {}

    public boolean canRead(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) return true;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Byte publicFlag = pdc.get(publicKey, PersistentDataType.BYTE);
        String owner = pdc.get(ownerUuidKey, PersistentDataType.STRING);
        if (owner == null || publicFlag == null || publicFlag == (byte) 1) return true;
        return owner.equals(player.getUniqueId().toString());
    }

    public void setHeldBookPublic(Player player, boolean isPublic) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || (item.getType() != Material.WRITTEN_BOOK && item.getType() != Material.WRITABLE_BOOK) || !item.hasItemMeta()) throw new IllegalArgumentException("Hold a library book first.");
        ItemMeta meta = item.getItemMeta();
        ensureOwner(meta, player);
        meta.getPersistentDataContainer().set(publicKey, PersistentDataType.BYTE, (byte) (isPublic ? 1 : 0));
        item.setItemMeta(meta);
    }

    public void convertHeldWrittenBookToEditable(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() != Material.WRITTEN_BOOK || !(held.getItemMeta() instanceof BookMeta oldMeta)) throw new IllegalArgumentException("Hold a written library book first.");
        BookOwnership ownership = ownership(oldMeta, player);
        if (!ownership.ownerUuid().equals(player.getUniqueId().toString()) && !ownership.ownerName().equalsIgnoreCase(player.getName())) throw new IllegalArgumentException("Only the linked owner can edit this book.");
        ItemStack editable = new ItemStack(Material.WRITABLE_BOOK, held.getAmount());
        BookMeta newMeta = (BookMeta) editable.getItemMeta();
        newMeta.setPages(oldMeta.getPages());
        tagLibraryBook(newMeta, player, ownership.isPublic());
        editable.setItemMeta(newMeta);
        player.getInventory().setItemInMainHand(editable);
    }

    private void traceParticles(Player player, Location target) {
        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int ticks;
            @Override public void run() {
                Location from = player.getEyeLocation();
                World world = from.getWorld();
                if (world == null || !world.equals(target.getWorld()) || ticks++ > 200) return;
                for (double t = 0; t <= 1.0; t += 0.08) {
                    double x = from.getX() + (target.getX() + 0.5 - from.getX()) * t;
                    double y = from.getY() + (target.getY() + 0.5 - from.getY()) * t;
                    double z = from.getZ() + (target.getZ() + 0.5 - from.getZ()) * t;
                    world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }, 0L, 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getScheduler().cancelTask(task), 205L);
    }

    private void markBookLocation(ItemStack item, Location location) {
        for (StoredBook book : books.values()) {
            if (book.toItemStack().isSimilar(item)) {
                books.put(book.id(), book.withLocation(serializeLocation(location)));
                saveAsync();
                return;
            }
        }
    }

    private void tagLibraryBook(ItemMeta meta, Player player, boolean isPublic) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ownerUuidKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(ownerNameKey, PersistentDataType.STRING, player.getName());
        pdc.set(publicKey, PersistentDataType.BYTE, (byte) (isPublic ? 1 : 0));
    }

    private void ensureOwner(ItemMeta meta, Player player) {
        if (!meta.getPersistentDataContainer().has(ownerUuidKey, PersistentDataType.STRING)) tagLibraryBook(meta, player, false);
    }

    private BookOwnership ownership(ItemMeta meta, Player fallback) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String ownerUuid = pdc.get(ownerUuidKey, PersistentDataType.STRING);
        String ownerName = pdc.get(ownerNameKey, PersistentDataType.STRING);
        Byte publicFlag = pdc.get(publicKey, PersistentDataType.BYTE);
        return new BookOwnership(ownerUuid == null ? fallback.getUniqueId().toString() : ownerUuid, ownerName == null ? fallback.getName() : ownerName, publicFlag != null && publicFlag == (byte) 1);
    }

    private Inventory bookshelfInventory(Block block) {
        try {
            BlockState state = block.getState();
            if (state instanceof InventoryHolder holder) return holder.getInventory();
            Method method = state.getClass().getMethod("getInventory");
            Object result = method.invoke(state);
            return result instanceof Inventory inv ? inv : null;
        } catch (ReflectiveOperationException ignored) { return null; }
    }

    private String serializeLocation(Location location) { return location == null || location.getWorld() == null ? "" : location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ(); }
    private Location deserializeLocation(String value) {
        try { String[] p = value.split(";"); World w = Bukkit.getWorld(p[0]); return w == null ? null : new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])); }
        catch (Exception ex) { return null; }
    }
    private String safe(String value) { return value == null ? "" : value; }
    private record BookOwnership(String ownerUuid, String ownerName, boolean isPublic) {}

    public record StoredBook(UUID id, UUID contributor, String contributorName, String ownerUuid, String ownerName, boolean isPublic, String title, String author, List<String> pages, Map<String, Object> item, String insertedAt, String shelfLocation, String category, String tags, String rating, String comments) {
        @SuppressWarnings("unchecked") public ItemStack toItemStack() { return ItemStack.deserialize(item); }
        public StoredBook withLocation(String location) { return new StoredBook(id, contributor, contributorName, ownerUuid, ownerName, isPublic, title, author, pages, item, insertedAt, location, category, tags, rating, comments); }
        public String displayContributor() {
            OfflinePlayer p = Bukkit.getOfflinePlayer(contributor);
            return p.getName() != null ? p.getName() : contributorName;
        }
        public void write(ConfigurationSection s) {
            s.set("contributor", contributor.toString()); s.set("contributor-name", contributorName); s.set("owner-uuid", ownerUuid); s.set("owner-name", ownerName); s.set("public", isPublic); s.set("title", title); s.set("author", author); s.set("pages", pages); s.set("item", item); s.set("inserted-at", insertedAt); s.set("shelf-location", shelfLocation); s.set("category", category); s.set("tags", tags); s.set("rating", rating); s.set("comments", comments);
        }
        public static StoredBook read(UUID id, ConfigurationSection s) {
            String contributor = s.getString("contributor");
            return new StoredBook(id, UUID.fromString(contributor), s.getString("contributor-name", "Unknown"), s.getString("owner-uuid", contributor), s.getString("owner-name", s.getString("contributor-name", "Unknown")), s.getBoolean("public", false), s.getString("title", ""), s.getString("author", ""), s.getStringList("pages"), s.getConfigurationSection("item").getValues(false), s.getString("inserted-at", ""), s.getString("shelf-location", ""), s.getString("category", ""), s.getString("tags", ""), s.getString("rating", ""), s.getString("comments", ""));
        }
    }
}
