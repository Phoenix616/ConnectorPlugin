package de.themoep.connectorplugin.connector;/*
 * ConnectorPlugin
 * Copyright (C) 2021 Max Lee aka Phoenix616 (max@themoep.de)
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

public class VersionMismatchException extends Exception {
    private final int receivedVersion;
    private final int supportedVersion;

    public VersionMismatchException(int receivedVersion, int supportedVersion, String message) {
        super(message);
        this.receivedVersion = receivedVersion;
        this.supportedVersion = supportedVersion;
    }

    public int getReceivedVersion() {
        return receivedVersion;
    }

    public int getSupportedVersion() {
        return supportedVersion;
    }
}
