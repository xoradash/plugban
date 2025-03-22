package com;

import com.commands.PermBanCommand;
import com.commands.UnPermBanCommand;
import com.database.DatabaseHandler;
import com.listeners.PlayerListener;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class plugban extends JavaPlugin {
    private DatabaseHandler databaseHandler;

    @Override
    public void onEnable() {
        // Сохраняем конфигурацию по умолчанию
        saveDefaultConfig();

        // Инициализируем обработчик базы данных
        try {
            databaseHandler = new DatabaseHandler(this);
            getLogger().info("База данных успешно подключена!");
        } catch (Exception e) {
            getLogger().severe("Не удалось подключиться к базе данных: " + e.getMessage());
            getLogger().severe("Плагин будет отключен!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Регистрируем слушателя событий
        getServer().getPluginManager().registerEvents(new PlayerListener(databaseHandler), this);

        // Регистрируем команды
        getCommand("permban").setExecutor(new PermBanCommand(databaseHandler));
        getCommand("unpermban").setExecutor(new UnPermBanCommand(databaseHandler));

        getLogger().info(ChatColor.GREEN + "Плагин PlugBan успешно запущен!");
    }

    @Override
    public void onDisable() {
        // Закрываем соединение с базой данных
        if (databaseHandler != null) {
            databaseHandler.closeConnection();
            getLogger().info("Соединение с базой данных закрыто.");
        }

        getLogger().info(ChatColor.RED + "Плагин PlugBan отключен!");
    }
}