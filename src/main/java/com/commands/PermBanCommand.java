package com.commands;

import com.database.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.regex.Pattern;

public class PermBanCommand implements CommandExecutor {
    private final DatabaseHandler databaseHandler;
    // Паттерн для проверки IP-адреса
    private final Pattern ipPattern = Pattern.compile("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    public PermBanCommand(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /permban [ник/IP] [причина]");
            return false;
        }

        String targetNameOrIP = args[0];

        // Собираем причину из всех оставшихся аргументов
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            reasonBuilder.append(args[i]).append(" ");
        }
        String reason = reasonBuilder.toString().trim();

        String senderName = sender instanceof Player ? sender.getName() : "Консоль";

        // Проверяем, является ли аргумент IP-адресом
        if (ipPattern.matcher(targetNameOrIP).matches()) {
            // Выполняем бан по IP (асинхронно)
            databaseHandler.banIP(targetNameOrIP, senderName, reason);

            // Кикаем всех игроков с этим IP
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getAddress().getAddress().getHostAddress().equals(targetNameOrIP)) {
                    player.kickPlayer(ChatColor.RED + "Ваш IP-адрес был заблокирован на сервере!\n" +
                            ChatColor.YELLOW + "Причина: " + ChatColor.WHITE + reason);
                }
            }

            sender.sendMessage(ChatColor.GREEN + "IP-адрес " + targetNameOrIP + " был заблокирован.");
            Bukkit.broadcastMessage(ChatColor.RED + "IP-адрес был заблокирован администратором " + senderName +
                    ChatColor.RED + " по причине: " + ChatColor.WHITE + reason);
        } else {
            // Выполняем бан по нику
            Player targetPlayer = Bukkit.getPlayerExact(targetNameOrIP);
            UUID targetUUID = null;

            if (targetPlayer != null) {
                targetUUID = targetPlayer.getUniqueId();
            }

            // Асинхронный бан
            databaseHandler.banPlayer(targetNameOrIP, targetUUID, senderName, reason);

            if (targetPlayer != null) {
                targetPlayer.kickPlayer(ChatColor.RED + "Вы были заблокированы на сервере!\n" +
                        ChatColor.YELLOW + "Причина: " + ChatColor.WHITE + reason);
            }

            sender.sendMessage(ChatColor.GREEN + "Игрок " + targetNameOrIP + " был заблокирован.");
            Bukkit.broadcastMessage(ChatColor.RED + "Игрок " + targetNameOrIP +
                    ChatColor.RED + " был заблокирован администратором " + senderName +
                    ChatColor.RED + " по причине: " + ChatColor.WHITE + reason);
        }

        return true;
    }
}