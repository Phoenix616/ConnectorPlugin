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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProxyPlayerCommand extends SubCommand {

    public ProxyPlayerCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), parent, "proxyplayercommand <playername> <command...>", parent.getPermission() + ".proxyplayercommand", "proxyplayer", "player", "ppc");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + getFullUsage());
            return;
        }

        Player player = Limbo.getInstance().getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage("No player with the name " + args[0] + " is online on this server!");
            return;
        }
        String commandString = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        sender.sendMessage("Executing '" + commandString + "' on the proxy for player '" + player.getName() + "'");
        plugin.getBridge().runProxyPlayerCommand(player, commandString).thenAccept(success -> sender.sendMessage(success ? "Successfully executed command!" : "Error while executing the command."));
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
