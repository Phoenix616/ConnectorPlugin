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
import de.themoep.connectorplugin.velocity.VelocityConnectorPlugin;
import de.themoep.connectorplugin.connector.Message;
import de.themoep.connectorplugin.connector.MqttConnection;

public class MqttConnector extends VelocityConnector {
    private final MqttConnection connection;

    public MqttConnector(VelocityConnectorPlugin plugin) {
        super(plugin);
        connection = new MqttConnection(
                plugin,
                plugin.getConfig().getString("mqtt.broker-uri"),
                plugin.getConfig().getString("mqtt.client-id", null),
                plugin.getConfig().getString("mqtt.username"),
                plugin.getConfig().getString("mqtt.password"),
                plugin.getConfig().getInt("mqtt.keep-alive"),
                (receiver, message) -> handle(receiver.isEmpty() ? null : getReceiver(receiver), message)
        );
    }

    @Override
    protected void sendDataImplementation(Player player, Message message) {
        connection.sendMessage(player != null ? player.getUsername() : "", message);
    }

    @Override
    public void close() {
        connection.close();
    }
}
