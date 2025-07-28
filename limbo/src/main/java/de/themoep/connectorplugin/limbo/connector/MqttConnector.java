package de.themoep.connectorplugin.limbo.connector;

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

import de.themoep.connectorplugin.connector.Message;
import de.themoep.connectorplugin.connector.MqttConnection;
import de.themoep.connectorplugin.limbo.LimboConnectorPlugin;

public class MqttConnector extends LimboConnector {

    private final MqttConnection connection;

    public MqttConnector(LimboConnectorPlugin plugin) {
        super(plugin, false);
        connection = new MqttConnection(
                plugin,
                plugin.getPluginConfig().get("mqtt.broker-uri", String.class),
                plugin.getPluginConfig().get("mqtt.client-id", String.class),
                plugin.getPluginConfig().get("mqtt.username", String.class),
                plugin.getPluginConfig().get("mqtt.password", String.class),
                plugin.getPluginConfig().get("mqtt.keep-alive", Integer.class),
                (receiver, message) -> plugin.runAsync(() -> handle(receiver, message))
        );
    }

    @Override
    protected void sendDataImplementation(String targetData, Message message) {
        connection.sendMessage(targetData, message);
    }

    @Override
    public void close() {
        connection.close();
    }
}
