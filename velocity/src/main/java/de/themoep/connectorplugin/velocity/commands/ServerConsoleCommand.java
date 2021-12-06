package de.themoep.connectorplugin.velocity.commands;

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

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class ServerConsoleCommand extends SubCommand {

    public ServerConsoleCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), "servercommand <servername|p:player> <command...>", parent.getPermission() + ".servercommand", "serverconsole", "serverconsolecommand", "server", "scc");
    }

    @Override
    public boolean run(CommandSource sender, String alias, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String serverName = args[0];
        if (serverName.startsWith("p:")) {
            Optional<Player> player = plugin.getProxy().getPlayer(serverName.substring(2));
            if (player.isPresent()) {
                if (player.get().getCurrentServer().isPresent()) {
                    serverName = player.get().getCurrentServer().get().getServerInfo().getName();
                } else {
                    sender.sendMessage(Component.text("Player '" + player.get().getUsername() + "' is not connected to any server?").color(NamedTextColor.RED));
                    return false;
                }
            } else {
                sender.sendMessage(Component.text("The player '" + serverName.substring(2) + "' is not online?").color(NamedTextColor.RED));
                return false;
            }
        } else
        if (!plugin.getProxy().getServer(serverName).isPresent()) {
            sender.sendMessage(Component.text("There is no server with the name of '" + serverName + "' on the proxy. Trying to send command anyways...").color(NamedTextColor.GRAY));
        }
        String commandString = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        sender.sendMessage(Component.text("Executing '" + commandString + "' on server '" + serverName + "'").color(NamedTextColor.GRAY));
        plugin.getBridge().runServerConsoleCommand(serverName, commandString, m -> sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(m)))
                .thenAccept(success -> sender.sendMessage(Component.text(success ? "Successfully executed command!" : "Error while executing the command.")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSource sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            return Collections.emptyList();
        }
        if (args.length == 0) {
            return plugin.getProxy().getAllServers().stream().map(s -> s.getServerInfo().getName()).collect(Collectors.toList());
        } else if (args.length == 1) {
            return plugin.getProxy().getAllServers().stream().map(s -> s.getServerInfo().getName()).filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
