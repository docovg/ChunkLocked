package fr.abdelnaim.chunklocked.listener;

import fr.abdelnaim.chunklocked.ChunkLockedPlugin;
import fr.abdelnaim.chunklocked.progression.ChunkProgression;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class ProtectionListener implements Listener {
    private final ChunkLockedPlugin plugin;
    private final ChunkProgression progression;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public ProtectionListener(ChunkLockedPlugin plugin, ChunkProgression progression) {
        this.plugin = plugin;
        this.progression = progression;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !isLocked(to)) {
            return;
        }

        Location from = event.getFrom();
        if (isLocked(from)) {
            Location safe = progression.getSafeSpawnLocation();
            if (safe != null) {
                event.setTo(safe);
            } else {
                event.setCancelled(true);
            }
        } else {
            event.setCancelled(true);
        }
        warn(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || !isLocked(to)) {
            return;
        }
        event.setCancelled(true);
        warn(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLocked(event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isLocked(event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && isLocked(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Location changedBlock = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        if (isLocked(changedBlock)) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isLocked(event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> moveToSafeIfNeeded(event.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (isLocked(event.getRespawnLocation())) {
            Location safe = progression.getSafeSpawnLocation();
            if (safe != null) {
                event.setRespawnLocation(safe);
            }
        }
    }

    private void moveToSafeIfNeeded(Player player) {
        if (!isLocked(player.getLocation())) {
            return;
        }
        Location safe = progression.getSafeSpawnLocation();
        if (safe != null) {
            player.teleport(safe);
            warn(player);
        }
    }

    private boolean isLocked(Location location) {
        return location != null
                && location.getWorld() != null
                && plugin.isManagedWorld(location.getWorld())
                && !progression.isUnlocked(location);
    }

    private void warn(Player player) {
        long now = System.currentTimeMillis();
        long previous = lastWarning.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 1500L) {
            return;
        }
        lastWarning.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text("Chunk verrouille", NamedTextColor.RED));
    }
}
