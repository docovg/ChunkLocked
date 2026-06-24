package fr.abdelnaim.chunklocked.command;

import fr.abdelnaim.chunklocked.ChunkLockedPlugin;
import fr.abdelnaim.chunklocked.message.MessageService;
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
    private final MessageService messages;

    public ChunkLockedCommand(ChunkLockedPlugin plugin, ChunkProgression progression, MessageService messages) {
        this.plugin = plugin;
        this.progression = progression;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("chunklocked.admin")) {
            sender.sendMessage(messages.component("command.no-permission", NamedTextColor.RED));
            return true;
        }

        String subCommand = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (subCommand) {
            case "info" -> sendInfo(sender);
            case "reload" -> {
                plugin.reloadChunkLocked();
                sender.sendMessage(messages.component("command.reloaded", NamedTextColor.GREEN));
            }
            case "reset" -> {
                progression.reset();
                plugin.refreshWorldState();
                sender.sendMessage(messages.component("command.reset", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(messages.component("command.usage", NamedTextColor.YELLOW, "label", label));
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
        String worldName = world == null ? messages.raw("command.info.none") : world.getName();
        String initialChunk = initial == null ? messages.raw("command.info.not-initialized") : initial.key();
        sender.sendMessage(messages.component("command.info.title", NamedTextColor.GOLD));
        sender.sendMessage(messages.component("command.info.world", NamedTextColor.GRAY, "world", worldName));
        sender.sendMessage(messages.component("command.info.initial", NamedTextColor.GRAY, "chunk", initialChunk));
        sender.sendMessage(messages.component("command.info.unlocked", NamedTextColor.GRAY, "count", progression.getUnlockedCount()));
        sender.sendMessage(messages.component("command.info.available", NamedTextColor.GRAY, "count", progression.getFrontierChunks().size()));
    }
}
