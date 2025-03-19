package me.m4nst3in.m4plugins.utils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class MessageUtils {

    private static String prefix = "&8[&b&l᠌ᐈ &f&lM4&b&lEventos &8] ";

    public static void init(org.bukkit.plugin.Plugin plugin) {
        // Método de inicialização seguro que não depende diretamente da config
        plugin.getLogger().info("Inicializando MessageUtils com prefixo padrão");
    }

    public static void setPrefix(String newPrefix) {
        if (newPrefix != null && !newPrefix.isEmpty()) {
            prefix = newPrefix;
        }
    }

    public static String color(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null) return;
        sender.sendMessage(color(prefix + message));
    }

    public static String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        return String.format("%02d:%02d", minutes, remainingSeconds);
    }

    public static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }
}