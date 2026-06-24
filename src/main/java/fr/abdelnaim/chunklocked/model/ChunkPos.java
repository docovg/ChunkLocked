package fr.abdelnaim.chunklocked.model;

import java.util.List;
import java.util.Objects;
import org.bukkit.Location;

public record ChunkPos(int x, int z) {
    public static ChunkPos fromLocation(Location location) {
        return new ChunkPos(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public static ChunkPos parse(String value) {
        String[] parts = value.split(",", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid chunk key: " + value);
        }
        return new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    public String key() {
        return x + "," + z;
    }

    public ChunkPos neighbor(Direction direction) {
        return new ChunkPos(x + direction.dx(), z + direction.dz());
    }

    public List<ChunkPos> neighbors() {
        return Direction.CARDINALS.stream().map(this::neighbor).toList();
    }

    public int chebyshevDistance(ChunkPos other) {
        Objects.requireNonNull(other, "other");
        return Math.max(Math.abs(x - other.x), Math.abs(z - other.z));
    }
}
