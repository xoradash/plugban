package com.database;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseHandler {
    private final Plugin plugin;
    private Connection connection;
    private String host, database, username, password;
    private int port;

    public DatabaseHandler(Plugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        createTables();
    }

    private void loadConfiguration() {
        // Создаем конфигурацию по умолчанию
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        // Загружаем настройки из конфигурации
        host = config.getString("database.host", "localhost");
        port = config.getInt("database.port", 3306);
        database = config.getString("database.name", "minecraft");
        username = config.getString("database.user", "root");
        password = config.getString("database.password", "");
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);
        }
        return connection;
    }

    private void createTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Создаем таблицу для хранения банов по нику (UUID)
            String playerBanTable = "CREATE TABLE IF NOT EXISTS player_bans (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_name VARCHAR(36) NOT NULL," +
                    "player_uuid VARCHAR(36)," +
                    "banned_by VARCHAR(36) NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "ban_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY player_name_unique (player_name)" +
                    ")";
            stmt.executeUpdate(playerBanTable);

            // Создаем таблицу для хранения IP-банов
            String ipBanTable = "CREATE TABLE IF NOT EXISTS ip_bans (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "ip VARCHAR(45) NOT NULL," +
                    "banned_by VARCHAR(36) NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "ban_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY ip_unique (ip)" +
                    ")";
            stmt.executeUpdate(ipBanTable);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при создании таблиц в базе данных", e);
        }
    }

    // Методы для работы с банами по имени игрока
    public void banPlayer(String playerName, UUID playerUUID, String bannedBy, String reason) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO player_bans (player_name, player_uuid, banned_by, reason) VALUES (?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE banned_by = ?, reason = ?, ban_time = CURRENT_TIMESTAMP")) {

            stmt.setString(1, playerName);
            stmt.setString(2, playerUUID != null ? playerUUID.toString() : null);
            stmt.setString(3, bannedBy);
            stmt.setString(4, reason);
            stmt.setString(5, bannedBy);
            stmt.setString(6, reason);

            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при добавлении бана игрока", e);
        }
    }

    public void unbanPlayer(String playerName) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM player_bans WHERE player_name = ?")) {

            stmt.setString(1, playerName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при удалении бана игрока", e);
        }
    }

    public boolean isPlayerBanned(String playerName) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM player_bans WHERE player_name = ?")) {

            stmt.setString(1, playerName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при проверке бана игрока", e);
            return false;
        }
    }

    public String getPlayerBanReason(String playerName) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT reason FROM player_bans WHERE player_name = ?")) {

            stmt.setString(1, playerName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("reason");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при получении причины бана игрока", e);
        }
        return "Неизвестно";
    }

    // Методы для работы с IP-банами
    public void banIP(String ip, String bannedBy, String reason) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO ip_bans (ip, banned_by, reason) VALUES (?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE banned_by = ?, reason = ?, ban_time = CURRENT_TIMESTAMP")) {

            stmt.setString(1, ip);
            stmt.setString(2, bannedBy);
            stmt.setString(3, reason);
            stmt.setString(4, bannedBy);
            stmt.setString(5, reason);

            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при добавлении IP-бана", e);
        }
    }

    public void unbanIP(String ip) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM ip_bans WHERE ip = ?")) {

            stmt.setString(1, ip);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при удалении IP-бана", e);
        }
    }

    public boolean isIPBanned(String ip) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM ip_bans WHERE ip = ?")) {

            stmt.setString(1, ip);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при проверке IP-бана", e);
            return false;
        }
    }

    public String getIPBanReason(String ip) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT reason FROM ip_bans WHERE ip = ?")) {

            stmt.setString(1, ip);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("reason");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при получении причины IP-бана", e);
        }
        return "Неизвестно";
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при закрытии соединения с базой данных", e);
        }
    }
}