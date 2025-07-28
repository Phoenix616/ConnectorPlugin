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

import com.loohp.limbo.Limbo;
import com.loohp.limbo.events.EventHandler;
import com.loohp.limbo.events.Listener;
import com.loohp.limbo.events.player.PlayerQuitEvent;
import com.loohp.limbo.file.FileConfiguration;
import com.loohp.limbo.plugins.LimboPlugin;
import com.loohp.limbo.player.Player;
import com.loohp.limbo.scheduler.LimboTask;
import de.themoep.connectorplugin.ConnectorPlugin;
import de.themoep.connectorplugin.connector.Connector;
import de.themoep.connectorplugin.connector.MessageTarget;
import de.themoep.connectorplugin.limbo.commands.ConnectorCommand;
import de.themoep.connectorplugin.limbo.connector.LimboConnector;
import de.themoep.connectorplugin.limbo.connector.MqttConnector;
import de.themoep.connectorplugin.limbo.connector.PluginMessageConnector;
import de.themoep.connectorplugin.limbo.connector.RedisConnector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LimboConnectorPlugin extends LimboPlugin implements ConnectorPlugin<Player>, Listener {

    private LimboConnector connector;
    private Bridge bridge;
    private boolean debug = true;
    private String globalGroup;
    private Map<String, String> pluginGroups;
    private String serverName;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        File configFile = new File(getDataFolder(), "limbo-config.yml");
        if (!configFile.exists()) {
            getDataFolder().mkdirs();
            try (InputStream in = getResourceAsStream("limbo-config.yml")) {
                if (in != null) {
                    java.nio.file.Files.copy(in, configFile.toPath());
                } else {
                    configFile.createNewFile();
                }
            } catch (IOException e) {
                logError("Could not create default config!", e);
                return;
            }
        }
        try {
            config = new FileConfiguration(configFile);
        } catch (IOException e) {
            logError("Error while loading config file " + configFile.getAbsolutePath(), e);
            return;
        }

        debug = config.get("debug", Boolean.class) != null ? config.get("debug", Boolean.class) : true;
        globalGroup = config.get("group", String.class) != null ? config.get("group", String.class) : "global";
        pluginGroups = new HashMap<>();
        serverName = config.get("server-name", String.class);
        if (serverName == null || "changeme".equals(serverName)) {
            serverName = new File(".").getAbsoluteFile().getParentFile().getName();
            logWarning("Server name is not configured! Please set it in your plugin config! Using the name of the server directory instead: " + serverName);
        }

        String messengerType = config.get("messenger-type", String.class);
        if (messengerType == null) {
            messengerType = "plugin_messages";
        }
        messengerType = messengerType.toLowerCase(Locale.ROOT);
        switch (messengerType) {
            default:
                logWarning("Messenger type '" + messengerType + "' is not supported, falling back to plugin messages!");
            case "plugin_messages":
                connector = new PluginMessageConnector(this);
                logWarning("Using plugin messages as the messenger type will come with" +
                        " some caveats like sending to servers without players or to" +
                        " other proxies not working!");
                logWarning("Please consider using one of the other messenger types!");
                break;
            case "redis":
                connector = new RedisConnector(this);
                break;
            case "mqtt":
                connector = new MqttConnector(this);
                break;
        }

        bridge = new Bridge(this);
        getLimbo().getEventsManager().registerEvents(this, this);
        getLimbo().getEventsManager().registerEvents(this, (Listener) bridge);
        getLimbo().getPluginManager().registerCommands(this, new ConnectorCommand(this));
    }

    @Override
    public void onDisable() {
        if (connector != null) {
            connector.close();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (bridge != null) {
            bridge.onPlayerQuit(event);
        }
    }

    /**
     * Get the bridge helper class for executing certain actions on the proxy and other servers
     * @return The bridge helper
     */
    public Bridge getBridge() {
        return bridge;
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }

    @Override
    public Connector<LimboConnectorPlugin, Player> getConnector() {
        return connector;
    }

    @Override
    public MessageTarget.Type getSourceType() {
        return MessageTarget.Type.SERVER;
    }

    @Override
    public void logDebug(String message, Throwable... throwables) {
        if (debug) {
            logInfo("[DEBUG] " + message, throwables);
        }
    }

    @Override
    public void logInfo(String message, Throwable... throwables) {
        Limbo.getInstance().getConsole().sendMessage(message);
        if (throwables.length > 0) {
            throwables[0].printStackTrace();
        }
    }

    @Override
    public void logWarning(String message, Throwable... throwables) {
        Limbo.getInstance().getConsole().sendMessage("[WARNING] " + message);
        if (throwables.length > 0) {
            throwables[0].printStackTrace();
        }
    }

    @Override
    public void logError(String message, Throwable... throwables) {
        Limbo.getInstance().getConsole().sendMessage("[ERROR] " + message);
        if (throwables.length > 0) {
            throwables[0].printStackTrace();
        }
    }

    @Override
    public String getServerName() {
        return serverName;
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
    public void runAsync(Runnable runnable) {
        getLimbo().getScheduler().runTaskAsync(this, new LimboTask() {
            @Override
            public void run() {
                runnable.run();
            }
        });
    }

    public Limbo getLimbo() {
        return Limbo.getInstance();
    }

    public InputStream getResourceAsStream(String filename) {
        return getClass().getClassLoader().getResourceAsStream(filename);
    }
}
