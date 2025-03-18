package me.m4nst3in.m4plugins.commands;

import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.events.AbstractEvent;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EventoCommand implements CommandExecutor, TabCompleter {

    private final M4Eventos plugin;

    public EventoCommand(M4Eventos plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, "&cEste comando só pode ser executado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("m4eventos.entrar")) {
            MessageUtils.send(player, plugin.getConfig().getString("mensagens.evento-sem-permissao"));
            return true;
        }

        if (args.length < 1) {
            MessageUtils.send(player, "&cUso: /evento <nome>");
            MessageUtils.send(player, "&7Eventos disponíveis: &fwitherstorm");
            return true;
        }

        String eventId = args[0].toLowerCase();
        AbstractEvent event = plugin.getEventManager().getEvent(eventId);

        if (event == null) {
            MessageUtils.send(player, plugin.getConfig().getString("mensagens.evento-nao-encontrado"));
            return true;
        }

        if (!event.isRunning() || !event.isOpen()) {
            MessageUtils.send(player, "&cEste evento não está aberto para entrada no momento.");
            return true;
        }

        if (plugin.getEventManager().isPlayerInAnyEvent(player)) {
            MessageUtils.send(player, plugin.getConfig().getString("mensagens.evento-ja-participando"));
            return true;
        }

        plugin.getEventManager().addPlayerToEvent(player, event);
        MessageUtils.send(player, plugin.getConfig().getString("mensagens.evento-entrou")
                .replace("%evento%", event.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("m4eventos.entrar")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return plugin.getEventManager().getAllEvents().stream()
                    .filter(AbstractEvent::isOpen)
                    .map(AbstractEvent::getId)
                    .filter(id -> id.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}