package fr.abdelnaim.chunklocked.command;

import fr.abdelnaim.chunklocked.ChunkLockedPlugin;
import fr.abdelnaim.chunklocked.model.ChunkPos;
import fr.abdelnaim.chunklocked.progression.ChunkProgression;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChunkLockedCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("info", "reload", "reset");

    private final ChunkLockedPlugin plugin;
    private final ChunkProgression progression;

    public ChunkLockedCommand(ChunkLockedPlugin plugin, ChunkProgression progression) {
        this.plugin = plugin;
        this.progression = progression;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("chunklocked.admin")) {
            sender.sendMessage(Component.text("Tu n'as pas la permission.", NamedTextColor.RED));
            return true;
        }

        String subCommand = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (subCommand) {
            case "info" -> sendInfo(sender);
            case "reload" -> {
                plugin.reloadChunkLocked();
                sender.sendMessage(Component.text("ChunkLocked recharge.", NamedTextColor.GREEN));
            }
            case "reset" -> {
                progression.reset();
                plugin.refreshWorldState();
                sender.sendMessage(Component.text("Progression ChunkLocked reinitialisee.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Usage: /" + label + " <info|reload|reset>", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length != 1 || !sender.hasPermission("chunklocked.admin")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String subCommand : SUBCOMMANDS) {
            if (subCommand.startsWith(prefix)) {
                matches.add(subCommand);
            }
        }
        return matches;
    }

    private void sendInfo(CommandSender sender) {
        World world = plugin.getTargetWorld();
        ChunkPos initial = progression.getInitialChunk();
        sender.sendMessage(Component.text("ChunkLocked", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Monde: " + (world == null ? "aucun" : world.getName()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Chunk initial: " + (initial == null ? "non initialise" : initial.key()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Chunks debloques: " + progression.getUnlockedCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Chunks disponibles: " + progression.getFrontierChunks().size(), NamedTextColor.GRAY));
    }
}
