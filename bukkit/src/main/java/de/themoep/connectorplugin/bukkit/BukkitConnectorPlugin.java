package de.themoep.connectorplugin.bukkit;

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

import de.themoep.connectorplugin.ConnectorPlugin;
import de.themoep.connectorplugin.bukkit.commands.ConnectorCommand;
import de.themoep.connectorplugin.bukkit.connector.BukkitConnector;
import de.themoep.connectorplugin.bukkit.connector.MqttConnector;
import de.themoep.connectorplugin.bukkit.connector.PluginMessageConnector;
import de.themoep.connectorplugin.bukkit.connector.RedisConnector;
import de.themoep.connectorplugin.connector.ConnectingPlugin;
import de.themoep.connectorplugin.connector.MessageTarget;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class BukkitConnectorPlugin extends JavaPlugin implements ConnectorPlugin<Player>, Listener {

    private BukkitConnector connector;
    private Bridge bridge;
    private boolean debug = true;
    private String globalGroup;
    private Map<String, String> pluginGroups;
    private String serverName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        debug = getConfig().getBoolean("debug");
        globalGroup = getConfig().getString("group", "global");
        pluginGroups = new HashMap<>();
        ConfigurationSection pluginGroupsConfig = getConfig().getConfigurationSection("plugin-groups");
        if (pluginGroupsConfig != null) {
            for (String pluginName : pluginGroupsConfig.getKeys(false)) {
                pluginGroups.put(pluginName.toLowerCase(Locale.ROOT), pluginGroupsConfig.getString(pluginName));
            }
        }
        serverName = getConfig().getString("server-name", "changeme");
        if ("changeme".equals(serverName)) {
            serverName = new File(".").getAbsoluteFile().getParentFile().getName();
            getLogger().log(Level.WARNING, "Server name is not configured! Please set it in your plugin config! Using the name of the server directory instead: " + serverName);
        }

        String messengerType = getConfig().getString("messenger-type", "plugin_messages").toLowerCase(Locale.ROOT);
        switch (messengerType) {
            default:
                getLogger().log(Level.WARNING, "Messenger type '" + messengerType + "' is not supported, falling back to plugin messages!");
            case "plugin_messages":
                connector = new PluginMessageConnector(this);
                getLogger().log(Level.WARNING, "Using plugin messages as the messenger type will come with" +
                        " some caveats like sending to servers without players or to" +
                        " other proxies not working!");
                getLogger().log(Level.WARNING, "Please consider using one of the other messenger types!");
                break;
            case "redis":
                connector = new RedisConnector(this);
                break;
            case "mqtt":
                connector = new MqttConnector(this);
                break;
        }

        getCommand("connectorplugin").setExecutor(new ConnectorCommand(this));

        bridge = new Bridge(this);
    }

    @Override
    public void onDisable() {
        connector.close();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() instanceof ConnectingPlugin) {
            connector.unregisterMessageHandlers((ConnectingPlugin) event.getPlugin());
        }
    }

    /**
     * Get the bridge helper class for executing certain actions on the proxy and other servers
     * @return The bridge helper
     */
    public Bridge getBridge() {
        return bridge;
    }

    @Override
    public BukkitConnector getConnector() {
        return connector;
    }

    @Override
    public void runAsync(Runnable runnable) {
        getServer().getScheduler().runTaskAsynchronously(this, runnable);
    }

    public void runSync(Runnable runnable) {
        if (getServer().isPrimaryThread()) {
            runnable.run();
            return;
        }
        getServer().getScheduler().runTask(this, runnable);
    }

    @Override
    public MessageTarget.Type getSourceType() {
        return MessageTarget.Type.SERVER;
    }

    @Override
    public void logDebug(String message, Throwable... throwables) {
        if (debug) {
            getLogger().log(Level.INFO, "[DEBUG] " + message, throwables.length > 0 ? throwables[0] : null);
        }
    }

    @Override
    public void logInfo(String message, Throwable... throwables) {
        getLogger().log(Level.INFO, message, throwables.length > 0 ? throwables[0] : null);
    }

    @Override
    public void logWarning(String message, Throwable... throwables) {
        getLogger().log(Level.WARNING, message, throwables.length > 0 ? throwables[0] : null);
    }

    @Override
    public void logError(String message, Throwable... throwables) {
        getLogger().log(Level.SEVERE, message, throwables.length > 0 ? throwables[0] : null);
    }

    @Override
    public String getGlobalGroup() {
        return globalGroup;
    }

    @Override
    public Map<String, String> getGroups() {
        return pluginGroups;
    }

    @Override
    public String getServerName() {
        return serverName;
    }
}
