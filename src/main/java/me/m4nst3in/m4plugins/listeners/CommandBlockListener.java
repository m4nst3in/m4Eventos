package me.m4nst3in.m4plugins.listeners;

import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.events.AbstractEvent;
import me.m4nst3in.m4plugins.events.FrogEvent;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CommandBlockListener implements Listener {

    private final M4Eventos plugin;

    public CommandBlockListener(M4Eventos plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Permitir sempre para administradores
        if (player.hasPermission("m4eventos.bypasscmds")) {
            return;
        }

        // Verificar se o jogador está participando do evento Frog
        AbstractEvent abstractEvent = plugin.getEventManager().getEvent("frog");
        if (!(abstractEvent instanceof FrogEvent)) {
            return;
        }

        FrogEvent frogEvent = (FrogEvent) abstractEvent;

        // Se o jogador está no evento e o evento está em modo de jogo
        if (frogEvent.isRunning() && frogEvent.isGameStarted() && frogEvent.getPlayers().contains(player.getUniqueId())) {
            // Extrair o comando (removendo a barra)
            String command = event.getMessage().substring(1);

            // Verificar se é um comando permitido
            if (!frogEvent.canUseCommand(command)) {
                event.setCancelled(true);
                MessageUtils.send(player, "&c✖ Comandos estão bloqueados durante o evento! Apenas /g é permitido.");
                return;
            }
        }
    }
}