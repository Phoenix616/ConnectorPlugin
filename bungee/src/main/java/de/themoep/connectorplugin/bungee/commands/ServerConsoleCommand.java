package de.themoep.connectorplugin.bungee.commands;

/*
 * ConnectorPlugin
 * Copyright (C) 2021 Max Lee aka Phoenix616 (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;

public class ServerConsoleCommand extends SubCommand {

    public ServerConsoleCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), "servercommand <servername|p:player> <command...>", parent.getPermission() + ".servercommand", "serverconsole", "serverconsolecommand", "server", "scc");
    }

    @Override
    public boolean run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String serverName = args[0];
        if (serverName.startsWith("p:")) {
            ProxiedPlayer player = plugin.getProxy().getPlayer(serverName.substring(2));
            if (player != null) {
                if (player.getServer() != null) {
                    serverName = player.getServer().getInfo().getName();
                } else {
                    sender.sendMessage(ChatColor.RED + "Player '" + player.getName() + "' is not connected to any server?");
                    return false;
                }
            } else {
                sender.sendMessage(ChatColor.RED + "The player '" + serverName.substring(2) + "' is not online?");
                return false;
            }
        } else if (plugin.getProxy().getServerInfo(serverName) == null) {
            sender.sendMessage(ChatColor.GRAY + "There is no server with the name of '" + serverName + "' on the proxy. Trying to send command anyways...");
        }
        String commandString = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        sender.sendMessage(ChatColor.GRAY + "Executing '" + commandString + "' on server '" + serverName + "'");
        plugin.getBridge().runServerConsoleCommand(serverName, commandString, sender::sendMessage).thenAccept(success -> sender.sendMessage(success ? "Successfully executed command!" : "Error while executing the command."));
        return true;
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            return Collections.emptySet();
        }
        if (args.length == 0) {
            return plugin.getProxy().getServers().keySet();
        } else if (args.length == 1) {
            return plugin.getProxy().getServers().keySet().stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        }
        return Collections.emptySet();
    }
}
