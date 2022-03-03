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

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.themoep.connectorplugin.ConnectorPlugin;
import de.themoep.connectorplugin.velocity.commands.ConnectorCommand;
import de.themoep.connectorplugin.velocity.connector.VelocityConnector;
import de.themoep.connectorplugin.velocity.connector.MqttConnector;
import de.themoep.connectorplugin.velocity.connector.PluginMessageConnector;
import de.themoep.connectorplugin.velocity.connector.RedisConnector;
import de.themoep.connectorplugin.connector.MessageTarget;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public final class VelocityConnectorPlugin implements ConnectorPlugin<Player> {

    private final ProxyServer proxy;
    private final Logger logger;
    private final File dataFolder;
    private PluginConfig config;
    private VelocityConnector connector;
    private Bridge bridge;
    private boolean debug = true;
    private String serverId;

    @Inject
    public VelocityConnectorPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataFolder) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataFolder = dataFolder.toFile();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        config = new PluginConfig(this, new File(dataFolder, "config.yml"), "velocity-config.yml");
        try {
            config.createDefaultConfig();
        } catch (IOException e) {
            logger.error("Could not created default config! " + e.getMessage());
            return;
        }
        if (!config.load()) {
            return;
        }

        debug = getConfig().getBoolean("debug");
        serverId = getConfig().getString("server-id");

        String messengerType = getConfig().getString("messenger-type", "plugin_messages").toLowerCase(Locale.ROOT);
        switch (messengerType) {
            default:
                logger.warn("Messenger type '" + messengerType + "' is not supported, falling back to plugin messages!");
            case "plugin_messages":
                connector = new PluginMessageConnector(this);
                break;
            case "redis":
                connector = new RedisConnector(this);
                break;
            case "mqtt":
                connector = new MqttConnector(this);
                break;
        }

        ConnectorCommand command = new ConnectorCommand(this);
        getProxy().getCommandManager().register(command, command);

        bridge = new Bridge(this);
    }

    @Subscribe
    public void onProxyInitialization(ProxyShutdownEvent event) {
        connector.close();
    }

    @Override
    public VelocityConnector getConnector() {
        return connector;
    }

    /**
     * Get the bridge helper class for executing certain actions on other servers
     * @return The bridge helper
     */
    public Bridge getBridge() {
        return bridge;
    }

    @Override
    public void runAsync(Runnable runnable) {
        getProxy().getScheduler().buildTask(this, runnable).schedule();
    }

    @Override
    public MessageTarget.Type getSourceType() {
        return MessageTarget.Type.PROXY;
    }

    @Override
    public void logDebug(String message, Throwable... throwables) {
        if (debug) {
            logger.info("[DEBUG] " + message, throwables.length > 0 ? throwables[0] : null);
        }
    }

    @Override
    public void logInfo(String message, Throwable... throwables) {
        logger.info(message, throwables.length > 0 ? throwables[0] : null);
    }

    @Override
    public void logWarning(String message, Throwable... throwables) {
        logger.warn(message, throwables.length > 0 ? throwables[0] : null);
    }

    @Override
    public void logError(String message, Throwable... throwables) {
        logger.error(message, throwables.length > 0 ? throwables[0] : null);
    }

    @Override
    public String getServerName() {
        return "proxy:" + serverId;
    }

    @Override
    public String getGlobalGroup() {
        return "";
    }

    @Override
    public Map<String, String> getGroups() {
        return Collections.emptyMap();
    }

    @Override
    public String getName() {
        return "ConnectorPlugin";
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public PluginConfig getConfig() {
        return config;
    }

    public InputStream getResourceAsStream(String file) {
        return getClass().getClassLoader().getResourceAsStream(file);
    }
}
