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

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.regex.Pattern;

public class PluginConfig {

    private static final Pattern PATH_PATTERN = Pattern.compile("\\.");

    private final VelocityConnectorPlugin plugin;
    private final File configFile;
    private final String defaultFile;
    private final YamlConfigurationLoader configLoader;
    private ConfigurationNode config;
    private ConfigurationNode defaultConfig;

    public PluginConfig(VelocityConnectorPlugin plugin, File configFile) {
        this(plugin, configFile, configFile.getName());
    }

    public PluginConfig(VelocityConnectorPlugin plugin, File configFile, String defaultFile) {
        this.plugin = plugin;
        this.configFile = configFile;
        this.defaultFile = defaultFile;
        configLoader = YamlConfigurationLoader.builder()
                .indent(2)
                .path(configFile.toPath())
                .nodeStyle(NodeStyle.FLOW)
                .build();
    }

    public boolean load() {
        try {
            config = configLoader.load();
            if (defaultFile != null) {
                defaultConfig = YamlConfigurationLoader.builder()
                        .indent(2)
                        .source(() -> new BufferedReader(new InputStreamReader(plugin.getResourceAsStream(defaultFile))))
                        .build().load();
                if (config.empty()) {
                    config = defaultConfig.copy();
                }
            }
            plugin.logDebug("Loaded " + configFile.getName());
            return true;
        } catch (IOException e) {
            plugin.logError("Unable to load configuration file " + configFile.getName(), e);
            return false;
        }
    }

    public boolean createDefaultConfig() throws IOException {
        try (InputStream in = plugin.getResourceAsStream(defaultFile)) {
            if (in == null) {
                plugin.logWarning("No default config '" + defaultFile + "' found in " + plugin.getName() + "!");
                return false;
            }
            if (!configFile.exists()) {
                File parent = configFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                try {
                    Files.copy(in, configFile.toPath());
                    return true;
                } catch (IOException ex) {
                    plugin.logError("Could not save " + configFile.getName() + " to " + configFile, ex);
                }
            }
        } catch (IOException ex) {
            plugin.logError("Could not load default config from " + defaultFile, ex);
        }
        return false;
    }

    public void save() {
        try {
            configLoader.save(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Object set(String path, Object value) throws SerializationException {
        ConfigurationNode node = config.node(splitPath(path));
        Object prev = node.raw();
        node.set(value);
        return prev;
    }

    public ConfigurationNode remove(String path) {
        ConfigurationNode node = config.node(splitPath(path));
        try {
            return node.virtual() ? node : node.set(null);
        } catch (SerializationException ignored) {}
        return node;
    }

    public ConfigurationNode getRawConfig() {
        return config;
    }

    public ConfigurationNode getRawConfig(String path) {
        return getRawConfig().node(splitPath(path));
    }

    public boolean has(String path) {
        return !getRawConfig(path).virtual();
    }

    public boolean isSection(String path) {
        return !getRawConfig(path).childrenMap().isEmpty();
    }

    public int getInt(String path) {
        return getInt(path, 0);
    }

    public int getInt(String path, int def) {
        return getRawConfig(path).getInt(def);
    }

    public long getLong(String path) {
        return getLong(path, 0);
    }

    public long getLong(String path, long def) {
        return getRawConfig(path).getLong(def);
    }

    public double getDouble(String path) {
        return getDouble(path, 0);
    }

    public double getDouble(String path, double def) {
        return getRawConfig(path).getDouble(def);
    }

    public String getString(String path) {
        return getString(path, null);
    }

    public String getString(String path, String def) {
        return getRawConfig(path).getString(def);
    }

    public boolean getBoolean(String path) {
        return getBoolean(path, false);
    }

    public boolean getBoolean(String path, boolean def) {
        return getRawConfig(path).getBoolean(def);
    }

    private static Object[] splitPath(String key) {
        return PATH_PATTERN.split(key);
    }
}
