package io.github.shyyyuki.regionbridge.spigot;

import io.github.shyyyuki.regionbridge.api.Platform;
import io.github.shyyyuki.regionbridge.api.PlatformAdapter;
import io.github.shyyyuki.regionbridge.api.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class SpigotAdapter implements PlatformAdapter {
    protected final Plugin plugin;

    public SpigotAdapter(Plugin plugin) { this.plugin = plugin; }

    @Override public Platform platform() { return Platform.SPIGOT; }
    @Override public boolean isGlobalThread() { return Bukkit.isPrimaryThread(); }
    @Override public boolean isOwned(Location location) { return Bukkit.isPrimaryThread(); }
    @Override public boolean isOwned(Entity entity) { return Bukkit.isPrimaryThread(); }

    @Override public TaskHandle runGlobal(Runnable t, long d, long p) { return schedule(t, d, p, false); }
    @Override public TaskHandle runRegion(Location l, Runnable t, long d, long p) { return schedule(t, d, p, false); }
    @Override public TaskHandle runAsync(Runnable t, long d, long p) { return schedule(t, d, p, true); }

    @Override
    public TaskHandle runEntity(Entity entity, Runnable task, Runnable retired, long delay, long period) {
        AtomicReference<TaskHandle> self = new AtomicReference<>();
        Runnable guarded = () -> {
            if (!entity.isValid()) {
                TaskHandle h = self.get();
                if (h != null) h.cancel();
                if (retired != null) retired.run();
                return;
            }
            task.run();
        };
        TaskHandle handle = schedule(guarded, delay, period, false);
        self.set(handle);
        return handle;
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location target) {
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        runEntity(entity, () -> f.complete(entity.teleport(target)), () -> f.complete(false), 0, 0);
        return f;
    }

    protected TaskHandle schedule(Runnable task, long delay, long period, boolean async) {
        BukkitScheduler s = Bukkit.getScheduler();
        long d = Math.max(delay, 0);
        BukkitTask t;
        if (period > 0) {
            t = async ? s.runTaskTimerAsynchronously(plugin, task, d, period) : s.runTaskTimer(plugin, task, d, period);
        } else if (d > 0) {
            t = async ? s.runTaskLaterAsynchronously(plugin, task, d) : s.runTaskLater(plugin, task, d);
        } else {
            t = async ? s.runTaskAsynchronously(plugin, task) : s.runTask(plugin, task);
        }
        return new Handle(t);
    }

    private record Handle(BukkitTask task) implements TaskHandle {
        @Override public void cancel() { task.cancel(); }
        @Override public boolean isCancelled() { return task.isCancelled(); }
    }
}
