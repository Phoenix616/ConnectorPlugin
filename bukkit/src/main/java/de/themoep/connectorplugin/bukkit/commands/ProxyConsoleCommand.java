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
import org.bukkit.command.PluginCommand;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ProxyConsoleCommand extends SubCommand {

    public ProxyConsoleCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), "proxycommand <command...>", parent.getPermission() + ".proxycommand", "proxyconsole", "proxyconsolecommand", "proxy", "pcc");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }

        String commandString = String.join(" ", args);
        sender.sendMessage(ChatColor.GRAY + "Executing '" + commandString + "' on proxy");
        plugin.getBridge().runProxyConsoleCommand(commandString, sender::sendMessage).thenAccept(success -> sender.sendMessage(success ? "Successfully executed command!" : "Error while executing the command."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length > 1) {
            PluginCommand pluginCommand = plugin.getServer().getPluginCommand(args[1]);
            if (pluginCommand != null) {
                return pluginCommand.tabComplete(sender, args[1], Arrays.copyOfRange(args, 2, args.length));
            }
        }
        return null;
    }
}
