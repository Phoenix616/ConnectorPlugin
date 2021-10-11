package de.themoep.connectorplugin;

/*
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

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public class LocationInfo {
    private final String server;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public LocationInfo(String server, String world, double x, double y, double z) {
        this(server, world, x, y, z, 0, 0);
    }

    public LocationInfo(String server, String world, double x, double y, double z, float yaw, float pitch) {
        this.server = server;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public LocationInfo(LocationInfo location) {
        this(
                location.getServer(),
                location.getWorld(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public String getServer() {
        return server;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void write(ByteArrayDataOutput out) {
        out.writeUTF(getServer());
        out.writeUTF(getWorld());
        out.writeDouble(getX());
        out.writeDouble(getY());
        out.writeDouble(getZ());
        out.writeFloat(getPitch());
        out.writeFloat(getYaw());
    }

    public static LocationInfo read(ByteArrayDataInput in) {
        String serverName = in.readUTF();
        if (serverName.isEmpty()) {
            return null;
        }
        return new LocationInfo(
                serverName,
                in.readUTF(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readFloat(),
                in.readFloat()
        );
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{server=" + getServer() + ",world=" + getWorld() + ",x=" + getX() + ",y=" + getY() + ",z=" + getZ() + ",yaw=" + getYaw() + ",pitch=" + getPitch() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof LocationInfo)) {
            return false;
        }
        if (o == this) {
            return true;
        }
        LocationInfo other = (LocationInfo) o;
        return other.getServer().equalsIgnoreCase(getServer())
                && ((other.getWorld() == null && getWorld() == null)
                || (other.getWorld() != null && other.getWorld().equalsIgnoreCase(getWorld())))
                && other.getX() == getX()
                && other.getY() == getY()
                && other.getZ() == getZ()
                && other.getYaw() == getYaw()
                && other.getPitch() == getPitch();
    }
}