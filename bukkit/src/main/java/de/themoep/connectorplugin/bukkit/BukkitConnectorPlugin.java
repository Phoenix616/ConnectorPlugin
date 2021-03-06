package de.themoep.connectorplugin.bukkit;

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

import de.themoep.connectorplugin.ConnectorPlugin;
import de.themoep.connectorplugin.bukkit.connector.BukkitConnector;
import de.themoep.connectorplugin.bukkit.connector.PluginMessageConnector;
import de.themoep.connectorplugin.connector.MessageTarget;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitConnectorPlugin extends JavaPlugin implements ConnectorPlugin {

    private BukkitConnector connector;

    @Override
    public void onEnable() {
        connector = new PluginMessageConnector(this);
    }

    @Override
    public BukkitConnector getConnector() {
        return connector;
    }

    @Override
    public MessageTarget.Source getSourceType() {
        return MessageTarget.Source.SERVER;
    }

    @Override
    public void logError(String message) {
        getLogger().severe(message);
    }
}
