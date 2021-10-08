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

import de.themoep.bungeeplugin.BungeePlugin;
import de.themoep.connectorplugin.ConnectorPlugin;
import de.themoep.connectorplugin.bungee.commands.ConnectorCommand;
import de.themoep.connectorplugin.bungee.connector.BungeeConnector;
import de.themoep.connectorplugin.bungee.connector.PluginMessageConnector;
import de.themoep.connectorplugin.bungee.connector.RedisConnector;
import de.themoep.connectorplugin.connector.MessageTarget;

import java.util.Locale;
import java.util.logging.Level;

public final class BungeeConnectorPlugin extends BungeePlugin implements ConnectorPlugin {

    private BungeeConnector connector;
    private Bridge bridge;
    private boolean debug = true;

    @Override
    public void onEnable() {
        connector = new PluginMessageConnector(this);

        getProxy().getPluginManager().registerCommand(this, new ConnectorCommand(this));

        debug = getConfig().getBoolean("debug");

        String messengerType = getConfig().getString("messenger-type", "plugin_messages").toLowerCase(Locale.ROOT);
        switch (messengerType) {
            default:
                getLogger().log(Level.WARNING, "Messenger type '" + messengerType + "' is not supported, falling back to plugin messages!");
            case "plugin_messages":
                connector = new PluginMessageConnector(this);
                break;
            case "redis":
                connector = new RedisConnector(this);
                break;
        }

        bridge = new Bridge(this);
    }

    @Override
    public void onDisable() {
        connector.close();
    }

    @Override
    public BungeeConnector getConnector() {
        return connector;
    }

    /**
     * Get the bridge helper class for executing certain actions on other servers
     * @return The bridge helper
     */
    public Bridge getBridge() {
        return bridge;
    }

    @Override
    public MessageTarget.Type getSourceType() {
        return MessageTarget.Type.PROXY;
    }

    @Override
    public void logDebug(String message, Throwable... throwables) {
        if (debug) {
            getLogger().log(Level.INFO, "[DEBUG] " + message, throwables.length > 0 ? throwables[0] : null);
        }
    }

    @Override
    public void logWarning(String message, Throwable... throwables) {
        getLogger().log(Level.WARNING, message, throwables.length > 0 ? throwables[0] : null);
    }

    @Override
    public void logError(String message, Throwable... throwables) {
        getLogger().log(Level.SEVERE, message, throwables.length > 0 ? throwables[0] : null);
    }

    @Override
    public String getServerName() {
        return "proxy:" + getProxy().getConfig().getUuid();
    }

    @Override
    public String getGroup() {
        return "";
    }

    @Override
    public String getName() {
        return getDescription().getName();
    }
}
