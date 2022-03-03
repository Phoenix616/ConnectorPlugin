package de.themoep.connectorplugin;

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

import de.themoep.connectorplugin.connector.ConnectingPlugin;
import de.themoep.connectorplugin.connector.Connector;
import de.themoep.connectorplugin.connector.MessageTarget;

import java.util.Locale;
import java.util.Map;

public interface ConnectorPlugin<R> extends ConnectingPlugin {

    /**
     * Get the Connector which is used for sending and handling data
     * @return The Connector
     */
    Connector<? extends ConnectorPlugin, R> getConnector();

    /**
     * The type of source that this plugin is. {@link MessageTarget.Type#SERVER} or {@link MessageTarget.Type#PROXY}
     * @return The type of {@link MessageTarget.Type} that this implementation provides.
     */
    MessageTarget.Type getSourceType();

    default String getMessageChannel() {
        return "bbc:connection";
    }

    void logDebug(String message, Throwable... throwables);

    void logInfo(String message, Throwable... throwables);

    void logWarning(String message, Throwable... throwables);

    void logError(String message, Throwable... throwables);

    String getServerName();

    /**
     * @deprecated Use {@link #getGroup(String)} or {@link #getGlobalGroup()}
     */
    @Deprecated
    default String getGroup() {
        return getGlobalGroup();
    }

    String getGlobalGroup();

    Map<String, String> getGroups();

    /**
     * Get the group that should apply to the plugin (either per-plugin group or global one)
     * @param pluginName The plugin's name
     * @return The per-plugin group or the global one
     */
    default String getGroup(String pluginName) {
        return getGroups().getOrDefault(pluginName.toLowerCase(Locale.ROOT), getGlobalGroup());
    }

    void runAsync(Runnable runnable);
}
