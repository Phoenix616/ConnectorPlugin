package de.themoep.connectorplugin.connector;

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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.ConnectorPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class Connector<P extends ConnectorPlugin, R> {
    protected final P plugin;

    private Table<String, String, BiConsumer<R, byte[]>> handlers = HashBasedTable.create();

    public Connector(P plugin) {
        this.plugin = plugin;
    }

    protected void handle(String pluginName, String action, MessageTarget target, R receiver, byte[] data) {
        BiConsumer<R, byte[]> handler = handlers.get(pluginName.toLowerCase(Locale.ROOT), action);
        if (handler != null) {
            handler.accept(receiver, data);
        } else {
            plugin.logError("Plugin '" + pluginName + " did not register an action '" + action + "' but we received data with target '" + target + "' by " + receiver);
        }
    }

    /**
     * Send data to a specific target
     * @param sender    The plugin which sends the data
     * @param action    The action for which data is sent
     * @param target    Where to send data to
     * @param data      The data
     */
    public void sendData(ConnectingPlugin sender, String action, MessageTarget target, byte[] data) {
        sendData(sender, action, target, null, data);
    }

    /**
     * Send data to a specific target
     * @param sender    The plugin which sends the data
     * @param action    The action for which data is sent
     * @param target    Where to send data to
     * @param player    Additional player data to use for sending (required in case the target is {@link MessageTarget#SERVER} or {@link MessageTarget#PROXY})
     * @param data      The data
     */
    public void sendData(ConnectingPlugin sender, String action, MessageTarget target, R player, byte[] data) {
        if (target.getSource() != null && target.getSource() != plugin.getSourceType()) {
            throw new UnsupportedOperationException("Cannot send message with target " + target + " from " + plugin.getSourceType());
        }

        sendDataImplementation(sender, action, target, player, data);
    }

    protected abstract void sendDataImplementation(ConnectingPlugin sender, String action, MessageTarget target, R player, byte[] data);

    protected byte[] writeToByteArray(MessageTarget target, ConnectingPlugin sender, String action, byte[] data) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(target.name());
        out.writeUTF(sender.getName());
        out.writeUTF(action);
        out.writeShort(data.length);
        out.write(data);
        return out.toByteArray();
    }

    /**
     * Register a handler for a certain action
     * @param plugin    The plugin to register the handler for
     * @param action    The action to register (case sensitive)
     * @param handler   A BiConsumer which takes the receiving player and the data
     * @return The previously registered handler if there was one
     */
    public BiConsumer<R, byte[]> registerHandler(ConnectingPlugin plugin, String action, BiConsumer<R, byte[]> handler) {
        return handlers.put(plugin.getName().toLowerCase(Locale.ROOT), action, handler);
    }

    /**
     * Unregister a handler from a certain action
     * @param plugin    The plugin to unregister the handler of
     * @param action    The action to unregister (case sensitive)
     * @return The previously registered handler if there was one or null
     */
    public BiConsumer<R, byte[]> unregisterHandler(ConnectingPlugin plugin, String action) {
        return handlers.remove(plugin.getName().toLowerCase(Locale.ROOT), action);
    }

    /**
     * Unregister a all handlers of a certain plugin
     * @param plugin    The plugin to unregister the handlers of
     * @return The previously registered handlers if there were some or null
     */
    public Map<String, BiConsumer<R, byte[]>> unregisterHandlers(ConnectingPlugin plugin) {
        return handlers.rowMap().remove(plugin.getName().toLowerCase(Locale.ROOT));
    }
}
