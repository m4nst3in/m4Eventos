package me.m4nst3in.m4plugins.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import me.m4nst3in.m4plugins.M4Eventos;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DatabaseManager {

    private final M4Eventos plugin;
    @Getter
    private HikariDataSource dataSource;
    private final String dbType;

    public DatabaseManager(M4Eventos plugin) {
        this.plugin = plugin;
        this.dbType = plugin.getConfig().getString("database.tipo", "SQLITE");
    }

    public void initialize() {
        try {
            setupHikariCP();
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao inicializar banco de dados", e);
        }
    }

    private void setupHikariCP() {
        HikariConfig config = new HikariConfig();

        // Configuração específica para SQLite
        if ("SQLITE".equalsIgnoreCase(dbType)) {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            String dbFile = plugin.getConfig().getString("database.arquivo", "database.db");
            File dbPath = new File(dataFolder, dbFile);

            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + dbPath.getAbsolutePath());
            config.addDataSourceProperty("foreignKeys", "true");

            // SQLite tem algumas limitações, então ajustamos as configurações apropriadamente
            config.setMaximumPoolSize(1);
            config.setConnectionTimeout(30000);
        } else {
            // Configurações para MySQL (para expansão futura)
            config.setJdbcUrl(plugin.getConfig().getString("database.mysql.url"));
            config.setUsername(plugin.getConfig().getString("database.mysql.usuario"));
            config.setPassword(plugin.getConfig().getString("database.mysql.senha"));
            config.setMaximumPoolSize(plugin.getConfig().getInt("database.hikari.maximumPoolSize", 10));
            config.setConnectionTimeout(plugin.getConfig().getInt("database.hikari.connectionTimeout", 30000));
        }

        dataSource = new HikariDataSource(config);
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Tabela de jogadores
            stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "name VARCHAR(16) NOT NULL, " +
                    "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Tabela de eventos
            stmt.execute("CREATE TABLE IF NOT EXISTS events (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "name VARCHAR(50) NOT NULL)");

            // Tabela de vitórias em eventos
            stmt.execute("CREATE TABLE IF NOT EXISTS event_wins (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "event_id VARCHAR(36) NOT NULL, " +
                    "win_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE, " +
                    "FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE)");

            // Insert default events using the same connection
            try {
                PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO events (id, name) VALUES (?, ?)");
                // Inserir evento Wither Storm
                ps.setString(1, "witherstorm");
                ps.setString(2, "Wither Storm");
                ps.executeUpdate();
                ps.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao inserir eventos padrão", e);
            }

            plugin.getLogger().info("Tabelas do banco de dados criadas com sucesso");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao criar tabelas no banco de dados", e);
        }
    }

    private void insertDefaultEvents() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO events (id, name) VALUES (?, ?)")) {

            // Inserir evento Wither Storm
            ps.setString(1, "witherstorm");
            ps.setString(2, "Wither Storm");
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao inserir eventos padrão", e);
        }
    }

    public void updatePlayerData(UUID uuid, String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO players (uuid, name, last_login) VALUES (?, ?, CURRENT_TIMESTAMP)")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao atualizar dados do jogador", e);
        }
    }

    public void addEventWin(UUID playerUUID, String eventId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO event_wins (player_uuid, event_id) VALUES (?, ?)")) {

            ps.setString(1, playerUUID.toString());
            ps.setString(2, eventId);
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao registrar vitória em evento", e);
        }
    }

    public Map<UUID, Integer> getTopWinners(String eventId, int limit) {
        Map<UUID, Integer> topWinners = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT p.uuid, p.name, COUNT(*) as wins " +
                             "FROM event_wins ew " +
                             "JOIN players p ON ew.player_uuid = p.uuid " +
                             "WHERE ew.event_id = ? " +
                             "GROUP BY p.uuid, p.name " +
                             "ORDER BY wins DESC " +
                             "LIMIT ?")) {

            ps.setString(1, eventId);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int wins = rs.getInt("wins");
                    topWinners.put(uuid, wins);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao buscar top vencedores", e);
        }

        return topWinners;
    }

    public String getPlayerName(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM players WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao buscar nome do jogador", e);
        }

        return "Desconhecido";
    }

    public int getPlayerWins(UUID uuid, String eventId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) as wins FROM event_wins " +
                             "WHERE player_uuid = ? AND event_id = ?")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, eventId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("wins");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao buscar vitórias do jogador", e);
        }

        return 0;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}