package com.database;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DatabaseHandler {
    private final Plugin plugin;
    private Connection connection;
    private String host, database, username, password;
    private int port;

    public Plugin getPlugin() {
        return plugin;
    }

    public DatabaseHandler(Plugin plugin) {
        this.plugin = plugin;
        loadConfiguration();

        // Асинхронно создаем таблицы при инициализации
        runAsync(this::createTables);
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
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&useUnicode=yes&characterEncoding=UTF-8";
            connection = DriverManager.getConnection(url, username, password);
        }
        return connection;
    }

    // Метод для выполнения асинхронных задач
    private BukkitTask runAsync(Runnable task) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    // Асинхронное получение boolean значений с колбэком
    private void runAsyncBooleanCheck(String sql, String param, Consumer<Boolean> callback) {
        runAsync(() -> {
            boolean result = false;
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, param);
                try (ResultSet rs = stmt.executeQuery()) {
                    result = rs.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при выполнении SQL запроса", e);
            }

            // Вызываем колбэк в основном потоке
            final boolean finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        });
    }

    // Асинхронное получение строк с колбэком
    private void runAsyncStringQuery(String sql, String param, Consumer<String> callback) {
        runAsync(() -> {
            String result = "Неизвестно";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, param);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        result = rs.getString("reason");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при выполнении SQL запроса", e);
            }

            // Вызываем колбэк в основном потоке
            final String finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        });
    }

    // Метод для асинхронной проверки с использованием CompletableFuture
    private CompletableFuture<Boolean> checkAsync(String sql, String param) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, param);
                try (ResultSet rs = stmt.executeQuery()) {
                    boolean result = rs.next();
                    future.complete(result);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при выполнении проверки", e);
                future.complete(false); // В случае ошибки возвращаем false
            }
        });

        return future;
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
                    ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            stmt.executeUpdate(playerBanTable);

            // Создаем таблицу для хранения IP-банов
            String ipBanTable = "CREATE TABLE IF NOT EXISTS ip_bans (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "ip VARCHAR(45) NOT NULL," +
                    "banned_by VARCHAR(36) NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "ban_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY ip_unique (ip)" +
                    ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            stmt.executeUpdate(ipBanTable);

            plugin.getLogger().info("Таблицы базы данных успешно созданы/проверены");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при создании таблиц в базе данных", e);
        }
    }

    // Методы для работы с банами по имени игрока
    public void banPlayer(String playerName, UUID playerUUID, String bannedBy, String reason) {
        runAsync(() -> {
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
                plugin.getLogger().info("Игрок " + playerName + " заблокирован (асинхронно)");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при добавлении бана игрока", e);
            }
        });
    }

    public void unbanPlayer(String playerName) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM player_bans WHERE player_name = ?")) {

                stmt.setString(1, playerName);
                stmt.executeUpdate();
                plugin.getLogger().info("Игрок " + playerName + " разблокирован (асинхронно)");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при удалении бана игрока", e);
            }
        });
    }

    // Проверка, забанен ли игрок (синхронный метод, который возвращает CompletableFuture)
    public CompletableFuture<Boolean> isPlayerBannedAsync(String playerName) {
        return checkAsync("SELECT * FROM player_bans WHERE player_name = ?", playerName);
    }

    // Старый синхронный метод, теперь использует колбэк для асинхронной работы
    public void isPlayerBanned(String playerName, Consumer<Boolean> callback) {
        runAsyncBooleanCheck("SELECT * FROM player_bans WHERE player_name = ?", playerName, callback);
    }

    // Синхронная версия для совместимости со старым кодом (не рекомендуется использовать)
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

    // Получение причины бана (асинхронно с колбэком)
    public void getPlayerBanReason(String playerName, Consumer<String> callback) {
        runAsyncStringQuery("SELECT reason FROM player_bans WHERE player_name = ?", playerName, callback);
    }

    // Синхронная версия для совместимости
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
        runAsync(() -> {
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
                plugin.getLogger().info("IP " + ip + " заблокирован (асинхронно)");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при добавлении IP-бана", e);
            }
        });
    }

    public void unbanIP(String ip) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM ip_bans WHERE ip = ?")) {

                stmt.setString(1, ip);
                stmt.executeUpdate();
                plugin.getLogger().info("IP " + ip + " разблокирован (асинхронно)");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при удалении IP-бана", e);
            }
        });
    }

    // Проверка, забанен ли IP (с CompletableFuture)
    public CompletableFuture<Boolean> isIPBannedAsync(String ip) {
        return checkAsync("SELECT * FROM ip_bans WHERE ip = ?", ip);
    }

    // Проверка с колбэком
    public void isIPBanned(String ip, Consumer<Boolean> callback) {
        runAsyncBooleanCheck("SELECT * FROM ip_bans WHERE ip = ?", ip, callback);
    }

    // Синхронная версия для совместимости
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

    // Получение причины IP-бана (асинхронно с колбэком)
    public void getIPBanReason(String ip, Consumer<String> callback) {
        runAsyncStringQuery("SELECT reason FROM ip_bans WHERE ip = ?", ip, callback);
    }

    // Синхронная версия для совместимости
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