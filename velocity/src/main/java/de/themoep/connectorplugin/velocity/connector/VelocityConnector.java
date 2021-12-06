package de.themoep.connectorplugin.velocity.connector;

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

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.themoep.connectorplugin.connector.Message;
import de.themoep.connectorplugin.velocity.VelocityConnectorPlugin;
import de.themoep.connectorplugin.connector.Connector;

public abstract class VelocityConnector extends Connector<VelocityConnectorPlugin, Player> {

    public VelocityConnector(VelocityConnectorPlugin plugin, boolean requiresPlayer) {
        super(plugin, requiresPlayer);
    }

    protected Player getReceiver(String name) {
        return plugin.getProxy().getPlayer(name).orElse(null);
    }

    protected RegisteredServer getTargetServer(String target) {
        if (target.startsWith(SERVER_PREFIX)) {
            return plugin.getProxy().getServer(target.substring(SERVER_PREFIX.length())).orElse(null);
        } else {
            Player player = getReceiver(target);
            if (player != null && player.getCurrentServer().isPresent()) {
                return player.getCurrentServer().get().getServer();
            }
        }
        return null;
    }

    @Override
    protected void sendDataImplementation(Object targetData, Message message) {
        sendDataImplementation(targetData instanceof Player ? ((Player) targetData).getUsername() : targetData instanceof String ? SERVER_PREFIX + targetData : "", message);
    }

    protected abstract void sendDataImplementation(String targetData, Message message);
}
