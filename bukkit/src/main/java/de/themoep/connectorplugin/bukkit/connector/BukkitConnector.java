package de.themoep.connectorplugin.bukkit.connector;

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

import de.themoep.connectorplugin.bukkit.BukkitConnectorPlugin;
import de.themoep.connectorplugin.connector.Connector;
import de.themoep.connectorplugin.connector.Message;
import org.bukkit.entity.Player;

public abstract class BukkitConnector extends Connector<BukkitConnectorPlugin, Player> {

    public BukkitConnector(BukkitConnectorPlugin plugin, boolean requiresPlayer) {
        super(plugin, requiresPlayer);
    }

    protected Player getReceiverImplementation(String name) {
        Player player = plugin.getServer().getPlayer(name);
        if (player != null && player.isOnline()) {
            return player;
        }
        return null;
    }

    @Override
    protected void handle(Player receiver, Message message) {
        switch (message.getTarget()) {
            case OTHERS_WITH_PLAYERS:
            case ALL_WITH_PLAYERS:
                if (plugin.getServer().getOnlinePlayers().isEmpty()) {
                    return;
                }
        }
        super.handle(receiver, message);
    }

    @Override
    protected void sendDataImplementation(Object targetData, Message message) {
        sendDataImplementation(targetData instanceof String
                ? (hasPrefix((String) targetData)
                        ? (String) targetData
                        : SERVER_PREFIX + targetData)
                : (targetData instanceof Player
                        ? PLAYER_PREFIX + ((Player) targetData).getName()
                        : ""), message);
    }

    protected abstract void sendDataImplementation(String targetData, Message message);

}
