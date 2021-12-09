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

import java.util.concurrent.CompletableFuture;

public abstract class ResponseHandler<T> {

    private final CompletableFuture<T> future;

    protected ResponseHandler(CompletableFuture<T> future) {
        this.future = future;
    }

    public CompletableFuture<T> getFuture() {
        return future;
    }

    public static class Boolean extends ResponseHandler<java.lang.Boolean> {
        public Boolean(CompletableFuture<java.lang.Boolean> future) {
            super(future);
        }
    }

    public static class String extends ResponseHandler<java.lang.String> {
        public String(CompletableFuture<java.lang.String> future) {
            super(future);
        }
    }

    public static class Location extends ResponseHandler<LocationInfo> {
        public Location(CompletableFuture<LocationInfo> future) {
            super(future);
        }
    }
}
