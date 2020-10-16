package de.themoep.connectorplugin.bungee;

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
import de.themoep.connectorplugin.bungee.connector.BungeeConnector;
import de.themoep.connectorplugin.bungee.connector.PluginMessageConnector;
import net.md_5.bungee.api.plugin.Plugin;

public final class BungeeConnectorPlugin extends Plugin implements ConnectorPlugin {

    private BungeeConnector connector;

    @Override
    public void onEnable() {
        connector = new PluginMessageConnector(this);
    }

    @Override
    public BungeeConnector getConnector() {
        return connector;
    }

    @Override
    public void logError(String message) {
        getLogger().severe(message);
    }
}
