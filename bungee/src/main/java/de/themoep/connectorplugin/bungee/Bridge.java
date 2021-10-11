package de.themoep.connectorplugin.bungee;

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
import de.themoep.connectorplugin.BridgeCommon;
import de.themoep.connectorplugin.LocationInfo;
import de.themoep.connectorplugin.ResponseHandler;
import de.themoep.connectorplugin.connector.MessageTarget;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Bridge extends BridgeCommon<BungeeConnectorPlugin> {

    public Bridge(BungeeConnectorPlugin plugin) {
        super(plugin);

        plugin.getConnector().registerHandler(plugin, Action.SEND_TO_SERVER, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();
            String targetServer = in.readUTF();

            ProxiedPlayer player = plugin.getProxy().getPlayer(playerName);
            if (player == null) {
                plugin.logDebug("Could not find player " + playerName + " on this proxy to send to server " + targetServer);
                sendResponse(senderServer, id, false, "Could not find player " + playerName + " on this proxy to send to server " + targetServer);
                return;
            }

            ServerInfo server = plugin.getProxy().getServerInfo(targetServer);
            if (server == null) {
                plugin.logDebug("Could not find server " + targetServer + " on this proxy to send player " + playerName + " to");
                sendResponse(senderServer, id, false, "Could not find server " + targetServer + " on this proxy to send player " + playerName + " to");
                return;
            }

            if (!player.getServer().getInfo().equals(server)) {
                plugin.logDebug("Sending '" + playerName + "' to server '" + targetServer + "'. Triggered from " + senderServer);

                player.connect(server, (success, ex) -> {
                    sendResponse(senderServer, id, success);
                    if (ex != null) {
                        sendResponseMessage(senderServer, id, ex.getMessage());
                    }
                });
            } else {
                plugin.logDebug("Player '" + playerName + "' is already on server '" + targetServer + "'! Triggered from " + senderServer);
                sendResponse(senderServer, id, true, playerName + " is already connected to server " + targetServer);
            }
        });

        plugin.getConnector().registerHandler(plugin, Action.TELEPORT, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();
            LocationInfo targetLocation = LocationInfo.read(in);

            teleport(playerName, targetLocation, messages -> sendResponseMessage(senderServer, id, messages))
                    .thenAccept(success -> sendResponse(senderServer, id, success));
        });

        plugin.getConnector().registerHandler(plugin, Action.PLAYER_COMMAND, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String serverName = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();
            UUID playerId = new UUID(in.readLong(), in.readLong());
            String command = in.readUTF();

            ProxiedPlayer player = plugin.getProxy().getPlayer(playerId);
            if (player == null) {
                player = plugin.getProxy().getPlayer(playerName);
            }
            if (player == null) {
                plugin.logDebug("Could not find player " + playerName + "/" + playerId + " on this proxy to execute command " + command);
                sendResponse(serverName, id, false, "Could not find player " + playerName + "/" + playerId + " on this proxy to execute command " + command);
                return;
            }

            plugin.logDebug("Command '" + command + "' for player '" + playerName + "' triggered from " + serverName);
            boolean success = plugin.getProxy().getPluginManager().dispatchCommand(player, command);

            sendResponse(serverName, id, player, success);
        });

        plugin.getConnector().registerHandler(plugin, Action.CONSOLE_COMMAND, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String command = in.readUTF();

            plugin.logDebug("Console command '" + command + "' triggered from " + senderServer);
            boolean success = plugin.getProxy().getPluginManager().dispatchCommand(new BridgedSender(senderServer, id), command);

            sendResponse(senderServer, id, success);
        });

        plugin.getConnector().registerHandler(plugin, Action.RESPONSE, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String serverName = in.readUTF();
            if (!serverName.equals(plugin.getServerName())) {
                return;
            }
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

    private void sendResponse(String server, long id, ProxiedPlayer player, boolean success, String... messages) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(server);
        out.writeLong(id);
        out.writeBoolean(true);
        out.writeBoolean(success);
        plugin.getConnector().sendData(plugin, Action.RESPONSE, MessageTarget.SERVER, player, out.toByteArray());

        if (messages.length > 0) {
            sendResponseMessage(server, id, messages);
        }
    }

    private void sendResponse(String server, long id, boolean success, String... messages) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(server);
        out.writeLong(id);
        out.writeBoolean(true);
        out.writeBoolean(success);
        plugin.getConnector().sendData(plugin, Action.RESPONSE, server.startsWith("proxy:") ? MessageTarget.OTHER_PROXIES : MessageTarget.ALL_QUEUE, out.toByteArray());

        if (messages.length > 0) {
            sendResponseMessage(server, id, messages);
        }
    }

    private void sendResponseMessage(String server, long id, String... messages) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(server);
        out.writeLong(id);
        out.writeBoolean(false);
        out.writeUTF(String.join("\n", messages));
        plugin.getConnector().sendData(plugin, Action.RESPONSE, server.startsWith("proxy:") ? MessageTarget.OTHER_PROXIES : MessageTarget.ALL_QUEUE, out.toByteArray());
    }

    /**
     * Teleport a player to a certain location in the network
     * @param playerName    The name of the player to teleport
     * @param location      The location to teleport to
     * @param consumer      Details about the teleport
     * @return A future about whether the player could be teleported
     */
    public CompletableFuture<Boolean> teleport(String playerName, LocationInfo location, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        ProxiedPlayer player = plugin.getProxy().getPlayer(playerName);
        if (player == null) {
            plugin.logDebug("Could not find player " + playerName + " on this proxy to send to teleport to " + location);
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find player " + playerName + " on this proxy to teleport to " + location);
            }
            return future;
        }

        ServerInfo server = plugin.getProxy().getServerInfo(location.getServer());
        if (server == null) {
            plugin.logDebug("Could not find server " + location.getServer() + " on this proxy to teleport player " + playerName + " to");
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find server " + location.getServer() + " on this proxy to teleport player " + playerName + " to");
            }
            return future;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(playerName);
        location.write(out);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        plugin.getConnector().sendData(plugin, Action.TELEPORT, MessageTarget.ALL_QUEUE, out.toByteArray());

        if (!player.getServer().getInfo().equals(server)) {
            plugin.logDebug("Sending '" + playerName + "' to server '" + server.getName() + "'");

            player.connect(server, (success, ex) -> {
                if (!success) {
                    future.complete(false);
                    for (Consumer<String> c : consumer) {
                        c.accept(ex.getMessage());
                    }
                }
            });
        }

        return future;
    }

    /**
     * Get the location of a player on the server
     * @param player    The player to get the location for
     * @return A future for when the location was queried
     */
    public CompletableFuture<LocationInfo> getLocation(ProxiedPlayer player) {
        CompletableFuture<LocationInfo> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player.getName());
        responses.put(id, new ResponseHandler.Location(future));
        plugin.getConnector().sendData(plugin, Action.GET_LOCATION, MessageTarget.SERVER, player, out.toByteArray());
        return future;
    }

    /**
     * Run a command for a player on the server they are connected to.
     * The player needs to have access to that command!
     * @param player    The player to run the command for
     * @param command   The command to run
     * @return A future for whether the command was run successfully
     */
    public CompletableFuture<Boolean> runServerPlayerCommand(ProxiedPlayer player, String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player.getName());
        out.writeLong(player.getUniqueId().getMostSignificantBits());
        out.writeLong(player.getUniqueId().getLeastSignificantBits());
        out.writeUTF(command);
        responses.put(id, new ResponseHandler.Boolean(future));
        plugin.getConnector().sendData(plugin, Action.PLAYER_COMMAND, MessageTarget.SERVER, player, out.toByteArray());
        return future;
    }

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
        plugin.getConnector().sendData(plugin, Action.CONSOLE_COMMAND, MessageTarget.ALL_QUEUE, out.toByteArray());
        return future;
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
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(command);
        responses.put(id, new ResponseHandler.Boolean(future));
        if (consumer != null && consumer.length > 0) {
            consumers.put(id, consumer);
        }
        plugin.getConnector().sendData(plugin, Action.CONSOLE_COMMAND, MessageTarget.OTHER_PROXIES, out.toByteArray());
        return future;
    }

    private class BridgedSender implements CommandSender {
        private final String serverName;
        private final long id;

        public BridgedSender(String serverName, long id) {
            this.serverName = serverName;
            this.id = id;
        }

        @Override
        public String getName() {
            return serverName + "BridgedSender";
        }

        @Override
        public void sendMessage(String message) {
            sendMessages(message);
        }

        @Override
        public void sendMessages(String... messages) {
            sendResponseMessage(serverName, id, messages);
        }

        @Override
        public void sendMessage(BaseComponent... message) {
            sendMessage(TextComponent.toLegacyText(message));
        }

        @Override
        public void sendMessage(BaseComponent message) {
            sendMessage(TextComponent.toLegacyText(message));
        }

        @Override
        public Collection<String> getGroups() {
            return null;
        }

        @Override
        public void addGroups(String... groups) {

        }

        @Override
        public void removeGroups(String... groups) {

        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public void setPermission(String permission, boolean value) {

        }

        @Override
        public Collection<String> getPermissions() {
            return Collections.emptySet();
        }
    }
}