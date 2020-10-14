package de.themoep.bukkitbungeeconnector.bungee.connector;

/*
 * BukkitBungeeConnector
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

import de.themoep.bukkitbungeeconnector.bungee.BukkitBungeeConnector;
import de.themoep.bukkitbungeeconnector.connector.Connector;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public abstract class BungeeConnector extends Connector<BukkitBungeeConnector, ProxiedPlayer> {

    public BungeeConnector(BukkitBungeeConnector plugin) {
        super(plugin);
    }
}
