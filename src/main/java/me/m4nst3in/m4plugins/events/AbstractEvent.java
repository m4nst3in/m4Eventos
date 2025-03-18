package me.m4nst3in.m4plugins.events;

import lombok.Getter;
import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public abstract class AbstractEvent {

    protected final M4Eventos plugin;
    @Getter
    protected final String id;
    @Getter
    protected final String name;
    @Getter
    protected String scheduledTime;
    @Getter
    protected boolean running;
    @Getter
    protected boolean open;
    @Getter
    protected Set<UUID> players;
    @Getter
    protected Location eventLocation;
    @Getter
    protected Location playerSpawnLocation;
    @Getter
    protected final List<ItemStack> rewardItems;
    @Getter
    protected int rewardCoins;

    public AbstractEvent(M4Eventos plugin, String id, String name) {
        this.plugin = plugin;
        this.id = id;
        this.name = name;
        this.running = false;
        this.open = false;
        this.players = new HashSet<>();
        this.rewardItems = new ArrayList<>();

        // Carregando configurações específicas do evento
        loadEventConfig();
    }

    protected void loadEventConfig() {
        ConfigurationSection eventConfig = plugin.getConfig().getConfigurationSection("eventos." + id);
        if (eventConfig != null) {
            this.scheduledTime = eventConfig.getString("agendamento");
            this.rewardCoins = eventConfig.getInt("recompensas.coins");

            // Carregando localização salva (se existir)
            this.eventLocation = getLocationFromConfig("eventos." + id + ".localizacoes.evento");
            this.playerSpawnLocation = getLocationFromConfig("eventos." + id + ".localizacoes.jogadores");
        }
    }

    protected Location getLocationFromConfig(String path) {
        ConfigurationSection locSection = plugin.getConfig().getConfigurationSection(path);
        if (locSection != null) {
            String world = locSection.getString("world");
            double x = locSection.getDouble("x");
            double y = locSection.getDouble("y");
            double z = locSection.getDouble("z");
            float yaw = (float) locSection.getDouble("yaw");
            float pitch = (float) locSection.getDouble("pitch");

            return new Location(plugin.getServer().getWorld(world), x, y, z, yaw, pitch);
        }
        return null;
    }

    protected void saveLocationToConfig(String path, Location location) {
        plugin.getConfig().set(path + ".world", location.getWorld().getName());
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(path + ".yaw", location.getYaw());
        plugin.getConfig().set(path + ".pitch", location.getPitch());
        plugin.saveConfig();
    }

    public boolean hasPlayer(Player player) {
        return players.contains(player.getUniqueId());
    }

    public void addPlayer(Player player) {
        players.add(player.getUniqueId());
        onPlayerJoin(player);
    }

    public void removePlayer(Player player) {
        players.remove(player.getUniqueId());
        onPlayerLeave(player);
    }

    public void broadcast(String message) {
        for (UUID uuid : players) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                MessageUtils.send(player, message);
            }
        }
    }

    public void setEventLocation(Location location) {
        this.eventLocation = location;
        saveLocationToConfig("eventos." + id + ".localizacoes.evento", location);
    }

    public void setPlayerSpawnLocation(Location location) {
        this.playerSpawnLocation = location;
        saveLocationToConfig("eventos." + id + ".localizacoes.jogadores", location);
    }

    public Optional<DayOfWeek> getScheduleDay() {
        if (scheduledTime == null || !scheduledTime.contains("-")) {
            return Optional.empty();
        }

        String[] parts = scheduledTime.split("-");
        if (parts.length != 2) {
            return Optional.empty();
        }

        try {
            return Optional.of(DayOfWeek.valueOf(parts[0].toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<LocalTime> getScheduleTime() {
        if (scheduledTime == null || !scheduledTime.contains("-")) {
            return Optional.empty();
        }

        String[] parts = scheduledTime.split("-");
        if (parts.length != 2) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalTime.parse(parts[1], DateTimeFormatter.ofPattern("HH:mm")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Métodos abstratos que as implementações específicas de eventos devem implementar
    public abstract boolean start();
    public abstract boolean stop();
    public abstract boolean canStart();
    public abstract boolean openForPlayers();
    public abstract boolean closeForPlayers();

    // Métodos de ciclo de vida do evento
    protected abstract void onEventStart();
    protected abstract void onEventEnd();
    protected abstract void onPlayerJoin(Player player);
    protected abstract void onPlayerLeave(Player player);
    protected abstract void handlePlayerWin(Player player);
}