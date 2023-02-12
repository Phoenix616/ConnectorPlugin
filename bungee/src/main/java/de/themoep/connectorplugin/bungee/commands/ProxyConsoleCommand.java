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

import de.themoep.bungeeplugin.PluginCommand;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ProxyConsoleCommand extends SubCommand {

    public ProxyConsoleCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), "proxycommand <command...>", parent.getPermission() + ".proxycommand", "proxyconsole", "proxyconsolecommand", "proxy", "pcc");
    }

    @Override
    public boolean run(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return false;
        }

        String commandString = String.join(" ", args);
        sender.sendMessage(ChatColor.GRAY + "Executing '" + commandString + "' on other proxies");
        plugin.getBridge().runProxyConsoleCommand(commandString, sender::sendMessage).thenAccept(success -> sender.sendMessage(success ? "Successfully executed command!" : "Error while executing the command."));
        return true;
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return plugin.getProxy().getPluginManager().getCommands().stream()
                    .map(Map.Entry::getValue)
                    .filter(e -> e instanceof PluginCommand ? ((PluginCommand<?>) e).hasCommandPermission(sender) : e.hasPermission(sender))
                    .map(Command::getName)
                    .sorted(String::compareToIgnoreCase)
                    .collect(Collectors.toList());
        } else if (args.length == 1) {
            return plugin.getProxy().getPluginManager().getCommands().stream()
                    .map(Map.Entry::getValue)
                    .filter(e -> e instanceof PluginCommand ? ((PluginCommand<?>) e).hasCommandPermission(sender) : e.hasPermission(sender))
                    .map(Command::getName)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .sorted(String::compareToIgnoreCase)
                    .collect(Collectors.toList());
        }
        return Collections.emptySet();
    }
}
