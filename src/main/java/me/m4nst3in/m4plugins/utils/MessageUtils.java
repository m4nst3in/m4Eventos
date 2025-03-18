package me.m4nst3in.m4plugins.utils;

import me.m4nst3in.m4plugins.M4Eventos;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class MessageUtils {

    private static String prefix = "&8[&b&lM4&f&lEventos&8] ";

    public static void init(M4Eventos plugin) {
        prefix = plugin.getConfig().getString("mensagens.prefix", prefix);
    }

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void send(CommandSender sender, String message) {
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