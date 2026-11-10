package io.github.shyyyuki.regionbridge.folia;

import io.github.shyyyuki.regionbridge.api.Platform;
import io.github.shyyyuki.regionbridge.api.PlatformAdapter;
import io.github.shyyyuki.regionbridge.api.TaskHandle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FoliaAdapter implements PlatformAdapter {
    private static final MethodHandle GLOBAL_TICK_THREAD = findGlobalTickThread();

    private final Plugin plugin;

    private static MethodHandle findGlobalTickThread() {
        MethodType type = MethodType.methodType(boolean.class);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            return lookup.findStatic(Bukkit.class, "isGlobalTickThread", type);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Class<?> server = Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return lookup.findStatic(server, "isGlobalTickThread", type);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public FoliaAdapter(Plugin plugin) { this.plugin = plugin; }

    @Override public Platform platform() { return Platform.FOLIA; }
    @Override
    public boolean isGlobalThread() {
        if (GLOBAL_TICK_THREAD == null) {
            return false;
        }
        try {
            return (boolean) GLOBAL_TICK_THREAD.invoke();
        } catch (Throwable t) {
            return false;
        }
    }
    @Override public boolean isOwned(Location l) { return Bukkit.isOwnedByCurrentRegion(l); }
    @Override public boolean isOwned(Entity e) { return Bukkit.isOwnedByCurrentRegion(e); }

    @Override
    public TaskHandle runGlobal(Runnable task, long delay, long period) {
        var s = Bukkit.getGlobalRegionScheduler();
        if (period > 0) return wrap(s.runAtFixedRate(plugin, t -> task.run(), Math.max(delay, 1), period));
        if (delay > 0) return wrap(s.runDelayed(plugin, t -> task.run(), delay));
        return wrap(s.run(plugin, t -> task.run()));
    }

    @Override
    public TaskHandle runRegion(Location l, Runnable task, long delay, long period) {
        var s = Bukkit.getRegionScheduler();
        if (period > 0) return wrap(s.runAtFixedRate(plugin, l, t -> task.run(), Math.max(delay, 1), period));
        if (delay > 0) return wrap(s.runDelayed(plugin, l, t -> task.run(), delay));
        return wrap(s.run(plugin, l, t -> task.run()));
    }

    @Override
    public TaskHandle runEntity(Entity e, Runnable task, Runnable retired, long delay, long period) {
        var s = e.getScheduler();
        ScheduledTask st;
        if (period > 0) st = s.runAtFixedRate(plugin, t -> task.run(), retired, Math.max(delay, 1), period);
        else if (delay > 0) st = s.runDelayed(plugin, t -> task.run(), retired, delay);
        else st = s.run(plugin, t -> task.run(), retired);
        if (st == null) {
            if (retired != null) retired.run();
            return TaskHandle.COMPLETED;
        }
        return wrap(st);
    }

    @Override
    public TaskHandle runAsync(Runnable task, long delay, long period) {
        var s = Bukkit.getAsyncScheduler();
        if (period > 0) {
            return wrap(s.runAtFixedRate(plugin, t -> task.run(), Math.max(delay, 1) * 50, period * 50, TimeUnit.MILLISECONDS));
        }
        if (delay > 0) return wrap(s.runDelayed(plugin, t -> task.run(), delay * 50, TimeUnit.MILLISECONDS));
        return wrap(s.runNow(plugin, t -> task.run()));
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location target) {
        return entity.teleportAsync(target);
    }

    private static TaskHandle wrap(ScheduledTask st) {
        return new TaskHandle() {
            @Override public void cancel() { st.cancel(); }
            @Override public boolean isCancelled() { return st.isCancelled(); }
        };
    }
}