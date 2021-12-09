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
    ALL_WITH_PLAYERS(Type.SERVER),
    /**
     * Tries to send to all servers. (With plugin messages it queues if no player is connected to server)
     */
    ALL_QUEUE(Type.SERVER),
    /**
     * Sends to all other servers that have players connected. (So this doesn't queue with plugin messages)
     */
    OTHERS_WITH_PLAYERS(Type.SERVER),
    /**
     * Tries to send to all other servers. (With plugin messages it queues if no player is connected to server)
     */
    OTHERS_QUEUE(Type.SERVER),
    /**
     * Send to the players current server.<br>
     * Requires a server name or player parameter.<br>
     */
    SERVER(Type.SERVER),
    /**
     * Send to the players current proxy.<br>
     * Requires a player parameter.<br>
     * Can only be sent from a {@link Type#SERVER}
     */
    PROXY(Type.PROXY, Type.SERVER),
    /**
     * Send to all connected proxies
     */
    ALL_PROXIES(Type.PROXY),
    /**
     * Send to all proxies that aren't the current proxy
     */
    OTHER_PROXIES(Type.PROXY, Type.PROXY);

    private final Type type;
    private final Type source;

    MessageTarget() {
        this(null, null);
    }

    MessageTarget(Type type) {
        this(type, null);
    }

    MessageTarget(Type type, Type source) {
        this.type = type;
        this.source = source;
    }

    public Type getType() {
        return type;
    }

    public Type getSource() {
        return source;
    }

    public enum Type {
        PROXY,
        SERVER
    }
}
