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


import de.themoep.connectorplugin.velocity.VelocityConnectorPlugin;

public class ConnectorCommand extends SubCommand {

    public ConnectorCommand(VelocityConnectorPlugin plugin) {
        super(plugin, "connectorpluginvelocity", "connectorplugin.command", "connectorvelocity", "connectorcommandvelocity", "connpluginvelocity", "cpv");
        registerSubCommand(new TeleportCommand(this));
        registerSubCommand(new TeleportToPlayerCommand(this));
        registerSubCommand(new ServerConsoleCommand(this));
        registerSubCommand(new ServerPlayerCommand(this));
        registerSubCommand(new ProxyConsoleCommand(this));
    }
}
