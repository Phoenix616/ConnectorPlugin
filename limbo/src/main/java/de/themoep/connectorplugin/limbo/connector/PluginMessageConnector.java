package de.themoep.connectorplugin.limbo.connector;

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
import com.loohp.limbo.events.EventHandler;
import com.loohp.limbo.events.Listener;
import com.loohp.limbo.events.player.PlayerJoinEvent;
import com.loohp.limbo.events.player.PluginMessageEvent;
import de.themoep.connectorplugin.connector.Message;
import de.themoep.connectorplugin.connector.VersionMismatchException;
import de.themoep.connectorplugin.limbo.LimboConnectorPlugin;
import net.kyori.adventure.key.Key;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

public class PluginMessageConnector extends LimboConnector implements Listener {

    public static final String CHANNEL_NAME = "bungeecord:main";
    public static final Key CHANNEL_KEY = Key.key(CHANNEL_NAME);
    private final Deque<byte[]> queue = new ArrayDeque<>();

    public PluginMessageConnector(LimboConnectorPlugin plugin) {
        super(plugin, true);
        plugin.getLimbo().getEventsManager().registerEvents(plugin, this);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.getChannel().equals(CHANNEL_NAME)) {
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            String subChannel = in.readUTF();
            if (!"ConnectorPlugin".equals(subChannel)) {
                return;
            }
            try {
                Message message = Message.fromByteArray(plugin.getGroup(plugin.getName()), in.readUTF().getBytes());
                handle(event.getPlayer(), message);
            } catch (VersionMismatchException e) {
                plugin.logError("Error while handling plugin message from " + event.getPlayer().getName(), e);
            }
        }
    }

    @Override
    protected void sendDataImplementation(String targetData, Message message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF(targetData.startsWith(SERVER_PREFIX) ? targetData.substring(SERVER_PREFIX.length()) : "ALL");
        out.writeUTF("ConnectorPlugin");

        byte[] messageBytes = message.writeToByteArray();
        out.writeShort(messageBytes.length);
        out.write(messageBytes);

        byte[] data = out.toByteArray();

        if (!plugin.getLimbo().getPlayers().isEmpty()) {
            try {
                plugin.getLimbo().getPlayers().iterator().next().sendPluginMessage(CHANNEL_KEY, data);
            } catch (IOException e) {
                plugin.logError("Error while sending plugin message", e);
            }
        } else {
            queue.add(data);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        while (!queue.isEmpty()) {
            try {
                event.getPlayer().sendPluginMessage(CHANNEL_KEY, queue.remove());
            } catch (IOException e) {
                plugin.logError("Error while sending queued plugin message", e);
            }
        }
    }
}
