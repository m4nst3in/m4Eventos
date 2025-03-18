package me.m4nst3in.m4plugins.scheduler;

import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.events.AbstractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.logging.Level;

public class EventScheduler {

    private final M4Eventos plugin;
    private BukkitRunnable schedulerTask;

    public EventScheduler(M4Eventos plugin) {
        this.plugin = plugin;
    }

    public void startScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }

        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkScheduledEvents();
            }
        };

        // Executa a cada minuto
        schedulerTask.runTaskTimer(plugin, 20L * 60L, 20L * 60L);
        plugin.getLogger().info("Agendador de eventos iniciado");
    }

    private void checkScheduledEvents() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek currentDay = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);

        for (AbstractEvent event : plugin.getEventManager().getAllEvents()) {
            try {
                Optional<DayOfWeek> eventDay = event.getScheduleDay();
                Optional<LocalTime> eventTime = event.getScheduleTime();

                if (eventDay.isPresent() && eventTime.isPresent()) {
                    DayOfWeek day = eventDay.get();
                    LocalTime time = eventTime.get();

                    if (currentDay == day && currentTime.equals(time)) {
                        if (!event.isRunning() && event.canStart()) {
                            plugin.getLogger().info("Iniciando automaticamente o evento agendado: " + event.getName());
                            event.start();
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao verificar evento agendado: " + event.getName(), e);
            }
        }
    }

    public void stopScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }
}