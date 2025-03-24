package me.m4nst3in.m4plugins.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class SelectionManager {

    private final Map<UUID, SelectionData> playerSelections;

    public SelectionManager() {
        this.playerSelections = new HashMap<>();
    }

    public void startSelection(Player player, SelectionType type, Consumer<Location> callback) {
        playerSelections.put(player.getUniqueId(), new SelectionData(type, callback));

        String typeMsg;
        switch (type) {
            case FROG_AREA_POS1:
                typeMsg = "primeira posição da área do evento Frog";
                break;
            case FROG_AREA_POS2:
                typeMsg = "segunda posição da área do evento Frog";
                break;
            case FROG_SPAWN_POS1:
                typeMsg = "primeira posição do spawn do evento Frog";
                break;
            case FROG_SPAWN_POS2:
                typeMsg = "segunda posição do spawn do evento Frog";
                break;
            default:
                typeMsg = "posição";
                break;
        }

        MessageUtils.send(player, "&a✓ Modo de seleção ativado para: &e" + typeMsg);
        MessageUtils.send(player, "&aDê um &eclique direito &aem um bloco para definir a posição.");
        MessageUtils.send(player, "&cDê um &eclique esquerdo &cem qualquer lugar ou digite &e/frog cancelar &cpara cancelar.");
    }

    public boolean hasSelection(Player player) {
        return playerSelections.containsKey(player.getUniqueId());
    }

    public SelectionData getSelection(Player player) {
        return playerSelections.get(player.getUniqueId());
    }

    public void completeSelection(Player player, Location location) {
        SelectionData data = playerSelections.remove(player.getUniqueId());
        if (data != null && data.getCallback() != null) {
            data.getCallback().accept(location);
        }
    }

    public void cancelSelection(Player player) {
        if (playerSelections.remove(player.getUniqueId()) != null) {
            MessageUtils.send(player, "&c✖ Seleção cancelada.");
        }
    }

    public enum SelectionType {
        FROG_AREA_POS1,
        FROG_AREA_POS2,
        FROG_SPAWN_POS1,
        FROG_SPAWN_POS2
    }

    public static class SelectionData {
        private final SelectionType type;
        private final Consumer<Location> callback;

        public SelectionData(SelectionType type, Consumer<Location> callback) {
            this.type = type;
            this.callback = callback;
        }

        public SelectionType getType() {
            return type;
        }

        public Consumer<Location> getCallback() {
            return callback;
        }
    }
}