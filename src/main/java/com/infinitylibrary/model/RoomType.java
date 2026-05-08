package com.infinitylibrary.model;

public enum RoomType {
    FILLER, BOOK, READ;

    public static RoomType parse(String value) {
        for (RoomType type : values()) {
            if (type.name().equalsIgnoreCase(value)) return type;
        }
        throw new IllegalArgumentException("Unknown room type: " + value);
    }
}
