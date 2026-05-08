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
    private final Set<Vector3i> proceduralBlocks = new HashSet<>();
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
                    ConnectionPoint worldPoint = cp.transform(pr.transform(), room.size());
                    Location cLoc = pr.origin().add(worldPoint.position()).toLocation(player.getWorld()).add(0.5, 0.5, 0.5);
                    if (cLoc.distanceSquared(loc) <= radius * radius) expansions.add(new Expansion(pr, cp, worldPoint));
                }
            });
        }
        int max = plugin.getConfig().getInt("generation.max-rooms-per-tick", 1);
        for (int i=0; i<Math.min(max, expansions.size()); i++) expand(expansions.get(i));
    }

    private void expand(Expansion expansion) {
        PlacedRoom parent = expansion.parent(); ConnectionPoint localTarget = expansion.localPoint(); ConnectionPoint target = expansion.worldPoint();
        if (parent.generatedConnections().contains(localTarget.id())) return;
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
                parent.generatedConnections().add(localTarget.id());
                PlacedRoom pr = new PlacedRoom(UUID.randomUUID(), room.id(), origin, transformedSize, transform);
                pr.generatedConnections().add(transformedCp.id());
                placed.add(pr);
                queuePlacement(room, origin, transform);
                save();
                return;
            }
            if (tryProceduralConnection(parent, localTarget, target)) {
                save();
                return;
            }
            parent.generatedConnections().add(localTarget.id());
            sealConnection(parent, target);
            save();
        }
    }

    private boolean tryProceduralConnection(PlacedRoom parent, ConnectionPoint localTarget, ConnectionPoint target) {
        if (!plugin.getConfig().getBoolean("generation.procedural-connectors.enabled", true)) return false;
        int maxDistance = Math.max(1, plugin.getConfig().getInt("generation.procedural-connectors.max-distance", 18));
        ConnectorTarget match = nearestProceduralTarget(parent, target, maxDistance);
        if (match == null) return false;
        parent.generatedConnections().add(localTarget.id());
        match.room().generatedConnections().add(match.localPoint().id());
        buildProceduralConnector(parent.origin(), target, match.room().origin(), match.worldPoint());
        return true;
    }

    private ConnectorTarget nearestProceduralTarget(PlacedRoom parent, ConnectionPoint target, int maxDistance) {
        ConnectorTarget best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean bestCompatible = false;
        Vector3i targetDoor = connectorStart(parent.origin(), target);
        for (PlacedRoom candidate : placed) {
            if (candidate == parent) continue;
            Room room = roomManager.get(candidate.roomId()).orElse(null);
            if (room == null) continue;
            for (ConnectionPoint local : room.connections()) {
                if (candidate.generatedConnections().contains(local.id())) continue;
                ConnectionPoint transformed = local.transform(candidate.transform(), room.size());
                int distance = manhattan(targetDoor, connectorStart(candidate.origin(), transformed));
                boolean compatible = target.compatibleWith(transformed);
                if (distance <= maxDistance && (distance < bestDistance || (distance == bestDistance && compatible && !bestCompatible))) {
                    bestDistance = distance;
                    bestCompatible = compatible;
                    best = new ConnectorTarget(candidate, local, transformed);
                }
            }
        }
        return best;
    }

    private void buildProceduralConnector(Vector3i fromOrigin, ConnectionPoint from, Vector3i toOrigin, ConnectionPoint to) {
        World w = ensureWorld();
        Vector3i fromStart = connectorStart(fromOrigin, from);
        Vector3i toStart = connectorStart(toOrigin, to);
        List<Vector3i> path = randomOrthogonalPath(fromStart, toStart);
        Material floor = configuredMaterial("generation.procedural-connectors.floor-material", Material.SMOOTH_STONE);
        Material wall = configuredMaterial("generation.procedural-connectors.wall-material", Material.BOOKSHELF);
        Material ceiling = configuredMaterial("generation.procedural-connectors.ceiling-material", Material.DARK_OAK_PLANKS);
        Material light = configuredMaterial("generation.procedural-connectors.light-material", Material.LANTERN);
        int halfWidth = Math.max(0, Math.max(from.width(), to.width()) / 2);
        int height = Math.max(2, Math.max(from.height(), to.height()));
        Set<Vector3i> interior = new HashSet<>();
        for (Vector3i step : path) {
            for (int dx = -halfWidth; dx <= halfWidth; dx++) for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                interior.add(new Vector3i(step.x() + dx, step.y(), step.z() + dz));
            }
        }
        int lightEvery = Math.max(2, plugin.getConfig().getInt("generation.procedural-connectors.light-spacing", 6));
        int index = 0;
        for (Vector3i cell : interior) {
            for (int y = 0; y < height; y++) setProceduralBlock(w, new Vector3i(cell.x(), cell.y() + y, cell.z()), Material.AIR);
            setProceduralBlock(w, new Vector3i(cell.x(), cell.y() - 1, cell.z()), floor);
            setProceduralBlock(w, new Vector3i(cell.x(), cell.y() + height, cell.z()), ceiling);
            if (index++ % lightEvery == 0) setProceduralBlock(w, new Vector3i(cell.x(), cell.y() + height - 1, cell.z()), light);
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
                Vector3i side = cell.add(faceVector(face));
                if (interior.contains(side)) continue;
                for (int y = 0; y < height; y++) setProceduralBlock(w, new Vector3i(side.x(), side.y() + y, side.z()), wall);
            }
        }
    }

    private void setProceduralBlock(World world, Vector3i pos, Material material) {
        world.getBlockAt(pos.x(), pos.y(), pos.z()).setType(material, false);
        proceduralBlocks.add(pos);
    }

    private List<Vector3i> randomOrthogonalPath(Vector3i from, Vector3i to) {
        List<Vector3i> path = new ArrayList<>();
        Vector3i cursor = from;
        path.add(cursor);
        List<Integer> axes = new ArrayList<>(List.of(0, 1, 2));
        Collections.shuffle(axes);
        for (int axis : axes) {
            while ((axis == 0 && cursor.x() != to.x()) || (axis == 1 && cursor.y() != to.y()) || (axis == 2 && cursor.z() != to.z())) {
                int dx = axis == 0 ? Integer.compare(to.x(), cursor.x()) : 0;
                int dy = axis == 1 ? Integer.compare(to.y(), cursor.y()) : 0;
                int dz = axis == 2 ? Integer.compare(to.z(), cursor.z()) : 0;
                cursor = cursor.add(new Vector3i(dx, dy, dz));
                path.add(cursor);
            }
        }
        return path;
    }

    private Vector3i connectorStart(Vector3i origin, ConnectionPoint point) {
        return origin.add(point.position()).add(faceVector(point.direction()));
    }

    private int manhattan(Vector3i a, Vector3i b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
    }

    private Material configuredMaterial(String path, Material fallback) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(path, fallback.name()));
        return material == null ? fallback : material;
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
            for (Vector3i pos : proceduralBlocks) w.getBlockAt(pos.x(), pos.y(), pos.z()).setType(Material.AIR, false);
            placed.clear(); placementQueue.clear();
            sealedBlocks.clear();
            proceduralBlocks.clear();
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
            for (ConnectionPoint localTarget : open) {
                ConnectionPoint target = localTarget.transform(parent.transform(), parentRoom.size());
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
                            parent.generatedConnections().add(localTarget.id());
                            PlacedRoom pr = new PlacedRoom(UUID.randomUUID(), room.id(), origin, transformedSize, transform);
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
        synchronized (placed) { for (PlacedRoom pr : placed) { ConfigurationSection s = root.createSection(String.valueOf(i++)); s.set("uuid", pr.instanceId().toString()); s.set("room-id", pr.roomId()); pr.origin().write(s.createSection("origin")); pr.size().write(s.createSection("size")); writeTransform(s.createSection("transform"), pr.transform()); s.set("generated-connections", new ArrayList<>(pr.generatedConnections())); } }
        try { y.save(file); } catch (IOException e) { plugin.getLogger().severe("Unable to save generated.yml: " + e.getMessage()); }
    }
    private void load() {
        placed.clear(); if (!file.exists()) return; YamlConfiguration y = YamlConfiguration.loadConfiguration(file); ConfigurationSection root = y.getConfigurationSection("rooms"); if (root == null) return;
        for (String key : root.getKeys(false)) { ConfigurationSection s = root.getConfigurationSection(key); PlacedRoom pr = new PlacedRoom(UUID.fromString(s.getString("uuid")), s.getString("room-id"), Vector3i.read(s.getConfigurationSection("origin")), Vector3i.read(s.getConfigurationSection("size")), readTransform(s.getConfigurationSection("transform"))); pr.generatedConnections().addAll(s.getStringList("generated-connections")); placed.add(pr); }
    }
    private void writeTransform(ConfigurationSection s, RoomTransform transform) {
        s.set("rotation", transform.rotation().name());
        s.set("flip-x", transform.flipX());
        s.set("flip-z", transform.flipZ());
    }
    private RoomTransform readTransform(ConfigurationSection s) {
        if (s == null) return RoomTransform.IDENTITY;
        return new RoomTransform(Rotation.valueOf(s.getString("rotation", Rotation.NONE.name())), s.getBoolean("flip-x", false), s.getBoolean("flip-z", false));
    }
    private record Expansion(PlacedRoom parent, ConnectionPoint localPoint, ConnectionPoint worldPoint) {}
    private record ConnectorTarget(PlacedRoom room, ConnectionPoint localPoint, ConnectionPoint worldPoint) {}
}
