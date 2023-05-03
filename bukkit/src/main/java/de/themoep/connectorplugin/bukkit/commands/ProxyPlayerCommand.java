package de.themoep.connectorplugin.bukkit.commands;

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

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ProxyPlayerCommand extends SubCommand {

    public ProxyPlayerCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), "proxyplayercommand <playername> <command...>", parent.getPermission() + ".proxyplayercommand", "proxyplayer", "player", "ppc");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            return false;
        }

        Player player = plugin.getServer().getPlayerExact(args[0]);
        if (player == null) {
            sender.sendMessage("No player with the name " + args[0] + " is online on this server!");
            return true;
        }
        String commandString = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        sender.sendMessage(ChatColor.GRAY + "Executing '" + commandString + "' on the proxy for player '" + player.getName() + "'");
        plugin.getBridge().runProxyPlayerCommand(player, commandString).thenAccept(success -> sender.sendMessage(success ? "Successfully executed command!" : "Error while executing the command."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return null;
    }
}
