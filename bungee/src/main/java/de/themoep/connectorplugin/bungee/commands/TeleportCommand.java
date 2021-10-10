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

import de.themoep.connectorplugin.LocationInfo;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;

public class TeleportCommand extends SubCommand {
    public TeleportCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), "teleport <player> <server> [<world> <x> <y> <z> [<yaw> <pitch>]]", parent.getPermission() + ".teleport", "tp", "send");
    }

    @Override
    public boolean run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        ProxiedPlayer player = plugin.getProxy().getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "No player with the name " + args[0] + " found!");
            return true;
        }

        ServerInfo server = plugin.getProxy().getServerInfo(args[1]);
        if (server == null) {
            sender.sendMessage(ChatColor.RED + "No server with the name " + args[1] + " found!");
            return true;
        }

        if (args.length == 2) {
            player.connect(server, (success, ex) -> {
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "Connected player " + player.getName() + " to server " + server.getName());
                } else {
                    sender.sendMessage(ChatColor.RED + "Error while connecting player " + player.getName() + " to server " + server.getName() + ": " + ex.getMessage());
                }
            });
            return true;
        }

        if (args.length < 6) {
            return false;
        }

        try {
            LocationInfo location = new LocationInfo(
                    server.getName(),
                    args[2],
                    Double.parseDouble(args[3]),
                    Double.parseDouble(args[4]),
                    Double.parseDouble(args[5]),
                    args.length > 6 ? Float.parseFloat(args[6]) : 0,
                    args.length > 7 ? Float.parseFloat(args[7]) : 0
            );

            plugin.getBridge().teleport(player.getName(), location, sender::sendMessage)
                    .thenAccept(success -> {
                        if (!success) {
                            sender.sendMessage(ChatColor.RED + "Error while teleporting...");
                        }
            });
            return true;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Error while parsing input! " + e.getMessage());
            return false;
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            return Collections.emptySet();
        }
        if (args.length == 0) {
            return plugin.getProxy().getPlayers().stream().map(ProxiedPlayer::getName).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        } else if (args.length == 1) {
            return plugin.getProxy().getPlayers().stream().map(ProxiedPlayer::getName).filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        } else if (args.length == 2) {
            return plugin.getProxy().getServers().values().stream().filter(s -> s.canAccess(sender)).map(ServerInfo::getName).filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        }
        return Collections.emptySet();
    }
}
