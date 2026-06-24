package fr.abdelnaim.chunklocked.requirement;

import fr.abdelnaim.chunklocked.model.ChunkPos;
import fr.abdelnaim.chunklocked.model.ChunkRequirement;
import fr.abdelnaim.chunklocked.storage.DataStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class RequirementGenerator {
    private static final Set<String> HARD_BLOCKED = Set.of(
            "AIR",
            "CAVE_AIR",
            "VOID_AIR",
            "BEDROCK",
            "BARRIER",
            "LIGHT",
            "COMMAND_BLOCK",
            "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK",
            "STRUCTURE_BLOCK",
            "STRUCTURE_VOID",
            "JIGSAW",
            "DEBUG_STICK",
            "END_PORTAL_FRAME",
            "END_PORTAL",
            "NETHER_PORTAL",
            "DRAGON_EGG",
            "SPAWNER",
            "TRIAL_SPAWNER",
            "VAULT",
            "REINFORCED_DEEPSLATE"
    );

    private final JavaPlugin plugin;
    private final DataStore dataStore;
    private List<Tier> tiers = List.of();
    private long seed;

    public RequirementGenerator(JavaPlugin plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        reload();
    }

    public void reload() {
        seed = dataStore.getOrCreateSeed(plugin.getConfig().getLong("requirements.random-seed", 0L));
        tiers = loadTiers();
    }

    public ChunkRequirement getOrCreateRequirement(ChunkPos chunk, ChunkPos initialChunk) {
        int distance = initialChunk == null ? 1 : chunk.chebyshevDistance(initialChunk);
        if (isStartArea(distance)) {
            ChunkRequirement startRequirement = createStartAreaRequirement(chunk);
            ChunkRequirement existing = dataStore.getRequirement(chunk);
            if (existing == null
                    || existing.material() != startRequirement.material()
                    || existing.amount() != startRequirement.amount()) {
                dataStore.setRequirement(chunk, startRequirement);
                dataStore.save();
            }
            return startRequirement;
        }

        ChunkRequirement existing = dataStore.getRequirement(chunk);
        if (existing != null && isAllowed(existing.material())) {
            return existing;
        }

        Tier tier = selectTier(distance);
        Random random = new Random(seedFor(chunk));
        Material material = tier.materials().get(random.nextInt(tier.materials().size()));
        int amount = tier.minAmount() + random.nextInt(tier.maxAmount() - tier.minAmount() + 1);
        ChunkRequirement requirement = new ChunkRequirement(material, amount);
        dataStore.setRequirement(chunk, requirement);
        dataStore.save();
        return requirement;
    }

    private List<Tier> loadTiers() {
        List<Tier> loaded = new ArrayList<>();
        Set<String> blocked = loadBlockedMaterials();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("requirements.tiers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection tierSection = section.getConfigurationSection(key);
                if (tierSection == null) {
                    continue;
                }
                int min = Math.max(1, tierSection.getInt("min-amount", 1));
                int max = Math.max(min, tierSection.getInt("max-amount", min));
                int maxDistance = Math.max(0, tierSection.getInt("max-distance", Integer.MAX_VALUE));
                List<Material> materials = parseMaterials(tierSection.getStringList("materials"), blocked);
                if (materials.isEmpty()) {
                    plugin.getLogger().warning("Requirement tier '" + key + "' has no valid materials and was ignored.");
                    continue;
                }
                loaded.add(new Tier(maxDistance, min, max, materials));
            }
        }

        if (loaded.isEmpty()) {
            loaded.add(new Tier(2, 4, 12, parseMaterials(List.of("DIRT", "COBBLESTONE", "OAK_LOG", "STICK"), blocked)));
            loaded.add(new Tier(Integer.MAX_VALUE, 8, 32, parseMaterials(List.of("COAL", "IRON_INGOT", "REDSTONE"), blocked)));
        }

        loaded.sort(Comparator.comparingInt(Tier::maxDistance));
        return loaded;
    }

    private Set<String> loadBlockedMaterials() {
        Set<String> blocked = new HashSet<>(HARD_BLOCKED);
        for (String name : plugin.getConfig().getStringList("requirements.blocked-materials")) {
            blocked.add(normalize(name));
        }
        return blocked;
    }

    private List<Material> parseMaterials(List<String> names, Set<String> blocked) {
        List<Material> materials = new ArrayList<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                plugin.getLogger().warning("Unknown material in requirement config: " + name);
                continue;
            }
            if (!blocked.contains(material.name()) && isAllowed(material)) {
                materials.add(material);
            }
        }
        return materials;
    }

    private boolean isAllowed(Material material) {
        return material != null
                && material.isItem()
                && !material.isAir()
                && !HARD_BLOCKED.contains(material.name());
    }

    private Tier selectTier(int distance) {
        for (Tier tier : tiers) {
            if (distance <= tier.maxDistance()) {
                return tier;
            }
        }
        return tiers.get(tiers.size() - 1);
    }

    private boolean isStartArea(int distance) {
        int maxDistance = Math.max(0, plugin.getConfig().getInt("requirements.start-area.max-distance", 2));
        return distance > 0 && distance <= maxDistance;
    }

    private ChunkRequirement createStartAreaRequirement(ChunkPos chunk) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("requirements.start-area.material", "DIRT"));
        if (material == null || !isAllowed(material)) {
            material = Material.DIRT;
        }
        int min = Math.max(1, plugin.getConfig().getInt("requirements.start-area.min-amount", 4));
        int max = Math.max(min, plugin.getConfig().getInt("requirements.start-area.max-amount", 12));
        Random random = new Random(seedFor(chunk) ^ 0xD1745EEDL);
        int amount = min + random.nextInt(max - min + 1);
        return new ChunkRequirement(material, amount);
    }

    private long seedFor(ChunkPos chunk) {
        long mixed = seed;
        mixed ^= 0x9E3779B97F4A7C15L * chunk.x();
        mixed = Long.rotateLeft(mixed, 27);
        mixed ^= 0xC2B2AE3D27D4EB4FL * chunk.z();
        mixed = Long.rotateLeft(mixed, 31);
        return mixed;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record Tier(int maxDistance, int minAmount, int maxAmount, List<Material> materials) {
    }
}
