package de.themoep.connectorplugin.bukkit;/*
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
import de.themoep.connectorplugin.connector.MessageTarget;
import io.papermc.lib.PaperLib;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
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
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Bridge extends BridgeCommon<BukkitConnectorPlugin> implements Listener {

    private Cache<String, TeleportRequest> teleportRequests = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    public Bridge(BukkitConnectorPlugin plugin) {
        super(plugin);

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
                teleportRequests.put(playerName.toLowerCase(Locale.ROOT), new TeleportRequest(senderServer, id, location));
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
            if (!targetServer.equals(plugin.getServerName())) {
                return;
            }
            long id = in.readLong();
            String command = in.readUTF();

            plugin.logDebug("Console command '" + command + "' triggered from " + senderServer);
            boolean success = plugin.getServer().dispatchCommand(new BridgedSender(senderServer, id), command);

            sendResponse(senderServer, id, success);
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
                boolean status = in.readBoolean();
                CompletableFuture<Boolean> future = futures.getIfPresent(id);
                if (future != null) {
                    futures.invalidate(id);
                    future.complete(status);
                } else {
                    plugin.logDebug("Could not find completion future for execution with ID " + id);
                }
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

    @EventHandler
    public void onSpawnLocationEvent(PlayerSpawnLocationEvent event) {
        TeleportRequest request = teleportRequests.getIfPresent(event.getPlayer().getName().toLowerCase(Locale.ROOT));
        if (request != null) {
            teleportRequests.invalidate(event.getPlayer().getName().toLowerCase(Locale.ROOT));
            event.setSpawnLocation(toBukkit(request.location));
            sendResponse(request.server, request.id, true, "Player login location changed");
        }
    }

    private Location toBukkit(LocationInfo location) {
        World world = plugin.getServer().getWorld(location.getWorld());
        if (world == null) {
            throw new IllegalArgumentException("No world with the name " + world.getName() + " exists!");
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

    private void sendResponse(String server, long id, boolean success, String... messages) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(server);
        out.writeLong(id);
        out.writeBoolean(true);
        out.writeBoolean(success);
        plugin.getConnector().sendData(plugin, Action.RESPONSE, server.startsWith("proxy:") ? MessageTarget.ALL_PROXIES : MessageTarget.OTHERS_QUEUE, out.toByteArray());

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
        plugin.getConnector().sendData(plugin, Action.RESPONSE, server.startsWith("proxy:") ? MessageTarget.ALL_PROXIES : MessageTarget.OTHERS_QUEUE, out.toByteArray());
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
        futures.put(id, future);
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
        futures.put(id, future);
        consumers.put(id, consumer);
        plugin.getConnector().sendData(plugin, Action.TELEPORT, MessageTarget.ALL_PROXIES, out.toByteArray());
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
        futures.put(id, future);
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
        futures.put(id, future);
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
        futures.put(id, future);
        if (consumer != null && consumer.length > 0) {
            consumers.put(id, consumer);
        }
        plugin.getConnector().sendData(plugin, Action.CONSOLE_COMMAND, MessageTarget.ALL_PROXIES, out.toByteArray());
        return future;
    }

    private class TeleportRequest {
        private final String server;
        private final long id;
        private final LocationInfo location;

        public TeleportRequest(String server, long id, LocationInfo location) {
            this.server = server;
            this.id = id;
            this.location = location;
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
}
