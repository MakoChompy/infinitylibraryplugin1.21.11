package com.infinitylibrary.model;

import org.bukkit.block.BlockFace;

public record RoomTransform(Rotation rotation, boolean flipX, boolean flipZ) {
    public static final RoomTransform IDENTITY = new RoomTransform(Rotation.NONE, false, false);

    public Vector3i transform(Vector3i point, Vector3i originalSize) {
        Vector3i p = point;
        if (flipX) p = new Vector3i(originalSize.x() - 1 - p.x(), p.y(), p.z());
        if (flipZ) p = new Vector3i(p.x(), p.y(), originalSize.z() - 1 - p.z());
        return rotation.rotate(p, originalSize.x(), originalSize.z());
    }

    public Vector3i transformedSize(Vector3i originalSize) {
        return rotation.rotatedSize(originalSize);
    }

    public BlockFace transform(BlockFace face) {
        BlockFace f = face;
        if (flipX) f = switch (f) {
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
            default -> f;
        };
        if (flipZ) f = switch (f) {
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            default -> f;
        };
        return rotation.rotate(f);
    }

    public static RoomTransform[] all() {
        RoomTransform[] transforms = new RoomTransform[Rotation.values().length * 4];
        int i = 0;
        for (Rotation rotation : Rotation.values()) {
            transforms[i++] = new RoomTransform(rotation, false, false);
            transforms[i++] = new RoomTransform(rotation, true, false);
            transforms[i++] = new RoomTransform(rotation, false, true);
            transforms[i++] = new RoomTransform(rotation, true, true);
        }
        return transforms;
    }
}
