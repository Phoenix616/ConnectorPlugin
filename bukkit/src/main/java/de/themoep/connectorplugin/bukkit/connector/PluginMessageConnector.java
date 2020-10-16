package de.themoep.connectorplugin.bukkit.connector;

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
import de.themoep.connectorplugin.connector.MessageTarget;
import de.themoep.connectorplugin.bukkit.BukkitConnectorPlugin;
import de.themoep.connectorplugin.connector.ConnectingPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayDeque;
import java.util.Deque;

public class PluginMessageConnector extends BukkitConnector implements PluginMessageListener, Listener {

    private Deque<byte[]> queue = new ArrayDeque<>();

    public PluginMessageConnector(BukkitConnectorPlugin plugin) {
        super(plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, plugin.getMessageChannel());
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, plugin.getMessageChannel(), this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(plugin.getMessageChannel())) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        try {
            MessageTarget target = MessageTarget.valueOf(in.readUTF());
            String senderPlugin = in.readUTF();
            String action = in.readUTF();
            short length = in.readShort();
            byte[] data = new byte[length];
            in.readFully(data);

            handle(senderPlugin, action, target, player, data);
        } catch (IllegalArgumentException e) {
            plugin.logError("Invalid message target! " + e.getMessage());
        }
    }

    @Override
    public void sendDataImplementation(ConnectingPlugin sender, String action, MessageTarget target, Player player, byte[] data) {
        byte[] dataToSend = writeToByteArray(target, sender, action, data);

        if (player != null) {
            player.sendPluginMessage(plugin, plugin.getMessageChannel(), dataToSend);
        } else if (target != MessageTarget.PROXY) {
            if (plugin.getServer().getOnlinePlayers().isEmpty()) {
                queue.add(dataToSend);
            } else {
                plugin.getServer().getOnlinePlayers().iterator().next().sendPluginMessage(plugin, plugin.getMessageChannel(), dataToSend);
            }
        } else {
            plugin.logError("Could not send data to " + target + " as player wasn't specified!");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        while (!queue.isEmpty()) {
            event.getPlayer().sendPluginMessage(plugin, plugin.getMessageChannel(), queue.remove());
        }
    }
}
