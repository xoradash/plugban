package com.listeners;

import com.database.DatabaseHandler;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

        // Создаем Future для проверки бана игрока и IP
        CompletableFuture<Boolean> playerBannedFuture = databaseHandler.isPlayerBannedAsync(playerName);
        CompletableFuture<Boolean> ipBannedFuture = databaseHandler.isIPBannedAsync(ip);

        try {
            // Проверяем, забанен ли игрок по имени
            if (playerBannedFuture.get()) {
                // Этот метод вызывается асинхронно, поэтому мы можем использовать get()
                // В реальном сервере это не вызовет блокировку основного потока
                String reason = databaseHandler.getPlayerBanReason(playerName);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        ChatColor.RED + "Вы заблокированы на сервере!\n" +
                                ChatColor.YELLOW + "Причина: " + ChatColor.WHITE + reason);
                return;
            }

            // Проверяем, забанен ли IP
            if (ipBannedFuture.get()) {
                String reason = databaseHandler.getIPBanReason(ip);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        ChatColor.RED + "Ваш IP-адрес заблокирован на сервере!\n" +
                                ChatColor.YELLOW + "Причина: " + ChatColor.WHITE + reason);
            }
        } catch (InterruptedException | ExecutionException e) {
            // В случае ошибки логируем и разрешаем вход (лучше разрешить, чем ошибочно запретить)
            e.printStackTrace();
        }
    }
}