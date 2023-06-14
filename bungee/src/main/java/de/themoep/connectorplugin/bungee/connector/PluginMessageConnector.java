package de.themoep.connectorplugin.bungee.connector;

/*
 * ConnectorPlugin
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

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.connector.Message;
import de.themoep.connectorplugin.bungee.BungeeConnectorPlugin;
import de.themoep.connectorplugin.connector.VersionMismatchException;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.logging.Level;

public class PluginMessageConnector extends BungeeConnector implements Listener {

    public PluginMessageConnector(BungeeConnectorPlugin plugin) {
        super(plugin, true);
        plugin.getProxy().registerChannel(plugin.getMessageChannel());
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.isCancelled() || !event.getTag().equals(plugin.getMessageChannel())) {
            return;
        }

        if (event.getSender() instanceof ProxiedPlayer) {
            event.setCancelled(true);
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String group = in.readUTF();
        String target = in.readUTF();

        int messageLength = in.readInt();
        byte[] messageData = new byte[messageLength];
        in.readFully(messageData);
        try {
            Message message = Message.fromByteArray(group, messageData);
            switch (message.getTarget()) {
                case ALL_WITH_PLAYERS:
                    sendToAllWithPlayers(event.getData(), null);
                    break;
                case ALL_QUEUE:
                    sendToAllAndQueue(event.getData(), null);
                    break;
                case OTHERS_WITH_PLAYERS:
                    sendToAllWithPlayers(event.getData(), ((ProxiedPlayer) event.getSender()).getServer().getInfo());
                    break;
                case OTHERS_QUEUE:
                    sendToAllAndQueue(event.getData(), ((ProxiedPlayer) event.getSender()).getServer().getInfo());
                    break;
                case PROXY:
                case ALL_PROXIES:
                    handle(target, message);
                    break;
                case SERVER:
                    if (!target.isEmpty()) {
                        ServerInfo server = getTargetServer(target);
                        if (server != null) {
                            server.sendData(plugin.getMessageChannel(), event.getData(), true);
                        } else {
                            plugin.logDebug(target + " doesn't exist?");
                        }
                    } else {
                        plugin.logError(message.getTarget() + " message target requires explicit target!");
                    }
                    break;
                default:
                    plugin.logError("Receiving " + message.getTarget() + " is not supported!");
            }
        } catch (IllegalArgumentException e) {
            plugin.logError("Invalid message target! " + e.getMessage());
        } catch (VersionMismatchException e) {
            plugin.getLogger().log(Level.WARNING, e.getMessage() + ". Ignoring message!");
        }
    }

    private void sendToAllWithPlayers(byte[] data, ServerInfo excludedServer) {
        sendToAll(data, false, excludedServer);
    }

    private void sendToAllAndQueue(byte[] data, ServerInfo excludedServer) {
        sendToAll(data, true, excludedServer);
    }

    private void sendToAll(byte[] data, boolean queue, ServerInfo excludedServer) {
        for (ServerInfo server : plugin.getProxy().getServers().values()) {
            if (excludedServer == null || excludedServer != server) {
                server.sendData(plugin.getMessageChannel(), data, queue);
            }
        }
    }

    @Override
    public void sendDataImplementation(String targetData, Message message) {
        byte[] messageData = message.writeToByteArray();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(message.getGroup());
        out.writeUTF(targetData);
        out.writeInt(messageData.length);
        out.write(messageData);
        byte[] dataToSend = out.toByteArray();

        ServerInfo server = getTargetServer(targetData);

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
                    server.sendData(plugin.getMessageChannel(), dataToSend);
                } else {
                    throw new UnsupportedOperationException("Could not send data to " + message.getTarget() + " as target server wasn't found from " + targetData + "!");
                }
                break;
            default:
                throw new UnsupportedOperationException("Sending to " + message.getTarget() + " is not supported!");
        }
    }
}
