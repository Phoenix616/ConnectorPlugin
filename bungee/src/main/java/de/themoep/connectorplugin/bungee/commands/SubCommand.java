package de.themoep.connectorplugin.bungee.commands;
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

import de.themoep.bungeeplugin.PluginCommand;
import de.themoep.connectorplugin.bungee.BungeeConnectorPlugin;
import net.md_5.bungee.api.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubCommand extends PluginCommand<BungeeConnectorPlugin> {
    private Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private Map<String, SubCommand> subCommandAliases = new LinkedHashMap<>();

    public SubCommand(BungeeConnectorPlugin plugin, String name) {
        super(plugin, name);
    }

    public SubCommand(BungeeConnectorPlugin plugin, String usage, String permission, String... aliases) {
        super(plugin, usage.split(" ", 2)[0], permission, null, null, "/" + usage, aliases);
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
    protected boolean run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        }
        SubCommand subCommand = getSubCommand(args[0]);
        if (subCommand != null) {
            subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        return false;
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            return Collections.emptySet();
        }
        if (args.length == 0 || args[0].isEmpty()) {
            return new ArrayList<>(subCommands.keySet());
        }
        SubCommand subCommand = getSubCommand(args[0]);
        if (subCommand != null && sender.hasPermission(subCommand.getPermission())) {
            return subCommand.onTabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
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

    public Map<String, SubCommand> getSubCommands() {
        return subCommands;
    }
}
