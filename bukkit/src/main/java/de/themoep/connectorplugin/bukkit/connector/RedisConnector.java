package de.themoep.connectorplugin.bukkit.connector;

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

import de.themoep.connectorplugin.bukkit.BukkitConnectorPlugin;
import de.themoep.connectorplugin.connector.Message;
import de.themoep.connectorplugin.connector.RedisConnection;
import org.bukkit.entity.Player;

public class RedisConnector extends BukkitConnector {
    private final RedisConnection connection;

    public RedisConnector(BukkitConnectorPlugin plugin) {
        super(plugin);
        connection = new RedisConnection(
                plugin,
                plugin.getConfig().getString("redis.uri"),
                plugin.getConfig().getString("redis.host"),
                plugin.getConfig().getInt("redis.port"),
                plugin.getConfig().getString("redis.password"),
                plugin.getConfig().getLong("redis.timeout"),
                (receiver, message) -> {
                    switch (message.getTarget()) {
                        case OTHERS_WITH_PLAYERS:
                        case ALL_WITH_PLAYERS:
                            if (plugin.getServer().getOnlinePlayers().isEmpty()) {
                                return;
                            }
                        case PROXY:
                            return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            handle(receiver.isEmpty() ? null : getReceiver(receiver), message));
                }
        );
    }

    @Override
    protected void sendDataImplementation(Player player, Message message) {
        connection.sendMessage(player != null ? player.getName() : "", message);
    }

    @Override
    public void close() {
        connection.close();
    }
}
