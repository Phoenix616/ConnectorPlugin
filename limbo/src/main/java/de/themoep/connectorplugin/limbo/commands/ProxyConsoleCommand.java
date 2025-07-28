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

import java.util.Collections;
import java.util.List;

public class ProxyConsoleCommand extends SubCommand {

    public ProxyConsoleCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), parent, "proxycommand <command...>", parent.getPermission() + ".proxycommand", "proxyconsole", "proxyconsolecommand", "proxy", "pcc");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Usage: /" + getFullUsage());
            return;
        }

        String commandString = String.join(" ", args);
        sender.sendMessage("Executing '" + commandString + "' on other proxies");
        plugin.getBridge().runProxyConsoleCommand(commandString, sender::sendMessage).thenAccept(success -> sender.sendMessage(success ? "Successfully executed command!" : "Error while executing the command."));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
