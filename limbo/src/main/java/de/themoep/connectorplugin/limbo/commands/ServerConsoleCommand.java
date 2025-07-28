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

import com.loohp.limbo.commands.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ServerConsoleCommand extends SubCommand {

    public ServerConsoleCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), parent, "servercommand <servername|p:player> <command...>", parent.getPermission() + ".servercommand", "serverconsole", "serverconsolecommand", "server", "scc");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + getFullUsage());
            return;
        }

        String serverName = args[0];
        String commandString = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        sender.sendMessage("Executing '" + commandString + "' on server '" + serverName + "'");
        plugin.getBridge().runServerConsoleCommand(serverName, commandString, sender::sendMessage).thenAccept(success -> sender.sendMessage(success ? "Successfully executed command!" : "Error while executing the command."));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // TODO: Add tab completion for servers
        return Collections.emptyList();
    }
}
