package de.themoep.connectorplugin;

/*
 * ConnectorPlugin
 * Copyright (C) 2025 Max Lee aka Phoenix616 (max@themoep.de)
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
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.connector.MessageTarget;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static de.themoep.connectorplugin.connector.Connector.PLAYER_PREFIX;
import static de.themoep.connectorplugin.connector.Connector.PROXY_ID_PREFIX;

public abstract class ServerBridgeCommon<P extends ConnectorPlugin<R>, R> extends BridgeCommon<P, R> {
    public ServerBridgeCommon(P plugin) {
        super(plugin);

        registerHandler(Action.RESPONSE, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            long id = in.readLong();
            boolean isCompletion = in.readBoolean();
            if (isCompletion) {
                handleResponse(id, in);
            } else {
                String message = in.readUTF();
                Consumer<String>[] consumer = consumers.getIfPresent(id);
                if (consumer != null) {
                    for (Consumer<String> stringConsumer : consumer) {
                        stringConsumer.accept(message);
                    }
                }
            }
        });
    }

    public void sendStarted(P plugin) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(plugin.getServerName());
        sendData(Action.STARTED, MessageTarget.ALL_PROXIES, out.toByteArray());
    }

    /**
     * Teleport a player to a certain server in the network
     * @param playerName    The name of the player to send
     * @param serverName    The name of the server to send to
     * @param consumer      Details about the sending
     * @return A future about whether the player could be sent
     */
    public CompletableFuture<Boolean> sendToServer(String playerName, String serverName, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        getServer(playerName).whenComplete((s, e) -> {
            // check player server existence
            if (s == null) {
                future.complete(false);
                for (Consumer<String> c : consumer) {
                    c.accept("Player " + playerName + " is not online!");
                }
                return;
            }
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            long id = RANDOM.nextLong();
            out.writeLong(id);
            out.writeUTF(playerName);
            out.writeUTF(serverName);
            responses.put(id, new ResponseHandler.Boolean(future));
            consumers.put(id, consumer);
            sendData(Action.SEND_TO_SERVER, MessageTarget.PROXY, PLAYER_PREFIX + playerName, out.toByteArray());
        });
        return future;
    }

    /**
     * Run a console command on the connected proxies
     * @param command   The command to run
     * @param consumer  Optional Consumer (or multiple) for the messages triggered by the command
     * @return A future for whether the command was run successfully
     */
    public CompletableFuture<Boolean> runProxyConsoleCommand(String command, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeLong(id);
        out.writeUTF(command);
        responses.put(id, new ResponseHandler.Boolean(future));
        if (consumer != null && consumer.length > 0) {
            consumers.put(id, consumer);
        }
        sendData(Action.CONSOLE_COMMAND, MessageTarget.ALL_PROXIES, out.toByteArray());
        return future;
    }

    @Override
    protected void sendResponseData(String target, byte[] out) {
        sendData(
                Action.RESPONSE,
                target.startsWith(PROXY_ID_PREFIX) ? MessageTarget.PROXY : MessageTarget.SERVER,
                target,
                out);
    }

    public static class LoginRequest {
        private final String server;
        private final long id;

        private LoginRequest(String server, long id) {
            this.server = server;
            this.id = id;
        }

        public String getServer() {
            return server;
        }

        public long getId() {
            return id;
        }
    }

    public static class LocationTeleportRequest extends LoginRequest {
        private final LocationInfo location;

        public LocationTeleportRequest(String server, long id, LocationInfo location) {
            super(server, id);
            this.location = location;
        }

        public LocationInfo getLocation() {
            return location;
        }
    }

    public static class PlayerTeleportRequest extends LoginRequest {
        private final String targetName;

        public PlayerTeleportRequest(String server, long id, String targetName) {
            super(server, id);
            this.targetName = targetName;
        }

        public String getTargetName() {
            return targetName;
        }
    }
}
