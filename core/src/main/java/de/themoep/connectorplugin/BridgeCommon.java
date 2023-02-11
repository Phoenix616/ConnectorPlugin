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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.connector.Message;
import de.themoep.connectorplugin.connector.MessageTarget;
import de.themoep.connectorplugin.connector.VersionMismatchException;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static de.themoep.connectorplugin.connector.Connector.PLAYER_PREFIX;

public abstract class BridgeCommon<P extends ConnectorPlugin<R>, R> {
    private static final int VERSION = 2;
    protected final P plugin;

    protected final Cache<Long, ResponseHandler<?>> responses = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();
    protected final Cache<Long, Consumer<String>[]> consumers = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();
    private final Set<String> isTeleporting = new HashSet<>();

    protected static final Random RANDOM = new Random();

    public BridgeCommon(P plugin) {
        this.plugin = plugin;
    }

    /**
     * Register a bridge data handler for a certain action
     * @param action    The action to register (case sensitive)
     * @param handler   A BiConsumer which takes the receiving player and the data
     * @return The previously registered handler if there was one
     */
    protected BiConsumer<R, Message> registerHandler(String action, BiConsumer<R, byte[]> handler) {
        return registerMessageHandler(action, (r, m) -> handler.accept(r, m.getData()));
    }

    /**
     * Register a bridge message handler for a certain action
     * @param action    The action to register (case sensitive)
     * @param handler   A BiConsumer which takes the receiving player and the message
     * @return The previously registered handler if there was one
     */
    protected BiConsumer<R, Message> registerMessageHandler(String action, BiConsumer<R, BridgeMessage> handler) {
        return plugin.getConnector().registerMessageHandler(plugin, action, (r, m) -> {
            try {
                BridgeMessage message = BridgeMessage.fromMessage(m);
                handler.accept((R) r, message);
            } catch (VersionMismatchException e) {
                plugin.logError(e.getMessage() + " Ignoring message!");
            }
        });
    }

    /**
     * Teleport a player to a certain location in the network
     * @param player        The player to teleport
     * @param location      The location to teleport to
     * @param consumer      Details about the teleport
     * @return A future about whether the player could be teleported
     */
    public abstract CompletableFuture<Boolean> teleport(R player, LocationInfo location, Consumer<String>... consumer);

    /**
     * Teleport a player to a certain location in the network
     * @param playerName    The name of the player to teleport
     * @param location      The location to teleport to
     * @param consumer      Details about the teleport
     * @return A future about whether the player could be teleported
     */
    public abstract CompletableFuture<Boolean> teleport(String playerName, LocationInfo location, Consumer<String>... consumer);


    /**
     * Teleport a player to a certain location in the network
     * @param playerName    The name of the player to teleport
     * @param serverName    The target server
     * @param worldName     The target world
     * @param consumer      Details about the teleport
     * @return A future about whether the player could be teleported
     */
    public abstract CompletableFuture<Boolean> teleport(String playerName, String serverName, String worldName, Consumer<String>... consumer);


    /**
     * Teleport a player to a certain location in the network
     * @param player        The player to teleport
     * @param serverName    The target server
     * @param worldName     The target world
     * @param consumer      Details about the teleport
     * @return A future about whether the player could be teleported
     */
    public abstract CompletableFuture<Boolean> teleport(R player, String serverName, String worldName, Consumer<String>... consumer);

    /**
     * Teleport a player to a certain other player in the network
     * @param player    The player to teleport
     * @param target    The target player
     * @param consumer  Details about the teleport
     * @return A future about whether the player could be teleported
     */
    public abstract CompletableFuture<Boolean> teleport(R player, R target, Consumer<String>... consumer);

    /**
     * Teleport a player to a certain other player in the network
     * @param playerName    The name of the player to teleport
     * @param targetName    The name of the target player
     * @param consumer      Details about the teleport
     * @return A future about whether the player could be teleported
     */
    public abstract CompletableFuture<Boolean> teleport(String playerName, String targetName, Consumer<String>... consumer);

    /**
     * Run a console command on the target server
     * @param server    The server to run teh command on
     * @param command   The command to run
     * @param consumer  Optional Consumer (or multiple) for the messages triggered by the command
     * @return A future for whether the command was run successfully
     */
    public CompletableFuture<Boolean> runServerConsoleCommand(String server, String command, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeUTF(server);
        out.writeLong(id);
        out.writeUTF(command);
        responses.put(id, new ResponseHandler.Boolean(future));
        if (consumer != null && consumer.length > 0) {
            consumers.put(id, consumer);
        }
        sendData(Action.CONSOLE_COMMAND, MessageTarget.SERVER, out.toByteArray());
        return future;
    }

    /**
     * Run a console command on a specific other proxy
     * @param proxy    The proxy to run the command on
     * @param command   The command to run
     * @param consumer  Optional Consumer (or multiple) for the messages triggered by the command
     * @return A future for whether the command was run successfully
     */
    public CompletableFuture<Boolean> runProxyConsoleCommand(String proxy, String command, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeUTF(proxy);
        out.writeLong(id);
        out.writeUTF(command);
        responses.put(id, new ResponseHandler.Boolean(future));
        if (consumer != null && consumer.length > 0) {
            consumers.put(id, consumer);
        }
        sendData(Action.CONSOLE_COMMAND, MessageTarget.PROXY, out.toByteArray());
        return future;
    }

    /**
     * Get the location a player is at
     * @param player    The player to get the location for
     * @return A future for when the location was queried
     */
    public abstract CompletableFuture<LocationInfo> getLocation(R player);

    /**
     * Get the location a player is at
     * @param player    The player to get the location for
     * @return A future for when the location was queried
     */
    public CompletableFuture<LocationInfo> getLocation(String player) {
        CompletableFuture<LocationInfo> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player);
        responses.put(id, new ResponseHandler.Location(future));
        sendData(Action.GET_LOCATION, MessageTarget.SERVER, PLAYER_PREFIX + player, out.toByteArray());
        return future;
    }

    /**
     * Get the server a player is connected to
     * @param player    The player to get the server for
     * @return A future for when the server was queried
     */
    public abstract CompletableFuture<String> getServer(R player);

    /**
     * Get the server a player is connected to
     * @param player    The player to get the server for
     * @return A future for when the server was queried
     */
    public CompletableFuture<String> getServer(String player) {
        CompletableFuture<String> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player);
        responses.put(id, new ResponseHandler.String(future));
        sendData(Action.GET_SERVER, MessageTarget.ALL_PROXIES, PLAYER_PREFIX + player, out.toByteArray());
        return future;
    }

    /**
     * Send data to a specific target
     * @param action    The action for which data is sent
     * @param target    Where to send data to
     * @param data      The data
     */
    public void sendData(String action, MessageTarget target, byte[] data) {
        plugin.getConnector().sendData(plugin, action, target, new BridgeMessage(data).writeToByteArray());
    }

    /**
     * Send data to a specific target
     * @param action    The action for which data is sent
     * @param target    Where to send data to
     * @param player    Additional player data to use for sending (required in case the target is {@link MessageTarget#SERVER} or {@link MessageTarget#PROXY})
     * @param data      The data
     */
    protected void sendData(String action, MessageTarget target, R player, byte[] data) {
        plugin.getConnector().sendData(plugin, action, target, player, new BridgeMessage(data).writeToByteArray());
    }

    /**
     * Send data to a specific target
     * @param action        The action for which data is sent
     * @param target        Where to send data to
     * @param targetData    Additional data to use for sending (required in case the target is {@link MessageTarget#SERVER})
     * @param data          The data
     */
    protected void sendData(String action, MessageTarget target, String targetData, byte[] data) {
        plugin.getConnector().sendData(plugin, action, target, targetData, new BridgeMessage(data).writeToByteArray());
    }

    protected void sendResponse(String target, long id, Object response, String... messages) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeLong(id);
        out.writeBoolean(true);
        if (response instanceof Boolean) {
            out.writeBoolean((Boolean) response);
        } else if (response instanceof String) {
            out.writeUTF((String) response);
        } else if (response instanceof LocationInfo) {
            ((LocationInfo) response).write(out);
        } else if (response == null) {
            out.writeUTF("");
        }
        sendResponseData(target, out.toByteArray());

        if (messages.length > 0) {
            sendResponseMessage(target, id, messages);
        }
    }

    protected void sendResponseMessage(String target, long id, String... messages) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeLong(id);
        out.writeBoolean(false);
        out.writeUTF(String.join("\n", messages));
        sendResponseData(target, out.toByteArray());
    }

    protected abstract void sendResponseData(String target, byte[] out);

    protected void handleResponse(long id, ByteArrayDataInput in) {
        ResponseHandler<?> responseHandler = responses.getIfPresent(id);
        if (responseHandler != null) {
            responses.invalidate(id);
            if (responseHandler instanceof ResponseHandler.Boolean) {
                ((ResponseHandler.Boolean) responseHandler).getFuture().complete(in.readBoolean());
            } else if (responseHandler instanceof ResponseHandler.String) {
                ((ResponseHandler.String) responseHandler).getFuture().complete(in.readUTF());
            } else if (responseHandler instanceof ResponseHandler.Location) {
                ((ResponseHandler.Location) responseHandler).getFuture().complete(LocationInfo.read(in));
            } else {
                plugin.logDebug("Response handler type " + responseHandler + " is not supported for ID " + id);
            }
        } else {
            plugin.logDebug("Could not find response for execution with ID " + id);
        }
    }

    protected static class BridgeMessage {
        private final Message receivedMessage;
        private final byte[] data;

        public BridgeMessage(byte[] data) {
            this(null, data);
        }

        /**
         * @since 1.5
         */
        protected BridgeMessage(Message receivedMessage, byte[] data) {
            this.receivedMessage = receivedMessage;
            this.data = data;
        }

        public byte[] writeToByteArray() {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeInt(VERSION);
            out.writeInt(data.length);
            out.write(data);
            return out.toByteArray();
        }

        /**
         * @deprecated Since 1.5. Use {@link #fromMessage(Message)}
         */
        @Deprecated
        public static BridgeMessage fromByteArray(byte[] messageData) throws VersionMismatchException {
            ByteArrayDataInput in = ByteStreams.newDataInput(messageData);
            int version = in.readInt();
            if (version < VERSION) {
                throw new VersionMismatchException(version, VERSION, "Received bridge message from an outdated version! Please update the sending plugin!");
            } else if (version > VERSION) {
                throw new VersionMismatchException(version, VERSION, "Received bridge message with a newer version! Please update this plugin!");
            }
            int length = in.readInt();
            byte[] data = new byte[length];
            in.readFully(data);
            return new BridgeMessage(null, data);
        }

        /**
         * @since 1.5
         */
        public static BridgeMessage fromMessage(Message message) throws VersionMismatchException {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            int version = in.readInt();
            if (version < VERSION) {
                throw new VersionMismatchException(version, VERSION, "Received bridge message from an outdated version! Please update the sending plugin!");
            } else if (version > VERSION) {
                throw new VersionMismatchException(version, VERSION, "Received bridge message with a newer version! Please update this plugin!");
            }
            int length = in.readInt();
            byte[] data = new byte[length];
            in.readFully(data);
            return new BridgeMessage(message, data);
        }

        public Message getReceivedMessage() {
            return receivedMessage;
        }

        public byte[] getData() {
            return data;
        }
    }

    /**
     * Get whether a player with that name is currently teleporting.
     * Useful for checking of a quit/join is from a teleport or not.
     * @return Whether the player is currently teleporting with ConnectorPlugin
     */
    public boolean isTeleporting(String playerName) {
        return isTeleporting.contains(playerName.toLowerCase(Locale.ROOT));
    }

    protected boolean markTeleporting(String playerName) {
        return isTeleporting.add(playerName.toLowerCase(Locale.ROOT));
    }

    protected boolean unmarkTeleporting(String playerName) {
        return isTeleporting.remove(playerName.toLowerCase(Locale.ROOT));
    }

    public static class Action {
        public static final String STARTED = "started";
        public static final String SEND_TO_SERVER = "send_to_server";
        public static final String TELEPORT = "teleport";
        public static final String TELEPORT_TO_WORLD = "teleport_to_world";
        public static final String TELEPORT_TO_PLAYER = "teleport_to_player";
        public static final String GET_LOCATION = "get_location";
        public static final String GET_SERVER = "get_server";
        public static final String PLAYER_COMMAND = "player_command";
        public static final String CONSOLE_COMMAND = "console_command";
        public static final String RESPONSE = "response";
        public static final String REGISTER_COMMAND = "register_command";
        public static final String EXECUTE_COMMAND = "execute_command";
    }
}
