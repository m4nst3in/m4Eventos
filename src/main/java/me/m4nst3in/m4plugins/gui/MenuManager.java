package me.m4nst3in.m4plugins.gui;

import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.events.AbstractEvent;
import me.m4nst3in.m4plugins.utils.ItemBuilder;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

public class MenuManager {

    private final M4Eventos plugin;
    private final Map<UUID, String> openMenus;

    public MenuManager(M4Eventos plugin) {
        this.plugin = plugin;
        this.openMenus = new HashMap<>();
    }

    public void openMainMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Menu de Eventos");

        // Item para eventos disponíveis
        ItemStack eventosItem = new ItemBuilder(Material.DRAGON_EGG)
                .name(ChatColor.GOLD + "Eventos Disponíveis")
                .lore(
                        ChatColor.GRAY + "Clique para ver todos os",
                        ChatColor.GRAY + "eventos disponíveis."
                )
                .build();

        // Item para top jogadores
        ItemStack topItem = new ItemBuilder(Material.NETHER_STAR)
                .name(ChatColor.AQUA + "Top Vencedores")
                .lore(
                        ChatColor.GRAY + "Clique para ver os melhores",
                        ChatColor.GRAY + "jogadores de cada evento."
                )
                .build();

        // Decoração do menu
        ItemStack glassPane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            menu.setItem(i, glassPane);
        }

        menu.setItem(11, eventosItem);
        menu.setItem(15, topItem);

        player.openInventory(menu);
        openMenus.put(player.getUniqueId(), "main");
    }

    public void openEventosMenu(Player player) {
        List<AbstractEvent> events = plugin.getEventManager().getAllEvents();
        int size = Math.min(54, ((events.size() / 9) + 1) * 9);
        Inventory menu = Bukkit.createInventory(null, size, ChatColor.GOLD + "Eventos Disponíveis");

        for (int i = 0; i < size; i++) {
            menu.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        int slot = 0;
        for (AbstractEvent event : events) {
            Material material = getMaterialForEvent(event.getId());
            List<String> lore = new ArrayList<>();

            lore.add("");
            lore.add(ChatColor.YELLOW + "Status: " + getStatusText(event));

            Optional<DayOfWeek> day = event.getScheduleDay();
            Optional<LocalTime> time = event.getScheduleTime();
            if (day.isPresent() && time.isPresent()) {
                lore.add(ChatColor.YELLOW + "Agendamento: " + ChatColor.WHITE +
                        day.get().getDisplayName(TextStyle.FULL, new Locale("pt", "BR")) +
                        " às " + time.get().format(DateTimeFormatter.ofPattern("HH:mm")));
            }

            lore.add("");
            if (event.isRunning() && event.isOpen()) {
                lore.add(ChatColor.GREEN + "Clique para entrar neste evento!");
            } else if (event.isRunning() && !event.isOpen()) {
                lore.add(ChatColor.RED + "Este evento está em andamento, mas fechado.");
            } else {
                lore.add(ChatColor.RED + "Este evento não está ativo no momento.");
            }

            ItemStack eventItem = new ItemBuilder(material)
                    .name(ChatColor.GOLD + event.getName())
                    .lore(lore)
                    .glow(event.isRunning())
                    .build();

            menu.setItem(slot++, eventItem);
        }

        // Botão voltar
        menu.setItem(size - 5, new ItemBuilder(Material.BARRIER)
                .name(ChatColor.RED + "Voltar")
                .build());

        player.openInventory(menu);
        openMenus.put(player.getUniqueId(), "eventos");
    }

    public void openTopMenu(Player player) {
        List<AbstractEvent> events = plugin.getEventManager().getAllEvents();
        int size = Math.min(54, ((events.size() / 9) + 1) * 9);
        Inventory menu = Bukkit.createInventory(null, size, ChatColor.AQUA + "Top Vencedores");

        for (int i = 0; i < size; i++) {
            menu.setItem(i, new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE).name(" ").build());
        }

        int slot = 0;
        for (AbstractEvent event : events) {
            Material material = getMaterialForEvent(event.getId());
            List<String> lore = new ArrayList<>();

            lore.add("");
            lore.add(ChatColor.YELLOW + "Clique para ver os melhores");
            lore.add(ChatColor.YELLOW + "jogadores deste evento.");
            lore.add("");

            ItemStack eventItem = new ItemBuilder(material)
                    .name(ChatColor.GOLD + "Top " + event.getName())
                    .lore(lore)
                    .build();

            menu.setItem(slot++, eventItem);
        }

        // Botão voltar
        menu.setItem(size - 5, new ItemBuilder(Material.BARRIER)
                .name(ChatColor.RED + "Voltar")
                .build());

        player.openInventory(menu);
        openMenus.put(player.getUniqueId(), "top");
    }

    public void openEventTopMenu(Player player, AbstractEvent event) {
        Inventory menu = Bukkit.createInventory(null, 36, ChatColor.AQUA + "Top " + event.getName());

        for (int i = 0; i < 36; i++) {
            menu.setItem(i, new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE).name(" ").build());
        }

        // Buscar os top 10 jogadores
        Map<UUID, Integer> topWinners = plugin.getDatabaseManager().getTopWinners(event.getId(), 10);
        int position = 1;

        for (Map.Entry<UUID, Integer> entry : topWinners.entrySet()) {
            UUID playerUuid = entry.getKey();
            int wins = entry.getValue();
            String playerName = plugin.getDatabaseManager().getPlayerName(playerUuid);

            Material material;
            if (position == 1) material = Material.GOLDEN_HELMET;
            else if (position == 2) material = Material.IRON_HELMET;
            else if (position == 3) material = Material.CHAINMAIL_HELMET;
            else material = Material.LEATHER_HELMET;

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Jogador: " + ChatColor.YELLOW + playerName);
            lore.add(ChatColor.GRAY + "Vitórias: " + ChatColor.GREEN + wins);

            ItemStack item = new ItemBuilder(material)
                    .name(ChatColor.GOLD + "#" + position + " - " + playerName)
                    .lore(lore)
                    .build();

            menu.setItem(9 + position, item);
            position++;
        }

        // Se não há vencedores
        if (topWinners.isEmpty()) {
            ItemStack noWinners = new ItemBuilder(Material.BARRIER)
                    .name(ChatColor.RED + "Nenhum vencedor encontrado")
                    .lore(ChatColor.GRAY + "Seja o primeiro a vencer este evento!")
                    .build();
            menu.setItem(13, noWinners);
        }

        // Botão voltar
        menu.setItem(31, new ItemBuilder(Material.BARRIER)
                .name(ChatColor.RED + "Voltar")
                .build());

        player.openInventory(menu);
        openMenus.put(player.getUniqueId(), "top_" + event.getId());
    }

    public boolean isPlayerInMenu(Player player) {
        return openMenus.containsKey(player.getUniqueId());
    }

    public String getPlayerMenu(Player player) {
        return openMenus.get(player.getUniqueId());
    }

    public void removePlayerFromMenu(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    private String getStatusText(AbstractEvent event) {
        if (event.isRunning() && event.isOpen()) {
            return ChatColor.GREEN + "Aberto para entrada";
        } else if (event.isRunning() && !event.isOpen()) {
            return ChatColor.GOLD + "Em andamento";
        } else {
            return ChatColor.RED + "Fechado";
        }
    }

    public void handleMenuClose(Player player) {
        if (isPlayerInMenu(player)) {
            removePlayerFromMenu(player);
        }
    }

    private Material getMaterialForEvent(String eventId) {
        switch (eventId.toLowerCase()) {
            case "witherstorm":
                return Material.WITHER_SKELETON_SKULL;
            case "frog":
                return Material.SNOW_BLOCK;
            default:
                return Material.PAPER;
        }
    }
}