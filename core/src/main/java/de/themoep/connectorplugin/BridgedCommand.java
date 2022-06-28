package de.themoep.connectorplugin;

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

import de.themoep.connectorplugin.connector.ConnectingPlugin;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class BridgedCommand<P extends ConnectingPlugin, S> {
    private final P plugin;
    private final String name;
    private final String[] aliases;
    private final String permission;
    private final String permissionMessage;
    private final String description;
    private final String usage;

    /**
     * A new bridged command
     * @param plugin    The plugin that this command is from
     * @param name      The name of the command
     */
    public BridgedCommand(P plugin, String name) {
        this(plugin, name, null);
    }

    /**
     * A new bridged command
     * @param plugin        The plugin that this command is from
     * @param name          The name of the command
     * @param permission    The permission of this command
     */
    public BridgedCommand(P plugin, String name, String permission) {
        this(plugin, name, permission, "", "", "/" + name);
    }

    /**
     * A new bridged command
     * @param plugin        The plugin that this command is from
     * @param name          The name of the command
     * @param permission    The permission of this command
     * @param description   The description of this command
     * @param usage         How the command can be used by the player
     * @param aliases       An optional array of aliases for the command
     */
    public BridgedCommand(P plugin, String name, String permission, String permissionMessage, String description, String usage, String... aliases) {
        this.plugin = plugin;
        this.name = name;
        this.aliases = aliases;
        this.permission = permission;
        this.permissionMessage = permissionMessage;
        this.description = description;
        this.usage = usage;
    }

    public P getPlugin() {
        return plugin;
    }

    public String getName() {
        return name;
    }

    public String[] getAliases() {
        return aliases;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usage;
    }

    public String getPermission() {
        return permission;
    }

    public String getPermissionMessage() {
        return permissionMessage;
    }

    public abstract boolean onCommand(S sender, LocationInfo location, String label, String[] args);

}
