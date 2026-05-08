package com.infinitylibrary.model;

import org.bukkit.block.BlockFace;

public enum Rotation {
    NONE, CLOCKWISE_90, CLOCKWISE_180, CLOCKWISE_270;

    public Vector3i rotate(Vector3i v, int sizeX, int sizeZ) {
        return switch (this) {
            case NONE -> v;
            case CLOCKWISE_90 -> new Vector3i(sizeZ - 1 - v.z(), v.y(), v.x());
            case CLOCKWISE_180 -> new Vector3i(sizeX - 1 - v.x(), v.y(), sizeZ - 1 - v.z());
            case CLOCKWISE_270 -> new Vector3i(v.z(), v.y(), sizeX - 1 - v.x());
        };
    }

    public Vector3i rotatedSize(Vector3i s) {
        return (this == CLOCKWISE_90 || this == CLOCKWISE_270) ? new Vector3i(s.z(), s.y(), s.x()) : s;
    }

    public BlockFace rotate(BlockFace face) {
        BlockFace f = face;
        int turns = ordinal();
        for (int i = 0; i < turns; i++) f = switch (f) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> f;
        };
        return f;
    }

    public static Rotation matching(BlockFace from, BlockFace to) {
        for (Rotation r : values()) if (r.rotate(from) == to) return r;
        return NONE;
    }
}
