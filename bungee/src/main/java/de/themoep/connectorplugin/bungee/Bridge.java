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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.BridgedCommand;
import de.themoep.connectorplugin.BridgedSuggestions;
import de.themoep.connectorplugin.LocationInfo;
import de.themoep.connectorplugin.ProxyBridgeCommon;
import de.themoep.connectorplugin.ResponseHandler;
import de.themoep.connectorplugin.connector.MessageTarget;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static de.themoep.connectorplugin.connector.Connector.PLAYER_PREFIX;

public class Bridge extends ProxyBridgeCommon<BungeeConnectorPlugin, ProxiedPlayer> implements Listener {

    private Table<String, String, BridgedCommand<?, CommandSender>> commands = HashBasedTable.create();

    public Bridge(BungeeConnectorPlugin plugin) {
        super(plugin);

        plugin.getProxy().getPluginManager().registerListener(plugin, this);

        registerMessageHandler(Action.SEND_TO_SERVER, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
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

        registerMessageHandler(Action.GET_LOCATION, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String playerName = in.readUTF();

            ProxiedPlayer player = plugin.getProxy().getPlayer(playerName);
            if (player != null && player.getServer() != null) {
                getLocation(player).thenAccept(location -> sendResponse(senderServer, id, location));
            } else {
                sendResponse(senderServer, id, "");
            }
        });

        registerMessageHandler(Action.PLAYER_COMMAND, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
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
                sendResponse(senderServer, id, false, "Could not find player " + playerName + "/" + playerId + " on this proxy to execute command " + command);
                return;
            }

            plugin.logDebug("Command '" + command + "' for player '" + playerName + "' triggered from " + senderServer);
            boolean success = plugin.getProxy().getPluginManager().dispatchCommand(player, command);

            sendResponse(player, id, success);
        });

        registerMessageHandler(Action.CONSOLE_COMMAND, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String command = in.readUTF();

            plugin.logDebug("Console command '" + command + "' triggered from " + senderServer);
            boolean success = plugin.getProxy().getPluginManager().dispatchCommand(new BridgedSender(senderServer, id), command);

            sendResponse(senderServer, id, success);
        });

        registerHandler(Action.EXECUTE_COMMAND, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String serverName = in.readUTF();
            if (!serverName.equals(plugin.getServerName())) {
                return;
            }
            String senderName = in.readUTF();
            String pluginName = in.readUTF();
            String commandName = in.readUTF();

            CommandSender sender;
            if (senderName.isEmpty()) {
                sender = plugin.getProxy().getConsole();
            } else {
                sender = plugin.getProxy().getPlayer(senderName);
            }

            if (sender == null) {
                plugin.logDebug("Could not find player " + senderName + " for execution of command " + commandName + " for plugin " + pluginName);
                return;
            }

            BridgedCommand<?, CommandSender> command = commands.get(pluginName.toLowerCase(Locale.ROOT), commandName.toLowerCase(Locale.ROOT));

            if (command == null) {
                plugin.logDebug("Could not find executor for command " + commandName + " for plugin " + pluginName);
                return;
            }

            String commandLabel = in.readUTF();
            int argsCount = in.readInt();
            String[] args = new String[argsCount];
            for (int i = 0; i < argsCount; i++) {
                args[i] = in.readUTF();
            }

            LocationInfo location = null;
            if (sender instanceof ProxiedPlayer) {
                location = LocationInfo.read(in);
            }

            try {
                if (!command.onCommand(sender, location, commandLabel, args) && command.getUsage().length() > 0) {
                    for (String line : command.getUsage().replace("<command>", commandLabel).split("\n")) {
                        sender.sendMessage(line);
                    }
                }
            } catch (Throwable ex) {
                plugin.logError("Unhandled exception executing bridged command '" + commandLabel + "' from plugin " + command.getPlugin().getName(), ex);
            }
        });
    }

    private void sendResponse(ProxiedPlayer player, long id, boolean success, String... messages) {
        sendResponse(PLAYER_PREFIX + player.getName(), id, success, messages);
    }

    @Override
    public CompletableFuture<Boolean> teleport(ProxiedPlayer player, LocationInfo location, Consumer<String>... consumer) {
        return teleport(player.getName(), location, consumer);
    }

    @Override
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
            plugin.logDebug("Could not find server " + location.getServer() + " on this proxy to teleport player " + player.getName() + " to");
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find server " + location.getServer() + " on this proxy to teleport player " + player.getName() + " to");
            }
            return future;
        }

        markTeleporting(playerName);
        future.thenAccept(success -> unmarkTeleporting(playerName));

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeLong(id);
        out.writeUTF(player.getName());
        location.write(out);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        sendData(Action.TELEPORT, MessageTarget.SERVER, server.getName(), out.toByteArray());

        sendToServerIfNecessary(player, server, future, consumer);

        return future;
    }

    @Override
    public CompletableFuture<Boolean> teleport(ProxiedPlayer player, String serverName, String worldName, Consumer<String>... consumer) {
        return teleport(player.getName(), serverName, worldName, consumer);
    }

    @Override
    public CompletableFuture<Boolean> teleport(String playerName, String serverName, String worldName, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        ProxiedPlayer player = plugin.getProxy().getPlayer(playerName);
        if (player == null) {
            plugin.logDebug("Could not find player " + playerName + " on this proxy to send to teleport to " + serverName + "/" + worldName);
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find player " + playerName + " on this proxy to teleport to " + serverName + "/" + worldName);
            }
            return future;
        }

        ServerInfo server = plugin.getProxy().getServerInfo(serverName);
        if (server == null) {
            plugin.logDebug("Could not find server " + serverName + " on this proxy to teleport player " + player.getName() + " to");
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find server " + serverName + " on this proxy to teleport player " + player.getName() + " to");
            }
            return future;
        }

        markTeleporting(playerName);
        future.thenAccept(success -> unmarkTeleporting(playerName));

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeLong(id);
        out.writeUTF(player.getName());
        out.writeUTF(serverName);
        out.writeUTF(worldName);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        sendData(Action.TELEPORT_TO_WORLD, MessageTarget.SERVER, server.getName(), out.toByteArray());

        sendToServerIfNecessary(player, server, future, consumer);

        return future;
    }

    @Override
    public CompletableFuture<Boolean> teleport(ProxiedPlayer player, ProxiedPlayer target, Consumer<String>... consumer) {
        return teleport(player.getName(), target.getName(), consumer);
    }

    @Override
    public CompletableFuture<Boolean> teleport(String playerName, String targetName, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        ProxiedPlayer player = plugin.getProxy().getPlayer(playerName);
        if (player == null) {
            plugin.logDebug("Could not find player " + playerName + " on this proxy to send to teleport to " + targetName);
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find player " + playerName + " on this proxy to teleport to " + targetName);
            }
            return future;
        }

        getServer(targetName).thenAccept(serverName -> {
            if (serverName == null || serverName.isEmpty()) {
                // Player is not online or not connected to server
                plugin.logDebug("Target player " + targetName + " is either not online or not connected to a server. (Tried to teleport " + player.getName() + " to them)");
                future.complete(false);
                for (Consumer<String> c : consumer) {
                    c.accept("Could not find target player " + targetName + " to teleport " + player.getName() + " to");
                }
                return;
            }

            ServerInfo server = plugin.getProxy().getServerInfo(serverName);
            if (server == null) {
                // Player is online but their server doesn't exist on this proxy
                plugin.logDebug("Target player " + targetName + " is online on server " + serverName + " which does not exist on this proxy! (Tried to teleport " + player.getName() + " to them)");
                future.complete(false);
                for (Consumer<String> c : consumer) {
                    c.accept("Could not find server " + serverName + " of target player " + targetName + " on " + player.getName() + "'s proxy to teleport them");
                }
                return;
            }

            markTeleporting(playerName);
            future.thenAccept(success -> unmarkTeleporting(playerName));

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            long id = RANDOM.nextLong();
            out.writeLong(id);
            out.writeUTF(player.getName());
            out.writeUTF(targetName);
            responses.put(id, new ResponseHandler.Boolean(future));
            consumers.put(id, consumer);
            sendData(Action.TELEPORT_TO_PLAYER, MessageTarget.ALL_QUEUE, out.toByteArray());

            sendToServerIfNecessary(player, server, future, consumer);
        });

        return future;
    }

    private void sendToServerIfNecessary(ProxiedPlayer player, ServerInfo server, CompletableFuture<Boolean> future, Consumer<String>... consumer) {
        if (!player.getServer().getInfo().equals(server) && plugin.getConnector().requiresPlayer() && server.getPlayers().isEmpty()) {
            plugin.logDebug("Sending '" + player.getName() + "' to server '" + server.getName() + "'");

            player.connect(server, (success, ex) -> {
                if (!success) {
                    future.complete(false);
                    if (ex != null) {
                        for (Consumer<String> c : consumer) {
                            c.accept(ex.getMessage());
                        }
                    }
                }
            });
        }
    }

    /**
     * Get the location of a player on the server
     * @param player    The player to get the location for
     * @return A future for when the location was queried
     */
    public CompletableFuture<LocationInfo> getLocation(ProxiedPlayer player) {
        return getLocation(player.getName());
    }

    /**
     * Get the server a player is connected to
     * @param player    The player to get the server for
     * @return A future for when the server was queried
     */
    public CompletableFuture<String> getServer(ProxiedPlayer player) {
        return getServer(player.getName());
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
        out.writeLong(id);
        out.writeUTF(player.getName());
        out.writeLong(player.getUniqueId().getMostSignificantBits());
        out.writeLong(player.getUniqueId().getLeastSignificantBits());
        out.writeUTF(command);
        responses.put(id, new ResponseHandler.Boolean(future));
        sendData(Action.PLAYER_COMMAND, MessageTarget.SERVER, player, out.toByteArray());
        return future;
    }

    /**
     * Register a command on a server
     * @param server    The server
     * @param command   The command to register
     */
    public void registerServerCommand(String server, BridgedCommand<?, CommandSender> command) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        write(out, command);
        sendData(Action.REGISTER_COMMAND, MessageTarget.SERVER, server, out.toByteArray());
    }

    @Override
    protected void registerServerCommands(String server) {
        for (BridgedCommand<?, CommandSender> command : commands.values()) {
            registerServerCommand(server, command);
        }
    }

    /**
     * Register a command on all servers
     * @param command   The command to register
     */
    public void registerServerCommand(BridgedCommand<? extends Plugin, CommandSender> command) {
        commands.put(command.getPlugin().getName().toLowerCase(Locale.ROOT), command.getName().toLowerCase(Locale.ROOT), command);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        write(out, command);
        sendData(Action.REGISTER_COMMAND, MessageTarget.ALL_QUEUE, out.toByteArray());

        plugin.getProxy().getPluginManager().registerCommand(command.getPlugin(), new ForwardingCommand(command.getName(), command.getPermission(), command));
        for (String alias : command.getAliases()) {
            plugin.getProxy().getPluginManager().registerCommand(command.getPlugin(), new ForwardingCommand(alias, command.getPermission(), command));
        }
    }

    private void write(ByteArrayDataOutput out, BridgedCommand<?, CommandSender> command) {
        out.writeUTF(command.getPlugin().getName());
        out.writeUTF(command.getName());
        out.writeUTF(command.getDescription() != null ? command.getDescription() : "");
        out.writeUTF(command.getUsage() != null ? command.getUsage() : "");
        out.writeUTF(command.getPermission() != null ? command.getPermission() : "");
        if (command.getPermissionMessage() != null) {
            out.writeBoolean(true);
            out.writeUTF(command.getPermissionMessage());
        } else {
            out.writeBoolean(false);
        }
        out.writeInt(command.getAliases().length);
        for (String alias : command.getAliases()) {
            out.writeUTF(alias);
        }
    }

    private class ForwardingCommand extends Command implements TabExecutor {
        private final BridgedCommand<? extends Plugin, CommandSender> command;

        public ForwardingCommand(String name, String permission, BridgedCommand<? extends Plugin, CommandSender> command) {
            super(name, permission);
            this.command = command;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer) {
                runServerPlayerCommand((ProxiedPlayer) sender, getName() + " " + String.join(" ", args));
            } else {
                command.onCommand(sender, null, getName(), args);
            }
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (command instanceof BridgedSuggestions && sender.hasPermission(command.getPermission() + ".tabcomplete." + getName())) {
                return ((BridgedSuggestions<CommandSender>) command).suggest(sender, getName(), args);
            }
            return Collections.emptySet();
        }
    }

    @EventHandler(priority = (byte) 128) // Monitor priority
    public void onPlayerJoin(ServerConnectEvent event) {
        // check if it's a fresh proxy join
        if (event.getPlayer().getServer() == null) {
            unmarkTeleporting(event.getPlayer().getName());
        }
    }

    @EventHandler(priority = (byte) 128) // Monitor priority
    public void onPlayerJoined(ServerSwitchEvent event) {
        onPlayerJoin(new PlayerInfo(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                event.getPlayer().getServer().getInfo().getName()
        ));
    }

    @EventHandler(priority = (byte) 128) // Monitor priority
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        onPlayerLeave(event.getPlayer().getName());
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
