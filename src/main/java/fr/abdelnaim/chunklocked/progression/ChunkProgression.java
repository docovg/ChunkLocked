package fr.abdelnaim.chunklocked.progression;

import fr.abdelnaim.chunklocked.ChunkLockedPlugin;
import fr.abdelnaim.chunklocked.model.ChunkPos;
import fr.abdelnaim.chunklocked.model.ChunkRequirement;
import fr.abdelnaim.chunklocked.requirement.RequirementGenerator;
import fr.abdelnaim.chunklocked.storage.DataStore;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ChunkProgression {
    private final ChunkLockedPlugin plugin;
    private final DataStore dataStore;
    private final RequirementGenerator requirementGenerator;
    private final Set<ChunkPos> unlockedChunks = new LinkedHashSet<>();
    private ChunkPos initialChunk;

    public ChunkProgression(ChunkLockedPlugin plugin, DataStore dataStore, RequirementGenerator requirementGenerator) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.requirementGenerator = requirementGenerator;
    }

    public void loadOrInitialize() {
        unlockedChunks.clear();
        unlockedChunks.addAll(dataStore.getUnlockedChunks());
        initialChunk = dataStore.getInitialChunk();

        if (initialChunk == null || unlockedChunks.isEmpty()) {
            World world = plugin.getTargetWorld();
            if (world == null) {
                plugin.getLogger().severe("No NORMAL world is available for ChunkLocked.");
                return;
            }
            initialChunk = ChunkPos.fromLocation(world.getSpawnLocation());
            unlockedChunks.clear();
            unlockedChunks.add(initialChunk);
            dataStore.setInitialChunk(initialChunk);
            persistUnlocked();
        }

        ensureFrontierRequirements();
    }

    public void reset() {
        dataStore.clearProgress();
        loadOrInitialize();
    }

    public boolean isUnlocked(ChunkPos chunk) {
        return unlockedChunks.contains(chunk);
    }

    public boolean isUnlocked(Location location) {
        if (location == null || location.getWorld() == null || !plugin.isManagedWorld(location.getWorld())) {
            return true;
        }
        return isUnlocked(ChunkPos.fromLocation(location));
    }

    public Set<ChunkPos> getUnlockedChunksSnapshot() {
        return Set.copyOf(unlockedChunks);
    }

    public ChunkPos getInitialChunk() {
        return initialChunk;
    }

    public int getUnlockedCount() {
        return unlockedChunks.size();
    }

    public Set<ChunkPos> getFrontierChunks() {
        Set<ChunkPos> frontier = new LinkedHashSet<>();
        for (ChunkPos unlocked : unlockedChunks) {
            for (ChunkPos neighbor : unlocked.neighbors()) {
                if (!unlockedChunks.contains(neighbor)) {
                    frontier.add(neighbor);
                }
            }
        }
        return frontier;
    }

    public ChunkRequirement getRequirement(ChunkPos chunk) {
        return requirementGenerator.getOrCreateRequirement(chunk, initialChunk);
    }

    public boolean isFrontierChunk(ChunkPos chunk) {
        if (unlockedChunks.contains(chunk)) {
            return false;
        }
        for (ChunkPos neighbor : chunk.neighbors()) {
            if (unlockedChunks.contains(neighbor)) {
                return true;
            }
        }
        return false;
    }

    public void ensureFrontierRequirements() {
        for (ChunkPos chunk : getFrontierChunks()) {
            getRequirement(chunk);
        }
    }

    public boolean tryUnlock(Player player, ChunkPos chunk) {
        if (unlockedChunks.contains(chunk)) {
            player.sendActionBar(Component.text("Ce chunk est deja debloque.", NamedTextColor.YELLOW));
            return false;
        }
        if (!isFrontierChunk(chunk)) {
            player.sendActionBar(Component.text("Ce chunk n'est pas adjacent a la zone debloquee.", NamedTextColor.RED));
            return false;
        }

        ChunkRequirement requirement = getRequirement(chunk);
        int available = countItems(player.getInventory(), requirement.material());
        if (available < requirement.amount()) {
            int missing = requirement.amount() - available;
            player.sendMessage(Component.text(
                    "Il manque " + missing + "x " + displayName(requirement.material()) + " pour debloquer ce chunk.",
                    NamedTextColor.RED
            ));
            return false;
        }

        removeItems(player.getInventory(), requirement.material(), requirement.amount());
        unlockedChunks.add(chunk);
        persistUnlocked();
        ensureFrontierRequirements();
        player.sendMessage(Component.text("Chunk debloque: " + chunk.key(), NamedTextColor.GREEN));
        return true;
    }

    public Location getSafeSpawnLocation() {
        World world = plugin.getTargetWorld();
        if (world == null || initialChunk == null) {
            return null;
        }
        int x = (initialChunk.x() << 4) + 8;
        int z = (initialChunk.z() << 4) + 8;
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5, world.getSpawnLocation().getYaw(), world.getSpawnLocation().getPitch());
    }

    private void persistUnlocked() {
        dataStore.setUnlockedChunks(unlockedChunks);
        dataStore.save();
    }

    private int countItems(PlayerInventory inventory, Material material) {
        int count = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private void removeItems(PlayerInventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack stack = contents[index];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int removed = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - removed);
            remaining -= removed;
            if (stack.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        inventory.setStorageContents(contents);
    }

    public String displayName(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
