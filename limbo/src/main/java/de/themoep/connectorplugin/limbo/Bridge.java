package de.themoep.connectorplugin.limbo;

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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.loohp.limbo.commands.CommandExecutor;
import com.loohp.limbo.commands.CommandSender;
import com.loohp.limbo.commands.TabCompletor;
import com.loohp.limbo.events.EventHandler;
import com.loohp.limbo.events.Listener;
import com.loohp.limbo.events.player.PlayerJoinEvent;
import com.loohp.limbo.events.player.PlayerQuitEvent;
import com.loohp.limbo.events.player.PlayerSpawnEvent;
import com.loohp.limbo.location.Location;
import com.loohp.limbo.player.Player;
import com.loohp.limbo.world.World;
import de.themoep.connectorplugin.LocationInfo;
import de.themoep.connectorplugin.ResponseHandler;
import de.themoep.connectorplugin.ServerBridgeCommon;
import de.themoep.connectorplugin.connector.MessageTarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static de.themoep.connectorplugin.connector.Connector.PLAYER_PREFIX;

public class Bridge extends ServerBridgeCommon<LimboConnectorPlugin, Player> implements Listener {

    private final Cache<String, LoginRequest> loginRequests = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    public Bridge(LimboConnectorPlugin plugin) {
        super(plugin);
        plugin.getLimbo().getEventsManager().registerEvents(plugin, this);

        registerMessageHandler(Action.TELEPORT, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String playerName = in.readUTF();
            LocationInfo location = LocationInfo.read(in);
            if (!location.getServer().equals(plugin.getServerName())) {
                return;
            }

            World world = plugin.getServer().getWorld(location.getWorld());
            if (world == null) {
                sendResponse(plugin.getServerName(), id, false, "No world with the name " + location.getWorld() + " exists on the server!");
                plugin.logDebug("[M] Player " + playerName + " is online but no world with the name " + location.getWorld() + " to teleport to exists?");
                return;
            }

            markTeleporting(playerName);

            Player player = plugin.getServer().getPlayer(playerName);
            if (player != null) {
                plugin.logDebug("[M] Player " + playerName + " is online. Teleporting to " + location);
                player.teleport(new Location(
                        world,
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch()
                ));
                sendResponse(plugin.getServerName(), id, true);
                unmarkTeleporting(playerName);
            } else {
                loginRequests.put(playerName.toLowerCase(Locale.ROOT), new LocationTeleportRequest(senderServer, id, location));
                if (!plugin.getConnector().requiresPlayer() || !plugin.getServer().getPlayers().isEmpty()) {
                    plugin.getBridge().sendToServer(playerName, location.getServer(),
                            messages -> sendResponseMessage(senderServer, id, messages)
                    ).whenComplete((success, ex) -> {
                        sendResponse(senderServer, id, success, success ? "Player teleported!" : "Unable to teleport " + (ex != null ? ex.getMessage() : ""));
                    });
                }
            }
        });

        registerMessageHandler(Action.TELEPORT_TO_WORLD, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
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
                plugin.logDebug("[M] Player " + playerName + " is online but no world with the name " + worldName + " to teleport to exists?");
                return;
            }

            markTeleporting(playerName);

            Player player = plugin.getServer().getPlayer(playerName);
            if (player != null) {
                plugin.logDebug("[M] Player " + playerName + " is online. Teleporting to spawn of world " + worldName);
                player.teleport(plugin.getLimbo().getServerProperties().getWorldSpawn());
                sendResponse(senderServer, id, true, "Player teleported to spawn of " + worldName + "!");
                unmarkTeleporting(playerName);
            } else {
                loginRequests.put(playerName.toLowerCase(Locale.ROOT), new LocationTeleportRequest(senderServer, id, adapt(plugin.getLimbo().getServerProperties().getWorldSpawn())));
                if (!plugin.getConnector().requiresPlayer() || !plugin.getServer().getPlayers().isEmpty()) {
                    sendToServer(playerName, serverName,
                            messages -> sendResponseMessage(senderServer, id, messages)
                    ).whenComplete((success, ex) -> {
                        sendResponse(senderServer, id, success, success ? "Player teleported to spawn of " + worldName + "!" : "Unable to teleport " + (ex != null ? ex.getMessage() : ""));
                    });
                }
            }
        });

        registerMessageHandler(Action.TELEPORT_TO_PLAYER, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String playerName = in.readUTF();
            String targetName = in.readUTF();

            markTeleporting(playerName);

            Player target = plugin.getServer().getPlayer(targetName);
            if (target != null) {
                Player player = plugin.getServer().getPlayer(playerName);
                if (player != null) {
                    plugin.logDebug("[M] Player " + playerName + " is online. Teleporting to player " + targetName);
                    player.teleport(target.getLocation());
                    sendResponse(senderServer, id, true, "Player teleported!");
                    unmarkTeleporting(playerName);
                } else {
                    loginRequests.put(playerName.toLowerCase(Locale.ROOT), new PlayerTeleportRequest(senderServer, id, targetName));
                    if (!plugin.getConnector().requiresPlayer() || !plugin.getServer().getPlayers().isEmpty()) {
                        sendToServer(playerName, plugin.getServerName(),
                                messages -> sendResponseMessage(senderServer, id, messages)
                        ).whenComplete((success, ex) -> {
                            sendResponse(senderServer, id, success, success ? "Player teleported!" : "Unable to teleport " + (ex != null ? ex.getMessage() : ""));
                        });
                    }
                }
            }
        });

        registerMessageHandler(Action.GET_LOCATION, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String playerName = in.readUTF();

            Player player = plugin.getServer().getPlayer(playerName);
            if (player != null) {
                sendResponse(senderServer, id, adapt(player.getLocation()));
            } else {
                sendResponse(senderServer, id, (LocationInfo) null);
            }
        });

        registerMessageHandler(Action.PLAYER_COMMAND, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            long id = in.readLong();
            String playerName = in.readUTF();
            UUID playerId = new UUID(in.readLong(), in.readLong());
            String command = in.readUTF();

            Player player = plugin.getLimbo().getPlayer(playerId);
            if (player == null) {
                player = plugin.getLimbo().getPlayer(playerName);
            }
            if (player == null) {
                plugin.logDebug("Could not find player " + playerName + "/" + playerId + " on this server to execute command " + command);
                sendResponse(senderServer, id, false, "Could not find player " + playerName + "/" + playerId + " on this server to execute command " + command);
                return;
            }

            plugin.logDebug("Command '" + command + "' for player '" + playerName + "' triggered from " + senderServer);
            plugin.getLimbo().dispatchCommand(player, command);

            sendResponse(senderServer, id, true);
        });

        registerMessageHandler(Action.CONSOLE_COMMAND, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            String targetServer = in.readUTF();
            if (targetServer.startsWith("p:")) {
                Player player = plugin.getLimbo().getPlayer(targetServer.substring(2));
                if (player == null) {
                    return;
                }
            }  else if (!targetServer.equals(plugin.getServerName())) {
                return;
            }
            long id = in.readLong();
            String command = in.readUTF();

            plugin.logDebug("Console command '" + command + "' triggered from " + senderServer);
            plugin.getLimbo().dispatchCommand(plugin.getLimbo().getConsole(), command);

            sendResponse(senderServer, id, true);
        });

        registerMessageHandler(Action.REGISTER_COMMAND, (receiver, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message.getData());
            String senderServer = message.getReceivedMessage().getSendingServer();
            String pluginName = in.readUTF();
            String name = in.readUTF();

            String description = in.readUTF();
            String usage = in.readUTF();
            String permission = in.readUTF();
            String permissionMessage = in.readBoolean() ? in.readUTF() : null;
            int aliasCount = in.readInt();
            List<String> aliases = new ArrayList<>();
            for (int i = 0; i < aliasCount; i++) {
                aliases.add(in.readUTF());
            }
            plugin.getLimbo().getPluginManager().registerCommands(plugin, new BridgedCommandExecutor(senderServer, pluginName, name, description, usage, aliases, permission, permissionMessage));
        });

        this.sendStarted(plugin);
    }

    @EventHandler
    public void onPlayerSpawnEvent(PlayerSpawnEvent event) {
        LoginRequest request = loginRequests.getIfPresent(event.getPlayer().getName().toLowerCase(Locale.ROOT));
        if (request != null) {
            loginRequests.invalidate(event.getPlayer().getName().toLowerCase(Locale.ROOT));
            if (request instanceof LocationTeleportRequest) {
                event.setSpawnLocation(adapt(((LocationTeleportRequest) request).getLocation()));
                sendResponse(request.getServer(), request.getId(), true, "Player login location changed");
                plugin.logDebug("Set spawn location of player " + event.getPlayer().getName() + " to " + ((LocationTeleportRequest) request).getLocation());
            } else if (request instanceof PlayerTeleportRequest) {
                Player target = plugin.getServer().getPlayer(((PlayerTeleportRequest) request).getTargetName());
                if (target == null) {
                    event.setSpawnLocation(plugin.getLimbo().getServerProperties().getWorldSpawn());
                    sendResponse(request.getServer(), request.getId(), false, "Target player " + ((PlayerTeleportRequest) request).getTargetName() + " is no longer online?");
                    plugin.logDebug("Tried to set spawn location of player " + event.getPlayer().getName() + " to " + ((PlayerTeleportRequest) request).getTargetName() + " but target wasn't online. Set to level spawn instead.");
                } else {
                    event.setSpawnLocation(target.getLocation());
                    sendResponse(request.getServer(), request.getId(), true, "Player login location changed to " + target.getName() + "'s location");
                    plugin.logDebug("Set spawn location of player " + event.getPlayer().getName() + " to " + ((PlayerTeleportRequest) request).getTargetName() + ". " + target.getLocation());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        unmarkTeleporting(event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unmarkTeleporting(event.getPlayer().getName());
    }

    public Location adapt(LocationInfo location) {
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

    public LocationInfo adapt(Location location) {
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
            adapt(((Player) sender).getLocation()).write(out);
            sendData(Action.EXECUTE_COMMAND, MessageTarget.PROXY, (Player) sender, out.toByteArray());
        } else {
            out.writeUTF(""); // Indicate empty location
            sendData(Action.EXECUTE_COMMAND, MessageTarget.ALL_PROXIES, out.toByteArray());
        }
    }

    @Override
    @SafeVarargs
    public final CompletableFuture<Boolean> teleport(Player player, LocationInfo location, Consumer<String>... consumer) {
        return teleport(player.getName(), location, consumer);
    }

    @Override
    @SafeVarargs
    public final CompletableFuture<Boolean> teleport(String playerName, LocationInfo location, Consumer<String>... consumer) {
        markTeleporting(playerName);
        if (location.getServer().equals(plugin.getServerName())) {
            Player player = plugin.getServer().getPlayer(playerName);
            if (player != null) {
                plugin.logDebug("Player " + playerName + " is online. Teleporting to " + location);
                player.teleport(adapt(location));
                plugin.logDebug("Teleport of player " + playerName + " was successful");
                unmarkTeleporting(playerName);
                return CompletableFuture.completedFuture(true);
            }
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        getServer(playerName).whenComplete((s, e) -> {
            // check player server existence
            if (s == null) {
                future.complete(false);
                for (Consumer<String> c : consumer) {
                    c.accept("Player " + playerName + " is not online!");
                }
                return;
            }
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            long id = RANDOM.nextLong();
            out.writeLong(id);
            out.writeUTF(playerName);
            location.write(out);
            responses.put(id, new ResponseHandler.Boolean(future));
            if (consumer != null && consumer.length > 0) {
                consumers.put(id, consumer);
            }
            sendData(Action.TELEPORT, MessageTarget.SERVER, location.getServer(), out.toByteArray());
        });
        return future;
    }

    @Override
    @SafeVarargs
    public final CompletableFuture<Boolean> teleport(Player player, String serverName, String worldName, Consumer<String>... consumer) {
        return teleport(player.getName(), serverName, worldName, consumer);
    }

    @Override
    @SafeVarargs
    public final CompletableFuture<Boolean> teleport(String playerName, String serverName, String worldName, Consumer<String>... consumer) {
        markTeleporting(playerName);
        if (serverName.equals(plugin.getServerName())) {
            Player player = plugin.getServer().getPlayer(playerName);
            if (player != null) {
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.logDebug("Player " + playerName + " is online but no world with the name " + worldName + " to teleport to exists?");
                    for (Consumer<String> c : consumer) {
                        c.accept("No world with the name " + worldName + " exists on the server!");
                    }
                    unmarkTeleporting(playerName);
                    return CompletableFuture.completedFuture(false);
                }
                plugin.logDebug("Player " + playerName + " is online. Teleporting to spawn of world " + worldName);
                player.teleport(plugin.getLimbo().getServerProperties().getWorldSpawn());
                plugin.logDebug("Teleport of player " + playerName + " was successful");
                unmarkTeleporting(playerName);
                return CompletableFuture.completedFuture(true);
            }
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        getServer(playerName).whenComplete((s, e) -> {
            // check player server existence
            if (s == null) {
                future.complete(false);
                for (Consumer<String> c : consumer) {
                    c.accept("Player " + playerName + " is not online!");
                }
                return;
            }
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            long id = RANDOM.nextLong();
            out.writeLong(id);
            out.writeUTF(playerName);
            out.writeUTF(serverName);
            out.writeUTF(worldName);
            responses.put(id, new ResponseHandler.Boolean(future));
            consumers.put(id, consumer);
            sendData(Action.TELEPORT_TO_WORLD, MessageTarget.PROXY, PLAYER_PREFIX + playerName, out.toByteArray());
        });
        return future;
    }

    @Override
    @SafeVarargs
    public final CompletableFuture<Boolean> teleport(Player player, Player target, Consumer<String>... consumer) {
        return teleport(player.getName(), target.getName(), consumer);
    }

    @Override
    @SafeVarargs
    public final CompletableFuture<Boolean> teleport(String playerName, String targetName, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        getServer(playerName).whenComplete((server, ex) -> {
            if (server == null) {
                future.complete(false);
                for (Consumer<String> c : consumer) {
                    c.accept("Player " + playerName + " is not online!");
                }
                return;
            }
            markTeleporting(playerName);
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            long id = RANDOM.nextLong();
            out.writeLong(id);
            out.writeUTF(playerName);
            out.writeUTF(targetName);
            responses.put(id, new ResponseHandler.Boolean(future));
            if (consumer != null && consumer.length > 0) {
                consumers.put(id, consumer);
            }
            sendData(Action.TELEPORT_TO_PLAYER, MessageTarget.SERVER, PLAYER_PREFIX + targetName, out.toByteArray());
        });
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
     * Get the location a player is connected to
     * @param player    The player to get the location for
     * @return A future for when the location was queried
     */
    public CompletableFuture<LocationInfo> getLocation(Player player) {
        return getLocation(player.getName());
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
        // Make sure target player is connected
        getServer(player).whenComplete((s, e) -> {
            // check player server existence
            if (s == null) {
                future.complete(false);
                return;
            }
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            long id = RANDOM.nextLong();
            out.writeLong(id);
            out.writeUTF(player.getName());
            out.writeLong(player.getUniqueId().getMostSignificantBits());
            out.writeLong(player.getUniqueId().getLeastSignificantBits());
            out.writeUTF(command);
            responses.put(id, new ResponseHandler.Boolean(future));
            sendData(Action.PLAYER_COMMAND, MessageTarget.PROXY, player, out.toByteArray());
        });
        return future;
    }

    private class BridgedCommandExecutor implements CommandExecutor, TabCompletor {
        private final String server;
        private final String pluginName;
        private final String name;
        private final String permission;
        private final Collection<String> aliases;

        public BridgedCommandExecutor(String server, String pluginName, String name, String description, String usage, List<String> aliases, String permission, String permissionMessage) {
            this.server = server;
            this.pluginName = pluginName;
            this.name = name;
            this.permission = permission;
            this.aliases = aliases;
        }

        public String getServer() {
            return server;
        }

        public String getPluginName() {
            return pluginName;
        }

        public String getName() {
            return name;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!sender.hasPermission(this.permission)) {
                return;
            }

            if (args.length == 0) {
                return;
            }
            if (!args[0].equalsIgnoreCase(name) && !aliases.contains(args[0].toLowerCase(Locale.ROOT))) {
                return;
            }

            sendCommandExecution(sender, this, args[0], Arrays.copyOfRange(args, 2, args.length));
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            if (!sender.hasPermission(this.permission)) {
                return new ArrayList<>();
            }

            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                for (String alias : this.aliases) {
                    if (alias.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(alias);
                    }
                }
            }
            return completions;
        }
    }
}
