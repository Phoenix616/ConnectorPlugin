package de.themoep.connectorplugin.connector;

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

public enum MessageTarget {
    /**
     * Sends to all servers that have players connected. (So this doesn't queue with plugin messages)
     */
    ALL_WITH_PLAYERS,
    /**
     * Tries to send to all servers. (With plugin messages it queues if no player is connected to server)
     */
    ALL_QUEUE,
    /**
     * Send to the players current server.<br>
     * Requires a player parameter.<br>
     * Can only be sent from a {@link Source#PROXY}
     */
    SERVER(Source.PROXY),
    /**
     * Send to the players current proxy.<br>
     * Requires a player parameter.<br>
     * Can only be sent from a {@link Source#SERVER}
     */
    PROXY(Source.SERVER);

    private final Source source;

    MessageTarget() {
        source = null;
    }

    MessageTarget(Source source) {
        this.source = source;
    }

    public Source getSource() {
        return source;
    }

    public enum Source {
        PROXY,
        SERVER
    }
}
