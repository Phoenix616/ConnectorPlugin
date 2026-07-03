package de.themoep.connectorplugin.bukkit.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FoliaConnectorScheduler implements ConnectorScheduler {

    private final Plugin plugin;
    private final Set<ScheduledTask> tasks = ConcurrentHashMap.newKeySet();

    public FoliaConnectorScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        runGlobal(ignored -> task.run());
    }

    @Override
    public void runGlobal(Consumer<ScheduledTask> task) {
        track(Bukkit.getGlobalRegionScheduler().run(plugin, tracked(task)));
    }

    @Override
    public void runAsync(Runnable task) {
        track(Bukkit.getAsyncScheduler().runNow(plugin, tracked(ignored -> task.run())));
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        runForPlayer(player, ignored -> task.run());
    }

    @Override
    public void runForPlayer(Player player, Consumer<ScheduledTask> task) {
        runForPlayer(player, task, null);
    }

    @Override
    public void runForPlayer(Player player, Consumer<ScheduledTask> task, Runnable retired) {
        track(player.getScheduler().run(plugin, tracked(task), retired));
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        runAtLocation(location, ignored -> task.run());
    }

    @Override
    public void runAtLocation(Location location, Consumer<ScheduledTask> task) {
        track(Bukkit.getRegionScheduler().run(plugin, location, tracked(task)));
    }

    @Override
    public void runLaterForPlayer(Player player, Consumer<ScheduledTask> task, long delayTicks) {
        track(player.getScheduler().runDelayed(plugin, tracked(task), null, delayTicks));
    }

    @Override
    public void runTimerGlobal(Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        track(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, tracked(task), delayTicks, periodTicks));
    }

    @Override
    public void cancelAll() {
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    private Consumer<ScheduledTask> tracked(Consumer<ScheduledTask> task) {
        return scheduledTask -> {
            track(scheduledTask);
            try {
                task.accept(scheduledTask);
            } finally {
                if (!scheduledTask.isRepeatingTask()) {
                    tasks.remove(scheduledTask);
                }
            }
        };
    }

    private void track(ScheduledTask task) {
        if (task != null) {
            tasks.add(task);
        }
    }
}
