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
import de.themoep.connectorplugin.ConnectorPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class Connector<P extends ConnectorPlugin, R> {
    protected final P plugin;

    /**
     * Use for prefixing the ID of the proxy
     */
    public static final String PROXY_ID_PREFIX = "proxy:";

    /**
     * Used for identifying the target type
     */
    public static final String SERVER_PREFIX = "server:";
    public static final String PLAYER_PREFIX = "player:";

    private final boolean requiresPlayer;

    private Table<String, String, BiConsumer<R, byte[]>> handlers = HashBasedTable.create();

    public Connector(P plugin, boolean requiresPlayer) {
        this.plugin = plugin;
        this.requiresPlayer = requiresPlayer;
    }

    protected void handle(String receiver, Message message) {
        if (isThis(receiver)) {
            handle(getReceiver(receiver), message);
        }
    }

    protected void handle(R receiver, Message message) {
        if (message.getTarget().getType() != plugin.getSourceType()) {
            return;
        }

        switch (message.getTarget()) {
            case OTHERS_WITH_PLAYERS:
            case OTHERS_QUEUE:
            case OTHER_PROXIES:
                if (message.getSendingServer().equals(plugin.getServerName())) {
                    return;
                }
                break;
            case PROXY:
                if (receiver == null) {
                    return;
                }
                break;
        }

        BiConsumer<R, byte[]> handler = handlers.get(message.getSendingPlugin().toLowerCase(Locale.ROOT), message.getAction());
        if (handler != null) {
            handler.accept(receiver, message.getData());
        } else {
            plugin.logDebug("Plugin '" + message.getSendingPlugin() + " did not register an action '" + message.getAction() + "' but we received data with target '" + message.getTarget() + "' by " + receiver);
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
        sendData(sender, action, target, (String) null, data);
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
        sendDataInternal(sender, action, target, player, data);
    }

    /**
     * Send data to a specific target
     * @param sender        The plugin which sends the data
     * @param action        The action for which data is sent
     * @param target        Where to send data to
     * @param targetData    Additional data to use for sending (required in case the target is {@link MessageTarget#SERVER})
     * @param data          The data
     */
    public void sendData(ConnectingPlugin sender, String action, MessageTarget target, String targetData, byte[] data) {
        sendDataInternal(sender, action, target, targetData, data);
    }

    public void sendDataInternal(ConnectingPlugin sender, String action, MessageTarget target, Object targetData, byte[] data) {
        if (target.getSource() != null && target.getSource() != plugin.getSourceType()) {
            throw new UnsupportedOperationException("Cannot send message with target " + target + " from " + plugin.getSourceType());
        }

        sendDataImplementation(targetData, new Message(target, plugin.getServerName(), sender.getName(), action, data));
    }

    protected abstract void sendDataImplementation(Object targetData, Message message);

    protected boolean hasPrefix(String target) {
        return target.startsWith(SERVER_PREFIX) || target.startsWith(PLAYER_PREFIX);
    }

    protected R getReceiver(String name) {
        if (name.startsWith(SERVER_PREFIX)) {
            return null;
        }
        if (name.startsWith(PLAYER_PREFIX)) {
            name = name.substring(PLAYER_PREFIX.length());
        }
        return getReceiverImplementation(name);
    }

    protected abstract R getReceiverImplementation(String name);

    protected boolean isThis(String receiver) {
        if (receiver == null || receiver.isEmpty()) {
            return true;
        } else if (receiver.startsWith(SERVER_PREFIX)) {
            return plugin.getServerName().equals(receiver.substring(SERVER_PREFIX.length()));
        } else if (receiver.startsWith(PLAYER_PREFIX)) {
            R player = getReceiver(receiver);
            return player != null;
        }
        return false;
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

    /**
     * Whether this connector requires a player on the target server. (Mostly for plugin message usage)
     * @return Whether this connector requires at least one player on the target server
     */
    public boolean requiresPlayer() {
        return requiresPlayer;
    }

    public void close() {};
}
