package de.themoep.connectorplugin.velocity.connector;

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

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.themoep.connectorplugin.connector.Message;
import de.themoep.connectorplugin.velocity.VelocityConnectorPlugin;
import de.themoep.connectorplugin.connector.VersionMismatchException;

public class PluginMessageConnector extends VelocityConnector {

    private final ChannelIdentifier messageChannel;
    private final Multimap<String, byte[]> messageQueue = MultimapBuilder.hashKeys().linkedListValues().build();

    public PluginMessageConnector(VelocityConnectorPlugin plugin) {
        super(plugin);
        messageChannel = MinecraftChannelIdentifier.from(plugin.getMessageChannel());
        plugin.getProxy().getChannelRegistrar().register(messageChannel);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getResult().isAllowed() || !event.getIdentifier().equals(messageChannel)) {
            return;
        }

        if (event.getSource() instanceof Player) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String group = in.readUTF();

        int messageLength = in.readInt();
        byte[] messageData = new byte[messageLength];
        in.readFully(messageData);
        try {
            Message message = Message.fromByteArray(messageData);
            switch (message.getTarget()) {
                case ALL_WITH_PLAYERS:
                    sendToAllWithPlayers(event.getData(), null);
                    break;
                case ALL_QUEUE:
                    sendToAllAndQueue(event.getData(), null);
                    break;
                case OTHERS_WITH_PLAYERS:
                    if (((Player) event.getTarget()).getCurrentServer().isPresent()) {
                        sendToAllWithPlayers(event.getData(), ((Player) event.getTarget()).getCurrentServer().get().getServer());
                    } else {
                        sendToAllWithPlayers(event.getData(), null);
                    }
                    break;
                case OTHERS_QUEUE:
                    if (((Player) event.getTarget()).getCurrentServer().isPresent()) {
                        sendToAllAndQueue(event.getData(), ((Player) event.getTarget()).getCurrentServer().get().getServer());
                    } else {
                        sendToAllAndQueue(event.getData(), null);
                    }
                    break;
                case PROXY:
                case ALL_PROXIES:
                    handle((Player) event.getTarget(), message);
                    break;
                default:
                    plugin.logError("Receiving " + message.getTarget() + " is not supported!");
            }
        } catch (IllegalArgumentException e) {
            plugin.logError("Invalid message target! " + e.getMessage());
        } catch (VersionMismatchException e) {
            plugin.logWarning(e.getMessage() + ". Ignoring message!");
        }
    }

    private void sendToAllWithPlayers(byte[] data, RegisteredServer excludedServer) {
        sendToAll(data, false, excludedServer);
    }

    private void sendToAllAndQueue(byte[] data, RegisteredServer excludedServer) {
        sendToAll(data, true, excludedServer);
    }

    private void sendToAll(byte[] data, boolean queue, RegisteredServer excludedServer) {
        for (RegisteredServer server : plugin.getProxy().getAllServers()) {
            if (excludedServer == null || excludedServer != server) {
                if (!server.sendPluginMessage(messageChannel, data) && queue) {
                    messageQueue.put(server.getServerInfo().getName(), data);
                }
            }
        }
    }

    @Subscribe
    public void onPlayerServerConnect(ServerConnectedEvent event) {
        for (byte[] data : messageQueue.removeAll(event.getServer().getServerInfo().getName())) {
            event.getServer().sendPluginMessage(messageChannel, data);
        }
    }

    @Override
    public void sendDataImplementation(String targetData, Message message) {
        byte[] messageData = message.writeToByteArray(plugin);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(plugin.getGroup());
        out.writeInt(messageData.length);
        out.write(messageData);
        byte[] dataToSend = out.toByteArray();

        RegisteredServer server = getTargetServer(targetData);

        switch (message.getTarget()) {
            case ALL_WITH_PLAYERS:
                sendToAllWithPlayers(dataToSend, null);
                break;
            case ALL_QUEUE:
                sendToAllAndQueue(dataToSend, null);
                break;
            case OTHERS_WITH_PLAYERS:
                sendToAllWithPlayers(dataToSend, server);
                break;
            case OTHERS_QUEUE:
                sendToAllAndQueue(dataToSend, server);
                break;
            case SERVER:
                if (server != null) {
                    server.sendPluginMessage(messageChannel, dataToSend);
                } else {
                    throw new UnsupportedOperationException("Could not send data to " + message.getTarget() + " as target server wasn't found from " + targetData + "!");
                }
                break;
            default:
                throw new UnsupportedOperationException("Sending to " + message.getTarget() + " is not supported!");
        }
    }
}
