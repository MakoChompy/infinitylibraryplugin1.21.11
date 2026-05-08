package com.infinitylibrary.model;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class Room {
    private final String id;
    private final RoomType type;
    private final Vector3i size;
    private final List<ConnectionPoint> connections;
    private final List<RoomBlock> blocks;
    private final List<VariationArea> variations;
    private final double spawnChance;

    public Room(String id, RoomType type, Vector3i size, List<ConnectionPoint> connections, List<RoomBlock> blocks, List<VariationArea> variations, double spawnChance) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.size = Objects.requireNonNull(size);
        this.connections = List.copyOf(connections);
        this.blocks = List.copyOf(blocks);
        this.variations = List.copyOf(variations);
        this.spawnChance = Math.max(0.0, Math.min(100.0, spawnChance));
        validate();
    }
    public String id() { return id; }
    public RoomType type() { return type; }
    public Vector3i size() { return size; }
    public List<ConnectionPoint> connections() { return connections; }
    public List<RoomBlock> blocks() { return blocks; }
    public List<VariationArea> variations() { return variations; }
    public double spawnChance() { return spawnChance; }

    public void validate() {
        if (connections.isEmpty()) throw new IllegalArgumentException("Room " + id + " must contain at least one connection point");
        if (size.x() < 1 || size.y() < 1 || size.z() < 1) throw new IllegalArgumentException("Room " + id + " has invalid size");
        boolean hasBookshelf = false, hasSeat = false;
        for (RoomBlock block : blocks) {
            Vector3i p = block.position();
            if (p.x() < 0 || p.y() < 0 || p.z() < 0 || p.x() >= size.x() || p.y() >= size.y() || p.z() >= size.z()) {
                throw new IllegalArgumentException("Block outside room bounds in " + id + ": " + p);
            }
            Material m = materialFromBlockData(block.blockData());
            if (m == Material.CHISELED_BOOKSHELF) hasBookshelf = true;
            if (m != null && (m.name().endsWith("_STAIRS") || m.name().endsWith("_SLAB"))) hasSeat = true;
        }
        for (ConnectionPoint cp : connections) {
            Vector3i p = cp.position();
            if (p.x() < 0 || p.y() < 0 || p.z() < 0 || p.x() >= size.x() || p.y() >= size.y() || p.z() >= size.z()) {
                throw new IllegalArgumentException("Connection outside room bounds in " + id + ": " + cp.id());
            }
        }
        if (type == RoomType.BOOK && !hasBookshelf) throw new IllegalArgumentException("BOOK room " + id + " must contain a chiseled bookshelf");
        if (type == RoomType.READ && !hasSeat) throw new IllegalArgumentException("READ room " + id + " must contain a stair or slab");
    }

    private Material materialFromBlockData(String data) {
        String key = data.split("\\[")[0];
        if (key.contains(":")) key = key.substring(key.indexOf(':') + 1);
        return Material.matchMaterial(key.toUpperCase(Locale.ROOT));
    }

    public BlockData data(RoomBlock block) { return org.bukkit.Bukkit.createBlockData(block.blockData()); }

    public void write(ConfigurationSection s) {
        s.set("type", type.name());
        ConfigurationSection sizeSec = s.createSection("size"); size.write(sizeSec);
        ConfigurationSection cSec = s.createSection("connections");
        for (ConnectionPoint cp : connections) cp.write(cSec.createSection(cp.id()));
        List<String> serialized = new ArrayList<>();
        for (RoomBlock b : blocks) serialized.add(b.position() + "|" + b.blockData());
        s.set("blocks", serialized);
        s.set("spawn-chance", spawnChance);
        ConfigurationSection vSec = s.createSection("variations");
        for (VariationArea variation : variations) variation.write(vSec.createSection(variation.id()));
    }

    public static Room read(String id, ConfigurationSection s) {
        RoomType type = RoomType.parse(s.getString("type", "FILLER"));
        Vector3i size = Vector3i.read(s.getConfigurationSection("size"));
        List<ConnectionPoint> cps = new ArrayList<>();
        ConfigurationSection cSec = s.getConfigurationSection("connections");
        if (cSec != null) for (String key : cSec.getKeys(false)) cps.add(ConnectionPoint.read(key, cSec.getConfigurationSection(key)));
        List<RoomBlock> blocks = new ArrayList<>();
        for (String line : s.getStringList("blocks")) {
            String[] p = line.split("\\|", 2);
            if (p.length == 2) blocks.add(new RoomBlock(Vector3i.parse(p[0]), p[1]));
        }
        List<VariationArea> variations = new ArrayList<>();
        ConfigurationSection vSec = s.getConfigurationSection("variations");
        if (vSec != null) for (String key : vSec.getKeys(false)) variations.add(VariationArea.read(key, vSec.getConfigurationSection(key)));
        return new Room(id, type, size, cps, blocks, variations, s.getDouble("spawn-chance", 100.0));
    }
}
