package de.themoep.connectorplugin.bukkit.commands;
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

import de.themoep.connectorplugin.bukkit.BukkitConnectorPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class SubCommand implements TabExecutor {
    protected final BukkitConnectorPlugin plugin;
    private final String name;
    private final String usage;
    private final String[] aliases;
    private final String permission;

    private Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private Map<String, SubCommand> subCommandAliases = new LinkedHashMap<>();

    public SubCommand(BukkitConnectorPlugin plugin, String usage, String permission, String... aliases) {
        this.plugin = plugin;
        String[] usageParts = usage.split(" ", 2);
        this.name = usageParts[0];
        this.usage = usageParts.length > 1 ? usageParts[1] : "";
        this.permission = permission;
        this.aliases = aliases;
    }

    public void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(Locale.ROOT), subCommand);
        for (String alias : subCommand.getAliases()) {
            subCommandAliases.put(alias.toLowerCase(Locale.ROOT), subCommand);
        }
    }

    public SubCommand getSubCommand(String name) {
        SubCommand subCommand = subCommands.get(name.toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            return subCommandAliases.get(name.toLowerCase(Locale.ROOT));
        }
        return subCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        SubCommand subCommand = getSubCommand(args[0]);
        if (subCommand != null) {
            if (sender.hasPermission(subCommand.getPermission())) {
                if (subCommand.onCommand(sender, command, label + " " + args[0], Arrays.copyOfRange(args, 1, args.length))) {
                    return true;
                } else if (!subCommand.getUsage().isEmpty()) {
                    sender.sendMessage("Usage: /" + label + " " + args[0] + " " + subCommand.getUsage());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].isEmpty()) {
            return new ArrayList<>(subCommands.keySet());
        }
        SubCommand subCommand = getSubCommand(args[0]);
        if (subCommand != null && sender.hasPermission(subCommand.getPermission())) {
            return subCommand.onTabComplete(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }
        List<String> completions = new ArrayList<>();
        for (Map.Entry<String, SubCommand> e : subCommands.entrySet()) {
            if (e.getKey().startsWith(args[0].toLowerCase(Locale.ROOT)) && sender.hasPermission(e.getValue().getPermission())) {
                completions.add(e.getKey());
            }
        }
        for (Map.Entry<String, SubCommand> e : subCommandAliases.entrySet()) {
            if (e.getKey().startsWith(args[0].toLowerCase(Locale.ROOT)) && sender.hasPermission(e.getValue().getPermission())) {
                completions.add(e.getKey());
            }
        }
        return completions;
    }

    public String getName() {
        return name;
    }

    public String getUsage() {
        return usage;
    }

    public String[] getAliases() {
        return aliases;
    }

    public String getPermission() {
        return permission;
    }

    public BukkitConnectorPlugin getPlugin() {
        return plugin;
    }

    public Map<String, SubCommand> getSubCommands() {
        return subCommands;
    }
}
