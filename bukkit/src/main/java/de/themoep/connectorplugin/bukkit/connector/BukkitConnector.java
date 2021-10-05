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

    public BukkitConnector(BukkitConnectorPlugin plugin) {
        super(plugin);
    }

    protected Player getReceiver(String name) {
        return plugin.getServer().getPlayer(name);
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

}
