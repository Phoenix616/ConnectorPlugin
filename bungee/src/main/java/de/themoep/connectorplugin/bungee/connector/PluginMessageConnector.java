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
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.MessageTarget;
import de.themoep.connectorplugin.bungee.BungeeConnectorPlugin;
import de.themoep.connectorplugin.connector.ConnectingPlugin;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PluginMessageConnector extends BungeeConnector implements Listener {

    public PluginMessageConnector(BungeeConnectorPlugin plugin) {
        super(plugin);
        plugin.getProxy().registerChannel(plugin.getMessageChannel());
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(plugin.getMessageChannel())) {
            return;
        }

        if (event.getSender() instanceof ProxiedPlayer) {
            event.setCancelled(true);
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        try {
            MessageTarget target = MessageTarget.valueOf(in.readUTF());
            switch (target) {
                case ALL_WITH_PLAYERS:
                    sendToAllWithPlayers(event.getData());
                    break;
                case ALL_QUEUE:
                    sendToAllAndQueue(event.getData());
                    break;
                case CURRENT:
                    String plugin = in.readUTF();
                    String action = in.readUTF();
                    short length = in.readShort();
                    byte[] data = new byte[length];
                    in.readFully(data);

                    handle(plugin, action, target, (ProxiedPlayer) event.getReceiver(), data);
                    break;
            }
        } catch (IllegalArgumentException e) {
            plugin.logError("Invalid message target! " + e.getMessage());
        }
    }

    private void sendToAllWithPlayers(byte[] data) {
        sendToAll(data, false);
    }

    private void sendToAllAndQueue(byte[] data) {
        sendToAll(data, true);
    }

    private void sendToAll(byte[] data, boolean queue) {
        for (ServerInfo server : plugin.getProxy().getServers().values()) {
            server.sendData(plugin.getMessageChannel(), data, queue);
        }
    }

    @Override
    public void sendData(ConnectingPlugin sender, String action, MessageTarget target, ProxiedPlayer player, byte[] data) {
        byte[] dataToSend = writeToByteArray(target, sender, action, data);

        switch (target) {
            case ALL_WITH_PLAYERS:
                sendToAllWithPlayers(dataToSend);
                break;
            case ALL_QUEUE:
                sendToAllAndQueue(dataToSend);
                break;
            case CURRENT:
                if (player != null) {
                    player.getServer().sendData(plugin.getMessageChannel(), dataToSend);
                } else {
                    throw new UnsupportedOperationException("Could not send data to " + target + " as player wasn't specified!");
                }
                break;
            default:
                throw new UnsupportedOperationException("Sending to " + target + " is not supported!");
        }
    }
}
