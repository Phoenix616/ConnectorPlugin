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
import de.themoep.connectorplugin.connector.RedisConnection;
import de.themoep.connectorplugin.limbo.LimboConnectorPlugin;

public class RedisConnector extends LimboConnector {

    private final RedisConnection connection;

    public RedisConnector(LimboConnectorPlugin plugin) {
        super(plugin, false);
        connection = new RedisConnection(
                plugin,
                plugin.getPluginConfig().get("redis.uri", String.class),
                plugin.getPluginConfig().get("redis.host", String.class),
                plugin.getPluginConfig().get("redis.port", Integer.class),
                plugin.getPluginConfig().get("redis.db", Integer.class),
                plugin.getPluginConfig().get("redis.password", String.class),
                plugin.getPluginConfig().get("redis.timeout", Integer.class),
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
