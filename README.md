# ConnectorPlugin

Plugin to simplify communication between multiple Minecraft servers in a network (and their proxy).

This does nothing on its own, it is meant to be depended on by other plugins so they can easily send data between servers without having to implement that logic themselves.

## Communication Methods

- [x] Plugin Messages
- [ ] peer-to-peer
- [ ] redis pub sub
- [ ] RabbitMQ

## Developer Info

### Usage

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
    <version>1.0-SNAPSHOT</version>
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