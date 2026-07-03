package de.themoep.connectorplugin.bukkit.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public interface ConnectorScheduler {

    void runGlobal(Runnable task);

    void runGlobal(Consumer<ScheduledTask> task);

    void runAsync(Runnable task);

    void runForPlayer(Player player, Runnable task);

    void runForPlayer(Player player, Consumer<ScheduledTask> task);

    void runForPlayer(Player player, Consumer<ScheduledTask> task, Runnable retired);

    void runAtLocation(Location location, Runnable task);

    void runAtLocation(Location location, Consumer<ScheduledTask> task);

    void runLaterForPlayer(Player player, Consumer<ScheduledTask> task, long delayTicks);

    void runTimerGlobal(Consumer<ScheduledTask> task, long delayTicks, long periodTicks);

    void cancelAll();
}
