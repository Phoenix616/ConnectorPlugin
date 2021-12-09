package de.themoep.connectorplugin.bukkit;

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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.BridgeCommon;
import de.themoep.connectorplugin.LocationInfo;
import de.themoep.connectorplugin.ResponseHandler;
import de.themoep.connectorplugin.connector.MessageTarget;
import io.papermc.lib.PaperLib;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static de.themoep.connectorplugin.connector.Connector.PLAYER_PREFIX;
import static de.themoep.connectorplugin.connector.Connector.PROXY_ID_PREFIX;

public class Bridge extends BridgeCommon<BukkitConnectorPlugin> implements Listener {

    private CommandMap commandMap = null;

    private Cache<String, LoginRequest> loginRequests = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    public Bridge(BukkitConnectorPlugin plugin) {
        super(plugin);

        try {
            Field commandMapField = SimplePluginManager.class.getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(plugin.getServer().getPluginManager());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getConnector().registerHandler(plugin, Action.TELEPORT, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();
            LocationInfo location = LocationInfo.read(in);
            if (!location.getServer().equals(plugin.getServerName())) {
                return;
            }

            if (plugin.getServer().getWorld(location.getWorld()) == null) {
                sendResponse(senderServer, id, false, "No world with the name " + location.getWorld() + " exists on the server " + location.getServer() + "!");
                return;
            }

            Player player = plugin.getServer().getPlayer(playerName);
            if (player != null) {
                PaperLib.teleportAsync(player, toBukkit(location)).whenComplete((success, ex) -> {
                    sendResponse(senderServer, id, success, success ? "Player teleported!" : "Unable to teleport " + (ex != null ? ex.getMessage() : ""));
                });
            } else {
                loginRequests.put(playerName.toLowerCase(Locale.ROOT), new LocationTeleportRequest(senderServer, id, location));
                if (!plugin.getConnector().requiresPlayer() || !plugin.getServer().getOnlinePlayers().isEmpty()) {
                    plugin.getBridge().sendToServer(playerName, location.getServer(),
                            messages -> sendResponseMessage(senderServer, id, messages)
                    ).whenComplete((success, ex) -> {
                        sendResponse(senderServer, id, success, success ? "Player teleported!" : "Unable to teleport " + (ex != null ? ex.getMessage() : ""));
                    });
                }
            }
        });

        plugin.getConnector().registerHandler(plugin, Action.TELEPORT_TO_WORLD, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();
            String serverName = in.readUTF();
            if (!serverName.equals(plugin.getServerName())) {
                return;
            }

            String worldName = in.readUTF();

            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                sendResponse(senderServer, id, false, "No world with the name " + worldName + " exists on the server!");
                return;
            }

            Player player = plugin.getServer().getPlayer(playerName);
            if (player != null) {
                PaperLib.teleportAsync(player, world.getSpawnLocation()).whenComplete((success, ex) -> {
                    sendResponse(senderServer, id, success, success ? "Player teleported to spawn of " + worldName + "!" : "Unable to teleport " + (ex != null ? ex.getMessage() : ""));
                });
            } else {
                loginRequests.put(playerName.toLowerCase(Locale.ROOT), new LocationTeleportRequest(senderServer, id, fromBukkit(world.getSpawnLocation())));
                if (!plugin.getConnector().requiresPlayer() || !plugin.getServer().getOnlinePlayers().isEmpty()) {
                    plugin.getBridge().sendToServer(playerName, serverName,
                            messages -> sendResponseMessage(senderServer, id, messages)
                    ).whenComplete((success, ex) -> {
                        sendResponse(senderServer, id, success, success ? "Player teleported to spawn of " + worldName + "!" : "Unable to teleport " + (ex != null ? ex.getMessage() : ""));
                    });
                }
            }
        });

        plugin.getConnector().registerHandler(plugin, Action.TELEPORT_TO_PLAYER, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();
            String targetName = in.readUTF();

            Player target = plugin.getServer().getPlayer(targetName);
            if (target != null) {
                Player player = plugin.getServer().getPlayer(playerName);
                if (player != null) {
                        PaperLib.teleportAsync(player, target.getLocation()).whenComplete((success, ex) -> {
                            sendResponse(senderServer, id, success, success ? "Player teleported!" : "Unable to teleport " + (ex != null ? ex.getMessage() : ""));
                        });
                } else {
                    loginRequests.put(playerName.toLowerCase(Locale.ROOT), new PlayerTeleportRequest(senderServer, id, targetName));
                    if (!plugin.getConnector().requiresPlayer() || !plugin.getServer().getOnlinePlayers().isEmpty()) {
                        plugin.getBridge().sendToServer(playerName, plugin.getServerName(),
                                messages -> sendResponseMessage(senderServer, id, messages)
                        ).whenComplete((success, ex) -> {
                            sendResponse(senderServer, id, success, success ? "Player teleported!" : "Unable to teleport " + (ex != null ? ex.getMessage() : ""));
                        });
                    }
                }
            }
        });

        plugin.getConnector().registerHandler(plugin, Action.GET_LOCATION, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();

            Player player = plugin.getServer().getPlayer(playerName);
            if (player != null) {
                sendResponse(senderServer, id, fromBukkit(player.getLocation()));
            } else {
                sendResponse(senderServer, id, (LocationInfo) null);
            }
        });

        plugin.getConnector().registerHandler(plugin, Action.PLAYER_COMMAND, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            long id = in.readLong();
            String playerName = in.readUTF();
            UUID playerId = new UUID(in.readLong(), in.readLong());
            String command = in.readUTF();

            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                player = plugin.getServer().getPlayer(playerName);
            }
            if (player == null) {
                plugin.logDebug("Could not find player " + playerName + "/" + playerId + " on this server to execute command " + command);
                sendResponse(senderServer, id, false, "Could not find player " + playerName + "/" + playerId + " on this server to execute command " + command);
                return;
            }

            plugin.logDebug("Command '" + command + "' for player '" + playerName + "' triggered from " + senderServer);
            boolean success = plugin.getServer().dispatchCommand(player, command);

            sendResponse(senderServer, id, success);
        });

        plugin.getConnector().registerHandler(plugin, Action.CONSOLE_COMMAND, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            String targetServer = in.readUTF();
            if (targetServer.startsWith("p:")) {
                Player player = plugin.getServer().getPlayer(targetServer.substring(2));
                if (player == null) {
                    return;
                }
            } else if (!targetServer.equals(plugin.getServerName())) {
                return;
            }
            long id = in.readLong();
            String command = in.readUTF();

            plugin.logDebug("Console command '" + command + "' triggered from " + senderServer);
            boolean success = plugin.getServer().dispatchCommand(new BridgedSender(senderServer, id), command);

            sendResponse(senderServer, id, success);
        });

        plugin.getConnector().registerHandler(plugin, Action.REGISTER_COMMAND, (receiver, data) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String senderServer = in.readUTF();
            String pluginName = in.readUTF();
            String name = in.readUTF();

            if (commandMap != null) {
                String description = in.readUTF();
                String usage = in.readUTF();
                String permission = in.readUTF();
                String permissionMessage = in.readBoolean() ? in.readUTF() : null;
                int aliasCount = in.readInt();
                List<String> aliases = new ArrayList<>();
                for (int i = 0; i < aliasCount; i++) {
                    aliases.add(in.readUTF());
                }
                commandMap.register(pluginName, new BridgedCommandExecutor(senderServer, pluginName, name, description, usage, aliases, permission, permissionMessage));
                plugin.logDebug("Registered command " + name + " by plugin " + pluginName + " from " + senderServer);
            } else {
                plugin.logError("Unable to register proxy command " + name + " for plugin " + pluginName + " due to missing command map access!");
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

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(plugin.getServerName());
        plugin.getConnector().sendData(plugin, Action.STARTED, MessageTarget.ALL_PROXIES, out.toByteArray());
    }

    @EventHandler
    public void onSpawnLocationEvent(PlayerSpawnLocationEvent event) {
        LoginRequest request = loginRequests.getIfPresent(event.getPlayer().getName().toLowerCase(Locale.ROOT));
        if (request != null) {
            loginRequests.invalidate(event.getPlayer().getName().toLowerCase(Locale.ROOT));
            if (request instanceof LocationTeleportRequest) {
                event.setSpawnLocation(toBukkit(((LocationTeleportRequest) request).location));
                sendResponse(request.server, request.id, true, "Player login location changed");
            } else if (request instanceof PlayerTeleportRequest) {
                Player target = plugin.getServer().getPlayer(((PlayerTeleportRequest) request).targetName);
                if (target == null) {
                    event.setSpawnLocation(plugin.getServer().getWorlds().get(0).getSpawnLocation());
                    sendResponse(request.server, request.id, false, "Target player " + ((PlayerTeleportRequest) request).targetName + " is no longer online?");
                } else {
                    event.setSpawnLocation(target.getLocation());
                    sendResponse(request.server, request.id, true, "Player login location changed to " + target.getName() + "'s location");
                }
            }
        }
    }

    private Location toBukkit(LocationInfo location) {
        World world = plugin.getServer().getWorld(location.getWorld());
        if (world == null) {
            throw new IllegalArgumentException("No world with the name " + location.getWorld() + " exists!");
        }
        return new Location(
                world,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    private LocationInfo fromBukkit(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        return new LocationInfo(
                plugin.getServerName(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    @Override
    protected void sendResponseData(String target, byte[] out) {
        plugin.getConnector().sendData(
                plugin,
                Action.RESPONSE,
                target.startsWith(PROXY_ID_PREFIX) ? MessageTarget.PROXY : MessageTarget.OTHERS_QUEUE,
                target,
                out);
    }

    private void sendCommandExecution(CommandSender sender, BridgedCommandExecutor executor, String label, String[] args) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(executor.getServer());
        out.writeUTF(sender instanceof Player ? sender.getName() : "");
        out.writeUTF(executor.getPluginName());
        out.writeUTF(executor.getName());
        out.writeUTF(label);
        out.writeInt(args.length);
        for (String arg : args) {
            out.writeUTF(arg);
        }

        if (sender instanceof Player) {
            fromBukkit(((Player) sender).getLocation()).write(out);
            plugin.getConnector().sendData(plugin, Action.EXECUTE_COMMAND, MessageTarget.PROXY, (Player) sender, out.toByteArray());
        } else {
            out.writeUTF(""); // Indicate empty location
            plugin.getConnector().sendData(plugin, Action.EXECUTE_COMMAND, MessageTarget.ALL_PROXIES, out.toByteArray());
        }
    }

    /**
     * Teleport a player to a certain server in the network
     * @param playerName    The name of the player to send
     * @param serverName    The name of the server to send to
     * @param consumer      Details about the sending
     * @return A future about whether the player could be sent
     */
    public CompletableFuture<Boolean> sendToServer(String playerName, String serverName, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(playerName);
        out.writeUTF(serverName);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        plugin.getConnector().sendData(plugin, Action.SEND_TO_SERVER, MessageTarget.ALL_PROXIES, out.toByteArray());
        return future;
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
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(playerName);
        location.write(out);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        plugin.getConnector().sendData(plugin, Action.TELEPORT, MessageTarget.ALL_PROXIES, out.toByteArray());
        return future;
    }

    /**
     * Teleport a player to a certain location in the network
     * @param playerName    The name of the player to teleport
     * @param serverName    The target server
     * @param worldName     The target world
     * @param consumer      Details about the teleport
     * @return A future about whether the player could be teleported
     */
    public CompletableFuture<Boolean> teleport(String playerName, String serverName, String worldName, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(playerName);
        out.writeUTF(serverName);
        out.writeUTF(worldName);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        plugin.getConnector().sendData(plugin, Action.TELEPORT_TO_WORLD, MessageTarget.ALL_PROXIES, out.toByteArray());
        return future;
    }

    /**
     * Teleport a player to a certain player in the network
     * @param playerName    The name of the player to teleport
     * @param targetName    The name of the target player
     * @param consumer      Details about the teleport
     * @return A future about whether the player could be teleported
     */
    public CompletableFuture<Boolean> teleport(String playerName, String targetName, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(playerName);
        out.writeUTF(targetName);
        responses.put(id, new ResponseHandler.Boolean(future));
        consumers.put(id, consumer);
        plugin.getConnector().sendData(plugin, Action.TELEPORT_TO_PLAYER, MessageTarget.ALL_PROXIES, out.toByteArray());
        return future;
    }

    /**
     * Get the server a player is connected to
     * @param player    The player to get the server for
     * @return A future for when the server was queried
     */
    public CompletableFuture<String> getServer(Player player) {
        return getServer(player.getName());
    }

    /**
     * Get the server a player is connected to
     * @param player    The player to get the server for
     * @return A future for when the server was queried
     */
    public CompletableFuture<String> getServer(String player) {
        CompletableFuture<String> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player);
        responses.put(id, new ResponseHandler.String(future));
        plugin.getConnector().sendData(plugin, Action.GET_SERVER, MessageTarget.ALL_PROXIES, PLAYER_PREFIX + player, out.toByteArray());
        return future;
    }


    /**
     * Get the location a player is connected to
     * @param player    The player to get the location for
     * @return A future for when the location was queried
     */
    public CompletableFuture<LocationInfo> getLocation(Player player) {
        return getLocation(player.getName());
    }

    /**
     * Get the location a player is connected to
     * @param player    The player to get the location for
     * @return A future for when the location was queried
     */
    public CompletableFuture<LocationInfo> getLocation(String player) {
        CompletableFuture<LocationInfo> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player);
        responses.put(id, new ResponseHandler.Location(future));
        plugin.getConnector().sendData(plugin, Action.GET_LOCATION, MessageTarget.ALL_PROXIES, PLAYER_PREFIX + player, out.toByteArray());
        return future;
    }

    /**
     * Run a command for a player on the proxy they are connected to.
     * The player needs to have access to that command!
     * @param player    The player to run the command for
     * @param command   The command to run
     * @return A future for whether the command was run successfully
     */
    public CompletableFuture<Boolean> runProxyPlayerCommand(Player player, String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(player.getName());
        out.writeLong(player.getUniqueId().getMostSignificantBits());
        out.writeLong(player.getUniqueId().getLeastSignificantBits());
        out.writeUTF(command);
        responses.put(id, new ResponseHandler.Boolean(future));
        plugin.getConnector().sendData(plugin, Action.PLAYER_COMMAND, MessageTarget.PROXY, player, out.toByteArray());
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
        plugin.getConnector().sendData(plugin, Action.CONSOLE_COMMAND, MessageTarget.OTHERS_QUEUE, out.toByteArray());
        return future;
    }

    /**
     * Run a console command on the connected proxies
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
        plugin.getConnector().sendData(plugin, Action.CONSOLE_COMMAND, MessageTarget.ALL_PROXIES, out.toByteArray());
        return future;
    }

    private static class LoginRequest {
        private final String server;
        private final long id;

        private LoginRequest(String server, long id) {
            this.server = server;
            this.id = id;
        }
    }

    private static class LocationTeleportRequest extends LoginRequest {
        private final LocationInfo location;

        public LocationTeleportRequest(String server, long id, LocationInfo location) {
            super(server, id);
            this.location = location;
        }
    }

    private static class PlayerTeleportRequest extends LoginRequest {
        private final String targetName;

        public PlayerTeleportRequest(String server, long id, String targetName) {
            super(server, id);
            this.targetName = targetName;
        }
    }

    private class BridgedSender implements ConsoleCommandSender {
        private final String serverName;
        private final long id;

        public BridgedSender(String serverName, long id) {
            this.serverName = serverName;
            this.id = id;
        }

        @Override
        public void sendMessage(String message) {
            sendMessage(new String[] {message});
        }

        @Override
        public void sendMessage(String[] messages) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(serverName);
            out.writeLong(id);
            out.writeBoolean(false);
            out.writeUTF(String.join("\n", messages));
            plugin.getConnector().sendData(plugin, Action.RESPONSE, MessageTarget.OTHERS_QUEUE, out.toByteArray());
        }

        @Override
        public void sendMessage(UUID sender, String message) {
            sendMessage(message);
        }

        @Override
        public void sendMessage(UUID sender, String[] messages) {
            sendMessage(messages);
        }

        @Override
        public Server getServer() {
            return plugin.getServer();
        }

        @Override
        public String getName() {
            return serverName + "BridgedSender";
        }

        @Override
        public Spigot spigot() {
            return new Spigot() {

                public void sendMessage(BaseComponent component) {
                    BridgedSender.this.sendMessage(TextComponent.toLegacyText(component));
                }

                public void sendMessage(BaseComponent... components) {
                    BridgedSender.this.sendMessage(TextComponent.toLegacyText(components));
                }
            };
        }

        @Override
        public boolean isConversing() {
            return false;
        }

        @Override
        public void acceptConversationInput(String input) {

        }

        @Override
        public boolean beginConversation(Conversation conversation) {
            return false;
        }

        @Override
        public void abandonConversation(Conversation conversation) {

        }

        @Override
        public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {

        }

        @Override
        public void sendRawMessage(String message) {
            sendMessage(message);
        }

        @Override
        public void sendRawMessage(UUID sender, String message) {
            sendMessage(message);
        }

        @Override
        public boolean isPermissionSet(String name) {
            return false;
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return false;
        }

        @Override
        public boolean hasPermission(String name) {
            return true;
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return true;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return null;
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {

        }

        @Override
        public void recalculatePermissions() {

        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return Collections.emptySet();
        }

        @Override
        public boolean isOp() {
            return true;
        }

        @Override
        public void setOp(boolean value) {

        }
    }

    private class BridgedCommandExecutor extends Command {
        private final String server;
        private final String pluginName;

        public BridgedCommandExecutor(String server, String pluginName, String name, String description, String usage, List<String> aliases, String permission, String permissionMessage) {
            super(name, description, usage, aliases);
            this.server = server;
            this.pluginName = pluginName;
            setPermission(permission);
            setPermissionMessage(permissionMessage);
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!testPermission(sender)) {
                return true;
            }

            sendCommandExecution(sender, this, label, args);
            return true;
        }

        public String getServer() {
            return server;
        }

        public String getPluginName() {
            return pluginName;
        }
    }
}
