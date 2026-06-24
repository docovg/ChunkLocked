package fr.abdelnaim.chunklocked.border;

import fr.abdelnaim.chunklocked.ChunkLockedPlugin;
import fr.abdelnaim.chunklocked.model.ChunkPos;
import fr.abdelnaim.chunklocked.model.Direction;
import fr.abdelnaim.chunklocked.progression.ChunkProgression;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class BorderManager {
    private final ChunkLockedPlugin plugin;
    private final ChunkProgression progression;
    private BukkitTask particleTask;

    public BorderManager(ChunkLockedPlugin plugin, ChunkProgression progression) {
        this.plugin = plugin;
        this.progression = progression;
    }

    public void start() {
        stop();
        long interval = Math.max(5L, plugin.getConfig().getLong("border.particle-interval-ticks", 20L));
        particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drawParticles, interval, interval);
    }

    public void restart() {
        start();
    }

    public void stop() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    private void drawParticles() {
        World world = plugin.getTargetWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            drawForPlayer(player);
        }
    }

    private void drawForPlayer(Player player) {
        if (!plugin.isManagedWorld(player.getWorld())) {
            return;
        }

        int radius = Math.max(1, plugin.getConfig().getInt("border.particle-view-distance-chunks", 3));
        int spacing = Math.max(1, plugin.getConfig().getInt("border.particle-spacing-blocks", 1));
        int height = Math.max(1, plugin.getConfig().getInt("border.particle-height-blocks", 4));
        float size = (float) Math.max(0.1D, plugin.getConfig().getDouble("border.particle-size", 1.25D));
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 70, 70), size);

        ChunkPos playerChunk = ChunkPos.fromLocation(player.getLocation());
        int baseY = clamp(player.getLocation().getBlockY() - 1, player.getWorld().getMinHeight(), player.getWorld().getMaxHeight() - height);
        for (ChunkPos unlocked : progression.getUnlockedChunksSnapshot()) {
            if (Math.abs(unlocked.x() - playerChunk.x()) > radius || Math.abs(unlocked.z() - playerChunk.z()) > radius) {
                continue;
            }
            for (Direction direction : Direction.CARDINALS) {
                ChunkPos neighbor = unlocked.neighbor(direction);
                if (!progression.isUnlocked(neighbor)) {
                    drawEdge(player, unlocked, direction, spacing, height, baseY, dust);
                }
            }
        }
    }

    private void drawEdge(
            Player player,
            ChunkPos unlocked,
            Direction direction,
            int spacing,
            int height,
            int baseY,
            Particle.DustOptions dust
    ) {
        int minX = unlocked.x() << 4;
        int minZ = unlocked.z() << 4;

        switch (direction) {
            case NORTH -> {
                double z = minZ;
                for (int offset = 0; offset <= 16; offset += spacing) {
                    spawnColumn(player, minX + offset, z, baseY, height, dust);
                }
            }
            case EAST -> {
                double x = minX + 16;
                for (int offset = 0; offset <= 16; offset += spacing) {
                    spawnColumn(player, x, minZ + offset, baseY, height, dust);
                }
            }
            case SOUTH -> {
                double z = minZ + 16;
                for (int offset = 0; offset <= 16; offset += spacing) {
                    spawnColumn(player, minX + offset, z, baseY, height, dust);
                }
            }
            case WEST -> {
                double x = minX;
                for (int offset = 0; offset <= 16; offset += spacing) {
                    spawnColumn(player, x, minZ + offset, baseY, height, dust);
                }
            }
        }
    }

    private void spawnColumn(Player player, double x, double z, int baseY, int height, Particle.DustOptions dust) {
        for (int yOffset = 0; yOffset < height; yOffset++) {
            player.spawnParticle(Particle.DUST, x, baseY + yOffset, z, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
