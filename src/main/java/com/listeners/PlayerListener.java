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

        try {
            // Проверяем, забанен ли игрок по имени
            CompletableFuture<Boolean> playerBannedFuture = databaseHandler.isPlayerBannedAsync(playerName);
            if (playerBannedFuture.get()) {
                // Получаем причину бана асинхронно
                CompletableFuture<String> reasonFuture = databaseHandler.getPlayerBanReasonAsync(playerName);
                String reason = reasonFuture.get(); // Безопасно, т.к. мы находимся в асинхронном обработчике

                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        ChatColor.RED + "Вы заблокированы на сервере!\n" +
                                ChatColor.YELLOW + "Причина: " + ChatColor.WHITE + reason);
                return;
            }

            // Проверяем, забанен ли IP
            CompletableFuture<Boolean> ipBannedFuture = databaseHandler.isIPBannedAsync(ip);
            if (ipBannedFuture.get()) {
                // Получаем причину IP-бана асинхронно
                CompletableFuture<String> reasonFuture = databaseHandler.getIPBanReasonAsync(ip);
                String reason = reasonFuture.get(); // Безопасно, т.к. мы находимся в асинхронном обработчике

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