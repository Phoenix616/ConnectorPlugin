# ConnectorPlugin

Plugin to simplify communication between multiple Minecraft servers in a network (and their proxy).

This includes a bridging utility and some basic commands to use the provided utlity functionality but it is mostly meant to be depended on by other plugins so they can easily query and send data between servers without having to implement that logic themselves.

## Features
- [x] Send arbitrary data to servers and proxies
- [x] Send commands to other servers and proxies
- [ ] Server-side command registration
- [ ] Location/server state querying
- [x] Teleporting
- [ ] Proxy-side Vault/Tresor integration

## Communication Methods

- [x] Plugin Messages
- [ ] peer-to-peer
- [x] redis pub sub
- [ ] RabbitMQ

## Commands

### On the Spigot server

> `/connectorplugin`  
> *Permission:* `connectorplugin.command`  
> *Aliases:* `connector`, `connectorcommand`, `connplugin`, `cp` 
> 
>> `teleport <player> <server> [<world> <x> <y> <z> [<yaw> <pitch>]]`  
>> *Permission:* `connectorplugin.command.teleport`  
>> *Aliases:* `tp`, `send`
>
>> `servercommand <server> <command>...`  
>> *Permission:* `connectorplugin.command.servercommand`  
>> *Aliases:* `serverconsole`, `serverconsolecommand`, `server`, `scc`
>
>> `proxycommand <command>...`  
>> *Permission:* `connectorplugin.command.proxycommand`  
>> *Aliases:* `proxyconsole`, `proxyconsolecommand`, `proxy`, `pcc`
> 
>> `proxyplayercommand <player> <command>...`  
>> *Permission:* `connectorplugin.command.proxyplayercommand`  
>> *Aliases:* `proxyplayer`, `player`, `ppc`

### On the Bungee proxy

> `/connectorpluginbungee`  
> *Permission:* `connectorplugin.command`  
> *Aliases:* `connectorbungee`, `connectorcommandbungee`, `connpluginbungee`, `cpb`  
>
>> `teleport <player> <server> [<world> <x> <y> <z> [<yaw> <pitch>]]`  
>> *Permission:* `connectorplugin.command.teleport`  
>> *Aliases:* `tp`, `send`
> 
>> `servercommand <server> <command>...`  
>> *Permission:* `connectorplugin.command.servercommand`  
>> *Aliases:* `serverconsole`, `serverconsolecommand`, `server`, `scc`
> 
>> `serverplayercommand <player> <command>...`  
>> *Permission:* `connectorplugin.command.serverplayercommand`  
>> *Aliases:* `serverplayer`, `player`, `spc`
> 
>> `proxycommand <command>...`  
>> *Permission:* `connectorplugin.command.proxycommand`  
>> *Aliases:* `proxyconsole`, `proxyconsolecommand`, `proxy`, `pcc`

## Developer Info

### Usage

[Javadocs](https://docs.phoenix616.dev/connectorplugin/)

Check [the wiki](https://wiki.phoenix616.dev/plugin:connectorplugin:usage:start) for usage examples.

### Maven Info

```xml
<repository>
    <id>minebench-repo</id>
    <url>https://repo.minebench.de/</url>
</repository>
```

```xml
<dependency>
    <groupId>de.themoep.connectorplugin</groupId>
    <artifactId>[bukkit|bungee]</artifactId>
    <version>1.3-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

## Download

Latest development builds can be found on the Minebench.de Jenkins: https://ci.minebench.de/job/ConnectorPlugin/

## License

This project is [licensed](LICENSE) under the AGPLv3:

```
 ConnectorPlugin
 Copyright (C) 2020 Max Lee aka Phoenix616 (max@themoep.de)

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published
 by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
