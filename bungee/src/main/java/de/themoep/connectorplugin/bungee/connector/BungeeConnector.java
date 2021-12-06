package de.themoep.connectorplugin.bungee.connector;

/*
 * ConnectorPlugin
 * Copyright (C) 2020 Max Lee aka Phoenix616 (max@themoep.de)
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

import de.themoep.connectorplugin.bungee.BungeeConnectorPlugin;
import de.themoep.connectorplugin.connector.Connector;
import de.themoep.connectorplugin.connector.Message;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public abstract class BungeeConnector extends Connector<BungeeConnectorPlugin, ProxiedPlayer> {

    public BungeeConnector(BungeeConnectorPlugin plugin, boolean requiresPlayer) {
        super(plugin, requiresPlayer);
    }

    protected ProxiedPlayer getReceiver(String name) {
        return plugin.getProxy().getPlayer(name);
    }

    protected ServerInfo getTargetServer(String target) {
        if (target.startsWith(SERVER_PREFIX)) {
            return plugin.getProxy().getServerInfo(target.substring(SERVER_PREFIX.length()));
        } else {
            ProxiedPlayer player = getReceiver(target);
            if (player != null && player.getServer() != null) {
                return player.getServer().getInfo();
            }
        }
        return null;
    }

    @Override
    protected void sendDataImplementation(Object targetData, Message message) {
        sendDataImplementation(targetData instanceof ProxiedPlayer ? ((ProxiedPlayer) targetData).getName() : targetData instanceof String ? SERVER_PREFIX + targetData : "", message);
    }

    protected abstract void sendDataImplementation(String targetData, Message message);
}
