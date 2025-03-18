package me.m4nst3in.m4plugins.commands;

import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.gui.MenuManager;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EventosCommand implements CommandExecutor {

    private final M4Eventos plugin;

    public EventosCommand(M4Eventos plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, "&cEste comando só pode ser executado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("m4eventos.menu")) {
            MessageUtils.send(player, plugin.getConfig().getString("mensagens.evento-sem-permissao"));
            return true;
        }

        // Abre o menu principal de eventos
        plugin.getMenuManager().openMainMenu(player);
        return true;
    }
}