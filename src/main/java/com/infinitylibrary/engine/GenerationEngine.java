package com.infinitylibrary.engine;

import com.infinitylibrary.InfinityLibraryPlugin;
import com.infinitylibrary.model.*;
import com.infinitylibrary.room.RoomManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GenerationEngine {
    private final InfinityLibraryPlugin plugin;
    private final RoomManager roomManager;
    private final File file;
    private final List<PlacedRoom> placed = new ArrayList<>();
    private final Queue<Runnable> placementQueue = new ConcurrentLinkedQueue<>();
    private final Set<Vector3i> sealedBlocks = new HashSet<>();
    private boolean running;
    private int taskId = -1;

    public GenerationEngine(InfinityLibraryPlugin plugin, RoomManager roomManager) {
        this.plugin = plugin; this.roomManager = roomManager; this.file = new File(plugin.getDataFolder(), "generated.yml");
    }

    public void start() {
        running = true;
        load();
        ensureWorld();
        if (placed.isEmpty()) resetToStart();
        int perTick = plugin.getConfig().getInt("generation.placement-blocks-per-tick", 1800);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> { for (int i=0;i<perTick;i++) { Runnable r = placementQueue.poll(); if (r == null) break; r.run(); } }, 1L, 1L);
    }
    public void stop() { running = false; if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); save(); }
    public World ensureWorld() {
        String name = plugin.getConfig().getString("world", "InfinityLibrary");
        World w = Bukkit.getWorld(name);
        if (w == null) w = Bukkit.createWorld(new WorldCreator(name).environment(World.Environment.NORMAL));
        return Objects.requireNonNull(w);
    }

    public void tickPlayer(Player player) {
        if (!running || !player.getWorld().getName().equals(plugin.getConfig().getString("world", "InfinityLibrary"))) return;
        double radius = plugin.getConfig().getDouble("proximity-radius", 9.0);
        Location loc = player.getLocation();
        List<Expansion> expansions = new ArrayList<>();
        synchronized (placed) {
            for (PlacedRoom pr : placed) roomManager.get(pr.roomId()).ifPresent(room -> {
                for (ConnectionPoint cp : room.connections()) if (!pr.generatedConnections().contains(cp.id())) {
                    Location cLoc = pr.origin().add(cp.position()).toLocation(player.getWorld()).add(0.5, 0.5, 0.5);
                    if (cLoc.distanceSquared(loc) <= radius * radius) expansions.add(new Expansion(pr, cp));
                }
            });
        }
        int max = plugin.getConfig().getInt("generation.max-rooms-per-tick", 1);
        for (int i=0; i<Math.min(max, expansions.size()); i++) expand(expansions.get(i));
    }

    private void expand(Expansion expansion) {
        PlacedRoom parent = expansion.parent(); ConnectionPoint target = expansion.point();
        if (parent.generatedConnections().contains(target.id())) return;
        List<RoomManager.RoomSelection> selections = roomManager.compatibleSelections(target, Math.random() < 0.35);
        int retries = Math.min(selections.size(), plugin.getConfig().getInt("generation.retry-room-selection", 16));
        Vector3i targetWorld = parent.origin().add(target.position());
        Vector3i attach = faceVector(target.direction());
        synchronized (placed) {
            for (int attempt = 0; attempt < retries; attempt++) {
                RoomManager.RoomSelection s = selections.get(attempt);
                Room room = s.room(); RoomTransform transform = s.transform();
                ConnectionPoint transformedCp = s.localConnection().transform(transform, room.size());
                Vector3i transformedSize = transform.transformedSize(room.size());
                Vector3i origin = targetWorld.add(attach).subtract(transformedCp.position());
                if (!isSafe(origin, transformedSize)) continue;
                parent.generatedConnections().add(target.id());
                PlacedRoom pr = new PlacedRoom(UUID.randomUUID(), room.id(), origin, transformedSize);
                pr.generatedConnections().add(transformedCp.id());
                placed.add(pr);
                queuePlacement(room, origin, transform);
                save();
                return;
            }
            parent.generatedConnections().add(target.id());
            sealConnection(parent, target);
            save();
        }
    }

    private void sealConnection(PlacedRoom parent, ConnectionPoint target) {
        if (!plugin.getConfig().getBoolean("generation.path-blocking.enabled", true)) return;
        World w = ensureWorld();
        Material blockMaterial = Material.matchMaterial(plugin.getConfig().getString("generation.path-blocking.material", "SMOOTH_STONE"));
        if (blockMaterial == null) blockMaterial = Material.SMOOTH_STONE;
        Vector3i base = parent.origin().add(target.position());
        Vector3i outward = faceVector(target.direction());
        for (int dy=0;dy<target.height();dy++) for (int dw=-(target.width()/2);dw<=target.width()/2;dw++) {
            int x = base.x() + outward.x(), y = base.y() + dy + outward.y(), z = base.z() + outward.z();
            if (target.direction()==BlockFace.NORTH || target.direction()==BlockFace.SOUTH) x += dw;
            else if (target.direction()==BlockFace.EAST || target.direction()==BlockFace.WEST) z += dw;
            w.getBlockAt(x,y,z).setType(blockMaterial, false);
            sealedBlocks.add(new Vector3i(x,y,z));
        }
    }

    public void resetToStart() {
        World w = ensureWorld();
        synchronized (placed) {
            for (PlacedRoom pr : placed) clearBox(w, pr.origin(), pr.size());
            for (Vector3i pos : sealedBlocks) w.getBlockAt(pos.x(), pos.y(), pos.z()).setType(Material.AIR, false);
            placed.clear(); placementQueue.clear();
            sealedBlocks.clear();
            Room start = roomManager.get(plugin.getConfig().getString("generation.start-room-id", "builtin_start")).orElseThrow();
            Vector3i origin = new Vector3i(plugin.getConfig().getInt("start-location.x"), plugin.getConfig().getInt("start-location.y"), plugin.getConfig().getInt("start-location.z"));
            PlacedRoom pr = new PlacedRoom(UUID.randomUUID(), start.id(), origin, start.size());
            placed.add(pr); queuePlacement(start, origin, RoomTransform.IDENTITY);
        }
        save();
    }

    private void queuePlacement(Room room, Vector3i origin, RoomTransform transform) {
        World w = ensureWorld();
        for (RoomBlock rb : room.blocks()) {
            Vector3i pos = origin.add(transform.transform(rb.position(), room.size()));
            BlockData data = Bukkit.createBlockData(rb.blockData()); rotateData(data, transform);
            placementQueue.add(() -> {
                Block block = w.getBlockAt(pos.x(), pos.y(), pos.z());
                block.setBlockData(data, false);
                if (block.getType() == Material.CHISELED_BOOKSHELF) plugin.getBookStorageManager().populateBookshelf(block);
                if (block.getState() instanceof Sign sign) updateLibrarySign(sign, pos);
            });
        }
        Map<String, List<VariationArea>> byCategory = new HashMap<>();
        for (VariationArea v : room.variations()) byCategory.computeIfAbsent(v.category(), k -> new ArrayList<>()).add(v);
        for (List<VariationArea> categoryVars : byCategory.values()) {
            Collections.shuffle(categoryVars);
            for (VariationArea v : categoryVars) {
                if (Math.random() * 100.0 > v.chancePercent()) continue;
                for (RoomBlock rb : v.blocks()) {
                    Vector3i pos = origin.add(transform.transform(rb.position(), room.size()));
                    BlockData data = Bukkit.createBlockData(rb.blockData()); rotateData(data, transform);
                    placementQueue.add(() -> w.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(data, false));
                }
                break;
            }
        }
    }

    private void rotateData(BlockData data, RoomTransform transform) {
        if (data instanceof Directional d) d.setFacing(transform.transform(d.getFacing()));
        if (data instanceof Rotatable r) r.setRotation(transform.transform(r.getRotation()));
    }

    private void updateLibrarySign(Sign sign, Vector3i pos) {
        if (placed.size() < plugin.getConfig().getInt("generation.sign-min-rooms", 6)) return;
        Vector3i start = new Vector3i(plugin.getConfig().getInt("start-location.x"), plugin.getConfig().getInt("start-location.y"), plugin.getConfig().getInt("start-location.z"));
        String bookDir = nearestRoomDirection(pos, RoomType.BOOK);
        String readDir = nearestRoomDirection(pos, RoomType.READ);
        sign.setLine(0, "Infinity Library");
        sign.setLine(1, "Start: " + directionToward(pos, start));
        sign.setLine(2, "Book:" + (bookDir == null ? "?" : bookDir) + " Read:" + (readDir == null ? "?" : readDir));
        sign.setLine(3, "Rooms: " + placed.size());
        sign.update(true, false);
    }

    private String nearestRoomDirection(Vector3i from, RoomType type) {
        double best = Double.MAX_VALUE;
        Vector3i bestTarget = null;
        synchronized (placed) {
            for (PlacedRoom pr : placed) {
                Room room = roomManager.get(pr.roomId()).orElse(null);
                if (room == null || room.type() != type) continue;
                Vector3i target = pr.origin().add(new Vector3i(room.size().x()/2, 1, room.size().z()/2));
                double dist = Math.pow(target.x()-from.x(),2) + Math.pow(target.y()-from.y(),2) + Math.pow(target.z()-from.z(),2);
                if (dist < best) { best = dist; bestTarget = target; }
            }
        }
        return bestTarget == null ? null : directionToward(from, bestTarget);
    }

    private String directionToward(Vector3i from, Vector3i to) {
        int dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
        if (Math.abs(dy) > Math.max(Math.abs(dx), Math.abs(dz))) return dy > 0 ? "UP" : "DOWN";
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "EAST" : "WEST";
        return dz > 0 ? "SOUTH" : "NORTH";
    }

    private boolean isSafe(Vector3i origin, Vector3i size) {
        for (PlacedRoom existing : placed) if (existing.overlaps(origin, size)) return false;
        World w = ensureWorld();
        for (int x=origin.x(); x<origin.x()+size.x(); x++) for (int y=origin.y(); y<origin.y()+size.y(); y++) for (int z=origin.z(); z<origin.z()+size.z(); z++) {
            Material m = w.getBlockAt(x,y,z).getType();
            if (m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR) return false;
        }
        return true;
    }


    private void clearBox(World w, Vector3i o, Vector3i s) {
        for (int x=o.x(); x<o.x()+s.x(); x++) for (int y=o.y(); y<o.y()+s.y(); y++) for (int z=o.z(); z<o.z()+s.z(); z++) w.getBlockAt(x,y,z).setType(Material.AIR, false);
    }
    private Vector3i faceVector(BlockFace f) { return new Vector3i(f.getModX(), f.getModY(), f.getModZ()); }

    public int generateAroundPlayer(Player player, int maxRooms) {
        int generated = 0;
        for (int i = 0; i < maxRooms; i++) {
            int before;
            synchronized (placed) { before = placed.size(); }
            tickPlayer(player);
            int after;
            synchronized (placed) { after = placed.size(); }
            if (after <= before) break;
            generated += (after - before);
        }
        return generated;
    }

    public boolean placeRoomImmediately(String roomId) {
        Room room = roomManager.get(roomId).orElse(null);
        if (room == null || room.connections().isEmpty()) return false;
        List<PlacedRoom> shuffledPlaced;
        synchronized (placed) {
            shuffledPlaced = new ArrayList<>(placed);
        }
        Collections.shuffle(shuffledPlaced);
        for (PlacedRoom parent : shuffledPlaced) {
            Room parentRoom = roomManager.get(parent.roomId()).orElse(null);
            if (parentRoom == null) continue;
            List<ConnectionPoint> open = new ArrayList<>();
            for (ConnectionPoint cp : parentRoom.connections()) if (!parent.generatedConnections().contains(cp.id())) open.add(cp);
            Collections.shuffle(open);
            for (ConnectionPoint target : open) {
                Vector3i targetWorld = parent.origin().add(target.position());
                Vector3i attach = faceVector(target.direction());
                for (ConnectionPoint cp : room.connections()) {
                    for (RoomTransform transform : RoomTransform.all()) {
                        ConnectionPoint transformedCp = cp.transform(transform, room.size());
                        if (!target.compatibleWith(transformedCp)) continue;
                        Vector3i transformedSize = transform.transformedSize(room.size());
                        Vector3i origin = targetWorld.add(attach).subtract(transformedCp.position());
                        synchronized (placed) {
                            if (!isSafe(origin, transformedSize)) continue;
                            parent.generatedConnections().add(target.id());
                            PlacedRoom pr = new PlacedRoom(UUID.randomUUID(), room.id(), origin, transformedSize);
                            pr.generatedConnections().add(transformedCp.id());
                            placed.add(pr);
                        }
                        queuePlacement(room, origin, transform);
                        while (!placementQueue.isEmpty()) {
                            Runnable run = placementQueue.poll();
                            if (run != null) run.run();
                        }
                        save();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration(); ConfigurationSection root = y.createSection("rooms"); int i=0;
        synchronized (placed) { for (PlacedRoom pr : placed) { ConfigurationSection s = root.createSection(String.valueOf(i++)); s.set("uuid", pr.instanceId().toString()); s.set("room-id", pr.roomId()); pr.origin().write(s.createSection("origin")); pr.size().write(s.createSection("size")); s.set("generated-connections", new ArrayList<>(pr.generatedConnections())); } }
        try { y.save(file); } catch (IOException e) { plugin.getLogger().severe("Unable to save generated.yml: " + e.getMessage()); }
    }
    private void load() {
        placed.clear(); if (!file.exists()) return; YamlConfiguration y = YamlConfiguration.loadConfiguration(file); ConfigurationSection root = y.getConfigurationSection("rooms"); if (root == null) return;
        for (String key : root.getKeys(false)) { ConfigurationSection s = root.getConfigurationSection(key); PlacedRoom pr = new PlacedRoom(UUID.fromString(s.getString("uuid")), s.getString("room-id"), Vector3i.read(s.getConfigurationSection("origin")), Vector3i.read(s.getConfigurationSection("size"))); pr.generatedConnections().addAll(s.getStringList("generated-connections")); placed.add(pr); }
    }
    private record Expansion(PlacedRoom parent, ConnectionPoint point) {}
}
