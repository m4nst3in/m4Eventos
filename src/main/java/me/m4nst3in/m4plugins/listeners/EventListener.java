package me.m4nst3in.m4plugins.listeners;

import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.events.AbstractEvent;
import me.m4nst3in.m4plugins.events.WitherStormEvent;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class EventListener implements Listener {

    private final M4Eventos plugin;

    public EventListener(M4Eventos plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Atualiza os dados do jogador no banco de dados
        Player player = event.getPlayer();
        plugin.getDatabaseManager().updatePlayerData(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remover jogador de qualquer evento se ele estiver participando
        AbstractEvent playerEvent = plugin.getEventManager().getEventByPlayer(player);
        if (playerEvent != null) {
            plugin.getEventManager().removePlayerFromEvent(player, playerEvent);
        }

        // Remover jogador do menu
        plugin.getMenuManager().removePlayerFromMenu(player);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Encaminha o evento para os gerenciadores de eventos específicos
        if (event.getEntity() instanceof Wither || event.getEntity() instanceof WitherSkeleton) {
            WitherStormEvent witherStormEvent = (WitherStormEvent) plugin.getEventManager().getEvent("witherstorm");
            if (witherStormEvent != null && witherStormEvent.isRunning()) {
                witherStormEvent.handleEntityDamage(event);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // Encaminha o evento para os gerenciadores de eventos específicos
        if (event.getEntity() instanceof Wither || event.getEntity() instanceof WitherSkeleton) {
            WitherStormEvent witherStormEvent = (WitherStormEvent) plugin.getEventManager().getEvent("witherstorm");
            if (witherStormEvent != null && witherStormEvent.isRunning()) {
                witherStormEvent.handleEntityDeath(event);
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        // Impedir explosões do Wither Storm
        if (entity instanceof Wither && entity.hasMetadata("witherstorm")) {
            event.blockList().clear();
        }

        // Impedir explosões dos guardiões do Wither Storm
        if (entity instanceof Wither && entity.hasMetadata("witherstorm_guardian")) {
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Impedir jogadores em eventos de quebrar blocos
        AbstractEvent playerEvent = plugin.getEventManager().getEventByPlayer(player);
        if (playerEvent != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getClickedInventory();

        if (inventory == null) return;
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.AIR) return;

        if (plugin.getMenuManager().isPlayerInMenu(player)) {
            event.setCancelled(true);
            handleMenuClick(player, event.getCurrentItem(), plugin.getMenuManager().getPlayerMenu(player));
        }
    }

    private void handleMenuClick(Player player, ItemStack clickedItem, String menuType) {
        if (menuType == null) return;

        // Menu principal
        if (menuType.equals("main")) {
            if (clickedItem.getType() == Material.DRAGON_EGG) {
                plugin.getMenuManager().openEventosMenu(player);
            } else if (clickedItem.getType() == Material.NETHER_STAR) {
                plugin.getMenuManager().openTopMenu(player);
            }
        }

        // Menu de eventos
        else if (menuType.equals("eventos")) {
            if (clickedItem.getType() == Material.BARRIER) {
                plugin.getMenuManager().openMainMenu(player);
            } else if (clickedItem.getType() == Material.WITHER_SKELETON_SKULL) {
                AbstractEvent event = plugin.getEventManager().getEvent("witherstorm");

                if (event != null && event.isRunning() && event.isOpen()) {
                    player.closeInventory();
                    player.performCommand("evento witherstorm");
                } else {
                    MessageUtils.send(player, "&cEste evento não está aberto para entrada no momento.");
                }
            }
        }

        // Menu de top jogadores
        else if (menuType.equals("top")) {
            if (clickedItem.getType() == Material.BARRIER) {
                plugin.getMenuManager().openMainMenu(player);
            } else if (clickedItem.getType() == Material.WITHER_SKELETON_SKULL) {
                AbstractEvent event = plugin.getEventManager().getEvent("witherstorm");
                if (event != null) {
                    plugin.getMenuManager().openEventTopMenu(player, event);
                }
            }
        }

        // Menu de top jogadores específico
        else if (menuType.startsWith("top_")) {
            if (clickedItem.getType() == Material.BARRIER) {
                plugin.getMenuManager().openTopMenu(player);
            }
        }
    }
}