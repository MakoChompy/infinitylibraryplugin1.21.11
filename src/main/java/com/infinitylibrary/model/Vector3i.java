package com.infinitylibrary.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public record Vector3i(int x, int y, int z) {
    public Vector3i add(Vector3i other) { return new Vector3i(x + other.x, y + other.y, z + other.z); }
    public Vector3i subtract(Vector3i other) { return new Vector3i(x - other.x, y - other.y, z - other.z); }
    public Location toLocation(World world) { return new Location(world, x, y, z); }
    public static Vector3i from(Location location) { return new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    public static Vector3i read(ConfigurationSection s) { return new Vector3i(s.getInt("x"), s.getInt("y"), s.getInt("z")); }
    public void write(ConfigurationSection s) { s.set("x", x); s.set("y", y); s.set("z", z); }
    @Override public String toString() { return x + "," + y + "," + z; }
    public static Vector3i parse(String text) {
        String[] p = text.split(",");
        if (p.length != 3) throw new IllegalArgumentException("Vector must be x,y,z");
        return new Vector3i(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
    }
    @Override public boolean equals(Object o) { return o instanceof Vector3i v && x == v.x && y == v.y && z == v.z; }
    @Override public int hashCode() { return Objects.hash(x, y, z); }
}
