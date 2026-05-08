package com.infinitylibrary.command;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.model.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final InfinityLibraryPlugin plugin;
    public CommandHandler(InfinityLibraryPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(sender); return true; }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "gui", "stats" -> { if (sender instanceof Player p) plugin.getGuiManager().openStats(p); else msg(sender, "Players only."); }
                case "roomgui" -> player(sender, p -> plugin.getGuiManager().openRoomEditor(p));
                case "wand" -> adminPlayer(sender, p -> { p.getInventory().addItem(plugin.getSelectionManager().createWand()); msg(p, "Selection wand added. Use /il wandmode pos1 or /il wandmode pos2, then click blocks while holding it."); });
                case "connectionwand", "connwand" -> adminPlayer(sender, p -> { p.getInventory().addItem(plugin.getSelectionManager().createConnectionWand()); msg(p, "Connection wand added. Click 2 points on one outer room face to auto-detect connection direction and size. Optional: /il connectionmode [prefix]"); });
                case "variationwand", "varwand" -> adminPlayer(sender, p -> { p.getInventory().addItem(plugin.getSelectionManager().createVariationWand()); msg(p, "Variation wand added. Use /il variationmode <category> <chancePercent> [prefix], then click 2 points."); });
                case "wandmode" -> adminPlayer(sender, p -> { require(args, 2, "/il wandmode <pos1|pos2>"); plugin.getSelectionManager().setMode(p, args[1]); });
                case "connectionmode", "connmode" -> adminPlayer(sender, p -> setConnectionMode(p, args));
                case "pos1" -> adminPlayer(sender, p -> { plugin.getRoomManager().setPos1(p); msg(p, "Selection position 1 set."); });
                case "pos2" -> adminPlayer(sender, p -> { plugin.getRoomManager().setPos2(p); msg(p, "Selection position 2 set."); });
                case "clearconnections" -> adminPlayer(sender, p -> { plugin.getSelectionManager().clearStagedConnections(p);
        plugin.getSelectionManager().clearStagedVariations(p); msg(p, "Staged connections cleared."); });
                case "clearvariations" -> adminPlayer(sender, p -> { plugin.getSelectionManager().clearStagedVariations(p); msg(p, "Staged variations cleared."); });
                case "variationmode", "varmode" -> adminPlayer(sender, p -> setVariationMode(p, args));
                case "addconnection" -> adminPlayer(sender, p -> addConnection(p, args));
                case "saveroom", "editroom", "savedefault" -> adminPlayer(sender, p -> saveRoom(p, args));
                case "applyvariations" -> adminPlayer(sender, p -> applyVariations(p, args));
                case "setrange" -> adminPlayer(sender, p -> setRange(p, args));
                case "genrange" -> adminPlayer(sender, p -> generateRange(p));
                case "toggleblocking" -> adminPlayer(sender, p -> toggleBlocking(p));
                case "setblocker" -> adminPlayer(sender, p -> setBlocker(p, args));
                case "deleteroom" -> { requireAdmin(sender); require(args, 2, "/il deleteroom <id>"); plugin.getRoomManager().delete(args[1]); msg(sender, "Room deleted live: " + args[1]); }
                case "listrooms" -> msg(sender, "Rooms: " + String.join(", ", plugin.getRoomManager().allRooms().stream().map(Room::id).toList()));
                case "roomchance" -> adminPlayer(sender, p -> setRoomChance(p, args));
                case "reset" -> { requireAdmin(sender); plugin.getGenerationEngine().resetToStart(); msg(sender, "Infinity Library reset to only the starting room."); }
                case "home", "start" -> player(sender, this::teleportStart);
                case "setstart" -> adminPlayer(sender, p -> { plugin.getConfig().set("start-location.x", p.getLocation().getBlockX()); plugin.getConfig().set("start-location.y", p.getLocation().getBlockY()); plugin.getConfig().set("start-location.z", p.getLocation().getBlockZ()); plugin.saveConfig(); msg(p, "Start location updated."); });
                case "book" -> player(sender, p -> bookCommand(p, args));
                case "bookmeta" -> player(sender, p -> bookMetaCommand(p, args));
                case "shelfcategory" -> player(sender, p -> shelfCategoryCommand(p, args));
                case "reload" -> { requireAdmin(sender); plugin.reloadEverything(); msg(sender, "Infinity Library reloaded live."); }
                default -> help(sender);
            }
        } catch (Exception ex) { msg(sender, "&c" + ex.getMessage()); }
        return true;
    }

    private void addConnection(Player p, String[] args) {
        require(args, 6, "/il addconnection <id> <x,y,z> <NORTH|EAST|SOUTH|WEST|UP|DOWN> <width> <height> [floor]");
        int floor = args.length >= 7 ? Integer.parseInt(args[6]) : 0;
        ConnectionPoint cp = new ConnectionPoint(args[1], Vector3i.parse(args[2]), BlockFace.valueOf(args[3].toUpperCase(Locale.ROOT)), Integer.parseInt(args[4]), Integer.parseInt(args[5]), floor);
        plugin.getSelectionManager().addStagedConnection(p, cp);
        msg(p, "Connection staged: " + cp.id());
    }

    private void setConnectionMode(Player p, String[] args) {
        String prefix = args.length >= 2 ? args[1] : "conn";
        plugin.getSelectionManager().setConnectionPrefix(p, prefix);
    }


    private void setVariationMode(Player p, String[] args) {
        require(args, 3, "/il variationmode <category> <chancePercent> [prefix]");
        String prefix = args.length >= 4 ? args[3] : "var";
        plugin.getSelectionManager().setVariationSettings(p, args[1], Double.parseDouble(args[2]), prefix);
        msg(p, "Variation mode set: category=" + args[1] + " chance=" + args[2] + "% prefix=" + prefix);
    }

    private void saveRoom(Player p, String[] args) {
        require(args, 3, "/il saveroom <id> <FILLER|BOOK|READ>");
        List<ConnectionPoint> cps = plugin.getSelectionManager().stagedConnections(p);
        Room room = plugin.getRoomManager().capture(p, args[1], RoomType.parse(args[2]), cps);
        plugin.getSelectionManager().clearStagedConnections(p);
        plugin.getSelectionManager().clearStagedVariations(p);
        boolean generatedNow = plugin.getGenerationEngine().placeRoomImmediately(room.id());
        msg(p, "Room saved live: " + room.id() + " (" + room.type() + ")." + (generatedNow ? " Spawned instantly into the active library." : " It will be used automatically on future expansions."));
    }

    private void applyVariations(Player p, String[] args) {
        require(args, 2, "/il applyvariations <roomId>");
        List<VariationArea> vars = plugin.getSelectionManager().stagedVariations(p);
        if (vars.isEmpty()) throw new IllegalArgumentException("No staged variations to apply.");
        plugin.getRoomManager().appendVariations(args[1], vars);
        plugin.getSelectionManager().clearStagedVariations(p);
        msg(p, "Applied " + vars.size() + " variation area(s) to room " + args[1] + ".");
    }

    private void setRange(Player p, String[] args) {
        require(args, 2, "/il setrange <1-5>");
        int range = Integer.parseInt(args[1]);
        if (range < 1 || range > 5) throw new IllegalArgumentException("Range must be between 1 and 5");
        plugin.getConfig().set("generation.manual-range", range);
        plugin.saveConfig();
        msg(p, "Manual generation range set to " + range + " room(s).");
    }

    private void generateRange(Player p) {
        int range = plugin.getConfig().getInt("generation.manual-range", 1);
        int generated = plugin.getGenerationEngine().generateAroundPlayer(p, range);
        msg(p, "Generated " + generated + " room(s) around your position.");
    }

    private void toggleBlocking(Player p) {
        boolean enabled = plugin.getConfig().getBoolean("generation.path-blocking.enabled", true);
        plugin.getConfig().set("generation.path-blocking.enabled", !enabled);
        plugin.saveConfig();
        msg(p, "Path blocking is now " + (!enabled ? "enabled" : "disabled") + ".");
    }

    private void setBlocker(Player p, String[] args) {
        require(args, 2, "/il setblocker <material>");
        Material m = Material.matchMaterial(args[1]);
        if (m == null || !m.isBlock()) throw new IllegalArgumentException("Invalid block material");
        plugin.getConfig().set("generation.path-blocking.material", m.name());
        plugin.saveConfig();
        msg(p, "Path blocker material set to " + m.name() + ".");
    }


    private void setRoomChance(Player p, String[] args) {
        require(args, 3, "/il roomchance <roomId> <0-100>");
        double chance = Double.parseDouble(args[2]);
        if (chance < 0 || chance > 100) throw new IllegalArgumentException("Chance must be 0-100");
        plugin.getRoomManager().setRoomChance(args[1], chance);
        msg(p, "Room chance set: " + args[1] + " => " + chance + "%");
    }

    private void teleportStart(Player player) {
        World world = plugin.getGenerationEngine().ensureWorld();
        Location loc = new Location(world, plugin.getConfig().getInt("start-location.x") + 0.5, plugin.getConfig().getInt("start-location.y") + 1.0, plugin.getConfig().getInt("start-location.z") + 0.5, (float) plugin.getConfig().getDouble("start-location.yaw"), (float) plugin.getConfig().getDouble("start-location.pitch"));
        player.teleport(loc);
        msg(player, "Returned to the Infinity Library starting room.");
    }

    private void bookCommand(Player player, String[] args) {
        require(args, 2, "/il book <public|private|edit>");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "public" -> { plugin.getBookStorageManager().setHeldBookPublic(player, true); msg(player, "Held library book is now public."); }
            case "private" -> { plugin.getBookStorageManager().setHeldBookPublic(player, false); msg(player, "Held library book is now private."); }
            case "edit" -> { plugin.getBookStorageManager().convertHeldWrittenBookToEditable(player); msg(player, "Converted your linked written book back into an editable writable book."); }
            default -> throw new IllegalArgumentException("/il book <public|private|edit>");
        }
    }


    private void shelfCategoryCommand(Player player, String[] args) {
        require(args, 2, "/il shelfcategory <category>");
        org.bukkit.block.Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType() != Material.CHISELED_BOOKSHELF) throw new IllegalArgumentException("Look at a chiseled bookshelf within 6 blocks.");
        plugin.getBookStorageManager().setShelfCategory(target.getLocation(), args[1]);
        msg(player, "Shelf category set to " + args[1] + ".");
    }

    private void bookMetaCommand(Player player, String[] args) {
        require(args, 5, "/il bookmeta <category> <rating> <tags_csv> <comments>");
        plugin.getBookStorageManager().setHeldBookMetadata(player, args[1], args[3], args[2], args[4]);
        msg(player, "Book metadata saved (category/rating/tags/comments).");
    }

    private void adminPlayer(CommandSender s, PlayerAction action) { requireAdmin(s); player(s, action); }
    private void player(CommandSender s, PlayerAction action) { if (!(s instanceof Player p)) throw new IllegalArgumentException("Players only."); action.run(p); }
    private void requireAdmin(CommandSender s) { if (!s.hasPermission("infinitylibrary.admin")) throw new IllegalArgumentException("Missing permission infinitylibrary.admin"); }
    private void require(String[] args, int n, String usage) { if (args.length < n) throw new IllegalArgumentException(usage); }
    private void msg(CommandSender s, String m) { s.sendMessage(ChatColor.translateAlternateColorCodes('&', "&5[InfinityLibrary] &f" + m)); }
    private void help(CommandSender s) { msg(s, "Commands: /il gui, roomgui, home, wand, connectionwand, variationwand, wandmode, connectionmode, variationmode, pos1, pos2, addconnection, clearconnections, clearvariations, saveroom, applyvariations, savedefault, editroom, deleteroom, listrooms, setrange, genrange, toggleblocking, setblocker, reset, setstart, book, bookmeta, shelfcategory, roomchance, reload"); }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return List.of("gui","roomgui","stats","home","start","wand","connectionwand","connwand","variationwand","varwand","wandmode","connectionmode","connmode","variationmode","varmode","pos1","pos2","addconnection","clearconnections","clearvariations","saveroom","applyvariations","savedefault","editroom","deleteroom","listrooms","setrange","genrange","toggleblocking","setblocker","reset","setstart","book","bookmeta","shelfcategory","roomchance","reload").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("wandmode")) return List.of("pos1", "pos2");
        if (args.length == 2 && (args[0].equalsIgnoreCase("connectionmode") || args[0].equalsIgnoreCase("connmode"))) return List.of("conn");
        if (args.length == 2 && args[0].equalsIgnoreCase("book")) return List.of("public", "private", "edit");
        if (args.length == 3 && (args[0].equalsIgnoreCase("saveroom") || args[0].equalsIgnoreCase("editroom") || args[0].equalsIgnoreCase("savedefault"))) return List.of("FILLER","BOOK","READ");
        return List.of();
    }
    private interface PlayerAction { void run(Player player); }
}
