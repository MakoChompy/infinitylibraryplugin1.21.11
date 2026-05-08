package com.infinitylibrary.model;

import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

public record ConnectionPoint(String id, Vector3i position, BlockFace direction, int width, int height, int floor) {
    public ConnectionPoint(String id, Vector3i position, BlockFace direction, int width, int height) {
        this(id, position, direction, width, height, 0);
    }

    public ConnectionPoint {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Connection id is required");
        if (!isSupportedDirection(direction)) throw new IllegalArgumentException("Connection direction must be NORTH/EAST/SOUTH/WEST/UP/DOWN");
        if (width < 1 || height < 1) throw new IllegalArgumentException("Connection width and height must be positive");
    }
    public boolean compatibleWith(ConnectionPoint other) {
        return width == other.width && height == other.height && floor == other.floor && direction.getOppositeFace() == other.direction;
    }
    public ConnectionPoint rotate(Rotation rotation, Vector3i roomSize) {
        return transform(new RoomTransform(rotation, false, false), roomSize);
    }
    public ConnectionPoint transform(RoomTransform transform, Vector3i roomSize) {
        return new ConnectionPoint(id, transform.transform(position, roomSize), transform.transform(direction), width, height, floor);
    }
    private static boolean isSupportedDirection(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.EAST || face == BlockFace.SOUTH || face == BlockFace.WEST || face == BlockFace.UP || face == BlockFace.DOWN;
    }
    public static ConnectionPoint read(String id, ConfigurationSection s) {
        Vector3i position = Vector3i.read(s.getConfigurationSection("position"));
        return new ConnectionPoint(id, position, BlockFace.valueOf(s.getString("direction", "NORTH")), s.getInt("width", 1), s.getInt("height", 2), s.getInt("floor", 0));
    }
    public void write(ConfigurationSection s) {
        ConfigurationSection p = s.createSection("position");
        position.write(p);
        s.set("direction", direction.name());
        s.set("width", width);
        s.set("height", height);
        s.set("floor", floor);
    }
}
