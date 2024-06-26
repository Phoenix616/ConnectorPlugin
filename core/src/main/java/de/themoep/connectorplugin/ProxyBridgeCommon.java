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
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.connector.MessageTarget;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static de.themoep.connectorplugin.connector.Connector.PROXY_ID_PREFIX;

public abstract class ProxyBridgeCommon<P extends ConnectorPlugin<R>, R> extends BridgeCommon<P, R> {

    public ProxyBridgeCommon(P plugin) {
        super(plugin);

        registerMessageHandler(Action.STARTED, (receiver, message)
                -> registerServerCommands(message.getReceivedMessage().getSendingServer()));

        registerMessageHandler(Action.TELEPORT, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String playerName = in.readUTF();
            LocationInfo targetLocation = LocationInfo.read(in);

            teleport(playerName, targetLocation, messages -> sendResponseMessage(senderServer, id, messages))
                    .thenAccept(success -> sendResponse(senderServer, id, success));
        });

        registerMessageHandler(Action.TELEPORT_TO_WORLD, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String playerName = in.readUTF();
            String targetServer = in.readUTF();
            String targetWorld = in.readUTF();

            teleport(playerName, targetServer, targetWorld, messages -> sendResponseMessage(senderServer, id, messages))
                    .thenAccept(success -> sendResponse(senderServer, id, success));
        });

        registerMessageHandler(Action.TELEPORT_TO_PLAYER, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String playerName = in.readUTF();
            String targetName = in.readUTF();

            teleport(playerName, targetName, messages -> sendResponseMessage(senderServer, id, messages))
                    .thenAccept(success -> sendResponse(senderServer, id, success));
        });

        registerHandler(Action.GET_PLAYER_INFO, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();

            R player = getPlayer(playerName);
            if (player == null) {
                sendResponseMessage(senderServer, id, "Player " + playerName + " not found!");
            } else {
                sendResponse(senderServer, id, createPlayerInfo(player));
            }
        });

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

    @Override
    protected void sendResponseData(String target, byte[] out) {
        sendData(
                Action.RESPONSE,
                target.startsWith(PROXY_ID_PREFIX) ? MessageTarget.PROXY : MessageTarget.SERVER,
                target,
                out);
    }

    /**
     * Run a console command on all other proxies
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
        sendData(Action.CONSOLE_COMMAND, MessageTarget.OTHER_PROXIES, out.toByteArray());
        return future;
    }

    protected void onPlayerJoin(PlayerInfo playerInfo) {
        addPlayerInfo(playerInfo);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        playerInfo.write(out);
        byte[] data = out.toByteArray();
        try {
            sendData(Action.PLAYER_JOIN, MessageTarget.OTHER_PROXIES, data);
        } catch (UnsupportedOperationException ignored) {
            // Sending to other proxies is not supported when using plugin messages
        }
        sendData(Action.PLAYER_JOIN, MessageTarget.ALL_QUEUE, data);
    }

    protected void onPlayerLeave(String player) {
        unmarkTeleporting(player);

        removePlayerInfo(player);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(player);
        byte[] data = out.toByteArray();
        try {
            sendData(Action.PLAYER_LEAVE, MessageTarget.OTHER_PROXIES, data);
        } catch (UnsupportedOperationException ignored) {
            // Sending to other proxies is not supported when using plugin messages
        }
        sendData(Action.PLAYER_LEAVE, MessageTarget.ALL_QUEUE, data);
    }

    /**
     * Register all known commands on a server
     * @param server The server
     */
    protected abstract void registerServerCommands(String server);

    /**
     * Get a player by name
     * @param playerName The name of the player
     * @return The player
     */
    protected abstract R getPlayer(String playerName);

    /**
     * Create a player info object for a player
     * @param player The player
     * @return The player info object
     */
    public abstract PlayerInfo createPlayerInfo(R player);
}
