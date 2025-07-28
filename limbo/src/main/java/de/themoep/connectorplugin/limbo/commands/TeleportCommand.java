package de.themoep.connectorplugin.limbo.commands;

/*
 * ConnectorPlugin
 * Copyright (C) 2025 Max Lee aka Phoenix616 (max@themoep.de)
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

import com.loohp.limbo.Limbo;
import com.loohp.limbo.commands.CommandSender;
import com.loohp.limbo.player.Player;
import de.themoep.connectorplugin.LocationInfo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TeleportCommand extends SubCommand {

    public TeleportCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), parent, "teleport <player> <server> [<world> <x> <y> <z> [<yaw> <pitch>]]", parent.getPermission() + ".teleport", "tp", "send");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + getFullUsage());
            return;
        }

        String playerName = args[0];
        String serverName = args[1];

        if (args.length == 2) {
            plugin.getBridge().sendToServer(playerName, serverName, sender::sendMessage)
                    .thenAccept(success -> {
                        if (success) {
                            sender.sendMessage("Successfully sent " + playerName + " to " + serverName);
                        } else {
                            sender.sendMessage("Error while sending " + playerName + " to " + serverName);
                        }
                    });
            return;
        }

        if (args.length == 3) {
            plugin.getBridge().teleport(playerName, serverName, args[2], sender::sendMessage)
                    .thenAccept(success -> {
                        if (success) {
                            sender.sendMessage("Successfully teleported " + playerName + " to " + args[2] + " on " + serverName);
                        } else {
                            sender.sendMessage("Error while teleporting " + playerName + " to " + args[2] + " on " + serverName);
                        }
                    });
            return;
        }

        if (args.length < 6) {
            sender.sendMessage("Usage: /" + getFullUsage());
            return;
        }

        try {
            LocationInfo location = new LocationInfo(
                    serverName,
                    args[2],
                    Double.parseDouble(args[3]),
                    Double.parseDouble(args[4]),
                    Double.parseDouble(args[5]),
                    args.length > 6 ? Float.parseFloat(args[6]) : 0,
                    args.length > 7 ? Float.parseFloat(args[7]) : 0
            );
            plugin.getBridge().teleport(playerName, location, sender::sendMessage)
                    .thenAccept(success -> {
                        if (success) {
                            sender.sendMessage("Successfully teleported " + playerName + " to " + location.getServer() + " at " + location.getWorld() + " " + location.getX() + " " + location.getY() + " " + location.getZ());
                        } else {
                            sender.sendMessage("Error while teleporting " + playerName);
                        }
                    });
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Invalid number format for coordinates/rotation!");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Limbo.getInstance().getPlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
