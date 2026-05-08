package com.infinitylibrary.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlacedRoom {
    private final UUID instanceId;
    private final String roomId;
    private final Vector3i origin;
    private final Vector3i size;
    private final RoomTransform transform;
    private final Set<String> generatedConnections = new HashSet<>();

    public PlacedRoom(UUID instanceId, String roomId, Vector3i origin, Vector3i size) {
        this(instanceId, roomId, origin, size, RoomTransform.IDENTITY);
    }

    public PlacedRoom(UUID instanceId, String roomId, Vector3i origin, Vector3i size, RoomTransform transform) {
        this.instanceId = instanceId;
        this.roomId = roomId;
        this.origin = origin;
        this.size = size;
        this.transform = transform;
    }
    public UUID instanceId() { return instanceId; }
    public String roomId() { return roomId; }
    public Vector3i origin() { return origin; }
    public Vector3i size() { return size; }
    public RoomTransform transform() { return transform; }
    public Set<String> generatedConnections() { return generatedConnections; }
    public boolean overlaps(Vector3i o, Vector3i s) {
        return origin.x() < o.x() + s.x() && origin.x() + size.x() > o.x()
            && origin.y() < o.y() + s.y() && origin.y() + size.y() > o.y()
            && origin.z() < o.z() + s.z() && origin.z() + size.z() > o.z();
    }
}
