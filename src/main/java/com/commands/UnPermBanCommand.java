package com.commands;

import com.database.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public class UnPermBanCommand implements CommandExecutor {
    private final DatabaseHandler databaseHandler;
    // Паттерн для проверки IP-адреса
    private final Pattern ipPattern = Pattern.compile("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    public UnPermBanCommand(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Использование: /unpermban [ник/IP]");
            return false;
        }

        String targetNameOrIP = args[0];
        String senderName = sender instanceof Player ? sender.getName() : "Консоль";

        // Проверяем, является ли аргумент IP-адресом
        if (ipPattern.matcher(targetNameOrIP).matches()) {
            // Проверяем, забанен ли IP
            if (!databaseHandler.isIPBanned(targetNameOrIP)) {
                sender.sendMessage(ChatColor.RED + "IP-адрес " + targetNameOrIP + " не заблокирован.");
                return true;
            }

            // Выполняем разбан по IP
            databaseHandler.unbanIP(targetNameOrIP);

            sender.sendMessage(ChatColor.GREEN + "IP-адрес " + targetNameOrIP + " был разблокирован.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "IP-адрес был разблокирован администратором " + senderName);
        } else {
            // Проверяем, забанен ли игрок
            if (!databaseHandler.isPlayerBanned(targetNameOrIP)) {
                sender.sendMessage(ChatColor.RED + "Игрок " + targetNameOrIP + " не заблокирован.");
                return true;
            }

            // Выполняем разбан по нику
            databaseHandler.unbanPlayer(targetNameOrIP);

            sender.sendMessage(ChatColor.GREEN + "Игрок " + targetNameOrIP + " был разблокирован.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "Игрок " + targetNameOrIP +
                    ChatColor.GREEN + " был разблокирован администратором " + senderName);
        }

        return true;
    }
}