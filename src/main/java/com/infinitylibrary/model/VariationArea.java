package com.infinitylibrary.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public record VariationArea(String id, String category, double chancePercent, List<RoomBlock> blocks) {
    public VariationArea {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Variation id required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("Variation category required");
        if (chancePercent < 0.0 || chancePercent > 100.0) throw new IllegalArgumentException("Variation chance must be 0..100");
        blocks = List.copyOf(blocks);
    }

    public void write(ConfigurationSection s) {
        s.set("category", category);
        s.set("chance", chancePercent);
        List<String> serialized = new ArrayList<>();
        for (RoomBlock b : blocks) serialized.add(b.position() + "|" + b.blockData());
        s.set("blocks", serialized);
    }

    public static VariationArea read(String id, ConfigurationSection s) {
        String category = s.getString("category", "default");
        double chance = s.getDouble("chance", 100.0);
        List<RoomBlock> blocks = new ArrayList<>();
        for (String line : s.getStringList("blocks")) {
            String[] p = line.split("\\|", 2);
            if (p.length == 2) blocks.add(new RoomBlock(Vector3i.parse(p[0]), p[1]));
        }
        return new VariationArea(id, category, chance, blocks);
    }
}
