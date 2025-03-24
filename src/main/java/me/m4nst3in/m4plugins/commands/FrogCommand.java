package me.m4nst3in.m4plugins.commands;

import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.events.AbstractEvent;
import me.m4nst3in.m4plugins.events.FrogEvent;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import me.m4nst3in.m4plugins.utils.SelectionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FrogCommand implements CommandExecutor, TabCompleter {

    private final M4Eventos plugin;

    public FrogCommand(M4Eventos plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("m4eventos.frog.admin")) {
            MessageUtils.send(sender, plugin.getConfig().getString("mensagens.evento-sem-permissao"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        AbstractEvent abstractEvent = plugin.getEventManager().getEvent("frog");
        if (!(abstractEvent instanceof FrogEvent)) {
            MessageUtils.send(sender, "&c✖ Evento Frog Race não encontrado. Contate um administrador.");
            return true;
        }

        FrogEvent event = (FrogEvent) abstractEvent;

        switch (args[0].toLowerCase()) {
            case "iniciar":
                if (event.isRunning()) {
                    MessageUtils.send(sender, plugin.getConfig().getString("mensagens.evento-ja-iniciado"));
                    return true;
                }

                if (!event.canStart()) {
                    MessageUtils.send(sender, "&c✖ Não é possível iniciar o evento. Verifique se as localizações foram definidas e se a área é válida (máximo 10x10).");
                    return true;
                }

                if (event.start()) {
                    MessageUtils.send(sender, "&a✓ Evento Frog Race iniciado com sucesso!");
                } else {
                    MessageUtils.send(sender, "&c✖ Ocorreu um erro ao iniciar o evento. Verifique o console.");
                }
                break;

            case "parar":
                if (!event.isRunning()) {
                    MessageUtils.send(sender, plugin.getConfig().getString("mensagens.evento-nao-iniciado"));
                    return true;
                }

                if (event.stop()) {
                    MessageUtils.send(sender, "&a✓ Evento Frog Race parado com sucesso!");
                } else {
                    MessageUtils.send(sender, "&c✖ Ocorreu um erro ao parar o evento. Verifique o console.");
                }
                break;

            case "setarea":
                if (!(sender instanceof Player)) {
                    MessageUtils.send(sender, "&c✖ Apenas jogadores podem definir localizações.");
                    return true;
                }

                if (args.length < 2) {
                    MessageUtils.send(sender, "&c✖ Uso: /frog setarea <pos1|pos2>");
                    return true;
                }

                Player player = (Player) sender;

                // Se o jogador já estiver no modo de seleção
                if (plugin.getSelectionManager().hasSelection(player)) {
                    MessageUtils.send(player, "&c✖ Você já está em modo de seleção. Clique com botão direito em um bloco ou digite /frog cancelar para cancelar.");
                    return true;
                }

                if (args[1].equalsIgnoreCase("pos1")) {
                    plugin.getSelectionManager().startSelection(
                            player,
                            SelectionManager.SelectionType.FROG_AREA_POS1,
                            loc -> {
                                event.setPos1(loc);
                                MessageUtils.send(player, plugin.getConfig().getString("mensagens.frog-pos-definida")
                                        .replace("%posicao%", "1 da área do evento"));

                                // Verificar se a área é válida
                                if (event.getPos2() != null && !event.isValidArea()) {
                                    MessageUtils.send(player, plugin.getConfig().getString("mensagens.frog-area-invalida"));
                                }
                            });
                } else if (args[1].equalsIgnoreCase("pos2")) {
                    plugin.getSelectionManager().startSelection(
                            player,
                            SelectionManager.SelectionType.FROG_AREA_POS2,
                            loc -> {
                                event.setPos2(loc);
                                MessageUtils.send(player, plugin.getConfig().getString("mensagens.frog-pos-definida")
                                        .replace("%posicao%", "2 da área do evento"));

                                // Verificar se a área é válida
                                if (event.getPos1() != null && !event.isValidArea()) {
                                    MessageUtils.send(player, plugin.getConfig().getString("mensagens.frog-area-invalida"));
                                }
                            });
                } else {
                    MessageUtils.send(sender, "&c✖ Posição inválida. Use 'pos1' ou 'pos2'.");
                }
                break;

            case "setspawn":
                if (!(sender instanceof Player)) {
                    MessageUtils.send(sender, "&c✖ Apenas jogadores podem definir localizações.");
                    return true;
                }

                if (args.length < 2) {
                    MessageUtils.send(sender, "&c✖ Uso: /frog setspawn <pos1|pos2>");
                    return true;
                }

                Player p = (Player) sender;

                // Se o jogador já estiver no modo de seleção
                if (plugin.getSelectionManager().hasSelection(p)) {
                    MessageUtils.send(p, "&c✖ Você já está em modo de seleção. Clique com botão direito em um bloco ou digite /frog cancelar para cancelar.");
                    return true;
                }

                if (args[1].equalsIgnoreCase("pos1")) {
                    plugin.getSelectionManager().startSelection(
                            p,
                            SelectionManager.SelectionType.FROG_SPAWN_POS1,
                            loc -> {
                                event.setSpawnPos1(loc);
                                MessageUtils.send(p, plugin.getConfig().getString("mensagens.frog-pos-definida")
                                        .replace("%posicao%", "1 da área de spawn"));
                            });
                } else if (args[1].equalsIgnoreCase("pos2")) {
                    plugin.getSelectionManager().startSelection(
                            p,
                            SelectionManager.SelectionType.FROG_SPAWN_POS2,
                            loc -> {
                                event.setSpawnPos2(loc);
                                MessageUtils.send(p, plugin.getConfig().getString("mensagens.frog-pos-definida")
                                        .replace("%posicao%", "2 da área de spawn"));
                            });
                } else {
                    MessageUtils.send(sender, "&c✖ Posição inválida. Use 'pos1' ou 'pos2'.");
                }
                break;

            case "cancelar":
                if (!(sender instanceof Player)) {
                    MessageUtils.send(sender, "&c✖ Apenas jogadores podem usar este comando.");
                    return true;
                }

                plugin.getSelectionManager().cancelSelection((Player) sender);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtils.send(sender, "&a✦&b&l『 Frog Race 』&a✦");
        MessageUtils.send(sender, "&e/frog iniciar &7- Inicia o evento Frog Race");
        MessageUtils.send(sender, "&e/frog parar &7- Para o evento Frog Race");
        MessageUtils.send(sender, "&e/frog setarea pos1 &7- Define a primeira posição da área do evento");
        MessageUtils.send(sender, "&e/frog setarea pos2 &7- Define a segunda posição da área do evento");
        MessageUtils.send(sender, "&e/frog setspawn pos1 &7- Define a primeira posição da área de spawn");
        MessageUtils.send(sender, "&e/frog setspawn pos2 &7- Define a segunda posição da área de spawn");
        MessageUtils.send(sender, "&e/frog cancelar &7- Cancela uma seleção em andamento");
        MessageUtils.send(sender, "&7Nota: &eA área do evento deve ser de no máximo 10x10 blocos.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("m4eventos.frog.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("iniciar", "parar", "setarea", "setspawn", "cancelar").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("setarea") || args[0].equalsIgnoreCase("setspawn"))) {
            return Arrays.asList("pos1", "pos2").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}