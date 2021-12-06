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
import com.velocitypowered.api.command.InvocableCommand;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.themoep.connectorplugin.BridgeCommon;
import de.themoep.connectorplugin.BridgedCommand;
import de.themoep.connectorplugin.LocationInfo;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Bridge extends BridgeCommon<VelocityConnectorPlugin> {

    private Table<String, String, BridgedCommand<?, CommandSource>> commands = HashBasedTable.create();

    public Bridge(VelocityConnectorPlugin plugin) {
        super(plugin);

        plugin.getConnector().registerHandler(plugin, Action.STARTED, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();

            for (BridgedCommand<?, CommandSource> command : commands.values()) {
                registerServerCommand(senderServer, command);
            }
        });

        plugin.getConnector().registerHandler(plugin, Action.SEND_TO_SERVER, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
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
                    result.getReasonComponent().ifPresent(c -> sendResponseMessage(senderServer, id, LegacyComponentSerializer.legacyAmpersand().serialize(c)));
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

        plugin.getConnector().registerHandler(plugin, Action.TELEPORT_TO_PLAYER, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();
            String targetName = in.readUTF();

            teleport(playerName, targetName, messages -> sendResponseMessage(senderServer, id, messages))
                    .thenAccept(success -> sendResponse(senderServer, id, success));
        });

        plugin.getConnector().registerHandler(plugin, Action.PLAYER_COMMAND, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String serverName = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();
            UUID playerId = new UUID(in.readLong(), in.readLong());
            String command = in.readUTF();

            Player player = plugin.getProxy().getPlayer(playerId)
                    .orElseGet(() -> plugin.getProxy().getPlayer(playerName).orElse(null));
            if (player == null) {
                plugin.logDebug("Could not find player " + playerName + "/" + playerId + " on this proxy to execute command " + command);
                sendResponse(serverName, id, false, "Could not find player " + playerName + "/" + playerId + " on this proxy to execute command " + command);
                return;
            }

            plugin.logDebug("Command '" + command + "' for player '" + playerName + "' triggered from " + serverName);
            Player finalPlayer = player;
            plugin.getProxy().getCommandManager().executeAsync(finalPlayer, command)
                    .thenAccept(success -> sendResponse(serverName, id, finalPlayer, success));
        });

        plugin.getConnector().registerHandler(plugin, Action.CONSOLE_COMMAND, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String command = in.readUTF();

            plugin.logDebug("Console command '" + command + "' triggered from " + senderServer);
            plugin.getProxy().getCommandManager().executeAsync(new BridgedSender(senderServer, id), command)
                    .thenAccept(success -> sendResponse(senderServer, id, success));
        });

        plugin.getConnector().registerHandler(plugin, Action.EXECUTE_COMMAND, (receiver, data) -> {
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

    private void sendResponse(String server, long id, Player player, boolean success, String... messages) {
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

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player.getUsername());
        location.write(out);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        plugin.getConnector().sendData(plugin, Action.TELEPORT, MessageTarget.SERVER, server.getServerInfo().getName(), out.toByteArray());

        return future;
    }

    /**
     * Teleport a player to a certain other player in the network
     * @param playerName    The name of the player to teleport
     * @param targetName    The name of the target player
     * @param consumer      Details about the teleport
     * @return A future about whether the player could be teleported
     */
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

        Player target = plugin.getProxy().getPlayer(targetName).orElse(null);
        if (target == null) {
            plugin.logDebug("Could not find target player " + targetName + " on this proxy to teleport " + player.getUsername() + " to");
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept("Could not find target player " + targetName + " on this proxy to teleport " + player.getUsername() + " to");
            }
            return future;
        }

        if (!target.getCurrentServer().isPresent()) {
            plugin.logDebug(target.getUsername() + " is not currently connected to any server.");
            future.complete(false);
            for (Consumer<String> c : consumer) {
                c.accept(target.getUsername() + " is not currently connected to any server.");
            }
            return future;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player.getUsername());
        out.writeUTF(target.getUsername());
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        plugin.getConnector().sendData(plugin, Action.TELEPORT_TO_PLAYER, MessageTarget.ALL_QUEUE, out.toByteArray());

        return future;
    }

    /**
     * Get the location of a player on the server
     * @param player    The player to get the location for
     * @return A future for when the location was queried
     */
    public CompletableFuture<LocationInfo> getLocation(Player player) {
        CompletableFuture<LocationInfo> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player.getUsername());
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
    public CompletableFuture<Boolean> runServerPlayerCommand(Player player, String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player.getUsername());
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

    /**
     * Register a command on a server
     * @param server    The server
     * @param command   The command to register
     */
    public void registerServerCommand(String server, BridgedCommand<?, CommandSource> command) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(plugin.getServerName());
        write(out, command);
        plugin.getConnector().sendData(plugin, Action.REGISTER_COMMAND, MessageTarget.SERVER, server, out.toByteArray());
    }

    /**
     * Register a command on all servers
     * @param command   The command to register
     */
    public void registerServerCommand(BridgedCommand<?, CommandSource> command) {
        commands.put(command.getPlugin().getName().toLowerCase(Locale.ROOT), command.getName().toLowerCase(Locale.ROOT), command);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(plugin.getServerName());
        write(out, command);
        plugin.getConnector().sendData(plugin, Action.REGISTER_COMMAND, MessageTarget.ALL_QUEUE, out.toByteArray());

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

    private class ForwardingCommand implements SimpleCommand, CommandMeta {
        private final BridgedCommand<?, CommandSource> command;

        public ForwardingCommand(BridgedCommand<?, CommandSource> command) {
            this.command = command;
        }

        @Override
        public void execute(Invocation invocation) {
            if (invocation.source() instanceof Player) {
                ((Player) invocation.source()).spoofChatInput("/" + invocation.alias() + " " + String.join(" ", invocation.arguments()));
            } else {
                command.onCommand(invocation.source(), null, invocation.alias(), invocation.arguments());
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            if (command instanceof InvocableCommand && invocation.source().hasPermission(command.getPermission() + ".tabcomplete." + invocation.alias())) {
                return ((InvocableCommand<Invocation>) command).suggest(invocation);
            }
            return Collections.emptyList();
        }

        @Override
        public Collection<CommandNode<CommandSource>> getHints() {
            if (command instanceof CommandMeta) {
                return ((CommandMeta) command).getHints();
            }
            return Collections.emptySet();
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
