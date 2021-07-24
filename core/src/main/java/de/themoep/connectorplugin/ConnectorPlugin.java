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

import de.themoep.connectorplugin.connector.Connector;
import de.themoep.connectorplugin.connector.MessageTarget;

public interface ConnectorPlugin {

    /**
     * Get the Connector which is used for sending and handling data
     * @return The Connector
     */
    Connector getConnector();

    /**
     * The type of source that this plugin is. {@link MessageTarget.Source#SERVER} or {@link MessageTarget.Source#PROXY}
     * @return The type of {@link MessageTarget.Source} that this implementation provides.
     */
    MessageTarget.Source getSourceType();

    default String getMessageChannel() {
        return "bbc:connection";
    }

    void logWarning(String message, Throwable... throwables);

    void logError(String message, Throwable... throwables);

    String getGroup();
}
