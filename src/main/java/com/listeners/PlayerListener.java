package com.listeners;

import com.database.DatabaseHandler;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.net.InetAddress;

public class PlayerListener implements Listener {
    private final DatabaseHandler databaseHandler;

    public PlayerListener(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerName = event.getName();
        InetAddress address = event.getAddress();
        String ip = address.getHostAddress();

        // Проверяем, забанен ли игрок по имени
        if (databaseHandler.isPlayerBanned(playerName)) {
            String reason = databaseHandler.getPlayerBanReason(playerName);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    ChatColor.RED + "Вы заблокированы на сервере!\n" +
                            ChatColor.YELLOW + "Причина: " + ChatColor.WHITE + reason);
            return;
        }

        // Проверяем, забанен ли IP
        if (databaseHandler.isIPBanned(ip)) {
            String reason = databaseHandler.getIPBanReason(ip);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    ChatColor.RED + "Ваш IP-адрес заблокирован на сервере!\n" +
                            ChatColor.YELLOW + "Причина: " + ChatColor.WHITE + reason);
        }
    }
}