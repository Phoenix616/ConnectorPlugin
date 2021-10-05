package de.themoep.connectorplugin;/*
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BridgeCommon<P extends ConnectorPlugin> {
    protected final P plugin;

    protected final Cache<Long, CompletableFuture<Boolean>> futures = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();
    protected final Cache<Long, Consumer<String>[]> consumers = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();

    protected static final Random RANDOM = new Random();

    public BridgeCommon(P plugin) {
        this.plugin = plugin;
    }

    public static class Action {
        public static final String PLAYER_COMMAND = "player_command";
        public static final String CONSOLE_COMMAND = "console_command";
        public static final String COMMAND_RESPONSE = "command_response";
    }
}
