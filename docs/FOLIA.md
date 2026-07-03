# Folia support

ConnectorPlugin's Bukkit runtime is marked as Folia-compatible for Lobby1's Folia migration.

## Supported runtime

- Server: Folia or Paper-compatible server matching the Paper API line used at build time.
- Java target: Java 25 bytecode (`maven.compiler.release=25`).
- Bukkit API dependency: `io.papermc.paper:paper-api:26.1.2.build.72-stable`.
- Plugin metadata: `plugin.yml` includes `folia-supported: true`.

`paper-plugin.yml` is not used because this project is still a classic Bukkit plugin with command metadata in `plugin.yml`. The Folia support flag is therefore kept in `plugin.yml`, which Paper/Folia supports for this plugin type.

## Build

Use JDK 25:

```bash
JAVA_HOME=/path/to/jdk-25 mvn -B test
JAVA_HOME=/path/to/jdk-25 mvn -B package
```

The shaded runtime jar is produced at:

```text
target/ConnectorPlugin.jar
```

## Folia changes

- Replaced Spigot API with Paper API.
- Removed PaperLib usage; local teleports now use Paper's native `Player#teleportAsync`.
- Added a Folia scheduler adapter using:
  - `Bukkit.getGlobalRegionScheduler()`
  - `Bukkit.getAsyncScheduler()`
  - `Bukkit.getRegionScheduler()`
  - `Player#getScheduler()`
- Redis and MQTT inbound callbacks no longer execute Bukkit API directly from connector/network threads.
- Player teleports, player command dispatch, player location reads, and plugin-message sends are handed to the owning player scheduler.
- Server-scoped console command dispatch runs on the global scheduler. `p:<player>` server command dispatch runs on that player's scheduler.
- Shared bridge bookkeeping was made concurrency-safe for regionized callback execution.
- A test guard fails the build if legacy Bukkit scheduler APIs are reintroduced under `bukkit/src/main/java`.

## Vault

ConnectorPlugin has no Vault, Economy, or ServicesManager runtime dependency. Vault is not required for Lobby1.

## Known limitations

- Remote arbitrary console commands can call into other plugins. ConnectorPlugin schedules the dispatch context safely, but the command implementation being invoked must also be Folia-compatible.
- Plugin-message transport still requires at least one online player, matching previous behavior.
- Folia compatibility has been compile/test verified locally; final validation must happen on a Folia staging server because region ownership violations are runtime-enforced by Folia.

## Lobby1 deployment

1. Back up the current ConnectorPlugin jar and config.
2. Remove the old ConnectorPlugin jar.
3. Install `target/ConnectorPlugin.jar`.
4. Start Folia Lobby1 with Java 25.
5. Confirm ConnectorPlugin enables without unsupported-plugin or Folia metadata warnings.
6. Run ConnectorPlugin commands and connector flows.
7. Watch console for async/thread violation warnings.
8. Stop and restart Lobby1 to confirm scheduler and connector cleanup.
9. Keep the previous jar/config available for rollback.

## Rollback

Stop Lobby1, replace the new jar with the backed-up ConnectorPlugin jar, restore the backed-up config if needed, and start the server again.
