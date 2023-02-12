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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ServerPlayerCommand extends SubCommand {

    public ServerPlayerCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), "serverplayercommand <playername> <command...>", parent.getPermission() + ".serverplayercommand", "serverplayer", "player", "spc");
    }

    @Override
    public boolean run(CommandSource sender, String alias, String[] args) {
        if (args.length < 2) {
            return false;
        }

        Player player = plugin.getProxy().getPlayer(args[0]).orElse(null);
        if (player == null) {
            sender.sendMessage(Component.text("No player with the name " + args[0] + " is online on this server!"));
            return true;
        }
        String commandString = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        sender.sendMessage(Component.text("Executing '" + commandString + "' on the server for player '" + player.getUsername() + "'").color(NamedTextColor.GRAY));
        plugin.getBridge().runServerPlayerCommand(player, commandString).thenAccept(success -> sender.sendMessage(Component.text(success ? "Successfully executed command!" : "Error while executing the command.")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSource sender, String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }
        if (args.length == 0) {
            return plugin.getProxy().getAllPlayers().stream().map(Player::getUsername).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        } else if (args.length == 1) {
            return plugin.getProxy().getAllPlayers().stream().map(Player::getUsername).filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
