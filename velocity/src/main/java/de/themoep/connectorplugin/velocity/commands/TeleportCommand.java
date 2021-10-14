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
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.themoep.connectorplugin.LocationInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TeleportCommand extends SubCommand {
    public TeleportCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), "teleport <player> <server> [<world> <x> <y> <z> [<yaw> <pitch>]]", parent.getPermission() + ".teleport", "tp", "send");
    }

    @Override
    public boolean run(CommandSource sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        Player player = plugin.getProxy().getPlayer(args[0]).orElse(null);
        if (player == null) {
            sender.sendMessage(Component.text("No player with the name " + args[0] + " found!").color(NamedTextColor.RED));
            return true;
        }

        RegisteredServer server = plugin.getProxy().getServer(args[1]).orElse(null);
        if (server == null) {
            sender.sendMessage(Component.text("No server with the name " + args[1] + " found!").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 2) {
            player.createConnectionRequest(server).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    sender.sendMessage(Component.text("Connected player " + player.getUsername() + " to server " + server.getServerInfo().getName()).color(NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Error while connecting player " + player.getUsername() + " to server " + server.getServerInfo().getName() + ": " + result.getReasonComponent().orElse(Component.empty())).color(NamedTextColor.RED));
                }
            });
            return true;
        }

        if (args.length < 6) {
            return false;
        }

        try {
            LocationInfo location = new LocationInfo(
                    server.getServerInfo().getName(),
                    args[2],
                    Double.parseDouble(args[3]),
                    Double.parseDouble(args[4]),
                    Double.parseDouble(args[5]),
                    args.length > 6 ? Float.parseFloat(args[6]) : 0,
                    args.length > 7 ? Float.parseFloat(args[7]) : 0
            );

            plugin.getBridge().teleport(player.getUsername(), location, m -> sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(m)))
                    .thenAccept(success -> {
                        if (!success) {
                            sender.sendMessage(Component.text("Error while teleporting...").color(NamedTextColor.RED));
                        }
            });
            return true;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Error while parsing input! " + e.getMessage()).color(NamedTextColor.RED));
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSource sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            return Collections.emptyList();
        }
        if (args.length == 0) {
            return plugin.getProxy().getAllPlayers().stream().map(Player::getUsername).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        } else if (args.length == 1) {
            return plugin.getProxy().getAllPlayers().stream().map(Player::getUsername).filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        } else if (args.length == 2) {
            return plugin.getProxy().getAllServers().stream().map(s -> s.getServerInfo().getName()).filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
