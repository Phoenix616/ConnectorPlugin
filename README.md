# ConnectorPlugin

Plugin to simplify communication between multiple Minecraft servers in a network (and their proxy). Support Spigot/Paper, BungeeCord and Velocity.

This includes a bridging utility and some basic commands to use the provided utility functionality but it is mostly meant to be depended on by other plugins so they can easily query and send data between servers without having to implement that logic themselves.

## Features
- [x] Send arbitrary data to servers and proxies
- [x] Send commands to other servers and proxies
- [x] Server-side command registration from proxy
- [x] Location querying
- [ ] Server state querying
- [x] Teleporting
- [ ] Proxy-side Vault/Tresor integration

## Communication Methods

- [x] Plugin Messages
- [ ] peer-to-peer
- [x] redis pub sub
- [x] MQTT (E.g. with RabbitMQ)

## Commands

### On the Spigot server

> `/connectorplugin`  
> *Permission:* `connectorplugin.command`  
> *Aliases:* `connector`, `connectorcommand`, `connplugin`, `cp` 
> 
>> `teleport <player> <server> [<world> [<x> <y> <z> [<yaw> <pitch>]]]`  
>> Teleport a player to the specified server/world/location   
>> *Permission:* `connectorplugin.command.teleport`  
>> *Aliases:* `tp`, `send`
> 
>> `teleporttoplayer <player> [<targetplayer>]`  
>> Teleport yourself to the player or the player to the target player   
>> *Permission:* `connectorplugin.command.teleporttoplayer`  
>> *Aliases:* `teleportplayer`, `tpp`
>
>> `servercommand <server> <command>...`  
>> Executes command in the console of the specified server   
>> *Permission:* `connectorplugin.command.servercommand`  
>> *Aliases:* `serverconsole`, `serverconsolecommand`, `server`, `scc`
>
>> `servercommand p:<player> <command>...`  
>> Executes command in the console of the server the player is connected to  
>> *Permission:* `connectorplugin.command.servercommand`  
>> *Aliases:* `serverconsole`, `serverconsolecommand`, `server`, `scc`
>
>> `proxycommand <command>...`  
>> Execute a command on all other proxies  
>> *Permission:* `connectorplugin.command.proxycommand`  
>> *Aliases:* `proxyconsole`, `proxyconsolecommand`, `proxy`, `pcc`
> 
>> `proxyplayercommand <player> <command>...`  
>> Execute a command as a player on the proxy they are connected to  
>> *Permission:* `connectorplugin.command.proxyplayercommand`  
>> *Aliases:* `proxyplayer`, `player`, `ppc`

### On the Proxies

### Main Bungee command
> `/connectorpluginbungee`  
> *Permission:* `connectorplugin.command`  
> *Aliases:* `connectorbungee`, `connectorcommandbungee`, `connpluginbungee`, `cpb`  
>
> 
### Main Velocity command
> `/connectorpluginvelocity`  
> *Permission:* `connectorplugin.command`  
> *Aliases:* `connectorvelocity`, `connectorcommandvelocity`, `connpluginvelocity`, `cpv`  
>

### Proxy sub commands
>> `teleport <player> <server> [<world> [<x> <y> <z> [<yaw> <pitch>]]]`  
>> Teleport a player to the specified server/world/location  
>> *Permission:* `connectorplugin.command.teleport`  
>> *Aliases:* `tp`, `send`
>
>> `teleporttoplayer <player> [<targetplayer>]`  
>> Teleport yourself to the player or the player to the target player   
>> *Permission:* `connectorplugin.command.teleporttoplayer`  
>> *Aliases:* `teleportplayer`, `tpp`
>
>> `servercommand <server> <command>...`  
>> Executes command in the console of the specified server   
>> *Permission:* `connectorplugin.command.servercommand`  
>> *Aliases:* `serverconsole`, `serverconsolecommand`, `server`, `scc`
> 
>> `servercommand p:<player> <command>...`  
>> Executes command in the console of the server the player is connected to  
>> *Permission:* `connectorplugin.command.servercommand`  
>> *Aliases:* `serverconsole`, `serverconsolecommand`, `server`, `scc`  
> 
>> `serverplayercommand <player> <command>...`  
>> Execute a command as the target player on their server  
>> *Permission:* `connectorplugin.command.serverplayercommand`  
>> *Aliases:* `serverplayer`, `player`, `spc`
> 
>> `proxycommand <command>...`  
>> Execute a command on all other proxies  
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
    <artifactId>[bukkit|bungee|velocity]</artifactId>
    <version>1.2-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

## Download

Latest development builds can be found on the Minebench.de Jenkins: https://ci.minebench.de/job/ConnectorPlugin/

## License

This project is [licensed](LICENSE) under the AGPLv3:

```
 ConnectorPlugin
 Copyright (C) 2021 Max Lee aka Phoenix616 (max@themoep.de)

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
