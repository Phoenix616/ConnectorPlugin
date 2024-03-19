package de.themoep.connectorplugin.velocity;

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
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.themoep.connectorplugin.BridgedCommand;
import de.themoep.connectorplugin.BridgedSuggestions;
import de.themoep.connectorplugin.LocationInfo;
import de.themoep.connectorplugin.ProxyBridgeCommon;
import de.themoep.connectorplugin.ResponseHandler;
import de.themoep.connectorplugin.connector.MessageTarget;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static de.themoep.connectorplugin.connector.Connector.PLAYER_PREFIX;

public class Bridge extends ProxyBridgeCommon<VelocityConnectorPlugin, Player> {

    private Table<String, String, BridgedCommand<?, CommandSource>> commands = HashBasedTable.create();

    public Bridge(VelocityConnectorPlugin plugin) {
        super(plugin);

        plugin.getProxy().getEventManager().register(plugin, this);

        registerMessageHandler(Action.SEND_TO_SERVER, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String playerName = in.readUTF();
            String targetServer = in.readUTF();

            Player player = plugin.getProxy().getPlayer(playerName).orElse(null);
            if (player == null) {
                plugin.logDebug("Could not find player " + playerName + " on this proxy to send to server " + targetServer);
                sendResponse(senderServer, id, false, "Could not find player " + playerName + " on this proxy to send to server " + targetServer);
                return;
            }

            RegisteredServer server = plugin.getProxy().getServer(targetServer).orElse(null);
            if (server == null) {
                plugin.logDebug("Could not find server " + targetServer + " on this proxy to send player " + playerName + " to");
                sendResponse(senderServer, id, false, "Could not find server " + targetServer + " on this proxy to send player " + playerName + " to");
                return;
            }

            if (!player.getCurrentServer().isPresent() || !player.getCurrentServer().get().getServer().equals(server)) {
                plugin.logDebug("Sending '" + playerName + "' to server '" + targetServer + "'. Triggered from " + senderServer);

                player.createConnectionRequest(server).connect().thenAccept(result -> {
                    sendResponse(senderServer, id, result.isSuccessful());
                    result.getReasonComponent().ifPresent(c -> sendResponseMessage(senderServer, id, LegacyComponentSerializer.legacySection().serialize(c)));
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

            Optional<Player> player = plugin.getProxy().getPlayer(playerName);
            if (player.isPresent() && player.get().getCurrentServer().isPresent()) {
                getLocation(player.get()).thenAccept(location -> sendResponse(senderServer, id, location));
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

            Player player = plugin.getProxy().getPlayer(playerId)
                    .orElseGet(() -> plugin.getProxy().getPlayer(playerName).orElse(null));
            if (player == null) {
                plugin.logDebug("Could not find player " + playerName + "/" + playerId + " on this proxy to execute command " + command);
                sendResponse(senderServer, id, false, "Could not find player " + playerName + "/" + playerId + " on this proxy to execute command " + command);
                return;
            }

            plugin.logDebug("Command '" + command + "' for player '" + playerName + "' triggered from " + senderServer);
            Player finalPlayer = player;
            plugin.getProxy().getCommandManager().executeAsync(finalPlayer, command)
                    .thenAccept(success -> sendResponse(finalPlayer, id, success));
        });

        registerMessageHandler(Action.CONSOLE_COMMAND, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String command = in.readUTF();

            plugin.logDebug("Console command '" + command + "' triggered from " + senderServer);
            plugin.getProxy().getCommandManager().executeAsync(new BridgedSender(senderServer, id), command)
                    .thenAccept(success -> sendResponse(senderServer, id, success));
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

            CommandSource sender;
            if (senderName.isEmpty()) {
                sender = plugin.getProxy().getConsoleCommandSource();
            } else {
                sender = plugin.getProxy().getPlayer(senderName).orElse(null);
            }

            if (sender == null) {
                plugin.logDebug("Could not find player " + senderName + " for execution of command " + commandName + " for plugin " + pluginName);
                return;
            }

            BridgedCommand<?, CommandSource> command = commands.get(pluginName.toLowerCase(Locale.ROOT), commandName.toLowerCase(Locale.ROOT));

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
            if (sender instanceof Player) {
                location = LocationInfo.read(in);
            }

            try {
                if (!command.onCommand(sender, location, commandLabel, args) && command.getUsage().length() > 0) {
                    for (String line : command.getUsage().replace("<command>", commandLabel).split("\n")) {
                        sender.sendMessage(Component.text(line));
                    }
                }
            } catch (Throwable ex) {
                plugin.logError("Unhandled exception executing bridged command '" + commandLabel + "' from plugin " + command.getPlugin().getName(), ex);
            }
        });
    }

    private void sendResponse(Player player, long id, boolean success, String... messages) {
        sendResponse(PLAYER_PREFIX + player, id, success, messages);
    }

    @Override
    public CompletableFuture<Boolean> teleport(Player player, LocationInfo location, Consumer<String>... consumer) {
        return teleport(player.getUsername(), location, consumer);
    }

    @Override
    public CompletableFuture<Boolean> teleport(String playerName, LocationInfo location, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Player player = plugin.getProxy().getPlayer(playerName).orElse(null);
        if (player == null) {
            plugin.logDebug("Could not find player " + playerName + " on this proxy to send to teleport to " + location);
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find player " + playerName + " on this proxy to teleport to " + location);
            }
            return future;
        }

        RegisteredServer server = plugin.getProxy().getServer(location.getServer()).orElse(null);
        if (server == null) {
            plugin.logDebug("Could not find server " + location.getServer() + " on this proxy to teleport player " + player.getUsername() + " to");
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find server " + location.getServer() + " on this proxy to teleport player " + player.getUsername() + " to");
            }
            return future;
        }

        markTeleporting(playerName);
        future.thenAccept(success -> unmarkTeleporting(playerName));

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeLong(id);
        out.writeUTF(player.getUsername());
        location.write(out);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        sendData(Action.TELEPORT, MessageTarget.SERVER, server.getServerInfo().getName(), out.toByteArray());

        sendToServerIfNecessary(player, server, future, consumer);

        return future;
    }

    @Override
    public CompletableFuture<Boolean> teleport(Player player, String serverName, String worldName, Consumer<String>... consumer) {
        return teleport(player.getUsername(), serverName, worldName, consumer);
    }

    @Override
    public CompletableFuture<Boolean> teleport(String playerName, String serverName, String worldName, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Player player = plugin.getProxy().getPlayer(playerName).orElse(null);
        if (player == null) {
            plugin.logDebug("Could not find player " + playerName + " on this proxy to send to teleport to " + serverName + "/" + worldName);
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find player " + playerName + " on this proxy to teleport to " + serverName + "/" + worldName);
            }
            return future;
        }

        RegisteredServer server = plugin.getProxy().getServer(serverName).orElse(null);
        if (server == null) {
            plugin.logDebug("Could not find server " + serverName + " on this proxy to teleport player " + player.getUsername() + " to");
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find server " + serverName + " on this proxy to teleport player " + player.getUsername() + " to");
            }
            return future;
        }

        markTeleporting(playerName);
        future.thenAccept(success -> unmarkTeleporting(playerName));

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeLong(id);
        out.writeUTF(player.getUsername());
        out.writeUTF(serverName);
        out.writeUTF(worldName);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        sendData(Action.TELEPORT_TO_WORLD, MessageTarget.SERVER, server.getServerInfo().getName(), out.toByteArray());

        sendToServerIfNecessary(player, server, future, consumer);

        return future;
    }

    @Override
    public CompletableFuture<Boolean> teleport(Player player, Player target, Consumer<String>... consumer) {
        return teleport(player.getUsername(), target.getUsername(), consumer);
    }

    @Override
    public CompletableFuture<Boolean> teleport(String playerName, String targetName, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Player player = plugin.getProxy().getPlayer(playerName).orElse(null);
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
                plugin.logDebug("Target player " + targetName + " is either not online or not connected to a server. (Tried to teleport " + player.getUsername() + " to them)");
                future.complete(false);
                for (Consumer<String> c : consumer) {
                    c.accept("Could not find target player " + targetName + " to teleport " + player.getUsername() + " to");
                }
                return;
            }

            Optional<RegisteredServer> server = plugin.getProxy().getServer(serverName);
            if (!server.isPresent()) {
                // Player is online but their server doesn't exist on this proxy
                plugin.logDebug("Target player " + targetName + " is online on server " + serverName + " which does not exist on this proxy! (Tried to teleport " + player.getUsername() + " to them)");
                future.complete(false);
                for (Consumer<String> c : consumer) {
                    c.accept("Could not find server " + serverName + " of target player " + targetName + " on " + player.getUsername() + "'s proxy to teleport them");
                }
                return;
            }

            markTeleporting(playerName);
            future.thenAccept(success -> unmarkTeleporting(playerName));

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            long id = RANDOM.nextLong();
            out.writeLong(id);
            out.writeUTF(player.getUsername());
            out.writeUTF(targetName);
            responses.put(id, new ResponseHandler.Boolean(future));
            consumers.put(id, consumer);
            sendData(Action.TELEPORT_TO_PLAYER, MessageTarget.ALL_QUEUE, out.toByteArray());

            sendToServerIfNecessary(player, server.get(), future, consumer);
        });

        return future;
    }

    private void sendToServerIfNecessary(Player player, RegisteredServer server, CompletableFuture<Boolean> future, Consumer<String>... consumer) {
        if ((!player.getCurrentServer().isPresent() || !player.getCurrentServer().get().getServer().equals(server))
                && plugin.getConnector().requiresPlayer() && server.getPlayersConnected().isEmpty()) {
            plugin.logDebug("Sending '" + player.getUsername() + "' to server '" + server.getServerInfo().getName() + "'");

            player.createConnectionRequest(server).connect().thenAccept(result -> {
                if (!result.isSuccessful()) {
                    future.complete(false);
                    result.getReasonComponent().ifPresent(component -> {
                        String reason = LegacyComponentSerializer.legacySection().serialize(component);
                        for (Consumer<String> c : consumer) {
                            c.accept(reason);
                        }
                    });
                }
            });
        }
    }

    /**
     * Get the location of a player on the server
     * @param player    The player to get the location for
     * @return A future for when the location was queried
     */
    public CompletableFuture<LocationInfo> getLocation(Player player) {
        return getLocation(player.getUsername());
    }

    /**
     * Get the server a player is connected to
     * @param player    The player to get the server for
     * @return A future for when the server was queried
     */
    public CompletableFuture<String> getServer(Player player) {
        return getServer(player.getUsername());
    }

    /**
     * Run a command for a player on the server they are connected to.
     * The player needs to have access to that command!
     * @param player    The player to run the command for
     * @param command   The command to run
     * @return A future for whether the command was run successfully
     */
    public CompletableFuture<Boolean> runServerPlayerCommand(Player player, String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeLong(id);
        out.writeUTF(player.getUsername());
        out.writeLong(player.getUniqueId().getMostSignificantBits());
        out.writeLong(player.getUniqueId().getLeastSignificantBits());
        out.writeUTF(command);
        responses.put(id, new ResponseHandler.Boolean(future));
        sendData(Action.PLAYER_COMMAND, MessageTarget.SERVER, player, out.toByteArray());
        return future;
    }

    @Override
    protected void registerServerCommands(String server) {
        for (BridgedCommand<?, CommandSource> command : commands.values()) {
            registerServerCommand(server, command);
        }
    }

    /**
     * Register a command on a server
     * @param server    The server
     * @param command   The command to register
     */
    public void registerServerCommand(String server, BridgedCommand<?, CommandSource> command) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        write(out, command);
        sendData(Action.REGISTER_COMMAND, MessageTarget.SERVER, server, out.toByteArray());
    }

    /**
     * Register a command on all servers
     * @param command   The command to register
     */
    public void registerServerCommand(BridgedCommand<?, CommandSource> command) {
        commands.put(command.getPlugin().getName().toLowerCase(Locale.ROOT), command.getName().toLowerCase(Locale.ROOT), command);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        write(out, command);
        sendData(Action.REGISTER_COMMAND, MessageTarget.ALL_QUEUE, out.toByteArray());

        ForwardingCommand mainCommand = new ForwardingCommand(command);
        plugin.getProxy().getCommandManager().register(mainCommand, mainCommand);
    }

    private void write(ByteArrayDataOutput out, BridgedCommand<?, CommandSource> command) {
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

    @Subscribe(order = PostOrder.LAST)
    public void onPlayerJoin(ServerPreConnectEvent event) {
        // check if it's a fresh proxy join
        if (!event.getPlayer().getCurrentServer().isPresent()) {
            unmarkTeleporting(event.getPlayer().getUsername());
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPlayerJoined(ServerPostConnectEvent event) {
        event.getPlayer().getCurrentServer().ifPresent(server -> {
            onPlayerJoin(new PlayerInfo(
                    event.getPlayer().getUniqueId(),
                    event.getPlayer().getUsername(),
                    server.getServerInfo().getName()
            ));
        });
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPlayerQuit(DisconnectEvent event) {
        onPlayerLeave(event.getPlayer().getUsername());
    }

    private class ForwardingCommand implements SimpleCommand, CommandMeta {
        private final BridgedCommand<?, CommandSource> command;

        public ForwardingCommand(BridgedCommand<?, CommandSource> command) {
            this.command = command;
        }

        @Override
        public void execute(Invocation invocation) {
            if (invocation.source() instanceof Player) {
                runServerPlayerCommand((Player) invocation.source(), invocation.alias() + " " + String.join(" ", invocation.arguments()));
            } else {
                command.onCommand(invocation.source(), null, invocation.alias(), invocation.arguments());
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            if (command instanceof BridgedSuggestions && invocation.source().hasPermission(command.getPermission() + ".tabcomplete." + invocation.alias())) {
                return ((BridgedSuggestions<CommandSource>) command).suggest(invocation.source(), invocation.alias(), invocation.arguments());
            }
            return Collections.emptyList();
        }

        @Override
        public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
            if (command instanceof BridgedSuggestions && invocation.source().hasPermission(command.getPermission() + ".tabcomplete." + invocation.alias())) {
                return ((BridgedSuggestions<CommandSource>) command).suggestAsync(invocation.source(), invocation.alias(), invocation.arguments());
            }
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        @Override
        public Collection<CommandNode<CommandSource>> getHints() {
            if (command instanceof CommandMeta) {
                return ((CommandMeta) command).getHints();
            }
            return Collections.emptySet();
        }

        @Override
        public Object getPlugin() {
            return plugin;
        }

        @Override
        public Collection<String> getAliases() {
            List<String> aliases = new ArrayList<>();
            aliases.add(command.getName());
            Collections.addAll(aliases, command.getAliases());
            return aliases;
        }
    }

    private class BridgedSender implements CommandSource {
        private final String serverName;
        private final long id;

        public BridgedSender(String serverName, long id) {
            this.serverName = serverName;
            this.id = id;
        }

        @Override
        public Tristate getPermissionValue(String permission) {
            return Tristate.TRUE;
        }

        @Override
        public void sendMessage(@NotNull ComponentLike message) {
            CommandSource.super.sendMessage(message);
        }

        @Override
        public void sendMessage(@NotNull Identity source, @NotNull Component message, @NotNull MessageType type) {
            sendResponseMessage(serverName, id, LegacyComponentSerializer.legacySection().serialize(message));
        }

        @Override
        public void sendActionBar(@NotNull Component message) {
            sendResponseMessage(serverName, id, LegacyComponentSerializer.legacySection().serialize(message));
        }

        @Override
        public void showTitle(@NotNull Title title) {
            sendResponseMessage(serverName, id, LegacyComponentSerializer.legacySection().serialize(title.title()) + "\n" + LegacyComponentSerializer.legacySection().serialize(title.subtitle()));
        }
    }
}
