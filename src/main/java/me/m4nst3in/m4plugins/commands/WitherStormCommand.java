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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WitherStormCommand implements CommandExecutor, TabCompleter {

    private final M4Eventos plugin;

    public WitherStormCommand(M4Eventos plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("m4eventos.witherstorm.admin")) {
            MessageUtils.send(sender, plugin.getConfig().getString("mensagens.evento-sem-permissao"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        AbstractEvent event = plugin.getEventManager().getEvent("witherstorm");
        if (event == null) {
            MessageUtils.send(sender, "&cEvento Wither Storm não encontrado. Contate um administrador.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "iniciar":
                if (event.isRunning()) {
                    MessageUtils.send(sender, plugin.getConfig().getString("mensagens.evento-ja-iniciado"));
                    return true;
                }

                if (!event.canStart()) {
                    MessageUtils.send(sender, "&cNão é possível iniciar o evento. Verifique se as localizações foram definidas.");
                    return true;
                }

                if (event.start()) {
                    MessageUtils.send(sender, "&aEvento Wither Storm iniciado com sucesso!");
                } else {
                    MessageUtils.send(sender, "&cOcorreu um erro ao iniciar o evento. Verifique o console.");
                }
                break;

            case "parar":
                if (!event.isRunning()) {
                    MessageUtils.send(sender, plugin.getConfig().getString("mensagens.evento-nao-iniciado"));
                    return true;
                }

                if (event.stop()) {
                    MessageUtils.send(sender, "&aEvento Wither Storm parado com sucesso!");
                } else {
                    MessageUtils.send(sender, "&cOcorreu um erro ao parar o evento. Verifique o console.");
                }
                break;

            case "setspawn":
                if (!(sender instanceof Player)) {
                    MessageUtils.send(sender, "&cApenas jogadores podem definir localizações.");
                    return true;
                }

                if (args.length < 2) {
                    MessageUtils.send(sender, "&cUso: /witherstorm setspawn <wither|players>");
                    return true;
                }

                Player player = (Player) sender;

                if (args[1].equalsIgnoreCase("wither")) {
                    event.setEventLocation(player.getLocation());
                    MessageUtils.send(sender, plugin.getConfig().getString("mensagens.evento-spawn-definido")
                            .replace("%tipo%", "do Wither Storm"));
                } else if (args[1].equalsIgnoreCase("players")) {
                    event.setPlayerSpawnLocation(player.getLocation());
                    MessageUtils.send(sender, plugin.getConfig().getString("mensagens.evento-spawn-definido")
                            .replace("%tipo%", "dos jogadores"));
                } else {
                    MessageUtils.send(sender, "&cTipo de spawn inválido. Use 'wither' ou 'players'.");
                }
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtils.send(sender, "&6==== &b&lWither Storm &6====");
        MessageUtils.send(sender, "&e/witherstorm iniciar &7- Inicia o evento Wither Storm");
        MessageUtils.send(sender, "&e/witherstorm parar &7- Para o evento Wither Storm");
        MessageUtils.send(sender, "&e/witherstorm setspawn wither &7- Define a localização de spawn do Wither Storm");
        MessageUtils.send(sender, "&e/witherstorm setspawn players &7- Define a localização de spawn dos jogadores");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("m4eventos.witherstorm.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("iniciar", "parar", "setspawn").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
            return Arrays.asList("wither", "players").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}