package fr.abdelnaim.chunklocked.message;

import java.io.File;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;
    private String language;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        language = plugin.getConfig().getString("language", "fr").toLowerCase(Locale.ROOT);
        if (!language.equals("fr") && !language.equals("en")) {
            plugin.getLogger().warning("Unsupported language '" + language + "', falling back to en.");
            language = "en";
        }

        saveDefaultLanguage("fr");
        saveDefaultLanguage("en");

        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String getLanguage() {
        return language;
    }

    public String raw(String key, Object... placeholders) {
        String value = messages.getString(key, key);
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            value = value.replace("{" + placeholders[index] + "}", String.valueOf(placeholders[index + 1]));
        }
        return value;
    }

    public String legacy(String key, Object... placeholders) {
        return ChatColor.translateAlternateColorCodes('&', raw(key, placeholders));
    }

    public Component component(String key, NamedTextColor color, Object... placeholders) {
        return Component.text(raw(key, placeholders), color);
    }

    private void saveDefaultLanguage(String code) {
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (!file.exists()) {
            plugin.saveResource("lang/" + code + ".yml", false);
        }
    }
}
