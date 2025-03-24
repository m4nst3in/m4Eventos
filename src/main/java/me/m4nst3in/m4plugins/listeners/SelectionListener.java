package me.m4nst3in.m4plugins.listeners;

import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import me.m4nst3in.m4plugins.utils.SelectionManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SelectionListener implements Listener {

    private final M4Eventos plugin;

    public SelectionListener(M4Eventos plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Verificar se o jogador está em modo de seleção
        if (!plugin.getSelectionManager().hasSelection(player)) {
            return;
        }

        // Botão direito em bloco = confirmar seleção
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            event.setCancelled(true);

            Location location = event.getClickedBlock().getLocation();
            SelectionManager.SelectionData data = plugin.getSelectionManager().getSelection(player);

            // Adicionar efeitos visuais para feedback
            player.playSound(location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            player.spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0.5, 1.5, 0.5), 10, 0.3, 0.3, 0.3, 0);

            // Completar a seleção
            plugin.getSelectionManager().completeSelection(player, location);
        }

        // Botão esquerdo = cancelar seleção
        else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            plugin.getSelectionManager().cancelSelection(player);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Limpar seleções quando o jogador sair
        plugin.getSelectionManager().cancelSelection(event.getPlayer());
    }
}