package de.themoep.connectorplugin.limbo.commands;

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

import com.loohp.limbo.commands.CommandExecutor;
import com.loohp.limbo.commands.CommandSender;
import com.loohp.limbo.commands.TabCompletor;
import de.themoep.connectorplugin.limbo.LimboConnectorPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class SubCommand implements CommandExecutor, TabCompletor {
    
    protected final LimboConnectorPlugin plugin;
    private final String name;
    private final String usage;
    private final String permission;
    private final Collection<String> aliases;
    private final ConnectorCommand parent;
    private Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private Map<String, SubCommand> subCommandAliases = new LinkedHashMap<>();
    private List<String> subCommandPermissions = new ArrayList<>();

    public SubCommand(LimboConnectorPlugin plugin, String usage, String permission, String... aliases) {
        this(plugin, null, usage, permission, aliases);
    }

    public SubCommand(LimboConnectorPlugin plugin, ConnectorCommand parent, String usage, String permission, String... aliases) {
        this.plugin = plugin;
        this.parent = parent;
        String[] usageParts = usage.split(" ", 2);
        this.name = usageParts[0];
        this.usage = usageParts.length > 1 ? usageParts[1] : "";
        this.permission = permission;
        this.aliases = new ArrayList<>();
        this.aliases.add(name);
        Collections.addAll(this.aliases, aliases);
    }

    public void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(Locale.ROOT), subCommand);
        subCommandPermissions.add(subCommand.getPermission());
        for (String alias : subCommand.getAliases()) {
            subCommandAliases.put(alias.toLowerCase(Locale.ROOT), subCommand);
        }
    }

    public SubCommand getSubCommand(String name) {
        SubCommand subCommand = subCommands.get(name.toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            subCommand = subCommandAliases.get(name.toLowerCase(Locale.ROOT));
        }
        return subCommand;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return;
        }
        if (!args[0].equalsIgnoreCase(name) && !aliases.contains(args[0].toLowerCase(Locale.ROOT))) {
            return;
        }

        if (args.length == 1) {
            sender.sendMessage("Usage: /" + getFullUsage());
            return;
        }

        SubCommand subCommand = getSubCommand(args[1]);
        if (subCommand != null) {
            if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
                sender.sendMessage("You do not have permission to use that command!"); // Or some other message
                return;
            }
            subCommand.execute(sender, Arrays.copyOfRange(args, 2, args.length));
            return;
        }
    }

    @Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> tab = new ArrayList<>();
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (sender.hasPermission(entry.getValue().getPermission())) {
                tab.add(entry.getValue().getName());
            }
        }
        if (tab.isEmpty()) {
            return Collections.emptyList();
        }
        if (args.length == 0) {
            tab.clear();
            tab.add(this.getName());
        }
        if (args.length > 0) {
            if (!args[0].equalsIgnoreCase(this.getName())) {
                return Collections.emptyList();
            }
        }
        if (args.length > 1) {
            SubCommand subCommand = getSubCommand(args[1]);
            if (subCommand != null) {
                if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
                    return Collections.emptyList();
                }
                return subCommand.tabComplete(sender, Arrays.copyOfRange(args, 2, args.length));
            }
        }
        return tab;
	}

    public String getName() {
        return name;
    }

    public String getUsage() {
        return name + " " + usage;
    }

    public String getFullUsage() {
        if (parent != null) {
            return parent.getName() + " " + getUsage();
        }
        return getUsage();
    }

    public String getPermission() {
        return permission;
    }

    public Collection<String> getAliases() {
        return aliases;
    }

    public LimboConnectorPlugin getPlugin() {
        return plugin;
    }

    public Map<String, SubCommand> getSubCommands() {
        return subCommands;
    }
}
