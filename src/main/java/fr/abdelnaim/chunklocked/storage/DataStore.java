package fr.abdelnaim.chunklocked.storage;

import fr.abdelnaim.chunklocked.model.ChunkPos;
import fr.abdelnaim.chunklocked.model.ChunkRequirement;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DataStore {
    private final JavaPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", exception);
        }
    }

    public long getOrCreateSeed(long configuredSeed) {
        if (configuredSeed != 0L) {
            return configuredSeed;
        }
        long existing = data.getLong("random-seed", 0L);
        if (existing != 0L) {
            return existing;
        }
        long generated = System.currentTimeMillis();
        data.set("random-seed", generated);
        save();
        return generated;
    }

    public ChunkPos getInitialChunk() {
        if (!data.isConfigurationSection("initial-chunk")) {
            return null;
        }
        return new ChunkPos(data.getInt("initial-chunk.x"), data.getInt("initial-chunk.z"));
    }

    public void setInitialChunk(ChunkPos chunk) {
        data.set("initial-chunk.x", chunk.x());
        data.set("initial-chunk.z", chunk.z());
    }

    public Set<ChunkPos> getUnlockedChunks() {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (String key : data.getStringList("unlocked")) {
            try {
                chunks.add(ChunkPos.parse(key));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid unlocked chunk key: " + key);
            }
        }
        return chunks;
    }

    public void setUnlockedChunks(Set<ChunkPos> chunks) {
        data.set("unlocked", chunks.stream().map(ChunkPos::key).sorted().toList());
    }

    public ChunkRequirement getRequirement(ChunkPos chunk) {
        String path = "requirements." + chunk.key();
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        Material material = Material.matchMaterial(section.getString("material", ""));
        int amount = section.getInt("amount", 0);
        if (material == null || amount <= 0) {
            return null;
        }
        return new ChunkRequirement(material, amount);
    }

    public void setRequirement(ChunkPos chunk, ChunkRequirement requirement) {
        String path = "requirements." + chunk.key();
        data.set(path + ".material", requirement.material().name());
        data.set(path + ".amount", requirement.amount());
    }

    public void clearProgress() {
        long seed = data.getLong("random-seed", 0L);
        data.set("initial-chunk", null);
        data.set("unlocked", null);
        data.set("requirements", null);
        if (seed != 0L) {
            data.set("random-seed", seed);
        }
        save();
    }
}
