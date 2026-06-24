package fr.abdelnaim.chunklocked;

import fr.abdelnaim.chunklocked.border.BorderManager;
import fr.abdelnaim.chunklocked.command.ChunkLockedCommand;
import fr.abdelnaim.chunklocked.hologram.HologramManager;
import fr.abdelnaim.chunklocked.listener.ProtectionListener;
import fr.abdelnaim.chunklocked.message.MessageService;
import fr.abdelnaim.chunklocked.progression.ChunkProgression;
import fr.abdelnaim.chunklocked.requirement.RequirementGenerator;
import fr.abdelnaim.chunklocked.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkLockedPlugin extends JavaPlugin {
    private NamespacedKey hologramMarkerKey;
    private NamespacedKey hologramChunkKey;
    private DataStore dataStore;
    private MessageService messages;
    private RequirementGenerator requirementGenerator;
    private ChunkProgression progression;
    private HologramManager hologramManager;
    private BorderManager borderManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        hologramMarkerKey = new NamespacedKey(this, "hologram");
        hologramChunkKey = new NamespacedKey(this, "hologram_chunk");
        dataStore = new DataStore(this);
        dataStore.load();
        messages = new MessageService(this);
        requirementGenerator = new RequirementGenerator(this, dataStore);
        progression = new ChunkProgression(this, dataStore, requirementGenerator, messages);
        hologramManager = new HologramManager(this, progression, messages);
        borderManager = new BorderManager(this, progression);

        progression.loadOrInitialize();

        Bukkit.getPluginManager().registerEvents(hologramManager, this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this, progression, messages), this);

        ChunkLockedCommand commandExecutor = new ChunkLockedCommand(this, progression, messages);
        PluginCommand command = getCommand("chunklocked");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }

        hologramManager.refreshAll();
        borderManager.start();
    }

    @Override
    public void onDisable() {
        if (borderManager != null) {
            borderManager.stop();
        }
        if (hologramManager != null) {
            hologramManager.cleanup();
        }
    }

    public void reloadChunkLocked() {
        reloadConfig();
        dataStore.load();
        messages.reload();
        requirementGenerator.reload();
        progression.loadOrInitialize();
        refreshWorldState();
        borderManager.restart();
    }

    public void refreshWorldState() {
        progression.ensureFrontierRequirements();
        hologramManager.refreshAll();
    }

    public World getTargetWorld() {
        String configuredWorld = getConfig().getString("world-name", "world");
        World world = Bukkit.getWorld(configuredWorld);
        if (world != null && world.getEnvironment() == World.Environment.NORMAL) {
            return world;
        }
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getEnvironment() == World.Environment.NORMAL) {
                return candidate;
            }
        }
        return null;
    }

    public boolean isManagedWorld(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        World targetWorld = getTargetWorld();
        return targetWorld != null && targetWorld.getUID().equals(world.getUID());
    }

    public NamespacedKey getHologramMarkerKey() {
        return hologramMarkerKey;
    }

    public NamespacedKey getHologramChunkKey() {
        return hologramChunkKey;
    }

    public MessageService messages() {
        return messages;
    }
}
