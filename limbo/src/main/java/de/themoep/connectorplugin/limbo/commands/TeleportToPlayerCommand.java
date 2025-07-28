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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TeleportToPlayerCommand extends SubCommand {

    public TeleportToPlayerCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), parent, "teleporttoplayer <player> [<target>]", parent.getPermission() + ".teleporttoplayer", "teleportplayer");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String playerName;
        String targetName;
        if (args.length == 1 && sender instanceof Player) {
            playerName = sender.getName();
            targetName = args[0];
        } else if (args.length == 2) {
            playerName = args[0];
            targetName = args[1];
        } else {
            sender.sendMessage("Usage: /" + getFullUsage());
            return;
        }

        plugin.getBridge().teleport(playerName, targetName, sender::sendMessage).thenAccept(success -> {
            if (success) {
                sender.sendMessage("Successfully teleported " + playerName + " to " + targetName);
            } else {
                sender.sendMessage("Error while teleporting " + playerName + " to " + targetName);
            }
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 || args.length == 2) {
            return Limbo.getInstance().getPlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
