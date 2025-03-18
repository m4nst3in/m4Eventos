package me.m4nst3in.m4plugins.events;

import lombok.Getter;
import me.m4nst3in.m4plugins.M4Eventos;
import org.bukkit.entity.Player;

import java.util.*;

public class EventManager {

    private final M4Eventos plugin;
    @Getter
    private final Map<String, AbstractEvent> registeredEvents;
    @Getter
    private final Set<UUID> activePlayers;

    public EventManager(M4Eventos plugin) {
        this.plugin = plugin;
        this.registeredEvents = new HashMap<>();
        this.activePlayers = new HashSet<>();
    }

    public void registerEvent(AbstractEvent event) {
        registeredEvents.put(event.getId().toLowerCase(), event);
        plugin.getLogger().info("Evento registrado: " + event.getName());
    }

    public AbstractEvent getEvent(String id) {
        return registeredEvents.get(id.toLowerCase());
    }

    public List<AbstractEvent> getAllEvents() {
        return new ArrayList<>(registeredEvents.values());
    }

    public boolean isPlayerInAnyEvent(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public void addPlayerToEvent(Player player, AbstractEvent event) {
        activePlayers.add(player.getUniqueId());
        event.addPlayer(player);
    }

    public void removePlayerFromEvent(Player player, AbstractEvent event) {
        activePlayers.remove(player.getUniqueId());
        event.removePlayer(player);
    }

    public AbstractEvent getEventByPlayer(Player player) {
        for (AbstractEvent event : registeredEvents.values()) {
            if (event.hasPlayer(player)) {
                return event;
            }
        }
        return null;
    }

    public boolean startEvent(String eventId) {
        AbstractEvent event = getEvent(eventId);
        if (event != null && !event.isRunning()) {
            return event.start();
        }
        return false;
    }

    public boolean stopEvent(String eventId) {
        AbstractEvent event = getEvent(eventId);
        if (event != null && event.isRunning()) {
            return event.stop();
        }
        return false;
    }

    public void stopAllEvents() {
        for (AbstractEvent event : registeredEvents.values()) {
            if (event.isRunning()) {
                event.stop();
            }
        }
        activePlayers.clear();
    }
}