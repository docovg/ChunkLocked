package fr.abdelnaim.chunklocked.hologram;

import fr.abdelnaim.chunklocked.ChunkLockedPlugin;
import fr.abdelnaim.chunklocked.message.MessageService;
import fr.abdelnaim.chunklocked.model.ChunkPos;
import fr.abdelnaim.chunklocked.model.ChunkRequirement;
import fr.abdelnaim.chunklocked.model.Direction;
import fr.abdelnaim.chunklocked.progression.ChunkProgression;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class HologramManager implements Listener {
    private final ChunkLockedPlugin plugin;
    private final ChunkProgression progression;
    private final MessageService messages;
    private final Set<UUID> spawnedEntities = new HashSet<>();

    public HologramManager(ChunkLockedPlugin plugin, ChunkProgression progression, MessageService messages) {
        this.plugin = plugin;
        this.progression = progression;
        this.messages = messages;
    }

    public void refreshAll() {
        cleanup();
        World world = plugin.getTargetWorld();
        if (world == null) {
            return;
        }

        for (ChunkPos unlocked : progression.getUnlockedChunksSnapshot()) {
            for (Direction direction : Direction.CARDINALS) {
                ChunkPos locked = unlocked.neighbor(direction);
                if (!progression.isUnlocked(locked)) {
                    spawnHologram(world, unlocked, direction, locked, progression.getRequirement(locked));
                }
            }
        }
    }

    public void cleanup() {
        for (UUID uuid : spawnedEntities) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        spawnedEntities.clear();

        World world = plugin.getTargetWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (isChunkLockedHologram(entity)) {
                entity.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ChunkPos chunk = readChunk(event.getRightClicked());
        if (chunk == null) {
            return;
        }
        event.setCancelled(true);
        attemptUnlock(event.getPlayer(), chunk);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ChunkPos chunk = readChunk(event.getEntity());
        if (chunk == null) {
            return;
        }
        event.setCancelled(true);
        attemptUnlock(player, chunk);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, this::refreshAll, 20L);
    }

    private void attemptUnlock(Player player, ChunkPos chunk) {
        if (progression.tryUnlock(player, chunk)) {
            plugin.refreshWorldState();
        }
    }

    private void spawnHologram(World world, ChunkPos unlockedChunk, Direction wallDirection, ChunkPos lockedChunk, ChunkRequirement requirement) {
        Location base = resolveBaseLocation(world, unlockedChunk, wallDirection);
        ItemDisplay itemDisplay = world.spawn(base.clone().add(0.0, 0.35, 0.0), ItemDisplay.class);
        itemDisplay.setItemStack(new ItemStack(requirement.material()));
        itemDisplay.setBillboard(Display.Billboard.CENTER);
        itemDisplay.setPersistent(false);
        itemDisplay.setGravity(false);
        tag(itemDisplay, lockedChunk);

        TextDisplay textDisplay = world.spawn(base.clone().add(0.0, 1.25, 0.0), TextDisplay.class);
        textDisplay.setText(ChatColor.GOLD + progression.displayName(requirement.material())
                + "\n" + ChatColor.YELLOW + "x" + requirement.amount()
                + "\n" + ChatColor.GRAY + messages.raw("hologram.right-click"));
        textDisplay.setBillboard(Display.Billboard.CENTER);
        textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
        textDisplay.setLineWidth(180);
        textDisplay.setSeeThrough(true);
        textDisplay.setShadowed(true);
        textDisplay.setPersistent(false);
        textDisplay.setGravity(false);
        tag(textDisplay, lockedChunk);

        Interaction interaction = world.spawn(base.clone().add(0.0, 0.75, 0.0), Interaction.class);
        interaction.setInteractionWidth((float) plugin.getConfig().getDouble("holograms.interaction-width", 2.75));
        interaction.setInteractionHeight((float) plugin.getConfig().getDouble("holograms.interaction-height", 3.0));
        interaction.setPersistent(false);
        interaction.setGravity(false);
        tag(interaction, lockedChunk);
    }

    private void tag(Entity entity, ChunkPos chunk) {
        entity.setInvulnerable(true);
        PersistentDataContainer container = entity.getPersistentDataContainer();
        container.set(plugin.getHologramMarkerKey(), PersistentDataType.STRING, "true");
        container.set(plugin.getHologramChunkKey(), PersistentDataType.STRING, chunk.key());
        spawnedEntities.add(entity.getUniqueId());
    }

    private ChunkPos readChunk(Entity entity) {
        if (!isChunkLockedHologram(entity)) {
            return null;
        }
        String value = entity.getPersistentDataContainer().get(plugin.getHologramChunkKey(), PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return ChunkPos.parse(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isChunkLockedHologram(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.getHologramMarkerKey(), PersistentDataType.STRING);
    }

    private Location resolveBaseLocation(World world, ChunkPos unlockedChunk, Direction wallDirection) {
        int minX = unlockedChunk.x() << 4;
        int minZ = unlockedChunk.z() << 4;
        double x = minX + 8.0;
        double z = minZ + 8.0;
        int y = highestWallY(world, unlockedChunk, wallDirection);
        double insideOffset = 0.05D;

        switch (wallDirection) {
            case NORTH -> {
                x = minX + 8.0;
                z = minZ + insideOffset;
            }
            case EAST -> {
                x = minX + 16.0 - insideOffset;
                z = minZ + 8.0;
            }
            case SOUTH -> {
                x = minX + 8.0;
                z = minZ + 16.0 - insideOffset;
            }
            case WEST -> {
                x = minX + insideOffset;
                z = minZ + 8.0;
            }
        }

        double offset = plugin.getConfig().getDouble("holograms.vertical-offset", 2.0);
        return new Location(world, x, y + offset, z);
    }

    private int highestWallY(World world, ChunkPos chunk, Direction wallDirection) {
        int minX = chunk.x() << 4;
        int minZ = chunk.z() << 4;
        int highest = world.getMinHeight();
        switch (wallDirection) {
            case NORTH -> {
                int z = minZ;
                for (int x = minX; x < minX + 16; x++) {
                    highest = Math.max(highest, world.getHighestBlockYAt(x, z));
                }
            }
            case EAST -> {
                int x = minX + 15;
                for (int z = minZ; z < minZ + 16; z++) {
                    highest = Math.max(highest, world.getHighestBlockYAt(x, z));
                }
            }
            case SOUTH -> {
                int z = minZ + 15;
                for (int x = minX; x < minX + 16; x++) {
                    highest = Math.max(highest, world.getHighestBlockYAt(x, z));
                }
            }
            case WEST -> {
                int x = minX;
                for (int z = minZ; z < minZ + 16; z++) {
                    highest = Math.max(highest, world.getHighestBlockYAt(x, z));
                }
            }
        }
        return highest;
    }
}
