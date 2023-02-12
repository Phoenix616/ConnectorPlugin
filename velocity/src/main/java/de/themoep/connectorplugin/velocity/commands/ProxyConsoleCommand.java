package de.themoep.connectorplugin.velocity.commands;

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

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ProxyConsoleCommand extends SubCommand {

    public ProxyConsoleCommand(ConnectorCommand parent) {
        super(parent.getPlugin(), "proxycommand <command...>", parent.getPermission() + ".proxycommand", "proxyconsole", "proxyconsolecommand", "proxy", "pcc");
    }

    @Override
    public boolean run(CommandSource sender, String alias, String[] args) {
        if (args.length < 1) {
            return false;
        }

        String commandString = String.join(" ", args);
        sender.sendMessage(Component.text("Executing '" + commandString + "' on other proxies").color(NamedTextColor.GRAY));
        plugin.getBridge().runProxyConsoleCommand(commandString, m -> sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(m)))
                .thenAccept(success -> sender.sendMessage(Component.text(success ? "Successfully executed command!" : "Error while executing the command.")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSource sender, String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }
        if (args.length == 0) {
            return new ArrayList<>(plugin.getProxy().getCommandManager().getAliases());
        } else if (args.length == 1) {
            return plugin.getProxy().getCommandManager().getAliases().stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String::compareToIgnoreCase).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
